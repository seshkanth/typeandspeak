
package com.googamaphone.typeandspeak.utils;

import java.text.CharacterIterator;

import android.text.TextUtils;

public final class CharSequenceIterator implements CharacterIterator, Cloneable {
    private final CharSequence mCharSequence;

    /** The current position. */
    private int mCursor;

    private CharSequenceIterator(CharSequenceIterator other) {
        mCharSequence = other.mCharSequence;
        mCursor = other.mCursor;
    }

    public CharSequenceIterator(CharSequence charSequence) {
        mCharSequence = charSequence;
        mCursor = 0;
    }

    @Override
    public Object clone() {
        return new CharSequenceIterator(this);
    }

    @Override
    public int getBeginIndex() {
        return 0;
    }

    @Override
    public int getEndIndex() {
        return mCharSequence.length();
    }

    @Override
    public int getIndex() {
        return mCursor;
    }

    @Override
    public char setIndex(int location) {
        mCursor = location;
        return current();
    }

    @Override
    public char next() {
        mCursor++;
        return current();
    }

    @Override
    public char previous() {
        mCursor--;
        return current();
    }

    @Override
    public char current() {
        if (getIndex() >= getEndIndex()) {
            return CharacterIterator.DONE;
        }

        return mCharSequence.charAt(getIndex());
    }

    @Override
    public char first() {
        return setIndex(getBeginIndex());
    }

    @Override
    public char last() {
        if (TextUtils.isEmpty(mCharSequence)) {
            return setIndex(getEndIndex());
        }

        return setIndex(getEndIndex() - 1);
    }
}
