package com.hhst.youtubelite.sync;

import android.content.Context;
import android.content.Intent;
import android.content.UriPermission;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.hhst.youtubelite.extension.Constant;
import com.hhst.youtubelite.extension.ExtensionManager;
import com.hhst.youtubelite.extractor.VideoDetails;
import com.tencent.mmkv.MMKV;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import dagger.hilt.android.qualifiers.ApplicationContext;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Layer 1 + 2 of watch sync: an append-only, per-device JSONL ledger stored in a
 * user-picked shared folder (kept in sync between devices by Syncthing or similar).
 * Never touches the network itself and is designed to tolerate missing folders,
 * partial lines and concurrent sync conflicts (union merge, latest timestamp wins).
 */
@Singleton
public class WatchSyncManager {
	private static final String TAG = "WatchSync";
	private static final String KEY_DEVICE_ID = "watch_sync:device_id";
	private static final String KEY_FOLDER_URI = "watch_sync:folder_uri";
	private static final String KEY_PENDING = "watch_sync:pending";
	private static final String PREFIX_ENTRY = "watch_sync:entry:";
	private static final String PREFIX_LAST_RECORD = "watch_sync:last_record:";
	private static final String LEDGER_FILE_PREFIX = "watch-ledger-";
	private static final String LEDGER_FILE_SUFFIX = ".jsonl";
	private static final long RECORD_THROTTLE_MS = 8_000L;
	private static final long SYNC_INTERVAL_S = 30L;
	/** Matches PlayerPreferences' progress storage so merged positions resume. */
	private static final String PREFIX_PROGRESS = "progress:";

	@NonNull
	private final Context context;
	@NonNull
	private final MMKV mmkv;
	@NonNull
	private final Gson gson;
	@NonNull
	private final ExtensionManager extensionManager;
	@NonNull
	private final YoutubeHistoryBridge historyBridge;
	private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
	private final Handler mainHandler = new Handler(Looper.getMainLooper());
	private volatile boolean started;

	@Inject
	public WatchSyncManager(@NonNull @ApplicationContext Context context,
	                        @NonNull MMKV mmkv,
	                        @NonNull Gson gson,
	                        @NonNull ExtensionManager extensionManager,
	                        @NonNull YoutubeHistoryBridge historyBridge) {
		this.context = context;
		this.mmkv = mmkv;
		this.gson = gson;
		this.extensionManager = extensionManager;
		this.historyBridge = historyBridge;
	}

	public synchronized void start() {
		if (started) return;
		started = true;
		scheduler.execute(this::safeMerge);
		scheduler.scheduleWithFixedDelay(() -> {
			safeFlush();
			safeMerge();
			historyBridge.tick(getHistory());
		}, SYNC_INTERVAL_S, SYNC_INTERVAL_S, TimeUnit.SECONDS);
	}

	public boolean isSyncEnabled() {
		return extensionManager.isEnabled(Constant.WATCH_SYNC);
	}

	@NonNull
	public String getDeviceId() {
		String id = mmkv.decodeString(KEY_DEVICE_ID, null);
		if (id == null || id.isBlank()) {
			id = UUID.randomUUID().toString().substring(0, 8);
			mmkv.encode(KEY_DEVICE_ID, id);
		}
		return id;
	}

	@Nullable
	public Uri getSyncFolder() {
		String value = mmkv.decodeString(KEY_FOLDER_URI, null);
		return value == null ? null : Uri.parse(value);
	}

