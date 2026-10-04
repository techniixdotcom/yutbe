package com.yutbe.app.extension;

import static com.yutbe.app.Constant.ENABLE_AUTOPLAY_SUGGESTIONS;
import static com.yutbe.app.Constant.ENABLE_BACKGROUND_PLAY;
import static com.yutbe.app.Constant.ENABLE_IN_APP_MINI_PLAYER;
import static com.yutbe.app.Constant.ENABLE_PIP;
import static com.yutbe.app.Constant.REMEMBER_LAST_POSITION;
import static com.yutbe.app.Constant.REMEMBER_RESIZE_MODE;
import static com.yutbe.app.Constant.SKIP_POI_HIGHLIGHT;
import static com.yutbe.app.Constant.SKIP_SELF_PROMO;
import static com.yutbe.app.Constant.SKIP_SPONSORS;

import java.util.List;
import java.util.Map;

/**
 * Shared constants used across the app.
 */
public final class Constant {
	public static final String ENABLE_DISPLAY_DISLIKES = "enable_display_dislikes";
	public static final String ENABLE_HIDE_SHORTS = "enable_hide_shorts";
	public static final String ENABLE_GREY_WATCHED = "enable_grey_watched";
	public static final String ENABLE_HIDE_SUBSCRIPTIONS = "enable_hide_subscriptions";
	public static final String WATCHED_THRESHOLD_PERCENT = "watched_threshold_percent";
	public static final String GESTURE_SWIPE_DOWN_MINIMIZE = "gesture_swipe_down_minimize";
	public static final String ACTION_BLOCKED_CHANNELS = "action_blocked_channels";
	public static final int DEFAULT_WATCHED_THRESHOLD_PERCENT = 90;
	public static final int MIN_WATCHED_THRESHOLD_PERCENT = 5;
	public static final int MAX_WATCHED_THRESHOLD_PERCENT = 100;
	public static final Map<String, Integer> DEFAULT_INT_PREFERENCES = Map.of(
					WATCHED_THRESHOLD_PERCENT, DEFAULT_WATCHED_THRESHOLD_PERCENT);
	public static final List<String> PERCENT_KEYS = List.of(WATCHED_THRESHOLD_PERCENT);
	public static final String QUALITY_WIFI = "quality_wifi";
	public static final String QUALITY_MOBILE = "quality_mobile";
	public static final String QUALITY_REMEMBERED = "remembered";
	public static final String QUALITY_BEST = "best";
	/**
	 * Values offered for the Wi-Fi and mobile data quality settings, best first.
	 */
	public static final List<String> QUALITY_CHOICES = List.of(
					QUALITY_REMEMBERED, QUALITY_BEST,
					"2160p", "1440p", "1080p", "720p", "480p", "360p", "240p", "144p");
	public static final Map<String, String> DEFAULT_STRING_PREFERENCES = Map.of(
					QUALITY_WIFI, QUALITY_REMEMBERED,
					QUALITY_MOBILE, QUALITY_REMEMBERED);
	public static final List<String> CHOICE_KEYS = List.of(QUALITY_WIFI, QUALITY_MOBILE);
	public static final String ACTION_SLEEP_TIMER = "action_sleep_timer";
	public static final String ACTION_QUEUE = "action_queue";
	public static final String ACTION_WATCH_HISTORY = "action_watch_history";
	public static final List<String> ACTION_KEYS = List.of(ACTION_BLOCKED_CHANNELS, ACTION_SLEEP_TIMER, ACTION_QUEUE,
					ACTION_WATCH_HISTORY);
	/**
	 * Gestures that are turned off while swipe down to minimize is on.
	 */
	public static final List<String> MINIMIZE_DISABLED_KEYS = List.of(
					"gesture_brightness_windowed",
					"gesture_brightness_fullscreen",
					"gesture_volume_windowed",
					"gesture_volume_fullscreen");
	public static final String REMEMBER_QUALITY = "remember_quality";
	public static final String REMEMBER_PLAYBACK_SPEED = "remember_playback_speed";
	// Legacy key kept for migration only.
	public static final String ENABLE_PLAYER_GESTURES = "enable_player_gestures";
	public static final String GESTURE_TAP_WINDOWED = "gesture_tap_windowed";
	public static final String GESTURE_TAP_FULLSCREEN = "gesture_tap_fullscreen";
	public static final String GESTURE_DOUBLE_TAP_WINDOWED = "gesture_double_tap_windowed";
	public static final String GESTURE_DOUBLE_TAP_FULLSCREEN = "gesture_double_tap_fullscreen";
	public static final String GESTURE_LONG_PRESS_WINDOWED = "gesture_long_press_windowed";
	public static final String GESTURE_LONG_PRESS_FULLSCREEN = "gesture_long_press_fullscreen";
	public static final String GESTURE_BRIGHTNESS_WINDOWED = "gesture_brightness_windowed";
	public static final String GESTURE_BRIGHTNESS_FULLSCREEN = "gesture_brightness_fullscreen";
	public static final String GESTURE_VOLUME_WINDOWED = "gesture_volume_windowed";
	public static final String GESTURE_VOLUME_FULLSCREEN = "gesture_volume_fullscreen";
	public static final String GESTURE_SEEK_WINDOWED = "gesture_seek_windowed";
	public static final String GESTURE_SEEK_FULLSCREEN = "gesture_seek_fullscreen";
	public static final String GESTURE_FULLSCREEN_WINDOWED = "gesture_fullscreen_windowed";
	public static final String GESTURE_FULLSCREEN_FULLSCREEN = "gesture_fullscreen_fullscreen";
	public static final List<String> GESTURE_KEYS = List.of(
					GESTURE_TAP_WINDOWED,
					GESTURE_TAP_FULLSCREEN,
					GESTURE_DOUBLE_TAP_WINDOWED,
					GESTURE_DOUBLE_TAP_FULLSCREEN,
					GESTURE_LONG_PRESS_WINDOWED,
					GESTURE_LONG_PRESS_FULLSCREEN,
					GESTURE_BRIGHTNESS_WINDOWED,
					GESTURE_BRIGHTNESS_FULLSCREEN,
					GESTURE_VOLUME_WINDOWED,
					GESTURE_VOLUME_FULLSCREEN,
					GESTURE_SEEK_WINDOWED,
					GESTURE_SEEK_FULLSCREEN,
					GESTURE_FULLSCREEN_WINDOWED,
					GESTURE_FULLSCREEN_FULLSCREEN
	);
	public static final Map<String, Boolean> DEFAULT_PREFERENCES = Map.ofEntries(
					Map.entry(ENABLE_DISPLAY_DISLIKES, true),
					Map.entry(ENABLE_HIDE_SHORTS, false),
					Map.entry(ENABLE_GREY_WATCHED, false),
					Map.entry(ENABLE_HIDE_SUBSCRIPTIONS, false),
					Map.entry(GESTURE_SWIPE_DOWN_MINIMIZE, false),
					Map.entry(SKIP_SPONSORS, true),
					Map.entry(SKIP_SELF_PROMO, true),
					Map.entry(SKIP_POI_HIGHLIGHT, true),
					Map.entry(REMEMBER_LAST_POSITION, true),
					Map.entry(ENABLE_AUTOPLAY_SUGGESTIONS, true),
					Map.entry(REMEMBER_QUALITY, true),
					Map.entry(ENABLE_BACKGROUND_PLAY, true),
					Map.entry(ENABLE_PIP, true),
					Map.entry(ENABLE_IN_APP_MINI_PLAYER, true),
					Map.entry(REMEMBER_RESIZE_MODE, false),
					Map.entry(REMEMBER_PLAYBACK_SPEED, false),
					Map.entry(GESTURE_TAP_WINDOWED, true),
					Map.entry(GESTURE_TAP_FULLSCREEN, true),
					Map.entry(GESTURE_DOUBLE_TAP_WINDOWED, true),
					Map.entry(GESTURE_DOUBLE_TAP_FULLSCREEN, true),
					Map.entry(GESTURE_LONG_PRESS_WINDOWED, true),
					Map.entry(GESTURE_LONG_PRESS_FULLSCREEN, true),
					Map.entry(GESTURE_BRIGHTNESS_WINDOWED, true),
					Map.entry(GESTURE_BRIGHTNESS_FULLSCREEN, true),
					Map.entry(GESTURE_VOLUME_WINDOWED, true),
					Map.entry(GESTURE_VOLUME_FULLSCREEN, true),
					Map.entry(GESTURE_SEEK_WINDOWED, true),
					Map.entry(GESTURE_SEEK_FULLSCREEN, true),
					Map.entry(GESTURE_FULLSCREEN_WINDOWED, true),
					Map.entry(GESTURE_FULLSCREEN_FULLSCREEN, true)
	);

	private Constant() {
	}
}
