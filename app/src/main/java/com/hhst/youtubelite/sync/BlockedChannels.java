package com.hhst.youtubelite.sync;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.tencent.mmkv.MMKV;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Local list of blocked channel names. Blocked channels are hidden from feeds
 * by the injected block_channels.js script.
 */
public final class BlockedChannels {
	private static final String KEY_BLOCKED = "blocked_channels";
	private static final TypeToken<List<String>> LIST_TYPE = new TypeToken<List<String>>() {
	};

	@NonNull
	private final MMKV mmkv;
	@NonNull
	private final Gson gson;

	public BlockedChannels() {
		this.mmkv = MMKV.defaultMMKV();
		this.gson = new Gson();
	}

	public synchronized void block(@Nullable String channelName) {
		String name = normalize(channelName);
		if (name == null) return;
		Set<String> names = new LinkedHashSet<>(readAll());
		names.add(name);
		mmkv.encode(KEY_BLOCKED, gson.toJson(new ArrayList<>(names), LIST_TYPE.getType()));
	}

	public synchronized void unblock(@Nullable String channelName) {
		String name = normalize(channelName);
		if (name == null) return;
		List<String> names = readAll();
		names.removeIf(existing -> existing.equalsIgnoreCase(name));
		mmkv.encode(KEY_BLOCKED, gson.toJson(names, LIST_TYPE.getType()));
	}

	public synchronized boolean isBlocked(@Nullable String channelName) {
		String name = normalize(channelName);
		if (name == null) return false;
		String needle = name.toLowerCase(Locale.ROOT);
		for (String existing : readAll()) {
			if (existing.toLowerCase(Locale.ROOT).equals(needle)) return true;
		}
		return false;
	}

	@NonNull
	public synchronized List<String> list() {
		return readAll();
	}

	@NonNull
	public synchronized String toJson() {
		return gson.toJson(readAll(), LIST_TYPE.getType());
	}

	@NonNull
	private List<String> readAll() {
		String json = mmkv.decodeString(KEY_BLOCKED, null);
		if (json == null || json.isBlank()) return new ArrayList<>();
		try {
			List<String> names = gson.fromJson(json, LIST_TYPE.getType());
			if (names == null) return new ArrayList<>();
			List<String> cleaned = new ArrayList<>();
			for (String name : names) {
				String normalized = normalize(name);
				if (normalized != null) cleaned.add(normalized);
			}
			return cleaned;
		} catch (Exception ignored) {
			return new ArrayList<>();
		}
	}

	@Nullable
	private static String normalize(@Nullable String value) {
		if (value == null) return null;
		String trimmed = value.replaceAll("\\s+", " ").trim();
		return trimmed.isEmpty() ? null : trimmed;
	}
}
