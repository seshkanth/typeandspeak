
package com.googamaphone.typeandspeak;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.TreeSet;

import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore.Audio.Media;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.Engine;
import android.speech.tts.TextToSpeech.OnInitListener;
import android.speech.tts.TextToSpeech.OnUtteranceCompletedListener;
import android.text.Editable;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
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

    private static final String PREF_LANG = "PREF_LANG";
    private static final String PREF_PITCH = "PREF_PITCH";
    private static final String PREF_SPEED = "PREF_SPEED";

    private static final int STREAM_TYPE = AudioManager.STREAM_MUSIC;

    private static final int DIALOG_INSTALL_DATA = 1;
    private static final int DIALOG_SAVE_FILE = 2;
    private static final int DIALOG_HELP = 3;
    private static final int DIALOG_PROPERTIES = 4;

    private static final int REQUEST_CHECK_DATA = 1;
    private static final int REQUEST_INSTALL_DATA = 2;

    private TextToSpeech mTts;

    private View mSpeakButton;
    private View mWriteButton;
    private EditText mInputText;
    private Spinner mLanguageSpinner;
    private LanguageAdapter mLanguageAdapter;
    private ProgressDialog mProgressDialog;

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
        SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        mPitch = prefs.getInt(PREF_PITCH, 50);
        mSpeed = prefs.getInt(PREF_SPEED, 50);

        mSpeakButton = findViewById(R.id.speak);
        mSpeakButton.setOnClickListener(mOnClickListener);

        mWriteButton = findViewById(R.id.write);
        mWriteButton.setOnClickListener(mOnClickListener);

        findViewById(R.id.clear).setOnClickListener(mOnClickListener);
        findViewById(R.id.prefs).setOnClickListener(mOnClickListener);

        mLanguageAdapter = new LanguageAdapter(this, R.layout.language, R.id.text, R.id.image);
        mLanguageAdapter.setDropDownViewResource(R.layout.language_dropdown);

        mLanguageSpinner = (Spinner) findViewById(R.id.language_spinner);
        mLanguageSpinner.setAdapter(mLanguageAdapter);
        mLanguageSpinner.setOnItemSelectedListener(onLangSelected);

        mInputText = (EditText) findViewById(R.id.input_text);
        mInputText.requestFocus();

        mHasSpoken = false;

        OnInitListener onInit = new OnInitListener() {
            @Override
            public void onInit(final int status) {
                mTts.setOnUtteranceCompletedListener(mOnUtteranceCompletedListener);
                
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        onTtsInit(status);
                    }
                });
            }
        };

        mTts = new TextToSpeech(this, onInit);

        final Intent intent = getIntent();

        if (savedInstanceState != null) {
            restoreState(savedInstanceState, false);
        } else if (Intent.ACTION_SEND.equals(intent.getAction())) {
            restoreState(intent.getExtras(), true);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        final String text = mInputText.getText().toString();

        outState.putString(Intent.EXTRA_TEXT, text);
    }

    @Override
    protected void onPause() {
        SharedPreferences prefs = getPreferences(Context.MODE_PRIVATE);
        Editor editor = prefs.edit();
        editor.putInt(PREF_PITCH, mPitch);
        editor.putInt(PREF_SPEED, mSpeed);
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
                DialogInterface.OnClickListener onClick = new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        switch (which) {
                            case DialogInterface.BUTTON_POSITIVE: {
                                Intent intent = new Intent(Engine.ACTION_INSTALL_TTS_DATA);
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

            case DIALOG_HELP: {
                return new Builder(this).setMessage(R.string.help_message)
                        .setTitle(R.string.help_title).setPositiveButton(android.R.string.ok, null)
                        .create();
            }

            case DIALOG_SAVE_FILE: {
                final EditText editText = new EditText(this);
                LinearLayout layout = new LinearLayout(this);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.FILL_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                params.setMargins(10, 0, 10, 0);
                layout.addView(editText, params);

                DialogInterface.OnClickListener onClick = new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        switch (which) {
                            case DialogInterface.BUTTON_POSITIVE: {
                                String filename = editText.getText().toString();
                                writeInput(filename);
                                break;
                            }
                        }
                    }
                };

                return new Builder(this).setMessage(R.string.save_file_message)
                        .setTitle(R.string.save_file_title)
                        .setPositiveButton(android.R.string.ok, onClick)
                        .setNegativeButton(android.R.string.cancel, onClick).setView(layout)
                        .create();
            }

            case DIALOG_PROPERTIES: {
                View properties = LayoutInflater.from(this).inflate(R.layout.properties, null);

                properties.findViewById(R.id.btnPitch).setOnClickListener(mOnClickListener);
                properties.findViewById(R.id.btnSpeed).setOnClickListener(mOnClickListener);
                properties.findViewById(R.id.btnHelp).setOnClickListener(mOnClickListener);
                ((SeekBar) properties.findViewById(R.id.seekPitch))
                        .setOnSeekBarChangeListener(seekListener);
                ((SeekBar) properties.findViewById(R.id.seekSpeed))
                        .setOnSeekBarChangeListener(seekListener);

                return new Builder(this).setView(properties).setTitle(R.string.properties_title)
                        .setPositiveButton(android.R.string.ok, null).create();
            }
        }

        return null;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_CHECK_DATA:
                onTtsCheck(resultCode, data);
                break;
            case REQUEST_INSTALL_DATA:
                onTtsInit(TextToSpeech.SUCCESS);
                break;
        }
    }

    @Override
    protected void onPrepareDialog(int id, Dialog dialog) {
        switch (id) {
            case DIALOG_PROPERTIES:
                ((SeekBar) dialog.findViewById(R.id.seekPitch)).setProgress(mPitch);
                ((SeekBar) dialog.findViewById(R.id.seekSpeed)).setProgress(mSpeed);
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

        /*
        if (fromIntent && text.matches("https?\\://.+?\\..+?")) {
            String content = null;

            try {
                content = fetchContent(text);
            } catch (MalformedURLException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }

            if (content != null) {
                text = content;
            }
        }
        */

        if (text != null) {
            mInputText.setText(text);
        }
    }

    @SuppressWarnings("unused")
    private String fetchContent(String text) throws MalformedURLException, IOException {
        final URL url = new URL(text);
        final StringBuffer buffer = new StringBuffer();
        final InputStreamReader stream = new InputStreamReader(url.openStream());
        final char[] chars = new char[4096];

        for (int length; (length = stream.read(chars)) > 0;) {
            buffer.append(chars, 0, length);
        }

        if (TextUtils.isEmpty(buffer)) {
            return null;
        }

        // TODO(alanv): Perform simple content extraction.
        return buffer.toString();
    }

    /**
     * Inserts media information into the database after a successful save
     * operation.
     * 
     * @param values The media descriptor values.
     */
    private void onSaveCompleted(ContentValues values) {
        String path = values.getAsString(Media.DATA);

        getContentResolver().insert(Media.getContentUriForPath(path), values);

        String title = getString(R.string.saved_title);
        String message = getString(R.string.saved_message, path);
        AlertDialog alert = new Builder(this).setTitle(title).setMessage(message)
                .setPositiveButton(android.R.string.ok, null).create();

        // Clears last queue element to avoid deletion on exit...
        mTts.speak("", TextToSpeech.QUEUE_FLUSH, new HashMap<String, String>());

        try {
            if (mProgressDialog.isShowing()) {
                mProgressDialog.dismiss();
            }
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        }

        try {
            alert.show();
        } catch (RuntimeException e) {
            e.printStackTrace();
        }
    }

    /**
     * Deletes the partially completed file after a canceled save operation.
     * 
     * @param values The media descriptor values.
     */
    private void onSaveCanceled(ContentValues values) {
        try {
            String path = values.getAsString(Media.DATA);
            new File(path).delete();
        } catch (Exception e) {
            e.printStackTrace();
        }

        String title = getString(R.string.canceled_title);
        String message = getString(R.string.canceled_message);
        AlertDialog alert = new Builder(this).setTitle(title).setMessage(message)
                .setPositiveButton(android.R.string.ok, null).create();

        mTts.stop();

        try {
            alert.show();
        } catch (RuntimeException e) {
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
        if (resultCode == Engine.CHECK_VOICE_DATA_PASS && data != null) {
            mSpeakButton.setEnabled(true);
            mWriteButton.setEnabled(true);

            TreeSet<Language> locales = new TreeSet<Language>();

            Bundle extras = data.getExtras();
            Object langs = extras.get(Engine.EXTRA_VOICE_DATA_FILES_INFO);

            // Some engines return a String[], which is not an Iterable<?>
            if (langs instanceof String[]) {
                langs = Arrays.asList((String[]) langs);
            }

            if (langs instanceof Iterable<?>) {
                @SuppressWarnings("unchecked")
                Iterable<String> strLocales = (Iterable<String>) langs;

                for (String strLocale : strLocales) {
                    String[] aryLocale = strLocale.split("-");

                    if (aryLocale.length == 2) {
                        Locale tmpLocale = new Locale(aryLocale[0], aryLocale[1]);
                        Language locale = new Language(tmpLocale);
                        locales.add(locale);
                    }
                }
            }

            for (Language locale : locales) {
                mLanguageAdapter.add(locale);
            }

            View language_panel = findViewById(R.id.language_panel);

            if (mLanguageAdapter.getCount() <= 0) {
                language_panel.setVisibility(View.GONE);
            } else {
                language_panel.setVisibility(View.VISIBLE);
            }

            // Set the selection from preferences, unless something went crazy
            SharedPreferences prefs = getPreferences(Context.MODE_PRIVATE);
            int sel = prefs.getInt(PREF_LANG, 0);
            sel = sel < mLanguageSpinner.getCount() ? sel : 0;
            mLanguageSpinner.setSelection(sel);

            return;
        }

        mSpeakButton.setEnabled(false);
        mWriteButton.setEnabled(false);

        showDialog(DIALOG_INSTALL_DATA);
    }

    private void onTtsInit(int status) {
        switch (status) {
            case TextToSpeech.SUCCESS:
                try {
                    Intent intent = new Intent(Engine.ACTION_CHECK_TTS_DATA);
                    startActivityForResult(intent, REQUEST_CHECK_DATA);
                    break;
                } catch (ActivityNotFoundException e) {
                    e.printStackTrace();
                }
                //$FALL-THROUGH$
            default:
                Toast.makeText(this, R.string.failed_init, 2000).show();
        }
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

        String directory = Environment.getExternalStorageDirectory().getPath() + "/media/audio";

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
            final String text = mInputText.getText().toString();
            final Language wrapLocale = (Language) mLanguageSpinner.getSelectedItem();
            final Locale locale = wrapLocale == null ? Locale.UK : wrapLocale.getLocale();

            final ContentValues values = new ContentValues(3);
            values.put(Media.DISPLAY_NAME, filename);
            values.put(Media.TITLE, filename);
            values.put(Media.ARTIST, getString(R.string.app_name));
            values.put(Media.ALBUM, getString(R.string.album_name));
            values.put(Media.IS_ALARM, true);
            values.put(Media.IS_RINGTONE, true);
            values.put(Media.IS_NOTIFICATION, true);
            values.put(Media.IS_MUSIC, true);
            values.put(Media.MIME_TYPE, "audio/wav");
            values.put(Media.DATA, outfile.getAbsolutePath());

            final String utteranceId = Integer.toString(values.hashCode());
            mContentMap.put(utteranceId, values);

            final HashMap<String, String> params = new HashMap<String, String>();
            params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId);

            mTts.setLanguage(locale);
            mTts.setPitch(mPitch / 50.0f);
            mTts.setSpeechRate(mSpeed / 50.0f);
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
        } catch (RuntimeException e) {
            e.printStackTrace();
        }
    }

    private void speak() {
        if (!mHasSpoken) {
            mHasSpoken = true;
            Toast.makeText(this, R.string.speaking, 3000).show();
        }

        String text = mInputText.getText().toString();
        HashMap<String, String> params = new HashMap<String, String>();
        Language locale = (Language) mLanguageSpinner.getSelectedItem();

        params.put(Engine.KEY_PARAM_STREAM, "" + STREAM_TYPE);

        if (locale != null) {
            mTts.setLanguage(locale.getLocale());
        }

        mTts.setPitch(mPitch / 50.0f);
        mTts.setSpeechRate(mSpeed / 50.0f);
        mTts.speak(text, TextToSpeech.QUEUE_FLUSH, params);
    }

    private void applyTag(String tag) {
        Editable editable = mInputText.getEditableText();

        int start = mInputText.getSelectionStart();
        int end = mInputText.getSelectionEnd();

        // Backwards selection works, too.
        if (start > end) {
            int temp = start;
            start = end;
            end = temp;
        }

        CharSequence selection = editable.subSequence(start, end);
        CharSequence replacement = String.format("<%s level=\"100\">%s</%s>", tag, selection, tag);
        editable.replace(start, end, replacement);
        start += tag.length() + 9;
        mInputText.setSelection(start, start + 3);
    }

    private final SeekBar.OnSeekBarChangeListener seekListener = new SeekBar.OnSeekBarChangeListener() {
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

    private final TextToSpeech.OnUtteranceCompletedListener mOnUtteranceCompletedListener = new OnUtteranceCompletedListener() {
        @Override
        public void onUtteranceCompleted(String utteranceId) {
            final ContentValues values = mContentMap.remove(utteranceId);

            if (values == null) {
                Log.e(TAG, "Missing values for completed utterance id!");
                return;
            } else {
                Log.i(TAG, "Received values for completed utterance id");
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
                case R.id.btnPitch:
                    applyTag("pitch");
                    break;
                case R.id.btnVolume:
                    applyTag("volume");
                    break;
                case R.id.btnSpeed:
                    applyTag("speed");
                    break;
                case R.id.btnHelp:
                    showDialog(DIALOG_HELP);
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

    private final OnItemSelectedListener onLangSelected = new OnItemSelectedListener() {
        @Override
        public void onItemSelected(AdapterView<?> view, View parent, int position, long id) {
            SharedPreferences prefs = getPreferences(Context.MODE_PRIVATE);
            Editor editor = prefs.edit();
            editor.putInt(PREF_LANG, position);
            editor.commit();
        }

        @Override
        public void onNothingSelected(AdapterView<?> arg0) {
            // Do nothing.
        }
    };

    private class TypeAndSpeakHandler extends Handler {
        private static final int MESSAGE_SAVED = 1;
        private static final int MESSAGE_CANCELED = 2;

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_SAVED:
                    TypeAndSpeak.this.onSaveCompleted((ContentValues) msg.obj);
                    break;
                case MESSAGE_CANCELED:
                    TypeAndSpeak.this.onSaveCanceled((ContentValues) msg.obj);
                    break;
            }
        }

        public void onSaveCompleted(ContentValues values) {
            obtainMessage(MESSAGE_SAVED, values).sendToTarget();
        }

        public void onSaveCanceled(ContentValues values) {
            obtainMessage(MESSAGE_CANCELED, values).sendToTarget();
        }
    }
}
