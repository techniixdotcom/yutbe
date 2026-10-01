package org.schabi.newpipe.extractor.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

public final class RegexUtils {
    private RegexUtils() {
    }

    @Nullable
    public static String extract(final String input, final String regex) {
        final Matcher matcher = Pattern.compile(regex).matcher(input);
        return matcher.find() ? matcher.group(0) : null;
    }
}
