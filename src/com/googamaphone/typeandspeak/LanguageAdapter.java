
package com.googamaphone.typeandspeak;

import java.util.Locale;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

public class LanguageAdapter extends ArrayAdapter<Locale> {
    private final int mTextViewId;

    public LanguageAdapter(Context context, int layoutId, int textId) {
        super(context, layoutId, textId);

        mTextViewId = textId;
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
        final Drawable drawable = getContext().getResources().getDrawable(drawableId);
        final TextView textView = (TextView) view.findViewById(mTextViewId);

        textView.setCompoundDrawables(drawable, null, null, null);
    }

    private static int getFlagForLocale(Locale locale) {
        final String language = locale.getISO3Language();
        final String country = locale.getISO3Country();

        if ("eng".equals(language)) {
            if ("USA".equals(country)) {
                return R.drawable.en_us;
            } else {
                return R.drawable.en_uk;
            }
        } else if ("deu".equals(language)) {
            return R.drawable.deu;
        } else if ("spa".equals(language)) {
            return R.drawable.spa;
        } else if ("fra".equals(language)) {
            return R.drawable.fra;
        } else if ("ita".equals(language)) {
            return R.drawable.ita;
        } else {
            return R.drawable.unknown;
        }
    }
}
