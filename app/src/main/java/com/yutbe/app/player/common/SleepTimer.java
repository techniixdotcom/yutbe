package com.yutbe.app.player.common;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Pauses playback at the end of the current video or at a clock time. It lives only as long as
 * the app process, like a kitchen timer.
 */
@Singleton
public final class SleepTimer {
	public enum Mode {
		OFF,
		END_OF_VIDEO,
		AT_TIME
	}

	@NonNull
	private Mode mode = Mode.OFF;
	private long stopAtMillis;
	@Nullable
	private LocalTime stopAt;

	@Inject
	public SleepTimer() {
	}

	@NonNull
	public synchronized Mode mode() {
		return mode;
	}

	@Nullable
	public synchronized LocalTime stopAt() {
		return mode == Mode.AT_TIME ? stopAt : null;
	}

	public synchronized void cancel() {
		mode = Mode.OFF;
		stopAt = null;
		stopAtMillis = 0L;
	}

	public synchronized void stopAfterThisVideo() {
		mode = Mode.END_OF_VIDEO;
		stopAt = null;
		stopAtMillis = 0L;
	}

	/**
	 * Stops at the next occurrence of the given time of day: today if it is still ahead,
	 * otherwise tomorrow.
	 */
	public synchronized void stopAt(@NonNull LocalTime time) {
		LocalDateTime now = LocalDateTime.now();
		LocalDateTime target = now.toLocalDate().atTime(time);
		if (!target.isAfter(now)) {
			target = target.plusDays(1);
		}
		mode = Mode.AT_TIME;
		stopAt = time;
		stopAtMillis = target.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
	}

	/**
	 * Called when a video ends. Returns true, and switches the timer off, when playback should
	 * stop instead of moving on to the next video.
	 */
	public synchronized boolean consumeEndOfVideo() {
		if (mode != Mode.END_OF_VIDEO) return false;
		cancel();
		return true;
	}

	/**
	 * Called while playing. Returns true, and switches the timer off, once the chosen time has
	 * been reached.
	 */
	public synchronized boolean consumeTimeReached() {
		if (mode != Mode.AT_TIME || System.currentTimeMillis() < stopAtMillis) return false;
		cancel();
		return true;
	}
}
