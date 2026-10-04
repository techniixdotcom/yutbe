package com.yutbe.app.browser;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.util.UnstableApi;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.yutbe.app.R;
import com.yutbe.app.filter.ContentFilters;
import com.yutbe.app.history.LocalHistoryActivity;
import com.yutbe.app.history.WatchHistory;
import com.yutbe.app.downloader.ui.DownloadActivity;
import com.yutbe.app.downloader.ui.DownloadDialog;
import com.yutbe.app.downloader.ui.PlaylistDownloadDialog;
import com.yutbe.app.downloader.ui.PlaylistDownloadItem;
import com.yutbe.app.extension.ExtensionActivity;
import com.yutbe.app.extension.ExtensionManager;
import com.yutbe.app.extractor.YoutubeExtractor;
import com.yutbe.app.gallery.GalleryActivity;
import com.yutbe.app.player.YuTbePlayer;
import com.yutbe.app.player.queue.QueueItem;
import com.yutbe.app.player.queue.QueueRepository;
import com.yutbe.app.ui.AboutActivity;
import com.yutbe.app.ui.MainActivity;
import com.yutbe.app.ui.MediaItemMenuDialog;
import com.yutbe.app.util.ToastUtils;
import com.yutbe.app.util.UrlUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Component that handles app logic.
 */
@UnstableApi
public final class JavascriptInterface {
	private static final String TAG = "JavascriptInterface";
	@NonNull
	private final Context context;
	@NonNull
	private final YoutubeWebview webView;
	@NonNull
	private final YoutubeExtractor youtubeExtractor;
	@NonNull
	private final YuTbePlayer player;
	@NonNull
	private final ExtensionManager extensionManager;
	@NonNull
	private final TabManager tabManager;
	@NonNull
	private final QueueRepository queueRepository;
	@NonNull
	private final ContentFilters contentFilters;
	@NonNull
	private final WatchHistory watchHistory;
	private static final int WATCH_LOG_ON_PAGE = 30;
	@NonNull
	private final Gson gson = new Gson();
	@NonNull
	private final Handler handler = new Handler(Looper.getMainLooper());

	public JavascriptInterface(@NonNull YoutubeWebview webView, @NonNull YoutubeExtractor youtubeExtractor, @NonNull YuTbePlayer player, @NonNull ExtensionManager extensionManager, @NonNull TabManager tabManager, @NonNull QueueRepository queueRepository, @NonNull ContentFilters contentFilters, @NonNull WatchHistory watchHistory) {
		this.contentFilters = contentFilters;
		this.watchHistory = watchHistory;
		this.context = webView.getContext();
		this.webView = webView;
		this.youtubeExtractor = youtubeExtractor;
		this.player = player;
		this.extensionManager = extensionManager;
		this.tabManager = tabManager;
		this.queueRepository = queueRepository;
	}

	@Nullable
	static MediaItemMenuPayload parseMediaItemMenuPayload(@Nullable String payloadJson) {
		String normalized = normalizePayloadJson(payloadJson);
		if (normalized == null) return null;
		try {
			return MediaItemMenuPayload.fromJson(normalized);
		} catch (RuntimeException ignored) {
			return null;
		}
	}

