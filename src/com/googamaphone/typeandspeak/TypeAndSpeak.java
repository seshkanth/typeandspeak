
package com.googamaphone.typeandspeak;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Locale;
import java.util.Set;

import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.database.Cursor;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.Engine;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Toast;

import com.googamaphone.GoogamaphoneActivity;
import com.googamaphone.typeandspeak.FileSynthesizer.FileSynthesizerListener;
import com.googamaphone.typeandspeak.SingAlongTextToSpeech.SingAlongListener;

import de.l3s.boilerpipe.BoilerpipeProcessingException;
import de.l3s.boilerpipe.extractors.ArticleExtractor;

public class TypeAndSpeak extends GoogamaphoneActivity {
    static final String TAG = "TypeAndSpeak";

    /** Extra used to enumerate available voices. */
    static final String EXTRA_AVAILABLE_VOICES = "availableVoices";

    private static final int OPTION_LIBRARY = 1;

    private static final String PREF_LOCALE = "PREF_LOCALE";
    private static final String PREF_PITCH = "PREF_PITCH";
    private static final String PREF_SPEED = "PREF_SPEED";
    private static final String PREF_STATE = "PREF_STATE";
    private static final String PREF_START = "PREF_START";
    private static final String PREF_STOP = "PREF_STOP";

    private static final int STREAM_TYPE = AudioManager.STREAM_MUSIC;

    private static final int DIALOG_INSTALL_DATA = 1;
    private static final int DIALOG_PROPERTIES = 4;

    private static final int REQUEST_CHECK_DATA = 1;
    private static final int REQUEST_INSTALL_DATA = 2;

    private String mTtsEngine;
    private TextToSpeech mTts;
    private SingAlongTextToSpeech mSingAlongTts;

    /** Synthesizer for writing speech to file. Lazily initialized. */
    private FileSynthesizer mSynth;

    private Button mSpeakButton;
    private ImageButton mWriteButton;
    private EditText mInputText;
    private Spinner mLanguageSpinner;

    private boolean mHasSpoken;

    private int mPitch;
    private int mSpeed;

