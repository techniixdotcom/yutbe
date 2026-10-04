package com.yutbe.app.update;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.text.format.Formatter;
import android.util.Log;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;
import androidx.core.content.pm.PackageInfoCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.yutbe.app.R;
import com.yutbe.app.util.ToastUtils;
import com.yutbe.app.util.ViewUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.inject.Inject;
import javax.inject.Singleton;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Checks GitHub for a newer release, downloads its APK with a progress bar and hands it to
 * Android's installer. The APK is only installed if it is this app, newer, and signed with the
 * same key.
 */
@Singleton
public final class AppUpdater {
	private static final String TAG = "AppUpdater";
	private static final long MAX_APK_BYTES = 300L * 1024L * 1024L;
	private static final String APK_MIME = "application/vnd.android.package-archive";

	@NonNull
	private final OkHttpClient client;
	@NonNull
	private final Gson gson;
	@NonNull
	private final Executor executor;
	private final AtomicBoolean checkedThisLaunch = new AtomicBoolean(false);
	private final AtomicBoolean busy = new AtomicBoolean(false);

	@Inject
	public AppUpdater(@NonNull OkHttpClient client, @NonNull Gson gson, @NonNull Executor executor) {
		this.client = client;
		this.gson = gson;
		this.executor = executor;
	}

	/**
	 * Result of a manual check, for the About screen.
	 */
	public interface Listener {
		void onFinished(boolean updateFound);
	}

	/**
	 * Checks once per app launch; only says something when an update exists.
	 */
	public void checkOnStart(@NonNull Activity activity) {
		if (!checkedThisLaunch.compareAndSet(false, true)) return;
		check(activity, false, null);
	}

	/**
	 * Checks now and reports the outcome, including "up to date" and errors.
	 */
	public void checkManually(@NonNull Activity activity, @Nullable Listener listener) {
		check(activity, true, listener);
	}

	private void check(@NonNull Activity activity, boolean manual, @Nullable Listener listener) {
		String apiUrl = activity.getString(R.string.update_api_url).trim();
		if (!apiUrl.startsWith("https://")) {
			if (manual) ToastUtils.show(activity, R.string.update_check_unavailable);
			if (listener != null) listener.onFinished(false);
			return;
		}
		String repo = activity.getString(R.string.source_link).trim();
		Context app = activity.getApplicationContext();
		executor.execute(() -> {
			Release release = null;
			boolean failed = false;
			try {
				release = fetchLatest(apiUrl, repo);
			} catch (IOException | RuntimeException e) {
				Log.w(TAG, "update check failed", e);
				failed = true;
			}
			String installed = installedVersionName(app);
			Release found = release != null && isNewerVersion(installed, release.tag) ? release : null;
			boolean error = failed;
			activity.runOnUiThread(() -> {
				if (listener != null) listener.onFinished(found != null);
				if (activity.isFinishing() || activity.isDestroyed()) return;
				if (found != null) {
					offer(activity, found, installed);
				} else if (manual) {
					ToastUtils.show(activity, error ? R.string.failed_to_check_for_updates : R.string.no_updates_available);
				}
			});
		});
	}