	@Nullable
	private static String normalizePayloadJson(@Nullable String payloadJson) {
		if (payloadJson == null) return null;
		String trimmed = payloadJson.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	@Nullable
	private static String getPayloadString(@Nullable JsonObject object, @NonNull String... keys) {
		if (object == null) return null;
		for (String key : keys) {
			if (key == null || !object.has(key) || object.get(key) == null || object.get(key).isJsonNull())
				continue;
			try {
				String value = object.get(key).getAsString();
				if (value != null && !value.isBlank()) return value;
			} catch (Exception ignored) {
			}
		}
		return null;
	}

	private static long getPayloadLong(@Nullable JsonObject object, @NonNull String... keys) {
		if (object == null) return 0L;
		for (String key : keys) {
			if (key == null || !object.has(key) || object.get(key) == null || object.get(key).isJsonNull())
				continue;
			try {
				String raw = object.get(key).getAsString();
				if (raw == null || raw.isBlank()) continue;
				String trimmed = raw.trim();
				if (trimmed.contains(":")) {
					long seconds = 0L;
					for (String part : trimmed.split(":")) {
						seconds = seconds * 60L + Long.parseLong(part.trim());
					}
					return Math.max(0L, seconds);
				}
				return Math.max(0L, Long.parseLong(trimmed));
			} catch (Exception ignored) {
			}
		}
		return 0L;
	}

	@android.webkit.JavascriptInterface
	public void finishRefresh() {
		if (!webView.isTrustedPage()) return;
		handler.post(() -> {
			if (webView.getParent() instanceof SwipeRefreshLayout)
				((SwipeRefreshLayout) webView.getParent()).setRefreshing(false);
		});
	}

	@android.webkit.JavascriptInterface
	public void setRefreshLayoutEnabled(boolean enabled) {
		if (!webView.isTrustedPage()) return;
		handler.post(() -> {
			if (webView.getParent() instanceof SwipeRefreshLayout)
				((SwipeRefreshLayout) webView.getParent()).setEnabled(enabled);
		});
	}

	@android.webkit.JavascriptInterface
	public void download(@Nullable String url) {
		if (!webView.isTrustedPage()) return;
		if (url != null) handler.post(() -> new DownloadDialog(url, context, youtubeExtractor).show());
	}

	@android.webkit.JavascriptInterface
	public void downloadPlaylist(@Nullable String payloadJson) {
		if (!webView.isTrustedPage()) return;
		if (payloadJson == null || payloadJson.isBlank()) return;
		JsonObject payload;
		try {
			payload = gson.fromJson(payloadJson, JsonObject.class);
		} catch (Exception e) {
			Log.e(TAG, "Failed to parse playlist payload", e);
			return;
		}
		if (payload == null) return;
		handler.post(() -> {
			try {
				JsonArray payloadItems = payload.has("items") && payload.get("items").isJsonArray() ? payload.getAsJsonArray("items") : null;
				if (payloadItems == null || payloadItems.isEmpty()) {
					ToastUtils.show(context, R.string.playlist_download_empty);
					return;
				}

				List<PlaylistDownloadItem> dialogItems = new ArrayList<>();
				int playlistIndex = 0;
				for (JsonElement element : payloadItems) {
					if (element == null || !element.isJsonObject()) continue;
					JsonObject itemPayload = element.getAsJsonObject();
					String videoId = getPayloadString(itemPayload, "videoId");
					String videoUrl = getPayloadString(itemPayload, "videoUrl", "url");
					if (videoId == null || videoId.isBlank() || videoUrl == null || videoUrl.isBlank())
						continue;

					PlaylistDownloadItem item = new PlaylistDownloadItem(playlistIndex++, videoId, videoUrl);
					item.setTitle(getPayloadString(itemPayload, "title"));
					item.setAuthor(getPayloadString(itemPayload, "author"));
					item.setThumbnailUrl(getPayloadString(itemPayload, "thumbnailUrl", "thumbnail"));
					item.setDurationSeconds(getPayloadLong(itemPayload, "durationSeconds", "duration"));
					item.setAvailabilityStatus(PlaylistDownloadItem.AvailabilityStatus.READY);
					item.setSelected(true);
					dialogItems.add(item);
				}

				if (dialogItems.isEmpty()) {
					ToastUtils.show(context, R.string.playlist_download_empty);
					return;
				}

				String seededTitle = getPayloadString(payload, "title");
				String playlistId = getPayloadString(payload, "playlistId");
				new PlaylistDownloadDialog(seededTitle == null || seededTitle.isBlank() ? null : seededTitle, dialogItems, null, playlistId == null || playlistId.isBlank() ? null : playlistId, context, youtubeExtractor, null).show();
			} catch (Exception e) {
				Log.e(TAG, "Failed to open playlist download dialog", e);
			}
		});
	}

	@android.webkit.JavascriptInterface
	public void extension() {
		if (!webView.isTrustedPage()) return;
		handler.post(() -> {
			Intent intent = ExtensionActivity.intent(context);
			if (!(context instanceof Activity)) {
				intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			}
			context.startActivity(intent);
		});
	}

	@android.webkit.JavascriptInterface
	public void download() {
		if (!webView.isTrustedPage()) return;
		handler.post(() -> context.startActivity(new Intent(context, DownloadActivity.class)));
	}

	@android.webkit.JavascriptInterface
	public void about() {
		if (!webView.isTrustedPage()) return;
		handler.post(() -> context.startActivity(new Intent(context, AboutActivity.class)));
	}

	@android.webkit.JavascriptInterface
	public void play(@Nullable String url) {
		if (!webView.isTrustedPage()) return;
		if (url != null) handler.post(() -> player.play(url));
	}

	@android.webkit.JavascriptInterface
	public void showHint(@Nullable String text, long durationMs) {
		if (!webView.isTrustedPage()) return;
		if (text == null) return;
		handler.post(() -> {
			MainActivity activity = getMainActivity();
			if (activity != null) {
				activity.showHint(text, durationMs);
			}
		});
	}

	@android.webkit.JavascriptInterface
	public void hideHint() {
		if (!webView.isTrustedPage()) return;
		handler.post(() -> {
			MainActivity activity = getMainActivity();
			if (activity != null) {
				activity.hideHint();
			}
		});
	}

	@android.webkit.JavascriptInterface
	public void goBack() {
		if (!webView.isTrustedPage()) return;
		handler.post(() -> {
			MainActivity activity = getMainActivity();
			if (activity != null) {
				activity.handleAppBack();
				return;
			}
			tabManager.evaluateJavascript("window.dispatchEvent(new Event('onGoBack'));", null);
			tabManager.goBack();
		});
	}

	@Nullable
	private MainActivity getMainActivity() {
		Context ctx = context;
		while (ctx instanceof ContextWrapper wrapper) {
			if (ctx instanceof MainActivity activity) {
				return activity;
			}
			ctx = wrapper.getBaseContext();
		}
		return null;
	}

	@android.webkit.JavascriptInterface
	public void addToQueue(@Nullable String itemJson) {
		if (!webView.isTrustedPage()) return;
		enqueue(itemJson, false);
	}

	@android.webkit.JavascriptInterface
	public void playNext(@Nullable String itemJson) {
		if (!webView.isTrustedPage()) return;
		enqueue(itemJson, true);
	}

	private void enqueue(@Nullable String itemJson, boolean next) {
		if (itemJson == null) return;
		handler.post(() -> {
			try {
				final QueueItem item;
				MediaItemMenuPayload mediaItemPayload = parseMediaItemMenuPayload(itemJson);
				item = mediaItemPayload != null ? mediaItemPayload.toQueueItem() : gson.fromJson(itemJson, QueueItem.class);
				if (item == null || item.getVideoUrl() == null) return;
				String videoId = item.getVideoId();
				if (videoId == null || videoId.isBlank() || item.getTitle() == null || item.getTitle().isBlank()) {
					ToastUtils.show(context, R.string.queue_item_unavailable);
					return;
				}
				item.setVideoId(videoId);
				if (!queueRepository.isEnabled()) {
					queueRepository.setEnabled(true);
				}
				if (next) {
					if (!queueRepository.addNext(item, player.getVideoId())) {
						ToastUtils.show(context, R.string.queue_item_already_playing);
						return;
					}
				} else {
					queueRepository.add(item);
				}
				player.refreshQueueNav();
				ToastUtils.show(context, next ? R.string.queue_item_play_next : R.string.queue_item_added);
			} catch (Exception e) {
				Log.e(TAG, "Failed to add queue item", e);
			}
		});
	}

	@android.webkit.JavascriptInterface
	public void openWith(@Nullable String url) {
		if (!webView.isTrustedPage()) return;
		if (url == null || url.isBlank()) return;
		handler.post(() -> {
			Intent send = new Intent(Intent.ACTION_SEND);
			send.putExtra(Intent.EXTRA_TEXT, url);
			send.setType("text/plain");
			context.startActivity(Intent.createChooser(send, context.getString(R.string.open_with)));
		});
	}

	void launchMediaItemMenu(@NonNull MediaItemMenuPayload payload) {
		if (context instanceof Activity activity && (activity.isFinishing() || activity.isDestroyed())) {
			return;
		}
		new MediaItemMenuDialog(context, payload, youtubeExtractor, queueRepository, player, contentFilters,
						webView::syncPreferences).show();
	}

	@android.webkit.JavascriptInterface
	public void showMediaItemMenu(@Nullable String payloadJson) {
		if (!webView.isTrustedPage()) return;
		MediaItemMenuPayload payload = parseMediaItemMenuPayload(payloadJson);
		if (payload == null) return;
		handler.post(() -> launchMediaItemMenu(payload));
	}

	@android.webkit.JavascriptInterface
	public void showQueueItemUnavailable() {
		if (!webView.isTrustedPage()) return;
		ToastUtils.show(context, R.string.queue_item_unavailable);
	}

	@android.webkit.JavascriptInterface
	public boolean isQueueEnabled() {
		if (!webView.isTrustedPage()) return false;
		return queueRepository.isEnabled();
	}

	@android.webkit.JavascriptInterface
	public void hidePlayer() {
		if (!webView.isTrustedPage()) return;
		handler.post(tabManager::hidePlayer);
	}

	@android.webkit.JavascriptInterface
	public void setPlayerHeight(int height) {
		if (!webView.isTrustedPage()) return;
		handler.post(() -> player.setHeight(height));
	}

	@android.webkit.JavascriptInterface
	public boolean seekLoadedVideo(@Nullable String url, long positionMs) {
		if (!webView.isTrustedPage()) return false;
		return player.seekLoadedVideo(url, positionMs);
	}

	@android.webkit.JavascriptInterface
	public void onPosterLongPress(@Nullable String urlsJson) {
		if (!webView.isTrustedPage()) return;
		if (urlsJson != null) {
			handler.post(() -> {
				List<String> urls;
				try {
					urls = gson.fromJson(urlsJson, new TypeToken<List<String>>() {
					}.getType());
				} catch (RuntimeException e) {
					return;
				}
				if (urls == null) return;
				urls.removeIf(url -> url == null || !url.startsWith("https://"));
				if (urls.isEmpty()) return;
				Intent intent = new Intent(context, GalleryActivity.class);
				intent.putStringArrayListExtra("thumbnails", new ArrayList<>(urls));
				intent.putExtra("filename", DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now()));
				context.startActivity(intent);
			});
		}
	}

