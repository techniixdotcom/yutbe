package org.schabi.newpipe.extractor.services.youtube.search.filter.protobuf;

import java.io.ByteArrayOutputStream;

public final class Extras {
    public final Boolean verbatim;

    private Extras(final Builder builder) {
        this.verbatim = builder.verbatim;
    }

    byte[] encode() {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (verbatim != null) {
            Proto.writeVarint(out, 1 << 3);
            Proto.writeVarint(out, verbatim ? 1 : 0);
        }
        return out.toByteArray();
    }

    public static final class Builder {
        private Boolean verbatim;

        public Builder verbatim(final boolean value) {
            this.verbatim = value;
            return this;
        }

        public Extras build() {
            return new Extras(this);
        }
    }
}
