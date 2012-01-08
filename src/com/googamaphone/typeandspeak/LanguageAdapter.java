
package com.googamaphone.typeandspeak;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;

public class LanguageAdapter extends ArrayAdapter<Language> {
    private final int mImageId;

    public LanguageAdapter(Context context, int layoutId, int textId, int imageId) {
        super(context, layoutId, textId);

        mImageId = imageId;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = null;

        try {
            view = super.getView(position, convertView, parent);

            final Language lang = getItem(position);

            final ImageView image = (ImageView) view.findViewById(mImageId);
            image.setImageResource(lang.getFlagResource());
        } catch (final IndexOutOfBoundsException e) {
            e.printStackTrace();
        }

        return view;
    }

    @Override
    public View getDropDownView(int position, View convertView, ViewGroup parent) {
        View view = null;

        try {
            view = super.getDropDownView(position, convertView, parent);

            final Language lang = getItem(position);

            final ImageView image = (ImageView) view.findViewById(mImageId);
            image.setImageResource(lang.getFlagResource());
        } catch (final IndexOutOfBoundsException e) {
            e.printStackTrace();
        }

        return view;
    }
}