	@NonNull
	@android.webkit.JavascriptInterface
	public String getNavLabels() {
		if (!webView.isTrustedPage()) return "{}";
		return navLabelsJson();
	}

	@NonNull
	private String navLabelsJson() {
		JsonObject labels = new JsonObject();
		labels.addProperty("home", context.getString(R.string.nav_home));
		labels.addProperty("shorts", context.getString(R.string.nav_shorts));
		labels.addProperty("subscriptions", context.getString(R.string.nav_subscriptions));
		labels.addProperty("you", context.getString(R.string.nav_you));
		return labels.toString();
	}

	@NonNull
	@android.webkit.JavascriptInterface
	public String getWatchLog() {
		if (!webView.isTrustedPage()) return "{}";
		return watchLogJson().toString();
	}

	@android.webkit.JavascriptInterface
	public void openWatchHistory() {
		if (!webView.isTrustedPage()) return;
		handler.post(() -> {
			context.startActivity(new Intent(context, LocalHistoryActivity.class));
		});
	}

	/**
	 * The newest local history entries plus the texts the page needs to show them.
	 */
	@NonNull
	private JsonObject watchLogJson() {
		JsonObject root = new JsonObject();
		root.addProperty("title", context.getString(R.string.local_history));
		root.addProperty("viewAll", context.getString(R.string.view_all));
		JsonArray items = new JsonArray();
		for (WatchHistory.Entry entry : watchHistory.entries()) {
			if (items.size() >= WATCH_LOG_ON_PAGE) break;
			JsonObject item = new JsonObject();
			item.addProperty("id", entry.videoId());
			item.addProperty("title", entry.title());
			item.addProperty("author", entry.author());
			item.addProperty("at", entry.watchedAt());
			items.add(item);
		}
		root.add("items", items);
		return root;
	}

