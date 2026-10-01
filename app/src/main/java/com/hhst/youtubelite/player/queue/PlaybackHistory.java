package com.hhst.youtubelite.player.queue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.tencent.mmkv.MMKV;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Persists the ids of videos that have already been played, so autoplay
 * never suggests the same video twice.
 */
@Singleton
public final class PlaybackHistory {
	static final String KEY_PLAYED_IDS = "played_video_ids";
	private static final int MAX_ENTRIES = 1000;
	private static final Type LIST_TYPE = new TypeToken<List<String>>() {
	}.getType();

	@NonNull
	private final MMKV mmkv;
	@NonNull
	private final Gson gson;

	@Inject
	public PlaybackHistory(@NonNull MMKV mmkv, @NonNull Gson gson) {
		this.mmkv = mmkv;
		this.gson = gson;
	}

	public synchronized void record(@Nullable String videoId) {
		if (videoId == null || videoId.isBlank()) return;
		List<String> ids = readIds();
		// Move to the end so the list stays ordered from oldest to newest.
		ids.remove(videoId);
		ids.add(videoId);
		while (ids.size() > MAX_ENTRIES) {
			ids.remove(0);
		}
		mmkv.encode(KEY_PLAYED_IDS, gson.toJson(ids, LIST_TYPE));
	}

	public synchronized boolean contains(@Nullable String videoId) {
		return videoId != null && readIds().contains(videoId);
	}

	@NonNull
	public synchronized List<String> getIds() {
		return readIds();
	}

	@NonNull
	public synchronized String toJson() {
		return gson.toJson(readIds(), LIST_TYPE);
	}

	@NonNull
	private List<String> readIds() {
		String json = mmkv.decodeString(KEY_PLAYED_IDS, null);
		if (json == null || json.isBlank()) return new ArrayList<>();
		try {
			List<String> ids = gson.fromJson(json, LIST_TYPE);
			if (ids == null) return new ArrayList<>();
			// Drop null/blank entries defensively while preserving order.
			Set<String> cleaned = new LinkedHashSet<>();
			for (String id : ids) {
				if (id != null && !id.isBlank()) cleaned.add(id);
			}
			return new ArrayList<>(cleaned);
		} catch (Exception ignored) {
			return new ArrayList<>();
		}
	}
}
