package org.schabi.newpipe.extractor.services.youtube.search.filter.protobuf;

public enum SortOrder {
    relevance(0),
    rating(1),
    date(2),
    views(3)
    ;

    private final int value;

    SortOrder(final int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public static SortOrder fromValue(final int value) {
        for (final SortOrder item : values()) {
            if (item.value == value) return item;
        }
        return null;
    }
}
