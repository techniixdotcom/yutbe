package com.yutbe.app.backup;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.yutbe.app.extension.Constant;
import com.yutbe.app.extension.ExtensionManager;
import com.yutbe.app.filter.BlockedChannel;
import com.yutbe.app.filter.ContentFilters;
import com.yutbe.app.history.WatchHistory;
import com.yutbe.app.player.queue.QueueItem;
import com.yutbe.app.player.queue.QueueRepository;
import com.yutbe.app.util.UrlUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Writes and reads a JSON backup of the settings, blocked channels, watch history and queue.
 * Android backups are off for this app, so this is how data moves to a new phone.
 */
@Singleton
public final class SettingsBackup {
	private static final String APP = "YuTbe";
	private static final int FORMAT = 1;
	private static final int MAX_BYTES = 32 * 1024 * 1024;
	private static final int MAX_QUEUE = 5_000;
	private static final int MAX_BLOCKED = 5_000;
	private static final int MAX_HISTORY = 50_000;

	@NonNull
	private final ExtensionManager extensions;
	@NonNull
	private final ContentFilters filters;
	@NonNull
	private final QueueRepository queue;
	@NonNull
	private final Gson gson;
	@NonNull
	private final WatchHistory watchLog;

	@Inject
	public SettingsBackup(@NonNull ExtensionManager extensions,
	                      @NonNull ContentFilters filters,
	                      @NonNull QueueRepository queue,
	                      @NonNull Gson gson,
	                      @NonNull WatchHistory watchLog) {
		this.watchLog = watchLog;
		this.extensions = extensions;
		this.filters = filters;
		this.queue = queue;
		this.gson = gson;
	}

	public void export(@NonNull OutputStream out) throws IOException {
		JsonObject root = new JsonObject();
		root.addProperty("app", APP);
		root.addProperty("format", FORMAT);
		root.addProperty("exportedAt", Instant.now().toString());

		JsonObject settings = new JsonObject();
		JsonObject booleans = new JsonObject();
		for (String key : Constant.DEFAULT_PREFERENCES.keySet()) {
			booleans.addProperty(key, extensions.isEnabled(key));
		}
		JsonObject numbers = new JsonObject();
		for (String key : Constant.DEFAULT_INT_PREFERENCES.keySet()) {
			numbers.addProperty(key, extensions.getInt(key));
		}
		JsonObject strings = new JsonObject();
		for (String key : Constant.DEFAULT_STRING_PREFERENCES.keySet()) {
			strings.addProperty(key, extensions.getString(key));
		}
		settings.add("booleans", booleans);
		settings.add("numbers", numbers);
		settings.add("strings", strings);
		root.add("settings", settings);

		root.add("blockedChannels", gson.toJsonTree(filters.blockedChannels()));

		JsonObject history = new JsonObject();
		for (Map.Entry<String, long[]> entry : filters.exportWatchHistory().entrySet()) {
			JsonArray value = new JsonArray();
			value.add(entry.getValue()[0]);
			value.add(entry.getValue()[1]);
			history.add(entry.getKey(), value);
		}
		root.add("watchHistory", history);
		root.add("queue", gson.toJsonTree(queue.getItems()));
		root.add("watchLog", gson.toJsonTree(watchLog.entries()));

		out.write(gson.toJson(root).getBytes(StandardCharsets.UTF_8));
		out.flush();
	}

