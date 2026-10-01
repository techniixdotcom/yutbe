package org.schabi.newpipe.extractor.services.youtube.search.filter.protobuf;

import java.io.ByteArrayOutputStream;

public final class Filters {
    public final Long date;
    public final Long type;
    public final Long length;
    public final Boolean is_hd;
    public final Boolean subtitles;
    public final Boolean ccommons;
    public final Boolean is_3d;
    public final Boolean live;
    public final Boolean purchased;
    public final Boolean is_4k;
    public final Boolean is_360;
    public final Boolean location;
    public final Boolean is_hdr;

    private Filters(final Builder builder) {
        this.date = builder.date;
        this.type = builder.type;
        this.length = builder.length;
        this.is_hd = builder.is_hd;
        this.subtitles = builder.subtitles;
        this.ccommons = builder.ccommons;
        this.is_3d = builder.is_3d;
        this.live = builder.live;
        this.purchased = builder.purchased;
        this.is_4k = builder.is_4k;
        this.is_360 = builder.is_360;
        this.location = builder.location;
        this.is_hdr = builder.is_hdr;
    }

    byte[] encode() {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (date != null) { Proto.writeVarint(out, 1 << 3); Proto.writeVarint(out, date); }
        if (type != null) { Proto.writeVarint(out, 2 << 3); Proto.writeVarint(out, type); }
        if (length != null) { Proto.writeVarint(out, 3 << 3); Proto.writeVarint(out, length); }
        writeBool(out, 4, is_hd);
        writeBool(out, 5, subtitles);
        writeBool(out, 6, ccommons);
        writeBool(out, 7, is_3d);
        writeBool(out, 8, live);
        writeBool(out, 9, purchased);
        writeBool(out, 14, is_4k);
        writeBool(out, 15, is_360);
        writeBool(out, 23, location);
        writeBool(out, 25, is_hdr);
        return out.toByteArray();
    }

    private static void writeBool(final ByteArrayOutputStream out, final int field,
                                  final Boolean value) {
        if (value != null) {
            Proto.writeVarint(out, field << 3);
            Proto.writeVarint(out, value ? 1 : 0);
        }
    }

    public static final class Builder {
        private Long date;
        private Long type;
        private Long length;
        private Boolean is_hd;
        private Boolean subtitles;
        private Boolean ccommons;
        private Boolean is_3d;
        private Boolean live;
        private Boolean purchased;
        private Boolean is_4k;
        private Boolean is_360;
        private Boolean location;
        private Boolean is_hdr;

        public Builder date(final long value) { this.date = value; return this; }
        public Builder type(final long value) { this.type = value; return this; }
        public Builder length(final long value) { this.length = value; return this; }
        public Builder is_hd(final boolean value) { this.is_hd = value; return this; }
        public Builder subtitles(final boolean value) { this.subtitles = value; return this; }
        public Builder ccommons(final boolean value) { this.ccommons = value; return this; }
        public Builder is_3d(final boolean value) { this.is_3d = value; return this; }
        public Builder live(final boolean value) { this.live = value; return this; }
        public Builder purchased(final boolean value) { this.purchased = value; return this; }
        public Builder is_4k(final boolean value) { this.is_4k = value; return this; }
        public Builder is_360(final boolean value) { this.is_360 = value; return this; }
        public Builder location(final boolean value) { this.location = value; return this; }
        public Builder is_hdr(final boolean value) { this.is_hdr = value; return this; }

        public Filters build() {
            return new Filters(this);
        }
    }
}