	public void setSyncFolder(@NonNull Uri uri) {
		try {
			context.getContentResolver().takePersistableUriPermission(
							uri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
		} catch (Exception e) {
			Log.w(TAG, "persist permission failed", e);
		}
		mmkv.encode(KEY_FOLDER_URI, uri.toString());
		scheduler.execute(() -> {
			safeFlush();
			safeMerge();
		});
	}

	public boolean hasFolderPermission() {
		Uri folder = getSyncFolder();
		if (folder == null) return false;
		for (UriPermission permission : context.getContentResolver().getPersistedUriPermissions()) {
			if (permission.getUri().equals(folder) && permission.isReadPermission() && permission.isWritePermission()) {
				return true;
			}
		}
		return true; // some providers don't report persisted grants; attempt anyway
	}

	/**
	 * Record a watch event. Throttled per video; updates the local index and queues
	 * a JSONL line for the shared ledger file.
	 */
	public void recordWatch(@Nullable VideoDetails details, long positionMs) {
		if (!isSyncEnabled() || details == null || details.getId() == null) return;
		try {
			long durationMs = details.getDuration() == null ? 0L : details.getDuration() * 1000L;
			recordWatch(details.getId(), details.getTitle(), details.getAuthor(),
							details.getThumbnailUrl(), positionMs, durationMs);
		} catch (Exception e) {
			Log.w(TAG, "record failed", e);
		}
	}

	public void recordWatch(@NonNull String videoId, @Nullable String title, @Nullable String author,
	                        @Nullable String thumbnailUrl, long positionMs, long durationMs) {
		if (!isSyncEnabled()) return;
		long now = System.currentTimeMillis();
		long last = mmkv.decodeLong(PREFIX_LAST_RECORD + videoId, 0L);
		boolean completed = durationMs > 0 && positionMs >= durationMs - 5_000L;
		if (now - last < RECORD_THROTTLE_MS && !completed) return;
		mmkv.encode(PREFIX_LAST_RECORD + videoId, now);
		WatchLedgerEntry entry = new WatchLedgerEntry(videoId, title, author, thumbnailUrl,
						positionMs, durationMs, now, getDeviceId(), deviceName(), false);
		mmkv.encode(PREFIX_ENTRY + videoId, gson.toJson(entry));
		appendLine(gson.toJson(entry));
	}

	/** Reset a video to unwatched (0%) — survives sync merges via its timestamp. */
	public void markUnwatched(@NonNull String videoId, @Nullable String title, @Nullable String author,
	                          @Nullable String thumbnailUrl) {
		WatchLedgerEntry entry = new WatchLedgerEntry(videoId, title, author, thumbnailUrl,
						0L, 0L, System.currentTimeMillis(), getDeviceId(), deviceName(), false);
		mmkv.encode(PREFIX_ENTRY + videoId, gson.toJson(entry));
		appendLine(gson.toJson(entry));
	}

	private void appendLine(@NonNull String line) {
		synchronized (KEY_PENDING) {
			String pending = mmkv.decodeString(KEY_PENDING, "");
			mmkv.encode(KEY_PENDING, pending + line + "\n");
		}
	}

	@NonNull
	public List<WatchLedgerEntry> getHistory() {
		List<WatchLedgerEntry> result = new ArrayList<>();
		String[] keys = mmkv.allKeys();
		if (keys == null) return result;
		for (String key : keys) {
			if (!key.startsWith(PREFIX_ENTRY)) continue;
			try {
				WatchLedgerEntry entry = gson.fromJson(mmkv.decodeString(key, null), WatchLedgerEntry.class);
				if (entry != null && entry.getVideoId() != null) result.add(entry);
			} catch (Exception ignored) {
			}
		}
		result.sort((a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));
		return result;
	}

	public void removeHistoryEntry(@NonNull String videoId) {
		mmkv.removeValueForKey(PREFIX_ENTRY + videoId);
		mmkv.removeValueForKey(PREFIX_PROGRESS + videoId);
		// Tombstone so synced devices drop it too instead of resurrecting it.
		WatchLedgerEntry tombstone = new WatchLedgerEntry(videoId, null, null, null,
						0L, 0L, System.currentTimeMillis(), getDeviceId(), deviceName(), true);
		appendLine(gson.toJson(tombstone));
	}

	/** Videos at 85%+ watched, for graying out thumbnails in the web UI. */
	@NonNull
	public List<String> getWatchedVideoIds() {
		List<String> ids = new ArrayList<>();
		for (WatchLedgerEntry entry : getHistory()) {
			if (entry.percentWatched() >= 85) ids.add(entry.getVideoId());
		}
		return ids;
	}

	public void clearHistory() {
		String[] keys = mmkv.allKeys();
		if (keys == null) return;
		for (String key : keys) {
			if (key.startsWith(PREFIX_ENTRY) || key.startsWith(PREFIX_PROGRESS)
							|| key.startsWith(PREFIX_LAST_RECORD)) {
				mmkv.removeValueForKey(key);
			}
		}
	}

	/** Append queued ledger lines to this device's file in the shared folder. */
	private void safeFlush() {
		try {
			String pending;
			synchronized (KEY_PENDING) {
				pending = mmkv.decodeString(KEY_PENDING, "");
				if (pending.isEmpty()) return;
				mmkv.encode(KEY_PENDING, "");
			}
			Uri folderUri = getSyncFolder();
			if (folderUri == null) {
				restorePending(pending);
				return;
			}
			DocumentFile folder = DocumentFile.fromTreeUri(context, folderUri);
			if (folder == null || !folder.canWrite()) {
				restorePending(pending);
				return;
			}
			String fileName = LEDGER_FILE_PREFIX + getDeviceId() + LEDGER_FILE_SUFFIX;
			DocumentFile file = folder.findFile(fileName);
			if (file == null) {
				file = folder.createFile("application/jsonl", fileName);
			}
			if (file == null) {
				restorePending(pending);
				return;
			}
			try (OutputStream out = context.getContentResolver().openOutputStream(file.getUri(), "wa")) {
				if (out == null) {
					restorePending(pending);
					return;
				}
				out.write(pending.getBytes(StandardCharsets.UTF_8));
			}
		} catch (Exception e) {
			Log.w(TAG, "flush failed", e);
		}
	}

	private void restorePending(@NonNull String pending) {
		synchronized (KEY_PENDING) {
			mmkv.encode(KEY_PENDING, pending + mmkv.decodeString(KEY_PENDING, ""));
		}
	}

	/** Merge every device's ledger file into the local index (latest timestamp wins). */
	private void safeMerge() {
		try {
			Uri folderUri = getSyncFolder();
			if (folderUri == null) return;
			DocumentFile folder = DocumentFile.fromTreeUri(context, folderUri);
			if (folder == null || !folder.canRead()) return;
			Map<String, WatchLedgerEntry> merged = new HashMap<>();
			for (DocumentFile file : folder.listFiles()) {
				String name = file.getName();
				if (name == null || !name.startsWith(LEDGER_FILE_PREFIX) || !name.endsWith(LEDGER_FILE_SUFFIX)) {
					continue;
				}
				readLedgerFile(file, merged);
			}
			for (WatchLedgerEntry entry : merged.values()) {
				applyEntry(entry);
			}
		} catch (Exception e) {
			Log.w(TAG, "merge failed", e);
		}
	}

	private void readLedgerFile(@NonNull DocumentFile file, @NonNull Map<String, WatchLedgerEntry> merged) {
		try (InputStream in = context.getContentResolver().openInputStream(file.getUri());
		     BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
			String line;
			while ((line = reader.readLine()) != null) {
				line = line.trim();
				if (line.isEmpty()) continue;
				try {
					WatchLedgerEntry entry = gson.fromJson(line, WatchLedgerEntry.class);
					if (entry == null || entry.getVideoId() == null) continue;
					WatchLedgerEntry existing = merged.get(entry.getVideoId());
					if (existing == null || entry.getTimestamp() > existing.getTimestamp()) {
						merged.put(entry.getVideoId(), entry);
					}
				} catch (Exception ignored) {
					// tolerate partially-written lines from an interrupted sync
				}
			}
		} catch (Exception e) {
			Log.w(TAG, "read failed: " + file.getName(), e);
		}
	}

	private void applyEntry(@NonNull WatchLedgerEntry entry) {
		String key = PREFIX_ENTRY + entry.getVideoId();
		try {
			WatchLedgerEntry local = gson.fromJson(mmkv.decodeString(key, null), WatchLedgerEntry.class);
			if (local != null && local.getTimestamp() >= entry.getTimestamp()) return;
			if (entry.isDeleted()) {
				mmkv.removeValueForKey(key);
				mmkv.removeValueForKey(PREFIX_PROGRESS + entry.getVideoId());
				return;
			}
			mmkv.encode(key, gson.toJson(entry));
			// Write through to PlayerPreferences' progress storage so playback resumes
			// at the position synced from the other device.
			long existingTs = 0L;
			String progressJson = mmkv.decodeString(PREFIX_PROGRESS + entry.getVideoId(), null);
			if (progressJson != null) {
				Map<String, Object> map = gson.fromJson(progressJson, new TypeToken<Map<String, Object>>() {
				}.getType());
				Object ts = map == null ? null : map.get("timestamp");
				if (ts instanceof Number) existingTs = ((Number) ts).longValue();
			}
			if (entry.getTimestamp() > existingTs && entry.getDurationMs() > 0) {
				Map<String, Object> progress = new HashMap<>();
				progress.put("position", entry.getPositionMs());
				progress.put("duration", entry.getDurationMs());
				progress.put("timestamp", entry.getTimestamp());
				mmkv.encode(PREFIX_PROGRESS + entry.getVideoId(), gson.toJson(progress));
			}
		} catch (Exception e) {
			Log.w(TAG, "apply failed", e);
		}
	}

	@NonNull
	private String deviceName() {
		String model = Build.MODEL;
		return model == null || model.isBlank() ? "Android" : model;
	}

	@NonNull
	public Executor io() {
		return scheduler;
	}

	public void runOnMain(@NonNull Runnable runnable) {
		mainHandler.post(runnable);
	}
}