	@Nullable
	private Release fetchLatest(@NonNull String apiUrl, @NonNull String repo) throws IOException {
		Request request = new Request.Builder()
						.url(apiUrl)
						.header("Accept", "application/vnd.github+json")
						.header("Cache-Control", "no-cache")
						.build();
		try (Response response = client.newCall(request).execute()) {
			// 404 means no release has been published yet.
			if (response.code() == 404) return null;
			// GitHub limits anonymous API use per network; fall back to the public release page.
			if (response.code() == 403 || response.code() == 429) return fetchLatestFromPage(repo);
			if (!response.isSuccessful()) throw new IOException("HTTP " + response.code());
			ResponseBody body = response.body();
			JsonObject json = gson.fromJson(body.string(), JsonObject.class);
			if (json == null || !json.has("tag_name")) return null;
			if (json.has("draft") && json.get("draft").getAsBoolean()) return null;
			if (json.has("prerelease") && json.get("prerelease").getAsBoolean()) return null;
			String tag = json.get("tag_name").getAsString();
			String page = json.has("html_url") ? json.get("html_url").getAsString() : null;
			if (page != null && !page.startsWith(repo + "/releases/")) page = null;
			String downloadPrefix = repo + "/releases/download/";
			String apkUrl = null;
			long apkSize = -1L;
			JsonElement assetsElement = json.get("assets");
			JsonArray assets = assetsElement != null && assetsElement.isJsonArray() ? assetsElement.getAsJsonArray() : new JsonArray();
			for (JsonElement element : assets) {
				if (!element.isJsonObject()) continue;
				JsonObject asset = element.getAsJsonObject();
				String name = asset.has("name") ? asset.get("name").getAsString() : "";
				String url = asset.has("browser_download_url") ? asset.get("browser_download_url").getAsString() : "";
				String lower = name.toLowerCase(Locale.ROOT);
				if (!lower.endsWith(".apk") || lower.contains("debug")) continue;
				if (!url.startsWith(downloadPrefix)) continue;
				apkUrl = url;
				apkSize = asset.has("size") ? asset.get("size").getAsLong() : -1L;
				break;
			}
			return new Release(tag, page, apkUrl, apkSize);
		}
	}

	/**
	 * Reads the newest release tag from github.com/OWNER/REPO/releases/latest, which redirects to
	 * /releases/tag/TAG, and assumes the APK follows the build's naming: yutbe1.2.3.apk.
	 */
	@Nullable
	private Release fetchLatestFromPage(@NonNull String repo) throws IOException {
		OkHttpClient noRedirects = client.newBuilder().followRedirects(false).followSslRedirects(false).build();
		Request request = new Request.Builder().url(repo + "/releases/latest").head().build();
		try (Response response = noRedirects.newCall(request).execute()) {
			String location = response.header("Location");
			String prefix = repo + "/releases/tag/";
			if (location == null || !location.startsWith(prefix)) return null;
			String tag = location.substring(prefix.length());
			if (!tag.matches("[A-Za-z0-9._-]{1,40}")) return null;
			String version = tag.replaceFirst("^[vV]", "");
			String apkUrl = repo + "/releases/download/" + tag + "/yutbe" + version + ".apk";
			return new Release(tag, location, apkUrl, -1L);
		}
	}

