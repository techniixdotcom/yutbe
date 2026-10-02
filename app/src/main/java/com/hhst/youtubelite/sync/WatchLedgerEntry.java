package com.hhst.youtubelite.sync;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One watch event in the cross-device ledger. Serialized as a single JSON line
 * inside per-device append-only ledger files.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WatchLedgerEntry {
	private String videoId;
	private String title;
	private String author;
	private String thumbnailUrl;
	private long positionMs;
	private long durationMs;
	private long timestamp;
	private String deviceId;
	private String deviceName;
	/** Tombstone: removes the video from history on every synced device. */
	private boolean deleted;

	public int percentWatched() {
		if (durationMs <= 0) return 0;
		return (int) Math.min(100, (positionMs * 100) / durationMs);
	}
}
