
package com.googamaphone.typeandspeak;

import java.io.File;
import java.io.IOException;

import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore.MediaColumns;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;

public class PlaybackDialog extends AlertDialog {
    private final MediaPlayer mMediaPlayer;
    private final MediaPoller mPoller;
    private final View mContentView;
    private final SeekBar mProgress;
    private final ImageButton mPlayButton;

    private File mSavedFile;

    @SuppressWarnings("unused")
    private ContentValues mContentValues;

    @SuppressWarnings("unused")
    private Uri mContentUri;

    private boolean mAdvanceSeekBar;
    private boolean mMediaPlayerReleased;

    public PlaybackDialog(Context context) {
        super(context);

        mAdvanceSeekBar = true;

        mMediaPlayer = new MediaPlayer();
        mMediaPlayer.setOnCompletionListener(mOnCompletionListener);

        mPoller = new MediaPoller();
        mPoller.startPolling();

        mContentView = LayoutInflater.from(context).inflate(R.layout.playback, null);

        mPlayButton = (ImageButton) mContentView.findViewById(R.id.play);
        mPlayButton.setOnClickListener(mViewClickListener);

        mProgress = (SeekBar) mContentView.findViewById(R.id.progress);
        mProgress.setOnSeekBarChangeListener(mOnSeekBarChangeListener);
        mProgress.setMax(mMediaPlayer.getDuration());

        setTitle(R.string.saved_title);
        setButton(DialogInterface.BUTTON_POSITIVE, context.getString(android.R.string.ok),
                mDialogClickListener);
        setView(mContentView);
    }

    @Override
    public void onStop() {
        mPoller.stopPolling();
        mMediaPlayer.release();

        mMediaPlayerReleased = true;
    }

    public void setFile(ContentValues contentValues) throws IOException {
        if (mMediaPlayerReleased) {
            throw new IOException("Media player was already released!");
        }

        final String path = contentValues.getAsString(MediaColumns.DATA);

        final TextView message = (TextView) mContentView.findViewById(R.id.message);
        message.setText(getContext().getString(R.string.saved_message, path));

        mSavedFile = new File(path);
        mContentValues = contentValues;

        mMediaPlayer.setDataSource(mSavedFile.getAbsolutePath());
        mMediaPlayer.prepare();
    }

    public void setUri(Uri contentUri) {
        mContentUri = contentUri;
    }

    private final MediaPlayer.OnCompletionListener mOnCompletionListener = new MediaPlayer.OnCompletionListener() {
        @Override
        public void onCompletion(MediaPlayer mp) {
            mp.seekTo(0);

            mPlayButton.setImageResource(android.R.drawable.ic_media_play);
            mPoller.stopPolling();
        }
    };

    private final View.OnClickListener mViewClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.play:
                    final ImageButton button = (ImageButton) v;

                    if (mMediaPlayer.isPlaying()) {
                        button.setImageResource(android.R.drawable.ic_media_play);
                        mMediaPlayer.pause();
                        mPoller.stopPolling();
                    } else {
                        button.setImageResource(android.R.drawable.ic_media_pause);
                        mMediaPlayer.start();
                        mPoller.startPolling();
                    }

                    break;
            }
        }
    };

    private final OnSeekBarChangeListener mOnSeekBarChangeListener = new OnSeekBarChangeListener() {
        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
            mAdvanceSeekBar = false;
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            mAdvanceSeekBar = true;
        }

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            if (fromUser) {
                mMediaPlayer.seekTo(progress);
            }
        }
    };

    private final DialogInterface.OnClickListener mDialogClickListener = new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            switch (which) {
                case DialogInterface.BUTTON_POSITIVE:
                    dismiss();
                    break;
            }

            dialog.dismiss();
        }
    };

    private class MediaPoller extends Handler {
        private static final int MSG_CHECK_PROGRESS = 1;

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_CHECK_PROGRESS:
                    if (mMediaPlayer.isPlaying()) {
                        if (mAdvanceSeekBar) {
                            mProgress.setMax(mMediaPlayer.getDuration());
                            mProgress.setProgress(mMediaPlayer.getCurrentPosition());

                            Log.e("playback", "set progress to " + mProgress.getProgress() + " of "
                                    + mProgress.getMax());
                        }

                        startPolling();
                    }

                    break;
            }
        }

        public void stopPolling() {
            removeMessages(MSG_CHECK_PROGRESS);
        }

        public void startPolling() {
            removeMessages(MSG_CHECK_PROGRESS);
            sendEmptyMessageDelayed(MSG_CHECK_PROGRESS, 200);
        }
    }
}