	@NonNull
	@android.webkit.JavascriptInterface
	public String getContentFilters() {
		if (!webView.isTrustedPage()) return "{}";
		return contentFilters.scriptData();
	}

	@NonNull
	@android.webkit.JavascriptInterface
	public String getPreferences() {
		if (!webView.isTrustedPage()) return "{}";
		return gson.toJson(extensionManager.getAllPreferences());
	}

	@android.webkit.JavascriptInterface
	public void openTab(@Nullable String url, @Nullable String tag) {
		if (!webView.isTrustedPage()) return;
		if (url == null || tag == null) return;
		handler.post(() -> tabManager.openTab(url, tag));
	}

	@android.webkit.JavascriptInterface
	public long getResumePosition(@Nullable String vid) {
		if (!webView.isTrustedPage()) return 0L;
		return player.getResumePosition(vid);
	}


	/**
	 * Values the page reads synchronously, pushed into the page as window.__yutbeState when the
	 * message bridge is used.
	 */
	@NonNull
	String bridgeState(@Nullable String url) {
		JsonObject state = new JsonObject();
		state.addProperty("preferences", gson.toJson(extensionManager.getAllPreferences()));
		state.addProperty("contentFilters", contentFilters.scriptData());
		state.addProperty("navLabels", navLabelsJson());
		state.addProperty("queueEnabled", queueRepository.isEnabled());
		JsonObject resume = new JsonObject();
		String videoId = YoutubeExtractor.getVideoId(url);
		if (videoId != null) {
			resume.addProperty(videoId, player.getResumePosition(videoId));
		}
		state.add("resume", resume);
		state.addProperty("watchLog", watchLogJson().toString());
		return state.toString();
	}

