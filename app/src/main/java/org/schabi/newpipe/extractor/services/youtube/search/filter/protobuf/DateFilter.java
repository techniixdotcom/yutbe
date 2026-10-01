package org.schabi.newpipe.extractor.services.youtube.search.filter.protobuf;

public enum DateFilter {
    hour(1),
    day(2),
    week(3),
    month(4),
    year(5)
    ;

    private final int value;

    DateFilter(final int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public static DateFilter fromValue(final int value) {
        for (final DateFilter item : values()) {
            if (item.value == value) return item;
        }
        return null;
    }
}
