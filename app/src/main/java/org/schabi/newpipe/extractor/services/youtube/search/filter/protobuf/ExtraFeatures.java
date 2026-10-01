package org.schabi.newpipe.extractor.services.youtube.search.filter.protobuf;

public enum ExtraFeatures {
    verbatim(0)
    ;

    private final int value;

    ExtraFeatures(final int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public static ExtraFeatures fromValue(final int value) {
        for (final ExtraFeatures item : values()) {
            if (item.value == value) return item;
        }
        return null;
    }
}