	/**
	 * Runs a call that the page sent through the message bridge. Only the names below can be
	 * called, with their arguments type-checked.
	 */
	void dispatch(@NonNull String json) {
		JsonObject message;
		try {
			JsonElement parsed = JsonParser.parseString(json);
			if (!parsed.isJsonObject()) return;
			message = parsed.getAsJsonObject();
		} catch (RuntimeException e) {
			return;
		}
		String name = argString(message.get("m"));
		JsonElement rawArgs = message.get("a");
		JsonArray args = rawArgs != null && rawArgs.isJsonArray() ? rawArgs.getAsJsonArray() : new JsonArray();
		if (name == null) return;
		switch (name) {
			case "finishRefresh" -> finishRefresh();
			case "setRefreshLayoutEnabled" -> setRefreshLayoutEnabled(argBoolean(args, 0));
			case "download" -> {
				if (args.isEmpty()) download();
				else download(argString(args, 0));
			}
			case "downloadPlaylist" -> downloadPlaylist(argString(args, 0));
			case "extension" -> extension();
			case "about" -> about();
			case "play" -> play(argString(args, 0));
			case "showHint" -> showHint(argString(args, 0), argLong(args, 1));
			case "hideHint" -> hideHint();
			case "goBack" -> goBack();
			case "addToQueue" -> addToQueue(argString(args, 0));
			case "playNext" -> playNext(argString(args, 0));
			case "openWith" -> openWith(argString(args, 0));
			case "showMediaItemMenu" -> showMediaItemMenu(argString(args, 0));
			case "showQueueItemUnavailable" -> showQueueItemUnavailable();
			case "hidePlayer" -> hidePlayer();
			case "setPlayerHeight" -> setPlayerHeight((int) argLong(args, 0));
			case "onPosterLongPress" -> onPosterLongPress(argString(args, 0));
			case "openTab" -> openTab(argString(args, 0), argString(args, 1));
			case "openWatchHistory" -> openWatchHistory();
			case "seekLoadedVideo" -> {
				String url = argString(args, 0);
				if (url != null && !seekLoadedVideo(url, argLong(args, 1)) && UrlUtils.isTrustedPageUrl(url)) {
					webView.loadUrl(url);
				}
			}
			default -> {
			}
		}
	}

	@Nullable
	private static String argString(@Nullable JsonElement element) {
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) return null;
		return element.getAsString();
	}

	@Nullable
	private static String argString(@NonNull JsonArray args, int index) {
		return index < args.size() ? argString(args.get(index)) : null;
	}

	private static long argLong(@NonNull JsonArray args, int index) {
		if (index >= args.size()) return 0L;
		JsonElement element = args.get(index);
		if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) return 0L;
		double value = element.getAsDouble();
		if (Double.isNaN(value)) return 0L;
		return (long) Math.max(Long.MIN_VALUE, Math.min(Long.MAX_VALUE, value));
	}

	private static boolean argBoolean(@NonNull JsonArray args, int index) {
		if (index >= args.size()) return false;
		JsonElement element = args.get(index);
		return element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean() && element.getAsBoolean();
	}
}
