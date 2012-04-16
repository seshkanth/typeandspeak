package com.googamaphone.typeandspeak;

import java.text.BreakIterator;
import java.util.Iterator;

import android.graphics.Color;
import android.text.Spannable;
import android.text.method.KeyListener;
import android.text.style.BackgroundColorSpan;
import android.text.style.CharacterStyle;
import android.text.style.ForegroundColorSpan;
import android.widget.EditText;

public class SingAlongTextView implements Iterator<CharSequence> {
    private final CharacterStyle mForegroundSpan = new ForegroundColorSpan(Color.BLACK);
    private final CharacterStyle mBackgroundSpan = new BackgroundColorSpan(Color.YELLOW);
    
    private final EditText mTextView;
    private final BreakIterator mBreakIterator;
    
    private KeyListener mKeyListener;
    private CharSequence mText;
    private boolean mIsDone;
    
    public SingAlongTextView(EditText textView) {
        mTextView = textView;
        mBreakIterator = BreakIterator.getSentenceInstance();
    }
    
    public void start() {
        mKeyListener = mTextView.getKeyListener();

        mTextView.setKeyListener(null);
        mTextView.setCursorVisible(false);
        mTextView.setSelection(0,0);
        
        mText = mTextView.getText();
        mBreakIterator.setText(mText.toString());
        mIsDone = false;
    }

    @Override
    public boolean hasNext() {
        return !mIsDone;
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException();
    }
    
    @Override
    public CharSequence next() {
        int start = mBreakIterator.current();
        
        if (mIsDone) {
            return null;
        }
        
        int end = mBreakIterator.next();
        
        if (end == BreakIterator.DONE) {
            mIsDone = true;
            end = mText.length();
        }
        
        Spannable text = (Spannable) mText;
        text.setSpan(mForegroundSpan, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setSpan(mBackgroundSpan, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        
        mTextView.setSelection(end, end);
        
        return mText.subSequence(start, end);
    }
    
    public void end() {
        mTextView.setSelection(0,0);
        mTextView.setCursorVisible(true);
        mTextView.setKeyListener(mKeyListener);
        mKeyListener = null;
        
        Spannable text = (Spannable) mTextView.getText();
        text.removeSpan(mForegroundSpan);
        text.removeSpan(mBackgroundSpan);
    }
}
