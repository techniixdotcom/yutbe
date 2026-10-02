package com.hhst.youtubelite.sync;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.webkit.CookieManager;

import androidx.annotation.NonNull;

import com.hhst.youtubelite.Constant;
import com.hhst.youtubelite.R;
import com.hhst.youtubelite.extension.ExtensionManager;
import com.hhst.youtubelite.util.ToastUtils;
import com.tencent.mmkv.MMKV;

import java.security.SecureRandom;
import java.util.List;
import java.util.Locale;

import dagger.hilt.android.qualifiers.ApplicationContext;

import javax.inject.Inject;
import javax.inject.Singleton;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Layer 3 (experimental): replays watch progress to YouTube's watchtime stats
 * endpoints so official clients also show the video as watched. Strictly
 * fail-safe: any repeated failure auto-disables the feature and never affects
 * playback, the ledger, or folder sync.
 */
@Singleton
public class YoutubeHistoryBridge {
	private static final String TAG = "YoutubeHistoryBridge";
	private static final String PREFIX_SYNCED = "watch_sync:yt_synced:";
	private static final String KEY_FAILURES = "watch_sync:yt_failures";
	private static final String CPN_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";
	private static final int MAX_CONSECUTIVE_FAILURES = 3;
	/** Only ping when the position advanced by at least this much since the last ping. */
	private static final long MIN_ADVANCE_MS = 30_000L;
	private static final int MIN_PERCENT_TO_SYNC = 10;
	private static final int MAX_PINGS_PER_TICK = 5;

	@NonNull
	private final Context context;
	@NonNull
	private final MMKV mmkv;
	@NonNull
	private final ExtensionManager extensionManager;
	private final OkHttpClient client = new OkHttpClient();
	private final SecureRandom random = new SecureRandom();
	private final Handler mainHandler = new Handler(Looper.getMainLooper());

	@Inject
	public YoutubeHistoryBridge(@NonNull @ApplicationContext Context context,
	                            @NonNull MMKV mmkv,
	                            @NonNull ExtensionManager extensionManager) {
		this.context = context;
		this.mmkv = mmkv;
		this.extensionManager = extensionManager;
	}

	/** Periodic entry point; must run off the main thread. */
	public void tick(@NonNull List<WatchLedgerEntry> history) {
		try {
			if (!extensionManager.isEnabled(com.hhst.youtubelite.extension.Constant.YOUTUBE_HISTORY_SYNC)) {
				return;
			}
			String cookie = CookieManager.getInstance().getCookie("https://www.youtube.com");
			if (cookie == null || cookie.isBlank()) return; // not logged in; nothing to do
			int pings = 0;
			for (WatchLedgerEntry entry : history) {
				if (pings >= MAX_PINGS_PER_TICK) break;
				if (entry.getDurationMs() <= 0 || entry.percentWatched() < MIN_PERCENT_TO_SYNC) continue;
				long synced = mmkv.decodeLong(PREFIX_SYNCED + entry.getVideoId(), -1L);
				boolean completed = entry.percentWatched() >= 95;
				if (synced >= 0 && entry.getPositionMs() - synced < MIN_ADVANCE_MS && !(completed && synced < entry.getDurationMs())) {
					continue;
				}
				if (ping(entry, cookie)) {
					mmkv.encode(PREFIX_SYNCED + entry.getVideoId(), entry.getPositionMs());
					recordSuccess();
					pings++;
				} else if (recordFailureAndMaybeDisable()) {
					return; // auto-disabled
				}
			}
		} catch (Exception e) {
			Log.w(TAG, "tick failed", e);
		}
	}

	private boolean ping(@NonNull WatchLedgerEntry entry, @NonNull String cookie) {
		try {
			String cpn = randomCpn();
			long lenSec = entry.getDurationMs() / 1000L;
			long posSec = entry.getPositionMs() / 1000L;
			String base = "ns=yt&el=detailpage&cpn=" + cpn + "&docid=" + entry.getVideoId()
							+ "&ver=2&cmt=" + posSec + "&plid=&ei=&len=" + lenSec
							+ "&fs=0&volume=100&muted=0";
			// "playback" init event followed by the watchtime update, like the web player.
			boolean ok = send("https://www.youtube.com/api/stats/playback?" + base + "&rt=1&st=0&et=0", cookie);
			ok &= send("https://www.youtube.com/api/stats/watchtime?" + base
							+ String.format(Locale.US, "&rt=2&st=0&et=%d&state=playing", posSec), cookie);
			return ok;
		} catch (Exception e) {
			Log.w(TAG, "ping failed", e);
			return false;
		}
	}

	private boolean send(@NonNull String url, @NonNull String cookie) {
		Request request = new Request.Builder()
						.url(url)
						.header("User-Agent", Constant.USER_AGENT)
						.header("Cookie", cookie)
						.header("Referer", "https://m.youtube.com/")
						.build();
		try (Response response = client.newCall(request).execute()) {
			return response.isSuccessful();
		} catch (Exception e) {
			Log.w(TAG, "send failed", e);
			return false;
		}
	}

	private void recordSuccess() {
		mmkv.encode(KEY_FAILURES, 0);
	}

	/** @return true if the feature was just auto-disabled. */
	private boolean recordFailureAndMaybeDisable() {
		int failures = mmkv.decodeInt(KEY_FAILURES, 0) + 1;
		mmkv.encode(KEY_FAILURES, failures);
		if (failures < MAX_CONSECUTIVE_FAILURES) return false;
		extensionManager.setEnabled(com.hhst.youtubelite.extension.Constant.YOUTUBE_HISTORY_SYNC, false);
		mmkv.encode(KEY_FAILURES, 0);
		mainHandler.post(() -> ToastUtils.show(context, R.string.youtube_history_sync_disabled));
		return true;
	}

	@NonNull
	private String randomCpn() {
		StringBuilder builder = new StringBuilder(16);
		for (int i = 0; i < 16; i++) {
			builder.append(CPN_ALPHABET.charAt(random.nextInt(CPN_ALPHABET.length())));
		}
		return builder.toString();
	}
}
