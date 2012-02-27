
package com.googamaphone.typeandspeak;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.database.Cursor;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.provider.MediaStore.Audio.AudioColumns;
import android.provider.MediaStore.Audio.Media;
import android.provider.MediaStore.MediaColumns;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.Engine;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Toast;

import com.googamaphone.GoogamaphoneActivity;

public class TypeAndSpeak extends GoogamaphoneActivity {
    private static final String TAG = "TypeAndSpeak";

    /** Extra used to enumerate available voices. */
    private static final String EXTRA_AVAILABLE_VOICES = "availableVoices";

    private static final int OPTION_LIBRARY = 1;

    private static final String PREF_LOCALE = "PREF_LOCALE";
    private static final String PREF_PITCH = "PREF_PITCH";
    private static final String PREF_SPEED = "PREF_SPEED";
    private static final String PREF_STATE = "PREF_STATE";
    private static final String PREF_START = "PREF_START";
    private static final String PREF_STOP = "PREF_STOP";

    private static final int STREAM_TYPE = AudioManager.STREAM_MUSIC;

    private static final int DIALOG_INSTALL_DATA = 1;
    private static final int DIALOG_SAVE_FILE = 2;
    private static final int DIALOG_PROPERTIES = 4;
    private static final int DIALOG_PLAYBACK = 5;

    private static final int REQUEST_CHECK_DATA = 1;
    private static final int REQUEST_INSTALL_DATA = 2;

    private String mTtsEngine;
    private TextToSpeech mTts;

    private View mSpeakButton;
    private View mWriteButton;
    private EditText mInputText;
    private Spinner mLanguageSpinner;
    private ProgressDialog mProgressDialog;

    // Recently saved file information.
    private ContentValues mContentValues;
    private Uri mContentUri;

    private boolean mCanceled;
    private boolean mHasSpoken;

    private int mPitch;
    private int mSpeed;

    private final HashMap<String, ContentValues> mContentMap = new HashMap<String, ContentValues>();
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

        mSpeakButton = findViewById(R.id.speak);
        mSpeakButton.setOnClickListener(mOnClickListener);

        mWriteButton = findViewById(R.id.write);
        mWriteButton.setOnClickListener(mOnClickListener);

        findViewById(R.id.clear).setOnClickListener(mOnClickListener);
        findViewById(R.id.prefs).setOnClickListener(mOnClickListener);

        mLanguageSpinner = (Spinner) findViewById(R.id.language_spinner);
        mLanguageSpinner.setOnItemSelectedListener(mOnLangSelected);

        mInputText = (EditText) findViewById(R.id.input_text);

        mHasSpoken = false;

