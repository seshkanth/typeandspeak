
package com.googamaphone.typeandspeak;

import java.util.Locale;

import android.content.Context;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

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
        textView.setText(getDisplayNameForLocale(locale));

        final ImageView imageView = (ImageView) view.findViewById(mImageId);
        imageView.setImageResource(drawableId);
    }

    private static CharSequence getDisplayNameForLocale(Locale locale) {
        final CharSequence displayName = locale.getDisplayName();
        if (!TextUtils.isEmpty(displayName)) {
            return displayName;
        }

        final StringBuilder builder = new StringBuilder();

        final CharSequence language = locale.getDisplayLanguage();
        final CharSequence country = locale.getDisplayCountry();
        final CharSequence variant = locale.getDisplayVariant();

        // If the language is empty, there's no hope here.
        if (TextUtils.isEmpty(language)) {
            return locale.toString();
        }

        builder.append(language);

        if (!TextUtils.isEmpty(country)) {
            builder.append(" (");
            builder.append(country);
            if (!TextUtils.isEmpty(variant)) {
                builder.append(", ");
                builder.append(variant);
            }
            builder.append(')');
        } else if (!TextUtils.isEmpty(variant)) {
            builder.append(" (");
            builder.append(variant);
            builder.append(')');
        }

        return builder;
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
