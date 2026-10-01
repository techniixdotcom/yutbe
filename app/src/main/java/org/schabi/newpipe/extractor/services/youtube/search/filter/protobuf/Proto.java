package org.schabi.newpipe.extractor.services.youtube.search.filter.protobuf;

import java.io.ByteArrayOutputStream;

final class Proto {
    private Proto() {
    }

    static void writeVarint(final ByteArrayOutputStream out, long value) {
        while (true) {
            if ((value & ~0x7FL) == 0) {
                out.write((int) value);
                return;
            }
            out.write(((int) value & 0x7F) | 0x80);
            value >>>= 7;
        }
    }

    static void writeBytes(final ByteArrayOutputStream out, final int field,
                           final byte[] data) {
        writeVarint(out, (field << 3) | 2);
        writeVarint(out, data.length);
        out.write(data, 0, data.length);
    }
}
