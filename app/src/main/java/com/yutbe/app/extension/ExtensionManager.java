package com.yutbe.app.extension;

import androidx.annotation.NonNull;

import com.tencent.mmkv.MMKV;

import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Manager that stores extension toggles and restores defaults.
 */
@Singleton
public class ExtensionManager {
	private static final String KEY_VERSION = "preferences:version";

	private final MMKV mmkv;

	@Inject
	public ExtensionManager(MMKV mmkv) {
		this.mmkv = mmkv;
		initializeDefaultPreferences();
	}

	private void initializeDefaultPreferences() {
		initializeGesturePreferences();
		for (Map.Entry<String, Boolean> entry : Constant.DEFAULT_PREFERENCES.entrySet()) {
			String key = prefKey(entry.getKey());
			if (!mmkv.contains(key)) {
				mmkv.encode(key, entry.getValue());
			}
		}
	}

	private void initializeGesturePreferences() {
		for (String key : Constant.GESTURE_KEYS) {
			if (mmkv.contains(prefKey(key))) {
				return;
			}
		}
		boolean enabled = true;
		String key = prefKey(Constant.ENABLE_PLAYER_GESTURES);
		if (mmkv.contains(key)) {
			enabled = mmkv.decodeBool(key, true);
		}
		for (String gesture : Constant.GESTURE_KEYS) {
			mmkv.encode(prefKey(gesture), enabled);
		}
	}

	private String prefKey(String key) {
		return "preferences:" + key;
	}

	public void setEnabled(String key, boolean enable) {
		String pref = prefKey(key);
		boolean changed = !mmkv.contains(pref) || mmkv.decodeBool(pref, Boolean.TRUE.equals(Constant.DEFAULT_PREFERENCES.getOrDefault(key, false))) != enable;
		mmkv.encode(pref, enable);
		if (changed) {
			bumpVersion();
		}
	}

	public boolean isEnabled(String key) {
		return mmkv.decodeBool(prefKey(key), Boolean.TRUE.equals(Constant.DEFAULT_PREFERENCES.getOrDefault(key, false)));
	}

	public int getInt(String key) {
		int fallback = Constant.DEFAULT_INT_PREFERENCES.getOrDefault(key, 0);
		return mmkv.decodeInt(prefKey(key), fallback);
	}

	public void setInt(String key, int value) {
		String pref = prefKey(key);
		boolean changed = !mmkv.contains(pref) || mmkv.decodeInt(pref, value + 1) != value;
		mmkv.encode(pref, value);
		if (changed) {
			bumpVersion();
		}
	}

	@NonNull
	public String getString(String key) {
		String fallback = Constant.DEFAULT_STRING_PREFERENCES.getOrDefault(key, "");
		String value = mmkv.decodeString(prefKey(key), fallback);
		return value == null ? fallback : value;
	}

	public void setString(String key, @NonNull String value) {
		String pref = prefKey(key);
		boolean changed = !value.equals(mmkv.decodeString(pref, null));
		mmkv.encode(pref, value);
		if (changed) {
			bumpVersion();
		}
	}

	/**
	 * Tells every page that content filter data (watched videos, blocked channels) changed.
	 */
	public void notifyChanged() {
		bumpVersion();
	}

	public void resetToDefault() {
		boolean changed = false;
		for (Map.Entry<String, String> entry : Constant.DEFAULT_STRING_PREFERENCES.entrySet()) {
			String key = prefKey(entry.getKey());
			if (!entry.getValue().equals(mmkv.decodeString(key, null))) {
				changed = true;
			}
			mmkv.encode(key, entry.getValue());
		}
		for (Map.Entry<String, Integer> entry : Constant.DEFAULT_INT_PREFERENCES.entrySet()) {
			String key = prefKey(entry.getKey());
			int value = entry.getValue();
			if (!mmkv.contains(key) || mmkv.decodeInt(key, value) != value) {
				changed = true;
			}
			mmkv.encode(key, value);
		}
		for (Map.Entry<String, Boolean> entry : Constant.DEFAULT_PREFERENCES.entrySet()) {
			String key = prefKey(entry.getKey());
			boolean value = entry.getValue();
			if (!mmkv.contains(key) || mmkv.decodeBool(key, value) != value) {
				changed = true;
			}
			mmkv.encode(key, value);
		}
		if (changed) {
			bumpVersion();
		}
	}

	public Map<String, Boolean> getAllPreferences() {
		Map<String, Boolean> allPreferences = new HashMap<>();
		for (String key : Constant.DEFAULT_PREFERENCES.keySet()) {
			allPreferences.put(key, isEnabled(key));
		}
		return allPreferences;
	}

	public long version() {
		return mmkv.decodeLong(KEY_VERSION, 0L);
	}

	private void bumpVersion() {
		mmkv.encode(KEY_VERSION, version() + 1L);
	}

}

