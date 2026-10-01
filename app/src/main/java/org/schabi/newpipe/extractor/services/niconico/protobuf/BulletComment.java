package org.schabi.newpipe.extractor.services.niconico.protobuf;

import java.util.List;

public final class BulletComment {
    public List<MessageItem> items;
    public String backward_url;
    public String snapshot_url;
    public List<String> segments;

    public static final class MessageItem {
        public Meta meta;
        public Message message;

        public static final class Meta {
            public String id;
            public Timestamp timestamp;
            public AdditionalInfo additional_info;

            public static final class Timestamp {
                public long seconds;
                public int nanos;
            }

            public static final class AdditionalInfo {
                public InnerInfo inner_info;

                public static final class InnerInfo {
                    public long value;
                }
            }
        }

        public static final class Message {
            public InnerMessage text_message;
            public InnerMessage gift;

            public static final class InnerMessage {
                public String text;
                public long time_to_now;
            }
        }
    }
}
