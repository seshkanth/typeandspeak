
package com.googamaphone.typeandspeak.utils;

import java.text.BreakIterator;
import java.util.HashMap;
import java.util.Locale;


import android.content.Context;
import android.os.Message;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.Engine;
import android.speech.tts.TextToSpeech.OnUtteranceCompletedListener;

/**
 * A wrapper class for {@link TextToSpeech} that adds support for reading at a
 * given granularity level using a {@link BreakIterator}.
 */
public class GranularTextToSpeech {
    private static final int UTTERANCE_COMPLETED = 1;
    private static final int RESUME_SPEAKING = 2;

    private final TextToSpeech mTts;
    private final HashMap<String, String> mParams;

    private BreakIterator mBreakIterator;
    private SingAlongListener mListener = null;
    private CharSequence mCurrentSequence = null;

    private int mSequenceId = 0;
    private int mUnitEnd = 0;
    private int mUnitStart = 0;

    private boolean mIsPaused = false;

    /**
     * Flag that lets the utterance completion listener know whether to advance
     * automatically. Automatically resets after each completed utterance.
     */
    private boolean mBypassAdvance = false;

    public GranularTextToSpeech(Context context, TextToSpeech tts, Locale defaultLocale) {
        mTts = tts;

        mParams = new HashMap<String, String>();
        mParams.put(Engine.KEY_PARAM_UTTERANCE_ID, "SingAlongTTS");

        if (defaultLocale != null) {
            mBreakIterator = BreakIterator.getSentenceInstance(defaultLocale);
        } else {
            mBreakIterator = BreakIterator.getSentenceInstance(Locale.US);
        }
    }

    public void setListener(SingAlongListener listener) {
        mListener = listener;
    }

    public TextToSpeech getTextToSpeech() {
        return mTts;
    }

    public void setLocale(Locale locale) {
        mBreakIterator = BreakIterator.getSentenceInstance(locale);
    }

    @SuppressWarnings("deprecation")
    public void speak(CharSequence text) {
        mIsPaused = true;

        mTts.stop();
        mTts.setOnUtteranceCompletedListener(mOnUtteranceCompletedListener);

        mSequenceId++;
        mCurrentSequence = text;
        mBreakIterator.setText(new CharSequenceIterator(mCurrentSequence));

        if (mListener != null) {
            mListener.onSequenceStarted(mSequenceId);
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
        speakCurrentUnit();
    }

    public void next() {
        nextInternal();
        mBypassAdvance = !mIsPaused;
        mTts.stop();
    }

    public void previous() {
        previousInternal();
        mBypassAdvance = !mIsPaused;
        mTts.stop();
    }

    @SuppressWarnings("deprecation")
    public void stop() {
        mIsPaused = true;

        mTts.stop();
        mTts.setOnUtteranceCompletedListener(null);

        if (mListener != null) {
            mListener.onSequenceCompleted(mSequenceId);
        }

        mSequenceId = -1;
        mCurrentSequence = null;
        mUnitStart = 0;
        mUnitEnd = 0;
    }

    /**
     * Move the break iterator forward by one unit. If the cursor is in the
     * middle of a unit, it will move to the next unit.
     * 
     * @return {@code true} if the iterator moved forward or {@code false} if it
     *         already at the last unit.
     */
    private boolean nextInternal() {
        if (mUnitStart >= mCurrentSequence.length()) {
            // This happens if the current sequence changes without resetting
            // the iterator.
            return false;
        }

        final int result = mBreakIterator.following(mUnitStart);

        if (result == BreakIterator.DONE) {
            return false;
        }

        mUnitStart = mUnitEnd;
        mUnitEnd = mBreakIterator.current();

        if (mListener != null) {
            mListener.onUnitSelected(mSequenceId, mUnitStart, mUnitEnd);
        }

        return true;
    }

    /**
     * Move the break iterator backward by one unit. If the cursor is in the
     * middle of a unit, it will move to the beginning of the unit.
     * 
     * @return {@code true} if the iterator moved backward or {@code false} if
     *         it already at the first unit.
     */
    private boolean previousInternal() {
        if (mUnitEnd > mCurrentSequence.length()) {
            // This happens if the current sequence changes without resetting
            // the iterator.
            return false;
        }

        final int result = mBreakIterator.preceding(mUnitEnd);

        if (result == BreakIterator.DONE) {
            return false;
        }

        mUnitEnd = mUnitStart;
        mUnitStart = mBreakIterator.current();

        if (mListener != null) {
            mListener.onUnitSelected(mSequenceId, mUnitStart, mUnitEnd);
        }

        return true;
    }

    private void onUtteranceCompleted(String utteranceId) {
        if (mCurrentSequence == null) {
            // Shouldn't be speaking now.
            return;
        }

        if (isSequenceCompleted()) {
            // TODO(alanv): Add support for more than one sequence.
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
            nextInternal();
        }

        speakCurrentUnit();
    }

    private void speakCurrentUnit() {
        final CharSequence text = mCurrentSequence.subSequence(mUnitStart, mUnitEnd);
        mTts.speak(text.toString(), TextToSpeech.QUEUE_FLUSH, mParams);
    }

    private boolean isSequenceCompleted() {
        // TODO: Does this always work? What if the sequence ends with
        // whitespace?
        return mUnitEnd >= mCurrentSequence.length();
    }

    private final SingAlongHandler mHandler = new SingAlongHandler(this);

    private final OnUtteranceCompletedListener mOnUtteranceCompletedListener = new OnUtteranceCompletedListener() {
        @Override
        public void onUtteranceCompleted(String utteranceId) {
            mHandler.obtainMessage(UTTERANCE_COMPLETED, utteranceId).sendToTarget();
        }
    };

    private static class SingAlongHandler extends ReferencedHandler<GranularTextToSpeech> {
        public SingAlongHandler(GranularTextToSpeech parent) {
            super(parent);
        }

        @Override
        protected void handleMessage(Message msg, GranularTextToSpeech parent) {
            switch (msg.what) {
                case UTTERANCE_COMPLETED:
                    parent.onUtteranceCompleted((String) msg.obj);
                    break;
                case RESUME_SPEAKING:
                    parent.resume();
                    break;
            }
        }
    };

    public interface SingAlongListener {
        public void onSequenceStarted(int id);

        public void onUnitSelected(int id, int start, int end);

        public void onSequenceCompleted(int id);
    }
}
