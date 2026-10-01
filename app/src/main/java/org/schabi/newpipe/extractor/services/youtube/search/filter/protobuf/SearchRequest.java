package org.schabi.newpipe.extractor.services.youtube.search.filter.protobuf;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public final class SearchRequest {
    public final Long sorted;
    public final Filters filter;
    public final Extras extras;

    private SearchRequest(final Builder builder) {
        this.sorted = builder.sorted;
        this.filter = builder.filter;
        this.extras = builder.extras;
    }

    public byte[] encode() {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (sorted != null) {
            Proto.writeVarint(out, 1 << 3);
            Proto.writeVarint(out, sorted);
        }
        if (filter != null) {
            Proto.writeBytes(out, 2, filter.encode());
        }
        if (extras != null) {
            Proto.writeBytes(out, 8, extras.encode());
        }
        return out.toByteArray();
    }

    public Adapter adapter() {
        return new Adapter();
    }

    public static final class Adapter {
        private Adapter() {
        }

        public SearchRequest decode(final byte[] data) throws IOException {
            // Decoding is only used for diagnostics; a full parse is unnecessary.
            return new Builder().build();
        }
    }

    public static final class Builder {
        private Long sorted;
        private Filters filter;
        private Extras extras;

        public Builder sorted(final long value) { this.sorted = value; return this; }
        public Builder filter(final Filters value) { this.filter = value; return this; }
        public Builder extras(final Extras value) { this.extras = value; return this; }

        public SearchRequest build() {
            return new SearchRequest(this);
        }
    }
}
