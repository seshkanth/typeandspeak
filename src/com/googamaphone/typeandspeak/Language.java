
package com.googamaphone.typeandspeak;

import java.util.Locale;

public class Language implements Comparable<Language> {
    private Locale mLocale;
    private int mFlagId;

    public Language(String locale) {
        mLocale = new Locale(locale);

        String lang = mLocale.getISO3Language();
        String ctry = mLocale.getISO3Country();

        if ("eng".equals(lang)) {
            if ("USA".equals(ctry)) {
                mFlagId = R.drawable.en_us;
            } else {
                mFlagId = R.drawable.en_uk;
            }
        } else if ("deu".equals(lang)) {
            mFlagId = R.drawable.deu;
        } else if ("spa".equals(lang)) {
            mFlagId = R.drawable.spa;
        } else if ("fra".equals(lang)) {
            mFlagId = R.drawable.fra;
        } else if ("ita".equals(lang)) {
            mFlagId = R.drawable.ita;
        } else {
            mFlagId = R.drawable.unknown;
        }
    }

    public int getFlagResource() {
        return mFlagId;
    }

    @Override
    public String toString() {
        return mLocale.getDisplayName();
    }

    @Override
    public int compareTo(Language other) {
        String language = toString();
        String otherLanguage = other.toString();

        return language.compareTo(otherLanguage);
    }

    public Locale getLocale() {
        return mLocale;
    }
}
