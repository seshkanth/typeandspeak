
package com.googamaphone.typeandspeak;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.Locale;

/**
 * An implementation of {@link ArrayAdapter} that displays locales with their
 * proper display names and flags.
 */
public class LanguageAdapter extends ArrayAdapter<Locale> {
    private final int mTextId;
    private final int mImageId;

    public LanguageAdapter(Context context, int layoutId, int textId, int imageId) {
        super(context, layoutId, textId);

        mTextId = textId;
        mImageId = imageId;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        final View view = super.getView(position, convertView, parent);

        setFlagDrawable(position, view);

        return view;
    }

    @Override
    public View getDropDownView(int position, View convertView, ViewGroup parent) {
        final View view = super.getDropDownView(position, convertView, parent);

        setFlagDrawable(position, view);

        return view;
    }

    /**
     * Sets the flag for the specified view and locale.
     * 
     * @param position The position of the locale within the adapter.
     * @param view The view that represents the locale.
     */
    private void setFlagDrawable(int position, View view) {
        final Locale locale = getItem(position);
        final int drawableId = getFlagForLocale(locale);

        final TextView textView = (TextView) view.findViewById(mTextId);
        textView.setText(locale.getDisplayName());

        final ImageView imageView = (ImageView) view.findViewById(mImageId);
        imageView.setImageResource(drawableId);
    }

    /**
     * Returns the drawable identifier for the flag associated specified locale.
     * If the locale does not have a flag, returns the drawable identifier for
     * the default flag.
     * 
     * @param locale A locale.
     * @return The drawable identifier for the locale's flag.
     */
    private static int getFlagForLocale(Locale locale) {
        final String language = locale.getISO3Language();
        final String country = locale.getISO3Country();
        
        // First, check for country code.
        if ("usa".equals(country)) {
            return R.drawable.united_states;
        } else if ("ita".equals(country)) {
            return R.drawable.italy;
        } else if ("deu".equals(country)) {
            return R.drawable.germany;
        } else if ("gbr".equals(country)) {
            return R.drawable.united_kingdom;
        } else if ("fra".equals(country)) {
            return R.drawable.france;
        } else if ("chn".equals(country)) {
            return R.drawable.china;
        } else if ("twn".equals(country)) {
            return R.drawable.taiwan;
        } else if ("jpn".equals(country)) {
            return R.drawable.japan;
        } else if ("spa".equals(country)) {
            return R.drawable.spain;
        } else if ("mex".equals(country)) {
            return R.drawable.mexico;
        } else if ("kor".equals(country)) {
            return R.drawable.korea;
        }
        
        // Next, check for language code.

        if (Locale.ENGLISH.getISO3Language().equalsIgnoreCase(language)) {
            return R.drawable.united_kingdom;
        } else if (Locale.GERMAN.getISO3Language().equalsIgnoreCase(language)) {
            return R.drawable.germany;
        } else if (Locale.FRENCH.getISO3Language().equalsIgnoreCase(language)) {
            return R.drawable.france;
        } else if (Locale.ITALIAN.getISO3Language().equalsIgnoreCase(language)) {
            return R.drawable.italy;
        } else if (Locale.CHINESE.getISO3Language().equalsIgnoreCase(language)) {
            return R.drawable.china;
        } else if (Locale.JAPANESE.getISO3Language().equalsIgnoreCase(language)) {
            return R.drawable.japan;
        } else if (Locale.KOREAN.getISO3Language().equalsIgnoreCase(language)) {
            return R.drawable.korea;
        } else if ("spa".equalsIgnoreCase(language)) {
            return R.drawable.spain;
        }

        return R.drawable.unknown;
    }
}
