package com.yutbe.app.extractor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * A suggested video together with the channel that uploaded it.
 */
public record RelatedVideo(@NonNull String id, @Nullable String uploaderName, @Nullable String uploaderUrl) {
}
