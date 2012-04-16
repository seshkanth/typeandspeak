
package com.googamaphone.typeandspeak;

import java.util.HashMap;

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.Engine;
import android.speech.tts.TextToSpeech.OnUtteranceCompletedListener;
import android.widget.EditText;

public class SingAlongTextToSpeech {
    private static final int UTTERANCE_COMPLETED = 1;

    private final TextToSpeech mTts;
    private final SingAlongTextView mSingAlong;
    private final HashMap<String, String> mParams;
    
    private SingAlongListener mListener;
    private boolean mIsSpeaking;

    public SingAlongTextToSpeech(Context context, TextToSpeech tts, EditText textView) {
        mTts = tts;        
        mSingAlong = new SingAlongTextView(textView);
        mParams = new HashMap<String, String>();
    }
    
    public void setListener(SingAlongListener listener) {
        mListener = listener;
    }
    
    public boolean isSpeaking() {
        return mIsSpeaking;
    }

    public TextToSpeech getTextToSpeech() {
        return mTts;
    }

    public void speak() {
        mIsSpeaking = true;
        mSingAlong.start();
        onUtteranceCompleted(null);
        
        if (mListener != null) {
            mListener.onSpeechStarted();
        }
    }
    
    public void stop() {
        mIsSpeaking = false;
        mTts.stop();
        mTts.setOnUtteranceCompletedListener(null);
        mSingAlong.end();
        
        if (mListener != null) {
            mListener.onSpeechCompleted();
        }
    }

    private void onUtteranceCompleted(String utteranceId) {
        if (!mIsSpeaking) {
            // The last utterance has completed.
            return;
        }
        
        if (!mSingAlong.hasNext()) {
            // This is the last utterance.
            stop();
            return;
        }

        String nextString = mSingAlong.next().toString();
        mParams.put(Engine.KEY_PARAM_UTTERANCE_ID, nextString);
        mTts.setOnUtteranceCompletedListener(mOnUtteranceCompletedListener);
        mTts.speak(nextString, TextToSpeech.QUEUE_ADD, mParams);
    }

    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case UTTERANCE_COMPLETED:
                    onUtteranceCompleted((String) msg.obj);
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
        public void onSpeechStarted();
        public void onSpeechCompleted();
    }
}
