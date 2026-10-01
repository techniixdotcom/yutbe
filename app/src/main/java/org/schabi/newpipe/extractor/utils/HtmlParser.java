package org.schabi.newpipe.extractor.utils;

import javax.annotation.Nullable;

public final class HtmlParser {
    private HtmlParser() {
    }

    @Nullable
    public static String htmlToString(@Nullable final String html) {
        if (html == null) {
            return null;
        }
        final String withNewLines = html.replaceAll("(?i)<br\\s*/?>", "\n");
        return withNewLines.replaceAll("<[^>]*>", "");
    }
}
