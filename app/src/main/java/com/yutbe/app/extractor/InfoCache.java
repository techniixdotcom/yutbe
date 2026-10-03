package com.yutbe.app.extractor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.tencent.mmkv.MMKV;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Cache for extractor playback and video details.
 */
@Singleton
public final class InfoCache {
	private static final String STREAM_KEY = "extractor:stream:";
	private static final String INFO_KEY = "extractor:info:";
	private static final String RELATED_KEY = "extractor:related2:";

	@NonNull
	private final MMKV kv;
	@NonNull
	private final Gson gson;

	@Inject
	public InfoCache(@NonNull MMKV kv,
	                 @NonNull Gson gson) {
		this.kv = kv;
		this.gson = gson;
	}

	@Nullable
	public PlaybackDetails getPlaybackDetails(@NonNull String videoId) {
		return read(STREAM_KEY + videoId, PlaybackDetails.class);
	}

	public void putPlaybackDetails(@NonNull String videoId,
	                               @NonNull PlaybackDetails details) {
		write(STREAM_KEY + videoId, details, TimeUnit.MINUTES.toMillis(2));
	}

	public void removePlaybackDetails(@NonNull String videoId) {
		kv.removeValueForKey(STREAM_KEY + videoId);
	}

	@Nullable
	public List<RelatedVideo> getRelatedVideos(@NonNull String videoId) {
		RelatedVideo[] items = read(RELATED_KEY + videoId, RelatedVideo[].class);
		return items == null ? null : new ArrayList<>(Arrays.asList(items));
	}

	public void putRelatedVideos(@NonNull String videoId,
	                             @NonNull List<RelatedVideo> items) {
		write(RELATED_KEY + videoId, items.toArray(new RelatedVideo[0]), TimeUnit.HOURS.toMillis(6));
	}

	@Nullable
	public VideoDetails getVideoDetails(@NonNull String videoId) {
		return read(INFO_KEY + videoId, VideoDetails.class);
	}

	public void putVideoDetails(@NonNull String videoId,
	                            @NonNull VideoDetails details) {
		write(INFO_KEY + videoId, details, TimeUnit.HOURS.toMillis(6));
	}

	@Nullable
	private <T> T read(@NonNull String key,
	                   @NonNull Class<T> type) {
		String raw = kv.decodeString(key, null);
		if (raw == null || raw.isBlank()) {
			return null;
		}
		try {
			Slot slot = gson.fromJson(raw, Slot.class);
			if (slot == null || slot.until() <= System.currentTimeMillis()) {
				return null;
			}
			return gson.fromJson(slot.json(), type);
		} catch (RuntimeException ignored) {
			return null;
		}
	}

	private void write(@NonNull String key,
	                   @NonNull Object value,
	                   final long ttlMs) {
		Slot slot = new Slot(System.currentTimeMillis() + ttlMs, gson.toJson(value));
		kv.encode(key, gson.toJson(slot));
	}

/**
 * Value object for app logic.
 */
	private record Slot(long until, @NonNull String json) {
	}
}