    private final HashMap<String, String> mParams = new HashMap<String, String>();
    private final TypeAndSpeakHandler mHandler = new TypeAndSpeakHandler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.main);

        // Ensure that volume control is appropriate
        setVolumeControlStream(STREAM_TYPE);

        // Load saved preferences
        final SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        mPitch = prefs.getInt(PREF_PITCH, 50);
        mSpeed = prefs.getInt(PREF_SPEED, 50);

        mSpeakButton = (Button) findViewById(R.id.speak);
        mSpeakButton.setOnClickListener(mOnClickListener);

        mWriteButton = (ImageButton) findViewById(R.id.write);
        mWriteButton.setOnClickListener(mOnClickListener);

        findViewById(R.id.clear).setOnClickListener(mOnClickListener);
        findViewById(R.id.prefs).setOnClickListener(mOnClickListener);

        mLanguageSpinner = (Spinner) findViewById(R.id.language_spinner);
        mLanguageSpinner.setOnItemSelectedListener(mOnLangSelected);

        mInputText = (EditText) findViewById(R.id.input_text);

        mParams.put(Engine.KEY_PARAM_UTTERANCE_ID, TAG);

        mHasSpoken = false;

        mTtsEngine = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.TTS_DEFAULT_SYNTH);

        mTts = new TextToSpeech(this, mOnTtsInitListener);

        final Intent intent = getIntent();

        if (Intent.ACTION_SEND.equals(intent.getAction())) {
            restoreState(intent.getExtras(), true);
        } else {
            // TODO: Shouldn't this be stored in a savedInstanceState?
            mInputText.setText(prefs.getString(PREF_STATE, ""));
            mInputText.setSelection(prefs.getInt(PREF_START, 0), prefs.getInt(PREF_STOP, 0));
        }
    }

    @Override
    protected void onPause() {
        final SharedPreferences prefs = getPreferences(Context.MODE_PRIVATE);
        final Editor editor = prefs.edit();
        editor.putInt(PREF_PITCH, mPitch);
        editor.putInt(PREF_SPEED, mSpeed);
        editor.putInt(PREF_START, mInputText.getSelectionStart());
        editor.putInt(PREF_STOP, mInputText.getSelectionEnd());
        editor.putString(PREF_STATE, mInputText.getText().toString());
        editor.commit();

        super.onPause();
    }

    @Override
    protected void onDestroy() {
        mTts.shutdown();

        super.onDestroy();
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {
            case DIALOG_INSTALL_DATA: {
                final DialogInterface.OnClickListener onClick = new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        switch (which) {
                            case DialogInterface.BUTTON_POSITIVE: {
                                final Intent intent = new Intent(Engine.ACTION_INSTALL_TTS_DATA);
                                intent.setPackage(mTtsEngine);
                                startActivityForResult(intent, REQUEST_INSTALL_DATA);
                                break;
                            }
                        }
                    }
                };

                return new Builder(this).setMessage(R.string.install_data_message)
                        .setTitle(R.string.install_data_title)
                        .setPositiveButton(android.R.string.ok, onClick)
                        .setNegativeButton(android.R.string.no, onClick).create();
            }

            case DIALOG_PROPERTIES: {
                final View properties = LayoutInflater.from(this)
                        .inflate(R.layout.properties, null);

                ((SeekBar) properties.findViewById(R.id.seekPitch))
                        .setOnSeekBarChangeListener(mSeekListener);
                ((SeekBar) properties.findViewById(R.id.seekSpeed))
                        .setOnSeekBarChangeListener(mSeekListener);

                return new Builder(this).setView(properties).setTitle(R.string.properties_title)
                        .setPositiveButton(android.R.string.ok, null).create();
            }
        }

        return null;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(Menu.NONE, OPTION_LIBRARY, Menu.NONE, R.string.library)
                .setIcon(android.R.drawable.ic_menu_view).setAlphabeticShortcut('l');

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case OPTION_LIBRARY: {
                startAlbumActivity();
                return true;
            }
        }

        return super.onOptionsItemSelected(item);
    }

    private void startAlbumActivity() {
        final String album = getString(R.string.album_name);
        final ContentResolver resolver = getContentResolver();
        final String[] projection = new String[] {
                MediaStore.Audio.Albums._ID, MediaStore.Audio.Albums.ALBUM
        };
        final String selection = MediaStore.Audio.Albums.ALBUM + "=?";
        final String[] args = new String[] {
            album
        };
        final Cursor albums = resolver.query(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
                projection, selection, args, null);

        if (albums.moveToFirst()) {
            final int albumId = albums.getInt(0);
            final Intent intent = new Intent(Intent.ACTION_PICK);
            intent.setDataAndType(Uri.EMPTY, "vnd.android.cursor.dir/track");
            intent.putExtra("album", albumId);
            startActivity(intent);
        }
    }

    @Override
    public void onPrepareDialog(int id, Dialog dialog) {
        switch (id) {
            case DIALOG_PROPERTIES:
                ((SeekBar) dialog.findViewById(R.id.seekPitch)).setProgress(mPitch);
                ((SeekBar) dialog.findViewById(R.id.seekSpeed)).setProgress(mSpeed);
                break;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_CHECK_DATA:
                onTtsCheck(resultCode, data);
                break;
            case REQUEST_INSTALL_DATA:
                onTtsInitialized(TextToSpeech.SUCCESS);
                break;
        }
    }

    /**
     * Restores a previously saved state.
     * 
     * @param savedInstanceState The previously saved state.
     */
    private void restoreState(Bundle savedInstanceState, boolean fromIntent) {
        String text = savedInstanceState.getString(Intent.EXTRA_TEXT);

        if (text == null) {
            return;
        }

        if (fromIntent && (text.startsWith("http://") || text.startsWith("https://"))) {
            // TODO: Extract full URL from RSS items.
            try {
                text = ArticleExtractor.getInstance().getText(new URL(text));
            } catch (MalformedURLException e) {
                e.printStackTrace();
            } catch (BoilerpipeProcessingException e) {
                e.printStackTrace();
            }
        }

        mInputText.setText(text);
    }

    private void showPlaybackDialog(ContentValues contentValues, Uri contentUri) {
        final PlaybackDialog playback = new PlaybackDialog(this);

        try {
            playback.setFile(contentValues);
            playback.setUri(contentUri);
            playback.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Speaks the text aloud.
     */
    private void speak() {
        if (mSingAlongTts == null) {
            mSingAlongTts = new SingAlongTextToSpeech(this, mTts, mInputText);
            mSingAlongTts.setListener(new SingAlongListener() {
                @Override
                public void onSpeechStarted() {
                    mSpeakButton.setText(R.string.stop_speaking);
                }
                
                @Override
                public void onSpeechCompleted() {
                    mSpeakButton.setText(R.string.speak);
                }
            });
        }
        
        if (mSingAlongTts.isSpeaking()) {
            mSingAlongTts.stop();
            return;
        }
        
        if (!mHasSpoken) {
            mHasSpoken = true;
            Toast.makeText(this, R.string.speaking, 3000).show();
        }

        final TextToSpeech tts = mSingAlongTts.getTextToSpeech();
        final Locale locale = (Locale) mLanguageSpinner.getSelectedItem();
        if (locale != null) {
            tts.setLanguage(locale);
        }

        tts.setPitch(mPitch / 50.0f);
        tts.setSpeechRate(mSpeed / 50.0f);

        mSingAlongTts.speak();
    }

    /**
     * Writes the text to file.
     */
    public void write() {
        if (mSynth == null) {
            mSynth = new FileSynthesizer(this, mTts);
            mSynth.setListener(new FileSynthesizerListener() {
                @Override
                public void onFileSynthesized(ContentValues contentValues, Uri contentUri) {
                    showPlaybackDialog(contentValues, contentUri);
                }
            });
        }
        
        final String text = mInputText.getText().toString();
        final Locale locale = (Locale) mLanguageSpinner.getSelectedItem();
        final int pitch = mPitch;
        final int rate = mSpeed;
        
        mSynth.synthesize(text, locale, pitch, rate);
    }

    /**
     * Clears the text input area.
     */
    public void clear() {
        mInputText.setText("");
    }

    private void onTtsCheck(int resultCode, Intent data) {
        // If data is null, always prompt the user to install voice data.
        if (data == null) {
            mSpeakButton.setEnabled(false);
            mWriteButton.setEnabled(false);
            showDialog(DIALOG_INSTALL_DATA);
            return;
        }

        mSpeakButton.setEnabled(true);
        mWriteButton.setEnabled(true);

        final boolean passed = (resultCode == TextToSpeech.Engine.CHECK_VOICE_DATA_PASS);
        final Set<Locale> locales = TextToSpeechUtils.loadTtsLanguages(data);

        if (!locales.isEmpty() || passed) {
            mSpeakButton.setEnabled(true);
            mWriteButton.setEnabled(true);
            populateAdapter(locales);
        } else {
            mSpeakButton.setEnabled(false);
            mWriteButton.setEnabled(false);
            showDialog(DIALOG_INSTALL_DATA);
        }
    }

    private void populateAdapter(Set<Locale> locales) {
        // Attempt to load the preferred locale from preferences.
        final SharedPreferences prefs = getPreferences(Context.MODE_PRIVATE);
        final String preferredLocale = prefs.getString(PREF_LOCALE, Locale.getDefault().toString());

        final LanguageAdapter languageAdapter = new LanguageAdapter(this, R.layout.language,
                R.id.text, R.id.image);
        languageAdapter.setDropDownViewResource(R.layout.language_dropdown);
        languageAdapter.clear();

        int preferredSelection = 0;

        // Add the available locales to the adapter, watching for the preferred
        // locale.
        for (final Locale locale : locales) {
            languageAdapter.add(locale);

            if (locale.toString().equals(preferredLocale)) {
                preferredSelection = (languageAdapter.getCount() - 1);
            }
        }

        final View languagePanel = findViewById(R.id.language_panel);

        // Hide the language panel if we didn't find any available locales.
        if (languageAdapter.getCount() <= 0) {
            languagePanel.setVisibility(View.GONE);
        } else {
            languagePanel.setVisibility(View.VISIBLE);
        }

        // Set up the language spinner.
        mLanguageSpinner.setAdapter(languageAdapter);
        mLanguageSpinner.setSelection(preferredSelection);
    }
    
    private void onTtsInitialized(int status) {
        switch (status) {
            case TextToSpeech.SUCCESS:
                try {
                    final Intent intent = new Intent(Engine.ACTION_CHECK_TTS_DATA);
                    intent.setPackage(mTtsEngine);
                    startActivityForResult(intent, REQUEST_CHECK_DATA);
                    break;
                } catch (final ActivityNotFoundException e) {
                    e.printStackTrace();
                }
                //$FALL-THROUGH$
            default:
                Toast.makeText(this, R.string.failed_init, 2000).show();
        }

        mSpeakButton.setEnabled(true);
        mWriteButton.setEnabled(true);
    }

    private final SeekBar.OnSeekBarChangeListener mSeekListener = new SeekBar.OnSeekBarChangeListener() {
        @Override
        public void onStopTrackingTouch(SeekBar v) {
            // Do nothing.
        }

        @Override
        public void onStartTrackingTouch(SeekBar v) {
            // Do nothing.
        }

        @Override
        public void onProgressChanged(SeekBar v, int progress, boolean fromUser) {
            if (!fromUser) {
                return;
            }

            switch (v.getId()) {
                case R.id.seekPitch:
                    mPitch = progress;
                    break;
                case R.id.seekSpeed:
                    mSpeed = progress;
                    break;
            }
        }
    };

    private final TextToSpeech.OnInitListener mOnTtsInitListener = new TextToSpeech.OnInitListener() {
        @Override
        public void onInit(int status) {
            mHandler.onTtsInitialized(status);
        }
    };

    private final View.OnClickListener mOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            switch (view.getId()) {
                case R.id.prefs:
                    showDialog(DIALOG_PROPERTIES);
                    break;
                case R.id.clear:
                    clear();
                    break;
                case R.id.speak:
                    speak();
                    break;
                case R.id.write:
                    write();
                    break;
            }
        }
    };

    private final OnItemSelectedListener mOnLangSelected = new OnItemSelectedListener() {
        @Override
        public void onItemSelected(AdapterView<?> view, View parent, int position, long id) {
            final Locale selectedLocale = (Locale) mLanguageSpinner.getSelectedItem();
            final SharedPreferences prefs = getPreferences(Context.MODE_PRIVATE);
            final Editor editor = prefs.edit();

            editor.putString(PREF_LOCALE, selectedLocale.toString());
            editor.commit();
        }

        @Override
        public void onNothingSelected(AdapterView<?> arg0) {
            // Do nothing.
        }
    };

    // TODO(alanv): Is this necessary?
    private class TypeAndSpeakHandler extends Handler {
        private static final int MESSAGE_TTS_INIT = 3;

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_TTS_INIT:
                    TypeAndSpeak.this.onTtsInitialized(msg.arg1);
                    break;
            }
        }

        public void onTtsInitialized(int status) {
            obtainMessage(MESSAGE_TTS_INIT, status, 0).sendToTarget();
        }
    }
}
