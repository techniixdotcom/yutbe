package org.schabi.newpipe.extractor.services.youtube.search.filter.protobuf;

public enum Features {
    is_hd(0),
    subtitles(1),
    ccommons(2),
    is_3d(3),
    live(4),
    purchased(5),
    is_4k(6),
    is_360(7),
    location(8),
    is_hdr(9)
    ;

    private final int value;

    Features(final int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public static Features fromValue(final int value) {
        for (final Features item : values()) {
            if (item.value == value) return item;
        }
        return null;
    }
}
