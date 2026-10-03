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

import androidx.annotation.NonNull;

import com.yutbe.app.R;

import java.util.List;

/**
 * Value object for app logic.
 */
public record Extension(String key, int title, int summary, int icon, List<Extension> children) {

	public Extension {
		children = children == null ? List.of() : children;
	}

	public static Extension root() {
		return page(R.string.extension, 0, 0, List.of(
						page(R.string.interface_category, R.string.interface_summary, R.drawable.ic_settings, List.of(
										toggle(Constant.ENABLE_DISPLAY_DISLIKES, R.string.display_dislikes),
										toggle(Constant.ENABLE_HIDE_SHORTS, R.string.hide_shorts),
										toggle(Constant.ENABLE_GREY_WATCHED, R.string.grey_watched_videos, R.string.grey_watched_videos_summary),
										item(Constant.WATCHED_THRESHOLD_PERCENT, R.string.watched_threshold, R.string.watched_threshold_summary),
										item(Constant.ACTION_BLOCKED_CHANNELS, R.string.blocked_channels, R.string.blocked_channels_summary)
						)),
						page(R.string.player, R.string.playback_summary, R.drawable.ic_play, List.of(
										toggle(REMEMBER_LAST_POSITION, R.string.remember_last_position),
										toggle(ENABLE_AUTOPLAY_SUGGESTIONS, R.string.autoplay_suggestions),
										toggle(Constant.REMEMBER_QUALITY, R.string.remember_quality),
										toggle(Constant.REMEMBER_PLAYBACK_SPEED, R.string.remember_playback_speed),
										toggle(REMEMBER_RESIZE_MODE, R.string.remember_resize_mode)
						)),
						page(R.string.gesture, R.string.gesture_summary, R.drawable.ic_gesture, List.of(
										toggle(Constant.GESTURE_SWIPE_DOWN_MINIMIZE, R.string.gesture_swipe_down_minimize, R.string.gesture_swipe_down_minimize_summary),
										page(R.string.gesture_single_tap, 0, 0, List.of(
														toggle(Constant.GESTURE_TAP_WINDOWED, R.string.enable_in_windowed),
														toggle(Constant.GESTURE_TAP_FULLSCREEN, R.string.enable_in_fullscreen)
										)),
										page(R.string.gesture_double_tap, 0, 0, List.of(
														toggle(Constant.GESTURE_DOUBLE_TAP_WINDOWED, R.string.enable_in_windowed),
														toggle(Constant.GESTURE_DOUBLE_TAP_FULLSCREEN, R.string.enable_in_fullscreen)
										)),
										page(R.string.gesture_long_press_speed, 0, 0, List.of(
														toggle(Constant.GESTURE_LONG_PRESS_WINDOWED, R.string.enable_in_windowed),
														toggle(Constant.GESTURE_LONG_PRESS_FULLSCREEN, R.string.enable_in_fullscreen)
										)),
										page(R.string.brightness, 0, 0, List.of(
														toggle(Constant.GESTURE_BRIGHTNESS_WINDOWED, R.string.enable_in_windowed),
														toggle(Constant.GESTURE_BRIGHTNESS_FULLSCREEN, R.string.enable_in_fullscreen)
										)),
										page(R.string.volume, 0, 0, List.of(
														toggle(Constant.GESTURE_VOLUME_WINDOWED, R.string.enable_in_windowed),
														toggle(Constant.GESTURE_VOLUME_FULLSCREEN, R.string.enable_in_fullscreen)
										)),
										page(R.string.gesture_seek, 0, 0, List.of(
														toggle(Constant.GESTURE_SEEK_WINDOWED, R.string.enable_in_windowed),
														toggle(Constant.GESTURE_SEEK_FULLSCREEN, R.string.enable_in_fullscreen)
										)),
										page(R.string.gesture_fullscreen_swipe, 0, 0, List.of(
														toggle(Constant.GESTURE_FULLSCREEN_WINDOWED, R.string.enable_in_windowed),
														toggle(Constant.GESTURE_FULLSCREEN_FULLSCREEN, R.string.enable_in_fullscreen)
										))
						)),
						page(R.string.background_mini_player, R.string.background_mini_player_summary, R.drawable.ic_pip, List.of(
										toggle(ENABLE_PIP, R.string.pip),
										toggle(ENABLE_IN_APP_MINI_PLAYER, R.string.in_app_mini_player),
										toggle(ENABLE_BACKGROUND_PLAY, R.string.background_play)
						)),
						page(R.string.sponsorblock, R.string.sponsorblock_summary, R.drawable.ic_block, List.of(
										toggle(SKIP_SPONSORS, R.string.skip_sponsors),
										toggle(SKIP_SELF_PROMO, R.string.skip_sponsors_selfpromo),
										toggle(SKIP_POI_HIGHLIGHT, R.string.skip_sponsors_highlight)
						))
		));
	}

	private static Extension page(int title, int summary, int icon, List<Extension> children) {
		return new Extension(null, title, summary, icon, children);
	}

	private static Extension toggle(String key, int title) {
		return new Extension(key, title, 0, 0, List.of());
	}

	private static Extension toggle(String key, int title, int summary) {
		return new Extension(key, title, summary, 0, List.of());
	}

	private static Extension item(String key, int title, int summary) {
		return new Extension(key, title, summary, 0, List.of());
	}

	/**
	 * True when this item or one of its children controls one of the given keys.
	 */
	public boolean controlsAny(@NonNull List<String> keys) {
		if (key != null && keys.contains(key)) return true;
		for (Extension child : children) {
			if (child.controlsAny(keys)) return true;
		}
		return false;
	}

	public boolean isPercent() {
		return key != null && Constant.PERCENT_KEYS.contains(key);
	}

	public boolean isAction() {
		return key != null && Constant.ACTION_KEYS.contains(key);
	}

	public boolean hasChildren() {
		return !children.isEmpty();
	}
}
