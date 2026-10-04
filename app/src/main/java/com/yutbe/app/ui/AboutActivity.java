package com.yutbe.app.ui;

import android.R.id;
import android.R.string;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.yutbe.app.Constant;
import com.yutbe.app.R;
import com.yutbe.app.update.AppUpdater;
import com.yutbe.app.cache.AppCacheCleaner;
import com.yutbe.app.util.ToastUtils;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Screen that shows app info, cache actions, and update checks.
 */
@AndroidEntryPoint
public class AboutActivity extends AppCompatActivity {
	private static final String TAG = "AboutActivity";
	@Inject
	AppUpdater appUpdater;
	@Inject
	AppCacheCleaner appCacheCleaner;
	private TextView updateText;
	private View updateLayout;

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		EdgeToEdge.enable(this);
		setContentView(R.layout.activity_about);
		View mainView = findViewById(id.content);
		ViewCompat.setOnApplyWindowInsetsListener(mainView, (v, insets) -> {
			Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
			v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
			return insets;
		});

		ImageView iconView = findViewById(R.id.app_icon);
		TextView name = findViewById(R.id.app_name);
		TextView version = findViewById(R.id.app_version);
		TextView desc = findViewById(R.id.app_description);
		View sourceLayout = findViewById(R.id.source_code_layout);
		updateLayout = findViewById(R.id.check_update_layout);
		updateText = findViewById(R.id.check_update_text);
		View clearLayout = findViewById(R.id.clear_cache_layout);
		View exportLayout = findViewById(R.id.export_log_layout);

		try {
			PackageManager pm = getPackageManager();
			PackageInfo info = pm.getPackageInfo(getPackageName(), 0);
			iconView.setImageDrawable(info.applicationInfo.loadIcon(pm));
			name.setText(R.string.app_name);
			version.setText(getString(R.string.version, info.versionName));
		} catch (Exception e) {
			Log.e(TAG, "Failed to load app info", e);
		}

		desc.setText(R.string.app_description);
		sourceLayout.setOnClickListener(v -> {
			Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.source_link)));
			startActivity(intent);
		});

		updateLayout.setOnClickListener(v -> checkForUpdates());
		clearLayout.setOnClickListener(v -> showClearCacheDialog());
		exportLayout.setOnClickListener(v -> exportLogs());
	}

	private void showClearCacheDialog() {
		new MaterialAlertDialogBuilder(this)
						.setTitle(R.string.clear_cache)
						.setMessage(R.string.clear_cache_confirmation)
						.setPositiveButton(R.string.clear, (dialog, which) -> clearAppCache())
						.setNegativeButton(string.cancel, null)
						.show();
	}

	private void checkForUpdates() {
		updateLayout.setEnabled(false);
		updateText.setText(R.string.checking_for_updates);
		appUpdater.checkManually(this, updateFound -> {
			updateLayout.setEnabled(true);
			updateText.setText(R.string.check_for_updates);
		});
	}


	private void clearAppCache() {
		new Thread(() -> {
			try {
				appCacheCleaner.clear(AboutActivity.this);
				ToastUtils.show(AboutActivity.this, R.string.cache_cleared);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				Log.e(TAG, "Cache clear interrupted", e);
			} catch (Exception e) {
				Log.e(TAG, "Failed to clear cache", e);
			}
		}).start();
	}

	private void exportLogs() {
		new Thread(() -> {
			try {
				String version = "unknown";
				try {
					version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
				} catch (Exception ignored) {
				}

				String time = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
				File destFile = new File(getExternalCacheDir(), "yutbe_error_log_" + time + ".txt");
				File srcFile = new File(getFilesDir(), Constant.LOGGING_FILENAME);

				String header = String.format(Locale.US, "--------- Device Info ---------\nDevice: %s\nModel: %s\nAndroid: %s\nApp Version: %s\n-------------------------------\n\n", Build.DEVICE, Build.MODEL, Build.VERSION.RELEASE, version);

				FileUtils.writeStringToFile(destFile, header, StandardCharsets.UTF_8);

				if (srcFile.exists()) {
					String logs = FileUtils.readFileToString(srcFile, StandardCharsets.UTF_8);
					FileUtils.writeStringToFile(destFile, logs, StandardCharsets.UTF_8, true);
				}

				Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".provider", destFile);
				Intent intent = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

				runOnUiThread(() -> startActivity(Intent.createChooser(intent, getString(R.string.export_error_log))));

			} catch (Exception e) {
				Log.e(TAG, "Log export error", e);
				ToastUtils.show(this, R.string.failed_to_export_log);
			}
		}).start();
	}



}
