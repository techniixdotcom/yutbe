package org.schabi.newpipe.extractor.services.youtube.search.filter.protobuf;

public enum TypeFilter {
    video(1),
    channel(2),
    playlist(3),
    movie(4),
    show(5)
    ;

    private final int value;

    TypeFilter(final int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public static TypeFilter fromValue(final int value) {
        for (final TypeFilter item : values()) {
            if (item.value == value) return item;
        }
        return null;
    }
}
