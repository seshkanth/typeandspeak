
package com.googamaphone.typeandspeak.utils;

import java.text.BreakIterator;
import java.util.HashMap;
import java.util.Locale;

import android.content.Context;
import android.os.Message;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.Engine;
import android.speech.tts.TextToSpeech.OnUtteranceCompletedListener;
import android.text.TextUtils;

/**
 * A wrapper class for {@link TextToSpeech} that adds support for reading at a
 * given granularity level using a {@link BreakIterator}.
 */
public class GranularTextToSpeech {
    private static final int UTTERANCE_COMPLETED = 1;
    private static final int RESUME_SPEAKING = 2;

    private final CharSequenceIterator mCharSequenceIterator = new CharSequenceIterator(null);
    private final TextToSpeech mTts;
    private final HashMap<String, String> mParams;

    private BreakIterator mBreakIterator;
    private SingAlongListener mListener = null;
    private CharSequence mCurrentSequence = null;

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

        // Reset the text since we had to recreate the break iterator.
        setText(mCurrentSequence);
    }

    @SuppressWarnings("deprecation")
    public void speak() {
        mIsPaused = true;

        mTts.stop();
        mTts.setOnUtteranceCompletedListener(mOnUtteranceCompletedListener);

        if (mListener != null) {
            mListener.onSequenceStarted();
        }

        mIsPaused = false;

        onUtteranceCompleted(null);
    }

    public void setText(CharSequence text) {
        mCurrentSequence = text;
        mCharSequenceIterator.setCharSequence(mCurrentSequence);
        mBreakIterator.setText(mCharSequenceIterator);
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

    public boolean isSpeaking() {
        return (mCurrentSequence != null);
    }

    public void setSegmentFromCursor(int cursor) {
        cursor = Math.min(Math.max(0, cursor), mCurrentSequence.length());

        if (mBreakIterator.isBoundary(cursor)) {
            mUnitStart = mBreakIterator.current();
            mBreakIterator.following(cursor);
            mUnitEnd = mBreakIterator.current();
        } else {
            mUnitEnd = mBreakIterator.current();
            mBreakIterator.preceding(cursor);
            mUnitStart = mBreakIterator.current();
        }

        mBypassAdvance = true;

        if (mListener != null) {
            mListener.onUnitSelected(mUnitStart, mUnitEnd);
        }
    }

    @SuppressWarnings("deprecation")
    public void stop() {
        mIsPaused = true;

        mTts.stop();
        mTts.setOnUtteranceCompletedListener(null);

        if (mListener != null) {
            mListener.onSequenceCompleted();
        }

        setText(null);

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

        do {
            final int result = mBreakIterator.following(mUnitEnd);

            if (result == BreakIterator.DONE) {
                return false;
            }

            mUnitStart = mUnitEnd;
            mUnitEnd = mBreakIterator.current();
        } while (isWhitespace(mCurrentSequence.subSequence(mUnitStart, mUnitEnd)));

        if (mListener != null) {
            mListener.onUnitSelected(mUnitStart, mUnitEnd);
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

        do {
            final int result = mBreakIterator.preceding(mUnitStart);

            if (result == BreakIterator.DONE) {
                return false;
            }

            mUnitEnd = mUnitStart;
            mUnitStart = mBreakIterator.current();
        } while (isWhitespace(mCurrentSequence.subSequence(mUnitStart, mUnitEnd)));

        if (mListener != null) {
            mListener.onUnitSelected(mUnitStart, mUnitEnd);
        }

        return true;
    }

    private static boolean isWhitespace(CharSequence text) {
        return TextUtils.getTrimmedLength(text) == 0;
    }

    private void onUtteranceCompleted(String utteranceId) {
        if (mCurrentSequence == null) {
            // Shouldn't be speaking now.
            return;
        }

        if (mIsPaused) {
            // Don't move to the next segment if paused.
            return;
        }

        if (mBypassAdvance) {
            mBypassAdvance = false;
        } else if (!nextInternal()) {
            stop();
            return;
        }

        speakCurrentUnit();
    }

    private void speakCurrentUnit() {
        final CharSequence text = mCurrentSequence.subSequence(mUnitStart, mUnitEnd);
        mTts.speak(text.toString(), TextToSpeech.QUEUE_FLUSH, mParams);
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
        public void onSequenceStarted();

        public void onUnitSelected(int start, int end);

        public void onSequenceCompleted();
    }
}
