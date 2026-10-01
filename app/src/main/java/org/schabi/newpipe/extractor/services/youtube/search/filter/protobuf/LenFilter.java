package org.schabi.newpipe.extractor.services.youtube.search.filter.protobuf;

public enum LenFilter {
    duration_short(1),
    duration_long(2)
    ;

    private final int value;

    LenFilter(final int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public static LenFilter fromValue(final int value) {
        for (final LenFilter item : values()) {
            if (item.value == value) return item;
        }
        return null;
    }
}