	private void offer(@NonNull Activity activity, @NonNull Release release, @NonNull String installed) {
		MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity)
						.setTitle(R.string.update_title)
						.setNegativeButton(R.string.update_later, null);
		if (release.apkUrl != null) {
			builder.setMessage(activity.getString(R.string.update_message, release.tag, installed))
							.setPositiveButton(R.string.update_now, (d, w) -> download(activity, release));
		} else if (release.pageUrl != null) {
			builder.setMessage(activity.getString(R.string.update_no_apk, release.tag))
							.setPositiveButton(R.string.update_open_page, (d, w) -> openPage(activity, release.pageUrl));
		} else {
			return;
		}
		builder.show();
	}

	private void download(@NonNull Activity activity, @NonNull Release release) {
		if (!busy.compareAndSet(false, true)) return;
		int pad = ViewUtils.dpToPx(activity, 24);
		LinearLayout content = new LinearLayout(activity);
		content.setOrientation(LinearLayout.VERTICAL);
		content.setPadding(pad, pad / 2, pad, 0);
		LinearProgressIndicator progress = new LinearProgressIndicator(activity);
		progress.setIndeterminate(true);
		progress.setMax(100);
		TextView status = new TextView(activity);
		status.setPadding(0, pad / 2, 0, 0);
		status.setText(R.string.update_downloading);
		content.addView(progress);
		content.addView(status);

		AtomicBoolean cancelled = new AtomicBoolean(false);
		Call[] call = new Call[1];
		AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
						.setTitle(activity.getString(R.string.update_title_version, release.tag))
						.setView(content)
						.setCancelable(false)
						.setNegativeButton(R.string.cancel, (d, w) -> {
							cancelled.set(true);
							if (call[0] != null) call[0].cancel();
						})
						.show();

		Context app = activity.getApplicationContext();
		File dir = new File(app.getCacheDir(), "updates");
		File target = new File(dir, "yutbe-update.apk");
		executor.execute(() -> {
			boolean ok = false;
			try {
				if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Cannot create " + dir);
				File partial = new File(dir, "yutbe-update.apk.part");
				Request request = new Request.Builder().url(release.apkUrl).header("Cache-Control", "no-store").build();
				call[0] = client.newCall(request);
				try (Response response = call[0].execute()) {
					if (!response.isSuccessful()) throw new IOException("HTTP " + response.code());
					ResponseBody body = response.body();
					long total = body.contentLength() > 0 ? body.contentLength() : release.size;
					if (total > MAX_APK_BYTES) throw new IOException("Update is too large");
					try (InputStream in = body.byteStream(); OutputStream out = new FileOutputStream(partial)) {
						byte[] buffer = new byte[64 * 1024];
						long done = 0L;
						long lastUi = 0L;
						int read;
						while ((read = in.read(buffer)) != -1) {
							if (cancelled.get()) throw new IOException("Cancelled");
							out.write(buffer, 0, read);
							done += read;
							if (done > MAX_APK_BYTES) throw new IOException("Update is too large");
							long now = System.currentTimeMillis();
							if (now - lastUi >= 150L) {
								lastUi = now;
								long current = done;
								activity.runOnUiThread(() -> showProgress(app, progress, status, current, total));
							}
						}
						long finalDone = done;
						activity.runOnUiThread(() -> showProgress(app, progress, status, finalDone, total));
					}
				}
				if (target.exists() && !target.delete()) throw new IOException("Cannot replace old update");
				if (!partial.renameTo(target)) throw new IOException("Cannot finish download");
				ok = true;
			} catch (IOException | RuntimeException e) {
				if (!cancelled.get()) Log.w(TAG, "update download failed", e);
			}
			boolean success = ok;
			activity.runOnUiThread(() -> {
				busy.set(false);
				if (dialog.isShowing()) dialog.dismiss();
				if (cancelled.get() || activity.isFinishing() || activity.isDestroyed()) return;
				if (!success) {
					ToastUtils.show(activity, R.string.update_download_failed);
					return;
				}
				offerInstall(activity, release, target);
			});
		});
	}

	private static void showProgress(@NonNull Context context,
	                                 @NonNull LinearProgressIndicator progress,
	                                 @NonNull TextView status,
	                                 long done,
	                                 long total) {
		if (total > 0) {
			int percent = (int) Math.min(100L, done * 100L / total);
			if (progress.isIndeterminate()) progress.setIndeterminate(false);
			progress.setProgressCompat(percent, true);
			status.setText(context.getString(R.string.update_progress, percent,
							Formatter.formatShortFileSize(context, done), Formatter.formatShortFileSize(context, total)));
		} else {
			status.setText(Formatter.formatShortFileSize(context, done));
		}
	}

	/**
	 * Download finished: asks whether to install now.
	 */
	private void offerInstall(@NonNull Activity activity, @NonNull Release release, @NonNull File apk) {
		new MaterialAlertDialogBuilder(activity)
						.setTitle(R.string.update_ready_title)
						.setMessage(activity.getString(R.string.update_ready_message, release.tag))
						.setCancelable(false)
						.setNegativeButton(R.string.update_later, null)
						.setPositiveButton(R.string.update_install, (d, w) -> install(activity, apk))
						.show();
	}

	/**
	 * Checks the APK and hands it to Android's installer.
	 */
	private void install(@NonNull Activity activity, @NonNull File apk) {
		if (!isTrustedUpdate(activity, apk)) {
			//noinspection ResultOfMethodCallIgnored
			apk.delete();
			new MaterialAlertDialogBuilder(activity)
							.setTitle(R.string.update_title)
							.setMessage(R.string.update_verify_failed)
							.setPositiveButton(R.string.confirm, null)
							.show();
			return;
		}
		if (!activity.getPackageManager().canRequestPackageInstalls()) {
			new MaterialAlertDialogBuilder(activity)
							.setTitle(R.string.update_title)
							.setMessage(R.string.update_allow_installs)
							.setNegativeButton(R.string.cancel, null)
							.setNeutralButton(R.string.update_settings, (d, w) -> {
								Intent settings = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
												Uri.parse("package:" + activity.getPackageName()));
								try {
									activity.startActivity(settings);
								} catch (ActivityNotFoundException ignored) {
									ToastUtils.show(activity, R.string.update_download_failed);
								}
							})
							.setPositiveButton(R.string.update_install, (d, w) -> install(activity, apk))
							.show();
			return;
		}
		Uri uri = FileProvider.getUriForFile(activity, activity.getPackageName() + ".provider", apk);
		Intent intent = new Intent(Intent.ACTION_VIEW)
						.setDataAndType(uri, APK_MIME)
						.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
		try {
			activity.startActivity(intent);
		} catch (ActivityNotFoundException e) {
			ToastUtils.show(activity, R.string.update_download_failed);
		}
	}

	/**
	 * Same package, newer version, same signing key as the installed app.
	 */
	@SuppressWarnings("deprecation")
	private static boolean isTrustedUpdate(@NonNull Context context, @NonNull File apk) {
		try {
			PackageManager pm = context.getPackageManager();
			int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
							? PackageManager.GET_SIGNING_CERTIFICATES
							: PackageManager.GET_SIGNATURES;
			PackageInfo archive = pm.getPackageArchiveInfo(apk.getAbsolutePath(), flags);
			PackageInfo installed = pm.getPackageInfo(context.getPackageName(), flags);
			if (archive == null || !context.getPackageName().equals(archive.packageName)) return false;
			if (PackageInfoCompat.getLongVersionCode(archive) <= PackageInfoCompat.getLongVersionCode(installed)) return false;
			Set<String> archiveSigners = signers(archive);
			if (archiveSigners.isEmpty()) {
				// Some Android versions cannot read signatures from a file that is not installed.
				// Android's installer still refuses an update signed with a different key.
				return true;
			}
			return archiveSigners.equals(signers(installed));
		} catch (PackageManager.NameNotFoundException | RuntimeException e) {
			return false;
		}
	}

	@NonNull
	@SuppressWarnings("deprecation")
	private static Set<String> signers(@NonNull PackageInfo info) {
		Signature[] signatures;
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
			signatures = info.signingInfo == null ? null : info.signingInfo.getApkContentsSigners();
		} else {
			signatures = info.signatures;
		}
		Set<String> out = new HashSet<>();
		if (signatures == null) return out;
		for (Signature signature : signatures) {
			out.add(signature.toCharsString());
		}
		return out;
	}

	private static void openPage(@NonNull Activity activity, @NonNull String url) {
		try {
			activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
		} catch (ActivityNotFoundException ignored) {
			ToastUtils.show(activity, R.string.update_download_failed);
		}
	}

	@NonNull
	private static String installedVersionName(@NonNull Context context) {
		try {
			String name = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
			return name == null ? "" : name;
		} catch (PackageManager.NameNotFoundException e) {
			return "";
		}
	}

	public static boolean isNewerVersion(@NonNull String current, @NonNull String latest) {
		String[] cur = current.replaceFirst("^[vV]", "").split("[.-]");
		String[] lat = latest.replaceFirst("^[vV]", "").split("[.-]");
		int length = Math.max(cur.length, lat.length);
		for (int i = 0; i < length; i++) {
			int c = i < cur.length ? part(cur[i]) : 0;
			int l = i < lat.length ? part(lat[i]) : 0;
			if (l != c) return l > c;
		}
		return false;
	}

	private static int part(@NonNull String value) {
		String digits = value.replaceAll("\\D", "");
		if (digits.isEmpty()) return 0;
		try {
			return Integer.parseInt(digits.length() > 9 ? digits.substring(0, 9) : digits);
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	private static final class Release {
		@NonNull
		final String tag;
		@Nullable
		final String pageUrl;
		@Nullable
		final String apkUrl;
		final long size;

		Release(@NonNull String tag, @Nullable String pageUrl, @Nullable String apkUrl, long size) {
			this.tag = tag;
			this.pageUrl = pageUrl;
			this.apkUrl = apkUrl;
			this.size = size;
		}
	}
}