	/**
	 * Imports a backup. Settings are replaced; blocked channels, watch history and queue items
	 * are added to what is already there. Unknown keys and invalid values are ignored.
	 */
	@NonNull
	public Result restore(@NonNull InputStream in) throws IOException {
		JsonObject root;
		try {
			JsonElement parsed = JsonParser.parseString(readLimited(in));
			if (!parsed.isJsonObject()) throw new IOException("Not a YuTbe backup");
			root = parsed.getAsJsonObject();
		} catch (RuntimeException e) {
			throw new IOException("Not a YuTbe backup", e);
		}
		if (!APP.equals(string(root.get("app")))) throw new IOException("Not a YuTbe backup");
		JsonElement format = root.get("format");
		if (format == null || !format.isJsonPrimitive() || !format.getAsJsonPrimitive().isNumber()
						|| format.getAsInt() > FORMAT) {
			throw new IOException("This backup was made by a newer YuTbe");
		}

		int settings = restoreSettings(object(root.get("settings")));

		List<BlockedChannel> blocked = new ArrayList<>();
		for (JsonElement element : array(root.get("blockedChannels"))) {
			if (blocked.size() >= MAX_BLOCKED) break;
			JsonObject item = object(element);
			String name = string(item.get("name"));
			if (name == null) continue;
			blocked.add(new BlockedChannel(name, string(item.get("path")), string(item.get("altPath")),
							number(item.get("blockedAt"))));
		}
		int channels = filters.importBlockedChannels(blocked);

		Map<String, long[]> history = new LinkedHashMap<>();
		for (Map.Entry<String, JsonElement> entry : object(root.get("watchHistory")).entrySet()) {
			if (history.size() >= MAX_HISTORY) break;
			JsonArray value = array(entry.getValue());
			if (value.size() < 2) continue;
			history.put(entry.getKey(), new long[]{number(value.get(0)), number(value.get(1))});
		}
		int videos = filters.importWatchHistory(history);

		List<QueueItem> items = new ArrayList<>();
		for (JsonElement element : array(root.get("queue"))) {
			if (items.size() >= MAX_QUEUE) break;
			JsonObject item = object(element);
			String videoId = string(item.get("videoId"));
			String videoUrl = string(item.get("videoUrl"));
			String title = string(item.get("title"));
			if (!ContentFilters.isVideoId(videoId) || !UrlUtils.isYoutubeLink(videoUrl)
							|| title == null || title.isBlank()) {
				continue;
			}
			String thumbnail = string(item.get("thumbnailUrl"));
			if (thumbnail != null && !thumbnail.startsWith("https://")) thumbnail = null;
			items.add(new QueueItem(videoId, videoUrl, clip(title, 500), clip(string(item.get("author")), 200), thumbnail));
		}
		int queued = queue.addMissing(items);

		List<WatchHistory.Entry> log = new ArrayList<>();
		for (JsonElement element : array(root.get("watchLog"))) {
			if (log.size() >= MAX_HISTORY) break;
			JsonObject item = object(element);
			String videoId = string(item.get("videoId"));
			if (!ContentFilters.isVideoId(videoId)) continue;
			log.add(new WatchHistory.Entry(videoId, string(item.get("title")), string(item.get("author")),
							string(item.get("thumbnailUrl")), number(item.get("watchedAt"))));
		}
		int logged = watchLog.merge(log);
		return new Result(settings, channels, videos + logged, queued);
	}

	private int restoreSettings(@NonNull JsonObject settings) {
		int count = 0;
		for (Map.Entry<String, JsonElement> entry : object(settings.get("booleans")).entrySet()) {
			JsonElement value = entry.getValue();
			if (Constant.DEFAULT_PREFERENCES.containsKey(entry.getKey())
							&& value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean()) {
				extensions.setEnabled(entry.getKey(), value.getAsBoolean());
				count++;
			}
		}
		for (Map.Entry<String, JsonElement> entry : object(settings.get("numbers")).entrySet()) {
			JsonElement value = entry.getValue();
			if (Constant.WATCHED_THRESHOLD_PERCENT.equals(entry.getKey())
							&& value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
				int percent = Math.max(Constant.MIN_WATCHED_THRESHOLD_PERCENT,
								Math.min(Constant.MAX_WATCHED_THRESHOLD_PERCENT, value.getAsInt()));
				extensions.setInt(entry.getKey(), percent);
				count++;
			}
		}
		for (Map.Entry<String, JsonElement> entry : object(settings.get("strings")).entrySet()) {
			String value = string(entry.getValue());
			if (Constant.CHOICE_KEYS.contains(entry.getKey()) && Constant.QUALITY_CHOICES.contains(value)) {
				extensions.setString(entry.getKey(), value);
				count++;
			}
		}
		return count;
	}

	@NonNull
	private static String readLimited(@NonNull InputStream in) throws IOException {
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		byte[] chunk = new byte[16 * 1024];
		int total = 0;
		int read;
		while ((read = in.read(chunk)) != -1) {
			total += read;
			if (total > MAX_BYTES) throw new IOException("Backup file is too large");
			buffer.write(chunk, 0, read);
		}
		return buffer.toString(StandardCharsets.UTF_8.name());
	}

	@NonNull
	private static JsonObject object(JsonElement element) {
		return element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
	}

	@NonNull
	private static JsonArray array(JsonElement element) {
		return element != null && element.isJsonArray() ? element.getAsJsonArray() : new JsonArray();
	}

	private static String string(JsonElement element) {
		if (element == null || !element.isJsonPrimitive()) return null;
		JsonPrimitive primitive = element.getAsJsonPrimitive();
		return primitive.isString() ? primitive.getAsString() : null;
	}

	private static long number(JsonElement element) {
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) return 0L;
		try {
			return element.getAsLong();
		} catch (NumberFormatException e) {
			return 0L;
		}
	}

	private static String clip(String value, int max) {
		if (value == null) return null;
		return value.length() > max ? value.substring(0, max) : value;
	}

	public record Result(int settings, int channels, int videos, int queued) {
	}
}
