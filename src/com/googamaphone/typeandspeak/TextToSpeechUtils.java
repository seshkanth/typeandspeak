
package com.googamaphone.typeandspeak;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

import android.content.Intent;
import android.os.Bundle;
import android.speech.tts.TextToSpeech.Engine;

public class TextToSpeechUtils {
    /** Extra used to enumerate available voices in API 14+ */
    private static final String EXTRA_AVAILABLE_VOICES = "availableVoices";

    private static final Comparator<Locale> LOCALE_COMPARATOR = new Comparator<Locale>() {
        @Override
        public int compare(Locale lhs, Locale rhs) {
            return lhs.getDisplayName().compareTo(rhs.getDisplayName());
        }
    };

    public static Set<Locale> loadTtsLanguages(Intent data) {
        if (data == null) {
            return Collections.emptySet();
        }

        final Bundle extras = data.getExtras();

        Object langs = null;

        if (langs == null) {
            langs = getAvailableVoicesICS(extras);
        }

        if (langs == null) {
            langs = getAvailableVoices(extras);
        }

        // Some engines return a String[], which is not an Iterable<?>
        if (langs instanceof String[]) {
            langs = Arrays.asList((String[]) langs);
        }

        // If it's not iterable, fail.
        if (!(langs instanceof Iterable<?>)) {
            return Collections.emptySet();
        }

        final Iterable<?> strLocales = (Iterable<?>) langs;
        final TreeSet<Locale> locales = new TreeSet<Locale>(LOCALE_COMPARATOR);

        for (final Object strLocale : strLocales) {
            if (strLocale == null) {
                continue;
            }

            final String[] codes = strLocale.toString().split("-");

            if (codes.length == 1) {
                locales.add(new Locale(codes[0]));
            } else if (codes.length == 2) {
                locales.add(new Locale(codes[0], codes[1]));
            } else if (codes.length == 3) {
                locales.add(new Locale(codes[0], codes[1], codes[2]));
            }
        }

        return locales;
    }

    private static Object getAvailableVoices(final Bundle extras) {
        final String root = extras.getString(Engine.EXTRA_VOICE_DATA_ROOT_DIRECTORY);
        final Object files = extras.get(Engine.EXTRA_VOICE_DATA_FILES);
        final Object langs = extras.get(Engine.EXTRA_VOICE_DATA_FILES_INFO);

        if ((root == null) || !(files instanceof String[]) || !(langs instanceof String[])) {
            return langs;
        }

        final String[] filesArray = (String[]) files;
        final String[] langsArray = (String[]) langs;
        final List<String> langsList = new LinkedList<String>();

        for (int i = 0; i < filesArray.length; i++) {
            final File file = new File(root, filesArray[i]);

            if (file.canRead()) {
                langsList.add(langsArray[i]);
            }
        }

        return langsList;
    }

    private static Object getAvailableVoicesICS(final Bundle extras) {
        return extras.get(EXTRA_AVAILABLE_VOICES);
    }
}
