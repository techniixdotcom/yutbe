package org.schabi.newpipe.settings;

import androidx.annotation.Nullable;

/**
 * Minimal stub used by the vendored extractor. Caption translation is not
 * exposed in this app, so there is never a target language.
 */
public final class CaptionTranslationPreferences {
    private CaptionTranslationPreferences() {
    }

    @Nullable
    public static String getTargetLanguage() {
        return null;
    }
}