        mTtsEngine = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.TTS_DEFAULT_SYNTH);
        mTts = new TextToSpeech(this, mOnTtsInitListener);

        final Intent intent = getIntent();

        if (Intent.ACTION_SEND.equals(intent.getAction())) {
            restoreState(intent.getExtras(), true);
        } else {
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

            case DIALOG_SAVE_FILE: {
                final EditText editText = new EditText(this);
                editText.setSingleLine();

                final LinearLayout layout = new LinearLayout(this);
                final LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT);
                params.setMargins(10, 0, 10, 0);
                layout.addView(editText, params);

                final DialogInterface.OnClickListener onClick = new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        switch (which) {
                            case DialogInterface.BUTTON_POSITIVE: {
                                final String filename = editText.getText().toString();
                                writeInput(filename);
                                break;
                            }
                        }
                    }
                };

                final AlertDialog dialog = new Builder(this).setMessage(R.string.save_file_message)
                        .setTitle(R.string.save_file_title)
                        .setPositiveButton(android.R.string.ok, onClick)
                        .setNegativeButton(android.R.string.cancel, onClick).setView(layout)
                        .create();

                editText.setOnKeyListener(new View.OnKeyListener() {
                    @Override
                    public boolean onKey(View v, int keyCode, KeyEvent event) {
                        if ((event.getAction() == KeyEvent.ACTION_DOWN)
                                && (keyCode == KeyEvent.KEYCODE_ENTER)) {
                            onClick.onClick(dialog, Dialog.BUTTON_POSITIVE);
                            dialog.dismiss();
                            return true;
                        }

                        return false;
                    }
                });

                return dialog;
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

            case DIALOG_PLAYBACK:
                return new PlaybackDialog(this);
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
            case DIALOG_PLAYBACK:
                final PlaybackDialog playback = (PlaybackDialog) dialog;

                if ((mContentValues != null) && (mContentUri != null)) {
                    try {
                        playback.setFile(mContentValues);
                        playback.setUri(mContentUri);
                    } catch (final IOException e) {
                        e.printStackTrace();
                    }
                }

                mContentValues = null;
                mContentUri = null;
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
        final String text = savedInstanceState.getString(Intent.EXTRA_TEXT);

        if (text != null) {
            mInputText.setText(text);
        }
    }

    /**
     * Inserts media information into the database after a successful save
     * operation.
     * 
     * @param values The media descriptor values.
     */
    private void onSaveCompleted(ContentValues values) {
        final String path = values.getAsString(MediaColumns.DATA);
        final Uri inserted = getContentResolver().insert(Media.getContentUriForPath(path), values);

        mContentValues = values;
        mContentUri = inserted;

        // Clears last queue element to avoid deletion on exit...
        mTts.speak("", TextToSpeech.QUEUE_FLUSH, null);

        try {
            if (mProgressDialog.isShowing()) {
                mProgressDialog.dismiss();
            }
        } catch (final IllegalArgumentException e) {
            e.printStackTrace();
        }

        showDialog(DIALOG_PLAYBACK);
    }

    /**
     * Deletes the partially completed file after a canceled save operation.
     * 
     * @param values The media descriptor values.
     */
    private void onSaveCanceled(ContentValues values) {
        try {
            final String path = values.getAsString(MediaColumns.DATA);
            new File(path).delete();
        } catch (final Exception e) {
            e.printStackTrace();
        }

        final String title = getString(R.string.canceled_title);
        final String message = getString(R.string.canceled_message);
        final AlertDialog alert = new Builder(this).setTitle(title).setMessage(message)
                .setPositiveButton(android.R.string.ok, null).create();

        mTts.stop();

        try {
            alert.show();
        } catch (final RuntimeException e) {
            e.printStackTrace();
        }
    }

    /**
     * Writes the text to file.
     */
    public void write() {
        showDialog(DIALOG_SAVE_FILE);
    }

    /**
     * Clears the text input area.
     */
    public void clear() {
        mInputText.setText("");
    }

    private void onTtsCheck(int resultCode, Intent data) {
        Log.e(TAG, "TTS check returned code " + resultCode);

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
        final Set<Locale> locales = loadTtsLanguages(data);

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

            Log.i(TAG, "Found locale " + locale);

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

    private Set<Locale> loadTtsLanguages(Intent data) {
        if (data == null) {
            Log.e(TAG, "data returned as null");
            return Collections.emptySet();
        }

        final Bundle extras = data.getExtras();

        Object langs = null;

        if (langs == null) {
            langs = getAvailableVoicesICS(extras);
        }

        if (langs == null) {
            langs = getAvailableVoices(extras);
        }

        // Some engines return a String[], which is not an Iterable<?>
        if (langs instanceof String[]) {
            langs = Arrays.asList((String[]) langs);
        }

        // If it's not iterable, fail.
        if (!(langs instanceof Iterable<?>)) {
            Log.e(TAG, "data not iterable");
            return Collections.emptySet();
        }

        final Iterable<?> strLocales = (Iterable<?>) langs;
        final TreeSet<Locale> locales = new TreeSet<Locale>(mLocaleComparator);

        for (final Object strLocale : strLocales) {
            if (strLocale == null) {
                continue;
            }

            final String[] codes = strLocale.toString().split("-");

            if (codes.length == 1) {
                locales.add(new Locale(codes[0]));
            } else if (codes.length == 2) {
                locales.add(new Locale(codes[0], codes[1]));
            } else if (codes.length == 3) {
                locales.add(new Locale(codes[0], codes[1], codes[2]));
            }
        }

        return locales;
    }

    private Object getAvailableVoices(final Bundle extras) {
        final String root = extras.getString(Engine.EXTRA_VOICE_DATA_ROOT_DIRECTORY);
        final Object files = extras.get(Engine.EXTRA_VOICE_DATA_FILES);
        final Object langs = extras.get(Engine.EXTRA_VOICE_DATA_FILES_INFO);

        if ((root == null) || !(files instanceof String[]) || !(langs instanceof String[])) {
            return langs;
        }

        final String[] filesArray = (String[]) files;
        final String[] langsArray = (String[]) langs;
        final List<String> langsList = new LinkedList<String>();

        for (int i = 0; i < filesArray.length; i++) {
            final File file = new File(root, filesArray[i]);

            if (file.canRead()) {
                langsList.add(langsArray[i]);
            }
        }

        return langsList;
    }

    private Object getAvailableVoicesICS(final Bundle extras) {
        return extras.get(EXTRA_AVAILABLE_VOICES);
    }

    private void onTtsInitialized(int status) {
        mTts.setOnUtteranceCompletedListener(mOnUtteranceCompletedListener);

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

    private void writeInput(String filename) {
        mCanceled = false;

        if (filename.toLowerCase().endsWith(".wav")) {
            filename = filename.substring(0, filename.length() - 4);
        }

        filename = filename.trim();

        if (filename.length() <= 0) {
            return;
        }

        final String directory = Environment.getExternalStorageDirectory().getPath()
                + "/media/audio";

        final File outdir = new File(directory);
        final File outfile = new File(directory + "/" + filename + ".wav");

        final String message;
        final AlertDialog alert;

        if (outfile.exists()) {
            message = getString(R.string.exists_message, filename);
            alert = new Builder(this).setTitle(R.string.exists_title).setMessage(message)
                    .setPositiveButton(android.R.string.ok, null).create();
        } else if (!outdir.exists() && !outdir.mkdirs()) {
            message = getString(R.string.no_write_message, filename);
            alert = new Builder(this).setTitle(R.string.no_write_title).setMessage(message)
                    .setPositiveButton(android.R.string.ok, null).create();
        } else {
            // Attempt to set the locale.
            final Locale locale = (Locale) mLanguageSpinner.getSelectedItem();
            if (locale != null) {
                mTts.setLanguage(locale);
            }

            // Populate content values for the media provider.
            final ContentValues values = new ContentValues(10);
            values.put(MediaColumns.DISPLAY_NAME, filename);
            values.put(MediaColumns.TITLE, filename);
            values.put(AudioColumns.ARTIST, getString(R.string.app_name));
            values.put(AudioColumns.ALBUM, getString(R.string.album_name));
            values.put(AudioColumns.IS_ALARM, true);
            values.put(AudioColumns.IS_RINGTONE, true);
            values.put(AudioColumns.IS_NOTIFICATION, true);
            values.put(AudioColumns.IS_MUSIC, true);
            values.put(MediaColumns.MIME_TYPE, "audio/wav");
            values.put(MediaColumns.DATA, outfile.getAbsolutePath());

            final String utteranceId = Integer.toString(values.hashCode());
            mContentMap.put(utteranceId, values);

            final HashMap<String, String> params = new HashMap<String, String>();
            params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId);

            mTts.setPitch(mPitch / 50.0f);
            mTts.setSpeechRate(mSpeed / 50.0f);

            final String text = mInputText.getText().toString();
            mTts.synthesizeToFile(text, params, outfile.getAbsolutePath());

            message = getString(R.string.saving_message, filename);

            mProgressDialog = new ProgressDialog(this);
            mProgressDialog.setCancelable(true);
            mProgressDialog.setTitle(R.string.saving_title);
            mProgressDialog.setMessage(message);
            mProgressDialog.setIndeterminate(true);
            mProgressDialog.setOnCancelListener(mOnCancelListener);

            alert = mProgressDialog;
        }

        try {
            alert.show();
        } catch (final RuntimeException e) {
            e.printStackTrace();
        }
    }

    private void speak() {
        if (!mHasSpoken) {
            mHasSpoken = true;
            Toast.makeText(this, R.string.speaking, 3000).show();
        }

        final String text = mInputText.getText().toString();

        // Attempt to set the locale.
        final Locale locale = (Locale) mLanguageSpinner.getSelectedItem();
        if (locale != null) {
            mTts.setLanguage(locale);
        }

        mTts.setPitch(mPitch / 50.0f);
        mTts.setSpeechRate(mSpeed / 50.0f);
        mTts.speak(text, TextToSpeech.QUEUE_FLUSH, null);
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

    private final TextToSpeech.OnUtteranceCompletedListener mOnUtteranceCompletedListener = new TextToSpeech.OnUtteranceCompletedListener() {
        @Override
        public void onUtteranceCompleted(String utteranceId) {
            final ContentValues values = mContentMap.remove(utteranceId);

            if (values == null) {
                Log.e(TAG, "Missing values for completed utterance id!");
                return;
            }

            if (mCanceled) {
                mHandler.onSaveCanceled(values);
            } else {
                mHandler.onSaveCompleted(values);
            }
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

    private final DialogInterface.OnCancelListener mOnCancelListener = new OnCancelListener() {
        @Override
        public void onCancel(DialogInterface dialog) {
            mCanceled = true;
            mTts.stop();
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
        private static final int MESSAGE_SAVED = 1;
        private static final int MESSAGE_CANCELED = 2;
        private static final int MESSAGE_TTS_INIT = 3;

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_SAVED:
                    TypeAndSpeak.this.onSaveCompleted((ContentValues) msg.obj);
                    break;
                case MESSAGE_CANCELED:
                    TypeAndSpeak.this.onSaveCanceled((ContentValues) msg.obj);
                    break;
                case MESSAGE_TTS_INIT:
                    TypeAndSpeak.this.onTtsInitialized(msg.arg1);
                    break;
            }
        }

        public void onSaveCompleted(ContentValues values) {
            obtainMessage(MESSAGE_SAVED, values).sendToTarget();
        }

        public void onSaveCanceled(ContentValues values) {
            obtainMessage(MESSAGE_CANCELED, values).sendToTarget();
        }

        public void onTtsInitialized(int status) {
            obtainMessage(MESSAGE_TTS_INIT, status, 0).sendToTarget();
        }
    }

    private final Comparator<Locale> mLocaleComparator = new Comparator<Locale>() {
        @Override
        public int compare(Locale lhs, Locale rhs) {
            return lhs.getDisplayName().compareTo(rhs.getDisplayName());
        }
    };
}
