
package com.googamaphone.typeandspeak;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.Locale;

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

    private void setFlagDrawable(int position, View view) {
        final Locale locale = getItem(position);
        final int drawableId = getFlagForLocale(locale);

        final TextView textView = (TextView) view.findViewById(mTextId);
        textView.setText(locale.getDisplayName());

        final ImageView imageView = (ImageView) view.findViewById(mImageId);
        imageView.setImageResource(drawableId);
    }

    private static int getFlagForLocale(Locale locale) {
        final String language = locale.getISO3Language();
        final String country = locale.getISO3Country();

        if ("eng".equalsIgnoreCase(language)) {
            if ("usa".equalsIgnoreCase(country)) {
                return R.drawable.en_us;
            } else {
                return R.drawable.en_uk;
            }
        } else if ("deu".equalsIgnoreCase(language)) {
            return R.drawable.deu;
        } else if ("spa".equalsIgnoreCase(language)) {
            return R.drawable.spa;
        } else if ("fra".equalsIgnoreCase(language)) {
            return R.drawable.fra;
        } else if ("ita".equalsIgnoreCase(language)) {
            return R.drawable.ita;
        } else {
            return R.drawable.unknown;
        }
    }
}
