
package com.googamaphone.typeandspeak;

import java.text.BreakIterator;
import java.util.HashMap;

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.Engine;
import android.speech.tts.TextToSpeech.OnUtteranceCompletedListener;

public class SingAlongTextToSpeech {
    private static final int UTTERANCE_COMPLETED = 1;
    private static final int RESUME_SPEAKING = 2;

    private final TextToSpeech mTts;
    private final HashMap<String, String> mParams;
    private final BreakIterator mBreakIterator;

    private SingAlongListener mListener = null;
    private CharSequence mCurrentUnit = null;
    private int mCurrentId = 0;
    private int mSegmentEnd = 0;
    private int mSegmentStart = 0;
    private boolean mIsPaused = false;

    /**
     * Flag that lets the utterance completion listener know whether to advance
     * automatically. Automatically resets after each completed utterance.
     */
    private boolean mBypassAdvance = false;

    public SingAlongTextToSpeech(Context context, TextToSpeech tts) {
        mTts = tts;

        mParams = new HashMap<String, String>();
        mParams.put(Engine.KEY_PARAM_UTTERANCE_ID, "SingAlongTTS");

        mBreakIterator = BreakIterator.getSentenceInstance();
    }

    public void setListener(SingAlongListener listener) {
        mListener = listener;
    }

    public TextToSpeech getTextToSpeech() {
        return mTts;
    }

    public void speak(CharSequence text) {
        mIsPaused = true;

        // TODO: Handle current speech completion.
        mTts.stop();
        mTts.setOnUtteranceCompletedListener(mOnUtteranceCompletedListener);

        mCurrentId++;
        mCurrentUnit = text;
        mBreakIterator.setText(new CharSequenceIterator(mCurrentUnit));

        if (mListener != null) {
            mListener.onUnitStarted(mCurrentId);
        }

        mIsPaused = false;

        onUtteranceCompleted(null);
    }

    public void pause() {
        mIsPaused = true;
        mTts.stop();
    }

    public void resume() {
        mIsPaused = false;
        speakCurrentSegment();
    }

    public void next() {
        advanceBreakIterator(1);
        mBypassAdvance = !mIsPaused;
        mTts.stop();
    }

    public void previous() {
        advanceBreakIterator(-1);
        mBypassAdvance = !mIsPaused;
        mTts.stop();
    }

    public void stop() {
        mIsPaused = true;

        mTts.stop();
        mTts.setOnUtteranceCompletedListener(null);

        if (mListener != null) {
            mListener.onUnitCompleted(mCurrentId);
        }

        mCurrentId = -1;
        mCurrentUnit = null;
        mSegmentStart = 0;
        mSegmentEnd = 0;
    }

    private boolean advanceBreakIterator(int steps) {
        while ((steps < 0) && (mSegmentStart > 0)) {
            mSegmentEnd = mSegmentStart;
            mBreakIterator.preceding(mSegmentEnd);
            mSegmentStart = mBreakIterator.current();

            steps++;
        }

        while ((steps > 0) && (mSegmentEnd < mCurrentUnit.length())) {
            mSegmentStart = mSegmentEnd;
            mBreakIterator.following(mSegmentStart);
            mSegmentEnd = mBreakIterator.current();

            steps--;
        }

        if (mListener != null) {
            mListener.onSegmentStarted(mCurrentId, mSegmentStart, mSegmentEnd);
        }

        return (steps == 0);
    }

    private void onUtteranceCompleted(String utteranceId) {
        if (mCurrentUnit == null) {
            // Shouldn't be speaking now.
            return;
        }

        if (isUnitCompleted()) {
            // TODO(alanv): Add support for more than one unit.
            stop();
            return;
        }

        if (mIsPaused) {
            // Don't move to the next segment if paused.
            return;
        }

        if (mBypassAdvance) {
            mBypassAdvance = false;
        } else {
            advanceBreakIterator(1);
        }

        speakCurrentSegment();
    }

    private void speakCurrentSegment() {
        final CharSequence text = mCurrentUnit.subSequence(mSegmentStart, mSegmentEnd);
        mTts.speak(text.toString(), TextToSpeech.QUEUE_FLUSH, mParams);
    }

    private boolean isUnitCompleted() {
        return mSegmentEnd >= mCurrentUnit.length();
    }

    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case UTTERANCE_COMPLETED:
                    onUtteranceCompleted((String) msg.obj);
                    break;
                case RESUME_SPEAKING:
                    resume();
                    break;
            }
        }
    };

    private final OnUtteranceCompletedListener mOnUtteranceCompletedListener = new OnUtteranceCompletedListener() {
        @Override
        public void onUtteranceCompleted(String utteranceId) {
            mHandler.obtainMessage(UTTERANCE_COMPLETED, utteranceId).sendToTarget();
        }
    };

    public interface SingAlongListener {
        public void onUnitStarted(int id);

        public void onSegmentStarted(int id, int start, int end);

        public void onUnitCompleted(int id);
    }
}
