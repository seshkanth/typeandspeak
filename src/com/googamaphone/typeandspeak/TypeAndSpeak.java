
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
import android.graphics.Color;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.BaseColumns;
import android.provider.MediaStore;
import android.provider.MediaStore.Audio.AlbumColumns;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.Engine;
import android.text.Spannable;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.method.KeyListener;
import android.text.style.BackgroundColorSpan;
import android.text.style.CharacterStyle;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
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
    private static final String TAG = TypeAndSpeak.class.getSimpleName();

    /** Stream to use for TTS output and volume control. */
    private static final int STREAM_TYPE = AudioManager.STREAM_MUSIC;

    // Preference keys.
    private static final String PREF_TEXT = "PREF_TEXT";
    private static final String PREF_LOCALE = "PREF_LOCALE";
    private static final String PREF_PITCH = "PREF_PITCH";
    private static final String PREF_SPEED = "PREF_SPEED";

    // Dialog identifiers.
    private static final int DIALOG_INSTALL_DATA = 1;
    private static final int DIALOG_PROPERTIES = 2;

    // Activity request identifiers.
    private static final int REQUEST_CHECK_DATA = 1;
    private static final int REQUEST_INSTALL_DATA = 2;

    // Sing-along colors.
    private static final CharacterStyle FOREGROUND_SPAN = new ForegroundColorSpan(Color.BLACK);
    private static final CharacterStyle BACKGROUND_SPAN = new BackgroundColorSpan(Color.YELLOW);

    /** Speech parameters. */
    private final HashMap<String, String> mParams = new HashMap<String, String>();

    /** Handler used for transferring TTS callbacks to the main thread. */
    private final TypeAndSpeakHandler mHandler = new TypeAndSpeakHandler();

    /** Default text-to-speech engine. */
    private String mTtsEngine;

    /** Text-to-speech service used for speaking. */
    private TextToSpeech mTts;

    /** Sing-along manager used to iterate through the edit text. */
    private SingAlongTextToSpeech mSingAlongTts;

    /** Synthesizer for writing speech to file. Lazily initialized. */
    private FileSynthesizer mSynth;

    // Interface components.
    private Button mSpeakButton;
    private ViewGroup mSpeakControls;
    private ImageButton mPauseButton;
    private ImageButton mResumeButton;
    private ImageButton mWriteButton;
    private EditText mInputText;
    private Spinner mLanguageSpinner;

    // Speech properties.
    private Locale mLocale;
    private int mLocalePosition;
    private int mPitch;
    private int mSpeed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.main);
        setupUserInterface();

        // Ensure that volume control is appropriate.
        setVolumeControlStream(STREAM_TYPE);

        // Set up text-to-speech.
        final ContentResolver resolver = getContentResolver();
        final TextToSpeech.OnInitListener initListener = new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                mHandler.transferOnTtsInitialized(status);
            }
        };

        mParams.put(Engine.KEY_PARAM_UTTERANCE_ID, TAG);
        mTtsEngine = Settings.Secure.getString(resolver, Settings.Secure.TTS_DEFAULT_SYNTH);
        mTts = new TextToSpeech(this, initListener);
        
        final SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        mInputText.setText(prefs.getString(PREF_TEXT, ""));

        // Load text from intent.
        onNewIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        setIntent(intent);

        if (Intent.ACTION_SEND.equals(intent.getAction())) {
            restoreState(intent.getExtras(), true);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Load saved preferences.
        final SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        final String defaultLocale = Locale.getDefault().toString();
        mLocale = new Locale(prefs.getString(PREF_LOCALE, defaultLocale));
        mPitch = prefs.getInt(PREF_PITCH, 50);
        mSpeed = prefs.getInt(PREF_SPEED, 50);
        
        // Never load the ADD_MORE locale as the default!
        if (LanguageAdapter.LOCALE_ADD_MORE.equals(mLocale)) {
            mLocale = Locale.getDefault();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        // Save preferences.
        final SharedPreferences prefs = getPreferences(Context.MODE_PRIVATE);
        final Editor editor = prefs.edit();
        editor.putInt(PREF_PITCH, mPitch);
        editor.putInt(PREF_SPEED, mSpeed);
        editor.putString(PREF_LOCALE, mLocale.toString());
        editor.putString(PREF_TEXT, mInputText.getText().toString());
        editor.commit();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        mTts.shutdown();
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {
            case DIALOG_INSTALL_DATA: {
                final DialogInterface.OnClickListener clickListener = new DialogInterface.OnClickListener() {
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
                        .setPositiveButton(android.R.string.ok, clickListener)
                        .setNegativeButton(android.R.string.no, null).create();
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

        return super.onCreateDialog(id);
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
            default:
                super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu, menu);

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_library: {
                startAlbumActivity(this);
                return true;
            }
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onPrepareDialog(int id, Dialog dialog) {
        switch (id) {
            case DIALOG_PROPERTIES:
                ((SeekBar) dialog.findViewById(R.id.seekPitch)).setProgress(mPitch);
                ((SeekBar) dialog.findViewById(R.id.seekSpeed)).setProgress(mSpeed);
                break;
            default:
                super.onPrepareDialog(id, dialog);
        }
    }

    /**
     * Loads interface components into variables and sets listeners.
     */
    private void setupUserInterface() {
        mSpeakButton = (Button) findViewById(R.id.speak);
        mSpeakButton.setOnClickListener(mOnClickListener);
        mSpeakButton.setVisibility(View.VISIBLE);

        mSpeakControls = (ViewGroup) findViewById(R.id.play_controls);
        mSpeakControls.setVisibility(View.GONE);

        mSpeakControls.findViewById(R.id.stop).setOnClickListener(mOnClickListener);
        mSpeakControls.findViewById(R.id.rewind).setOnClickListener(mOnClickListener);
        mSpeakControls.findViewById(R.id.fast_forward).setOnClickListener(mOnClickListener);

        mPauseButton = (ImageButton) mSpeakControls.findViewById(R.id.pause);
        mPauseButton.setOnClickListener(mOnClickListener);
        mPauseButton.setVisibility(View.VISIBLE);

        mResumeButton = (ImageButton) mSpeakControls.findViewById(R.id.resume);
        mResumeButton.setOnClickListener(mOnClickListener);
        mResumeButton.setVisibility(View.GONE);

        mWriteButton = (ImageButton) findViewById(R.id.write);
        mWriteButton.setOnClickListener(mOnClickListener);

        findViewById(R.id.clear).setOnClickListener(mOnClickListener);
        findViewById(R.id.prefs).setOnClickListener(mOnClickListener);

        mLanguageSpinner = (Spinner) findViewById(R.id.language_spinner);
        mLanguageSpinner.setOnItemSelectedListener(mOnLangSelected);

        mInputText = (EditText) findViewById(R.id.input_text);
    }

    /**
     * Displays the activity for viewing the Type and Speak album.
     */
    public static void startAlbumActivity(Context context) {
        final String album = context.getString(R.string.album_name);
        final ContentResolver resolver = context.getContentResolver();
        final String[] projection = new String[] {
                BaseColumns._ID, AlbumColumns.ALBUM
        };
        final String selection = AlbumColumns.ALBUM + "=?";
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
            context.startActivity(intent);
        }
    }

    /**
     * Restores a previously saved state.
     *
     * @param savedInstanceState The previously saved state.
     * @boolean fromIntent Whether the state is coming from an intent.
     */
    private void restoreState(Bundle savedInstanceState, boolean fromIntent) {
        String text = savedInstanceState.getString(Intent.EXTRA_TEXT);

        if (text == null) {
            return;
        }

        if (fromIntent && (text.startsWith("http://") || text.startsWith("https://"))) {
            // TODO: Extract full URL from RSS items.
            try {
                final String extracted = ArticleExtractor.getInstance().getText(new URL(text));

                if (TextUtils.isEmpty(extracted)) {
                    text = getString(R.string.failed_extraction, text, text);
                } else {
                    text = extracted;
                }
            } catch (final MalformedURLException e) {
                e.printStackTrace();
            } catch (final BoilerpipeProcessingException e) {
                e.printStackTrace();
            }
        }

        mInputText.setText(text);
    }

    /**
     * Shows the media playback dialog for the given values.
     *
     * @param contentValues The content values for the media.
     * @param contentUri The URI for the media.
     */
    private void showPlaybackDialog(ContentValues contentValues) {
        final PlaybackDialog playback = new PlaybackDialog(this);

        try {
            playback.setFile(contentValues);
            playback.show();
        } catch (final IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Speaks the currrent text aloud.
     */
    private void speak() {
        if (mSingAlongTts == null) {
            mSingAlongTts = new SingAlongTextToSpeech(this, mTts);
            mSingAlongTts.setListener(mSingAlongListener);
        }

        final Locale locale = (Locale) mLanguageSpinner.getSelectedItem();
        if (locale != null) {
            mTts.setLanguage(locale);
        }

        mTts.setPitch(mPitch / 50.0f);
        mTts.setSpeechRate(mSpeed / 50.0f);

        mSingAlongTts.speak(mInputText.getText());
    }

    /**
     * Writes the current text to file.
     */
    private void write() {
        if (mSynth == null) {
            mSynth = new FileSynthesizer(this, mTts);
            mSynth.setListener(new FileSynthesizerListener() {
                @Override
                public void onFileSynthesized(ContentValues contentValues) {
                    showPlaybackDialog(contentValues);
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
    private void clear() {
        mInputText.setText("");
    }

    /**
     * Populates the language adapter with the specified locales. Attempts to
     * set the current selection based on {@link #mLocale}.
     *
     * @param locales The locales to populate.
     */
    private void populateAdapter(Set<Locale> locales) {
        final LanguageAdapter languageAdapter = new LanguageAdapter(this, R.layout.language,
                R.id.text, R.id.image);
        languageAdapter.setDropDownViewResource(R.layout.language_dropdown);
        languageAdapter.clear();

        final String preferredLocale = mLocale.toString();
        int preferredSelection = 0;

        // Add the available locales to the adapter, watching for the preferred
        // locale.
        for (final Locale locale : locales) {
            languageAdapter.add(locale);

            if (locale.toString().equalsIgnoreCase(preferredLocale)) {
                preferredSelection = (languageAdapter.getCount() - 1);
            }
        }

        final View languagePanel = findViewById(R.id.language_panel);

        // Hide the language panel if we didn't find any available locales.
        if (languageAdapter.getCount() <= 0) {
            languagePanel.setVisibility(View.GONE);
        } else {
            languageAdapter.add(LanguageAdapter.LOCALE_ADD_MORE);
            languagePanel.setVisibility(View.VISIBLE);
        }

        // Set up the language spinner.
        mLanguageSpinner.setAdapter(languageAdapter);
        mLanguageSpinner.setSelection(preferredSelection);
    }

    /**
     * Handles the text-to-speech language check callback.
     *
     * @param resultCode The result code.
     * @param data The returned data.
     */
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
            return;
        }

        // Failed to find languages, prompt the user to install voice data.
        mSpeakButton.setEnabled(false);
        mWriteButton.setEnabled(false);
        showDialog(DIALOG_INSTALL_DATA);
    }

    /**
     * Handles the text-to-speech initialization callback.
     *
     * @param status The initialization status.
     */
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
                Toast.makeText(this, R.string.failed_init, Toast.LENGTH_LONG).show();
        }

        mSpeakButton.setEnabled(true);
        mWriteButton.setEnabled(true);
    }

    /**
     * Listens for seek bar changes and updates the pitch and speech rate.
     */
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

    /**
     * Listens for language selection and updates the current locale.
     */
    private final OnItemSelectedListener mOnLangSelected = new OnItemSelectedListener() {
        @Override
        public void onItemSelected(AdapterView<?> view, View parent, int position, long id) {
            switch (view.getId()) {
                case R.id.language_spinner:
                    final Locale selected = (Locale) mLanguageSpinner.getSelectedItem();

                    if (LanguageAdapter.LOCALE_ADD_MORE.equals(selected)) {
                        mLanguageSpinner.setSelection(mLocalePosition);

                        final Intent intent = new Intent(Intent.ACTION_VIEW);
                        intent.setData(Uri.parse("market://search?q=tts&c=apps"));
                        intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY
                                | Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);

                        startActivity(intent);
                        
                        return;
                    }
                    
                    mLocale = selected;
                    mLocalePosition = position;

                    break;
            }
        }

        @Override
        public void onNothingSelected(AdapterView<?> arg0) {
            // Do nothing.
        }
    };

    /**
     * Listens for clicks.
     */
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
                case R.id.stop:
                    mSingAlongTts.stop();
                    break;
                case R.id.pause:
                    mSingAlongTts.pause();
                    mPauseButton.setVisibility(View.GONE);
                    mResumeButton.setVisibility(View.VISIBLE);
                    break;
                case R.id.resume:
                    mSingAlongTts.resume();
                    mResumeButton.setVisibility(View.GONE);
                    mPauseButton.setVisibility(View.VISIBLE);
                    break;
                case R.id.rewind:
                    mSingAlongTts.previous();
                    break;
                case R.id.fast_forward:
                    mSingAlongTts.next();
                    break;
            }
        }
    };

    private final SingAlongListener mSingAlongListener = new SingAlongListener() {
        @Override
        public void onUnitStarted(int id) {
            mSpeakButton.setVisibility(View.GONE);
            mSpeakControls.setVisibility(View.VISIBLE);
            mPauseButton.setVisibility(View.VISIBLE);
            mResumeButton.setVisibility(View.GONE);

            mInputText.setSelection(0, 0);
        }

        @Override
        public void onSegmentStarted(int id, int start, int end) {
            mInputText.setSelection(start, start);

            final Spannable text = mInputText.getText();
            text.setSpan(FOREGROUND_SPAN, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            text.setSpan(BACKGROUND_SPAN, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        @Override
        public void onUnitCompleted(int id) {
            mInputText.setSelection(0, 0);

            final Spannable text = mInputText.getText();
            text.removeSpan(FOREGROUND_SPAN);
            text.removeSpan(BACKGROUND_SPAN);

            mSpeakControls.setVisibility(View.GONE);
            mSpeakButton.setVisibility(View.VISIBLE);
        }
    };

    /**
     * Transfers callbacks to the main thread.
     */
    private class TypeAndSpeakHandler extends Handler {
        private static final int TTS_INITIALIZED = 1;

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case TTS_INITIALIZED:
                    onTtsInitialized(msg.arg1);
                    break;
            }
        }

        public void transferOnTtsInitialized(int status) {
            obtainMessage(TTS_INITIALIZED, status, 0).sendToTarget();
        }
    }
}
