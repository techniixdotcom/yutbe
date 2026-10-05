package com.yutbe.app;

import android.app.Application;
import android.os.Build;
import android.util.Log;
import android.webkit.WebSettings;
import android.webkit.WebView;

import com.tencent.mmkv.MMKV;
import com.yutbe.app.util.UserAgents;

import java.io.File;
import java.io.IOException;

import dagger.hilt.android.HiltAndroidApp;

/**
 * Application entry point that initializes shared runtime state and logging.
 */
@HiltAndroidApp
public class App extends Application {

	@Override
	public void onCreate() {
		super.onCreate();
		MMKV.initialize(this);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
			String processName = getProcessName();
			if (!getPackageName().equals(processName)) {
				WebView.setDataDirectorySuffix(processName);
			}
		}
		String webViewUserAgent;
		try {
			webViewUserAgent = WebSettings.getDefaultUserAgent(this);
		} catch (RuntimeException e) {
			// The WebView can be missing or mid-update; the built-in version is used then.
			webViewUserAgent = null;
		}
		Constant.CHROME_MAJOR = UserAgents.chromeMajor(webViewUserAgent);
		Constant.USER_AGENT = UserAgents.mobile(Constant.CHROME_MAJOR);
		startLogging();
	}

	private void startLogging() {
		File logFile = new File(getFilesDir(), Constant.LOGGING_FILENAME);
		try {
			String[] command = new String[]{"logcat", "-v", "threadtime", "*:E", "-f", logFile.getAbsolutePath(), "-n", "1", "-r", "1024"};
			Runtime.getRuntime().exec(command);
		} catch (IOException e) {
			Log.e("App", "Failed to start logging", e);
		}
	}

}
