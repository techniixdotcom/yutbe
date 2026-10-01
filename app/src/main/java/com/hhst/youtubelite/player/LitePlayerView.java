package com.hhst.youtubelite.player;


import android.app.Activity;
import android.app.PictureInPictureParams;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Outline;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Rational;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewTreeObserver;
import android.view.ViewParent;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.media3.common.text.Cue;
import androidx.media3.common.text.CueGroup;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.DefaultTimeBar;
import androidx.media3.ui.PlayerView;
import androidx.media3.ui.SubtitleView;

import com.hhst.youtubelite.R;
import com.hhst.youtubelite.player.common.Constant;
import com.hhst.youtubelite.player.common.PlayerPreferences;
import com.hhst.youtubelite.player.controller.ControllerState;
import com.hhst.youtubelite.player.sponsor.SponsorBlockManager;
import com.hhst.youtubelite.player.sponsor.SponsorOverlayView;
import com.hhst.youtubelite.util.ImageUtils;
import com.hhst.youtubelite.util.ViewUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import dagger.hilt.android.scopes.ActivityScoped;
import lombok.Getter;

/**
 * Custom player view with mini-player, fullscreen, and PiP support.
 */
@UnstableApi
@AndroidEntryPoint
@ActivityScoped
public class LitePlayerView extends PlayerView {
	private static final float SUBTITLE_LINE_FRACTION = 0.92f;
	private static final float SUBTITLE_POSITION_FRACTION = 0.5f;
	private static final int MINI_BAR_HEIGHT_DP = 64;
	private static final long MINI_TRANSITION_MS = 260L;
	private static final int[] MINI_PLAYER_TAP_TARGET_IDS = {
					R.id.btn_mini_queue,
					R.id.btn_mini_close,
					R.id.btn_mini_prev,
					R.id.btn_mini_play,
					R.id.btn_mini_pause,
					R.id.btn_mini_next,
					R.id.btn_mini_restore
	};
	@NonNull
	private final Rect miniPlayerTouchBounds = new Rect();
	@NonNull
	private final int[] miniPlayerLocationOnScreen = new int[2];
	@Inject
	SponsorBlockManager sponsor;
	@Inject
	Activity activity;
	@Inject
	PlayerPreferences prefs;
	@Nullable
	private SubtitleView subtitleView;
	@Getter
	private boolean isFs = false;
	private int playerWidth = 0;
	private int playerHeight = 0;
	private int normalHeight = 0;
	@Getter
	private boolean inAppMiniPlayer = false;
	@Nullable
	private Runnable onMiniPlayerRestore;
	@Nullable
	private Runnable onMiniPlayerClose;
	@Nullable
	private Runnable onMiniPlayerBackgroundTap;
	private int miniPlayerRestoreResizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT;
	private boolean miniPlayerRestoreFullscreen;
	private float miniPlayerTouchDownRawX;
	private float miniPlayerTouchDownRawY;
	private float miniPlayerStartTranslationX;
	private float miniPlayerStartTranslationY;
	private float miniPlayerSavedTranslationX;
	private float miniPlayerSavedTranslationY;
	private boolean miniPlayerTranslationStashedForFullscreen;
	private boolean miniPlayerTouchCaptured;
	private boolean miniPlayerDragging;
	@Nullable
	private View miniPlayerPendingTapTarget;
	private boolean parentInsetsSuppressed;
	private int parentPaddingLeft;
	private int parentPaddingTop;
	private int parentPaddingRight;
	private int parentPaddingBottom;
	private boolean portraitNormalStateRequested;
	private boolean miniAnimating;
	private int miniAnimToken;

	public LitePlayerView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
	}

	public LitePlayerView(Context context, @Nullable AttributeSet attrs) {
		super(context, attrs);
	}

	public LitePlayerView(Context context) {
		super(context);
	}

	public void setup() {
		setControllerAnimationEnabled(false);
		setControllerHideOnTouch(false);
		setControllerAutoShow(false);
		setControllerShowTimeoutMs(0);
		setOutlineProvider(new ViewOutlineProvider() {
			@Override
			public void getOutline(View view, Outline outline) {
				outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dpToPx(16));
			}
		});
		setClipToOutline(false);
		setResizeMode(prefs.getResizeMode());
		ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) getLayoutParams();
		params.topMargin = ViewUtils.dpToPx(activity, Constant.TOP_MARGIN_DP);
		params.width = ConstraintLayout.LayoutParams.MATCH_PARENT;
		int screenWidth = ViewUtils.getScreenWidth(activity);
		params.height = (int) (screenWidth * 9 / 16.0);
		setLayoutParams(params);

		addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
			if (right - left != playerWidth || bottom - top != playerHeight) {
				playerWidth = right - left;
				playerHeight = bottom - top;
			}
			if (inAppMiniPlayer) invalidateOutline();
		});
	}

	public void applyControllerState(@NonNull ControllerState.Mode previousState,
	                                 @NonNull ControllerState.Mode newState,
	                                 int fsOrientation,
	                                 int defaultResizeMode) {
		post(() -> {
			switch (newState) {
				case NORMAL, MINI_PLAYER -> applyNormalState(defaultResizeMode);
				case FULLSCREEN_UNLOCK, FULLSCREEN_LOCK ->
								applyFullscreenState(previousState, fsOrientation, defaultResizeMode);
				case PIP -> applyPictureInPictureState(previousState);
			}
		});
	}

	public void updatePlayerLayout(boolean fullscreen) {
		ViewGroup.LayoutParams layoutParams = getLayoutParams();
		if (layoutParams instanceof ConstraintLayout.LayoutParams params) {
			if (inAppMiniPlayer && !fullscreen) {
				applyMiniPlayerLayout(params);
				clearViewTranslation();
				miniPlayerTranslationStashedForFullscreen = false;
				return;
			}
			if (fullscreen && inAppMiniPlayer) {
				if (!miniPlayerTranslationStashedForFullscreen) {
					saveCurrentMiniPlayerTranslation();
					miniPlayerTranslationStashedForFullscreen = true;
				}
				setTranslationX(0.0f);
				setTranslationY(0.0f);
			} else {
				resetMiniPlayerTranslation();
			}
			applyStandardPlayerAnchors(params);
			if (fullscreen) {
				params.width = ConstraintLayout.LayoutParams.MATCH_PARENT;
				params.height = ConstraintLayout.LayoutParams.MATCH_PARENT;
				params.topMargin = 0;
				params.rightMargin = 0;
				params.bottomMargin = 0;
			} else {
				params.width = ConstraintLayout.LayoutParams.MATCH_PARENT;
				params.height = normalHeight > 0 ? normalHeight : (int) (ViewUtils.getScreenWidth(activity) * 9 / 16.0);
				params.topMargin = ViewUtils.dpToPx(activity, Constant.TOP_MARGIN_DP);
				params.rightMargin = 0;
				params.bottomMargin = 0;
			}
			setLayoutParams(params);
		}
	}

	public void enterPiP() {
		if (activity.isInPictureInPictureMode()) return;
		if (!isFs && !inAppMiniPlayer) normalHeight = playerHeight;
		PictureInPictureParams params = buildPiPParams(true);
		activity.enterPictureInPictureMode(params);
	}

	public void disableAutoPiP() {
		if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) return;
		activity.setPictureInPictureParams(buildPiPParams(false));
	}

	// Re-asserts the bottom-bar layout; safe no-op when not minimized.
	public void reapplyMiniPlayerLayout() {
		if (!inAppMiniPlayer) return;
		stopMiniTransition();
		clearViewTranslation();
		updatePlayerLayout(false);
		updateMiniPlayerCornerClipping();
	}

	public void enterInAppMiniPlayer() {
		if (inAppMiniPlayer) return;
		float startX = getX();
		float startY = getY();
		int startWidth = getWidth();
		int startHeight = getHeight();
		stopMiniTransition();
		miniPlayerRestoreResizeMode = getResizeMode();
		miniPlayerRestoreFullscreen = isFs;
		inAppMiniPlayer = true;
		updatePlayerLayout(false);
		updateMiniPlayerCornerClipping();
		updateMiniPlayerInteractionHandlers();
		animateMiniTransition(startX, startY, startWidth, startHeight);
	}

	public void exitInAppMiniPlayer() {
		if (!inAppMiniPlayer) return;
		float startX = getX();
		float startY = getY();
		int startWidth = getWidth();
		int startHeight = getHeight();
		stopMiniTransition();
		inAppMiniPlayer = false;
		resetMiniPlayerTouchTracking();
		resetMiniPlayerTranslation();
		updatePlayerLayout(miniPlayerRestoreFullscreen);
		updateMiniPlayerCornerClipping();
		setResizeMode(miniPlayerRestoreResizeMode);
		updateMiniPlayerInteractionHandlers();
		animateMiniTransition(startX, startY, startWidth, startHeight);
	}

	public void setMiniPlayerCallbacks(@Nullable Runnable onRestore, @Nullable Runnable onClose) {
		onMiniPlayerRestore = onRestore;
		onMiniPlayerClose = onClose;
		updateMiniPlayerInteractionHandlers();
	}

	public void setOnMiniPlayerBackgroundTap(@Nullable Runnable onBackgroundTap) {
		onMiniPlayerBackgroundTap = onBackgroundTap;
	}

	private void updateMiniPlayerInteractionHandlers() {
		ImageButton closeButton = findViewById(R.id.btn_mini_close);
		ImageButton restoreButton = findViewById(R.id.btn_mini_restore);
		setMiniPlayerButtonAction(closeButton, inAppMiniPlayer ? onMiniPlayerClose : null);
		setMiniPlayerButtonAction(restoreButton, inAppMiniPlayer ? onMiniPlayerRestore : null);
	}

	private void setMiniPlayerButtonAction(@Nullable ImageButton button, @Nullable Runnable action) {
		if (button == null) return;
		button.setOnClickListener(action == null ? null : v -> action.run());
	}

	public boolean handleMiniPlayerTouch(@NonNull MotionEvent event) {
		if (!inAppMiniPlayer) return false;
		int action = event.getActionMasked();
		switch (action) {
			case MotionEvent.ACTION_DOWN -> {
				View tapTarget = getMiniPlayerTapTarget(event);
				captureMiniPlayerTouchStart(event);
				miniPlayerPendingTapTarget = tapTarget;
				return true;
			}
			case MotionEvent.ACTION_MOVE -> {
				if (!miniPlayerTouchCaptured) return false;
				float deltaX = event.getRawX() - miniPlayerTouchDownRawX;
				float deltaY = event.getRawY() - miniPlayerTouchDownRawY;
				if (!miniPlayerDragging && exceedsTouchSlop(deltaX, deltaY)) {
					miniPlayerDragging = true;
					clearMiniPlayerPendingTapTarget();
				}
				return true;
			}
			case MotionEvent.ACTION_UP -> {
				if (!miniPlayerTouchCaptured) return false;
				View tap = miniPlayerPendingTapTarget;
				boolean wasDragging = miniPlayerDragging;
				resetMiniPlayerTouchTracking();
				if (wasDragging) return true;
				if (tap != null) {
					tap.performClick();
				} else if (onMiniPlayerRestore != null) {
					onMiniPlayerRestore.run();
				} else if (onMiniPlayerBackgroundTap != null) {
					onMiniPlayerBackgroundTap.run();
				}
				return true;
			}
			case MotionEvent.ACTION_CANCEL -> {
				if (!miniPlayerTouchCaptured) return false;
				resetMiniPlayerTouchTracking();
				return true;
			}
			default -> {
				return miniPlayerTouchCaptured;
			}
		}
	}

	@Override
	public boolean dispatchTouchEvent(@NonNull MotionEvent event) {
		if (miniAnimating && event.getActionMasked() == MotionEvent.ACTION_DOWN) {
			stopMiniTransition();
		}
		if (inAppMiniPlayer && handleMiniPlayerTouch(event)) {
			return true;
		}
		return super.dispatchTouchEvent(event);
	}

	private void animateMiniTransition(float startX,
	                                   float startY,
	                                   int startWidth,
	                                   int startHeight) {
		if (startWidth <= 0 || startHeight <= 0 || !isAttachedToWindow()) return;
		int token = ++miniAnimToken;
		miniAnimating = true;
		getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
			@Override
			public boolean onPreDraw() {
				ViewTreeObserver observer = getViewTreeObserver();
				if (observer.isAlive()) observer.removeOnPreDrawListener(this);
				if (token != miniAnimToken || !isAttachedToWindow() || getWidth() <= 0 || getHeight() <= 0) {
					finishMiniTransition(token);
					return true;
				}
				float endX = getX();
				float endY = getY();
				int endWidth = getWidth();
				int endHeight = getHeight();
				float endTranslationX = getTranslationX();
				float endTranslationY = getTranslationY();
				setPivotX(endWidth / 2.0f);
				setPivotY(endHeight / 2.0f);
				setScaleX(startWidth / (float) endWidth);
				setScaleY(startHeight / (float) endHeight);
				setTranslationX(endTranslationX + startX + startWidth / 2.0f - (endX + endWidth / 2.0f));
				setTranslationY(endTranslationY + startY + startHeight / 2.0f - (endY + endHeight / 2.0f));
				animate().cancel();
				animate()
								.translationX(endTranslationX)
								.translationY(endTranslationY)
								.scaleX(1.0f)
								.scaleY(1.0f)
								.setDuration(MINI_TRANSITION_MS)
								.setInterpolator(new OvershootInterpolator(0.7f))
								.withLayer()
								.withEndAction(() -> finishMiniTransition(token))
								.start();
				return true;
			}
		});
	}

	private void stopMiniTransition() {
		miniAnimating = false;
		miniAnimToken++;
		animate().cancel();
		setScaleX(1.0f);
		setScaleY(1.0f);
	}

	private void finishMiniTransition(int token) {
		if (token != miniAnimToken) return;
		miniAnimating = false;
		setScaleX(1.0f);
		setScaleY(1.0f);
	}

	private void applyMiniPlayerLayout(@NonNull ConstraintLayout.LayoutParams params) {
		// bottom bar mode: full-width bar docked to the bottom of the screen;
		// the parent view already pads for the navigation-bar inset
		params.width = ConstraintLayout.LayoutParams.MATCH_CONSTRAINT;
		params.height = Math.round(dpToPx(MINI_BAR_HEIGHT_DP));
		params.topMargin = 0;
		params.rightMargin = 0;
		params.bottomMargin = 0;
		params.topToTop = ConstraintLayout.LayoutParams.UNSET;
		params.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID;
		params.startToStart = ConstraintLayout.LayoutParams.PARENT_ID;
		params.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID;
		params.rightToRight = ConstraintLayout.LayoutParams.UNSET;
		setLayoutParams(params);
	}

	private void applyStandardPlayerAnchors(@NonNull ConstraintLayout.LayoutParams params) {
		params.topToTop = ConstraintLayout.LayoutParams.PARENT_ID;
		params.startToStart = ConstraintLayout.LayoutParams.PARENT_ID;
		params.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID;
		params.topToBottom = ConstraintLayout.LayoutParams.UNSET;
		params.bottomToTop = ConstraintLayout.LayoutParams.UNSET;
		params.bottomToBottom = ConstraintLayout.LayoutParams.UNSET;
		params.leftToLeft = ConstraintLayout.LayoutParams.UNSET;
		params.leftToRight = ConstraintLayout.LayoutParams.UNSET;
		params.rightToLeft = ConstraintLayout.LayoutParams.UNSET;
		params.rightToRight = ConstraintLayout.LayoutParams.UNSET;
		params.startToEnd = ConstraintLayout.LayoutParams.UNSET;
		params.endToStart = ConstraintLayout.LayoutParams.UNSET;
	}

	private void updateMiniPlayerCornerClipping() {
		// bottom bar spans the full screen width; no corner clipping
		setClipToOutline(false);
		invalidateOutline();
	}

	private boolean exceedsTouchSlop(float deltaX, float deltaY) {
		int touchSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
		return Math.abs(deltaX) > touchSlop || Math.abs(deltaY) > touchSlop;
	}

	@Nullable
	private View getMiniPlayerTapTarget(@NonNull MotionEvent event) {
		return getMiniPlayerTapTarget(event, 0);
	}

	@Nullable
	private View getMiniPlayerTapTarget(@NonNull MotionEvent event, int pointerIndex) {
		if (pointerIndex < 0 || pointerIndex >= event.getPointerCount()) return null;
		for (int targetId : MINI_PLAYER_TAP_TARGET_IDS) {
			View target = findViewById(targetId);
			if (isPointInsideVisibleView(event, pointerIndex, target)) {
				return target;
			}
		}
		return null;
	}

	private boolean isPointInsideVisibleView(@NonNull MotionEvent event,
	                                         int pointerIndex,
	                                         @Nullable View view) {
		if (view == null || view.getVisibility() != View.VISIBLE) return false;
		if (pointerIndex < 0 || pointerIndex >= event.getPointerCount()) return false;
		if (!view.getGlobalVisibleRect(miniPlayerTouchBounds)) return false;
		getLocationOnScreen(miniPlayerLocationOnScreen);
		int pointerRawX = Math.round(miniPlayerLocationOnScreen[0] + event.getX(pointerIndex));
		int pointerRawY = Math.round(miniPlayerLocationOnScreen[1] + event.getY(pointerIndex));
		return miniPlayerTouchBounds.contains(pointerRawX, pointerRawY);
	}

	private void captureMiniPlayerTouchStart(@NonNull MotionEvent event) {
		animate().cancel();
		miniAnimating = false;
		miniPlayerTouchCaptured = true;
		miniPlayerDragging = false;
		miniPlayerTouchDownRawX = event.getRawX();
		miniPlayerTouchDownRawY = event.getRawY();
		miniPlayerStartTranslationX = getTranslationX();
		miniPlayerStartTranslationY = getTranslationY();
	}

	private void resetMiniPlayerTouchTracking() {
		clearMiniPlayerPendingTapTarget();
		miniPlayerTouchCaptured = false;
		miniPlayerDragging = false;
	}

	private void clearMiniPlayerPendingTapTarget() {
		miniPlayerPendingTapTarget = null;
	}

	private void resetMiniPlayerTranslation() {
		miniPlayerSavedTranslationX = 0.0f;
		miniPlayerSavedTranslationY = 0.0f;
		miniPlayerTranslationStashedForFullscreen = false;
		clearViewTranslation();
	}

	private void clearViewTranslation() {
		setTranslationX(0.0f);
		setTranslationY(0.0f);
	}

	private void saveCurrentMiniPlayerTranslation() {
		miniPlayerSavedTranslationX = getTranslationX();
		miniPlayerSavedTranslationY = getTranslationY();
	}

	private int getScreenWidthDp() {
		int screenWidthDp = getResources().getConfiguration().screenWidthDp;
		if (screenWidthDp != Configuration.SCREEN_WIDTH_DP_UNDEFINED) {
			return screenWidthDp;
		}
		return pxToDp(ViewUtils.getScreenWidth(activity));
	}

	private int getBottomInsetDp() {
		WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(this);
		if (insets == null) return 0;
		Insets systemInsets = insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.systemBars());
		return pxToDp(systemInsets.bottom);
	}

	private int pxToDp(int px) {
		return Math.round(px / getResources().getDisplayMetrics().density);
	}

	private float pxToDp(float px) {
		return px / getResources().getDisplayMetrics().density;
	}

	private float dpToPx(float dp) {
		return dp * getResources().getDisplayMetrics().density;
	}

	@NonNull
	private PictureInPictureParams buildPiPParams(boolean autoEnter) {
		PictureInPictureParams.Builder builder = new PictureInPictureParams.Builder()
						.setAspectRatio(new Rational(16, 9));
		if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
			builder.setAutoEnterEnabled(autoEnter);
		}
		Rect sourceRectHint = new Rect();
		if (getGlobalVisibleRect(sourceRectHint)) {
			builder.setSourceRectHint(sourceRectHint);
		}
		return builder.build();
	}

	private void applyNormalState(int defaultResizeMode) {
		isFs = false;
		setParentInsetsSuppressed(false);
		int requestedOrientation = portraitNormalStateRequested
						? ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
						: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
		activity.setRequestedOrientation(requestedOrientation);
		portraitNormalStateRequested = false;
		ViewUtils.setFullscreen(activity.getWindow().getDecorView(), false);
		updatePlayerLayout(false);
		setResizeMode(inAppMiniPlayer ? AspectRatioFrameLayout.RESIZE_MODE_FIT : defaultResizeMode);
		updateMiniPlayerCornerClipping();
		updateFullscreenButton(false);
	}

	private void applyFullscreenState(@NonNull ControllerState.Mode previousState,
	                                  int fsOrientation,
	                                  int defaultResizeMode) {
		isFs = true;
		if (previousState == ControllerState.Mode.NORMAL && !activity.isInPictureInPictureMode()) {
			normalHeight = playerHeight;
		}
		setParentInsetsSuppressed(true);
		activity.setRequestedOrientation(fsOrientation);
		ViewUtils.setFullscreen(activity.getWindow().getDecorView(), true);
		updatePlayerLayout(true);
		setResizeMode(defaultResizeMode);
		updateMiniPlayerCornerClipping();
		updateFullscreenButton(true);
	}

	private void applyPictureInPictureState(@NonNull ControllerState.Mode previousState) {
		isFs = false;
		setParentInsetsSuppressed(false);
		if (previousState == ControllerState.Mode.NORMAL) {
			normalHeight = playerHeight;
		}
		updatePlayerLayout(true);
		setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
		updateMiniPlayerCornerClipping();
	}

	public void requestPortraitNormalState() {
		portraitNormalStateRequested = true;
	}

	private void setParentInsetsSuppressed(boolean suppressed) {
		ViewParent parent = getParent();
		if (!(parent instanceof View parentView)) return;
		if (suppressed) {
			if (parentInsetsSuppressed) return;
			parentPaddingLeft = parentView.getPaddingLeft();
			parentPaddingTop = parentView.getPaddingTop();
			parentPaddingRight = parentView.getPaddingRight();
			parentPaddingBottom = parentView.getPaddingBottom();
			parentView.setPadding(0, 0, 0, 0);
			parentInsetsSuppressed = true;
			return;
		}
		if (!parentInsetsSuppressed) return;
		parentView.setPadding(parentPaddingLeft, parentPaddingTop, parentPaddingRight, parentPaddingBottom);
		parentInsetsSuppressed = false;
		ViewCompat.requestApplyInsets(parentView);
	}

	private void updateFullscreenButton(boolean fullscreen) {
		ImageButton fullscreenButton = findViewById(R.id.btn_fullscreen);
		if (fullscreenButton != null) {
			fullscreenButton.setImageResource(
							fullscreen ? R.drawable.ic_fullscreen_exit : R.drawable.ic_fullscreen);
		}
	}

	public void cueing(@NonNull CueGroup cueGroup) {
		if (subtitleView == null) {
			SubtitleView defaultSubtitleView = getSubtitleView();
			if (defaultSubtitleView != null) defaultSubtitleView.setVisibility(View.GONE);
			subtitleView = findViewById(R.id.custom_subtitle_view);
		}
		List<Cue> cues = new ArrayList<>();
		for (Cue cue : cueGroup.cues)
			cues.add(cue.buildUpon()
							.setLine(SUBTITLE_LINE_FRACTION, Cue.LINE_TYPE_FRACTION)
							.setLineAnchor(Cue.ANCHOR_TYPE_END)
							.setPosition(SUBTITLE_POSITION_FRACTION)
							.setPositionAnchor(Cue.ANCHOR_TYPE_MIDDLE)
							.build());
		subtitleView.setCues(cues);
	}

	@Override
	public void setResizeMode(int resizeMode) {
		super.setResizeMode(resizeMode);
		View videoSurfaceView = getVideoSurfaceView();
		if (videoSurfaceView instanceof AspectRatioFrameLayout frameLayout) {
			frameLayout.setResizeMode(resizeMode);
		}
	}

	public void show() {
		setVisibility(View.VISIBLE);
	}

	public void hide() {
		setVisibility(View.GONE);
	}

	public void setTitle(@Nullable String title) {
		TextView titleView = findViewById(R.id.tv_title);
		titleView.setText(title);
		titleView.setSelected(true);
		TextView barTitle = findViewById(R.id.mini_bar_title);
		if (barTitle != null) {
			barTitle.setText(title);
			barTitle.setSelected(true);
		}
	}

	public void setThumbnail(@Nullable String url) {
		ImageView thumb = findViewById(R.id.mini_bar_thumb);
		if (thumb != null) ImageUtils.loadThumb(thumb, url);
	}

	public void updateSkipMarkers(long duration, TimeUnit unit) {
		List<long[]> segs = sponsor.getSegments();
		List<long[]> validSegs = new ArrayList<>();
		for (long[] seg : segs) if (seg != null && seg.length >= 2) validSegs.add(seg);

		SponsorOverlayView layer = findViewById(R.id.sponsor_overlay);
		layer.setData(validSegs.isEmpty() ? null : validSegs, duration, unit);

		DefaultTimeBar bar = findViewById(R.id.exo_progress);
		if (validSegs.isEmpty()) {
			bar.setAdGroupTimesMs(null, null, 0);
		} else {
			long[] times = new long[validSegs.size() * 2];
			for (int i = 0; i < validSegs.size(); i++) {
				times[i * 2] = validSegs.get(i)[0];
				times[i * 2 + 1] = validSegs.get(i)[1];
			}
			bar.setAdGroupTimesMs(times, new boolean[times.length], times.length);
		}
	}

	public void setHeight(int height) {
		if (activity.isInPictureInPictureMode() || isFs || inAppMiniPlayer || height <= 0) return;
		int deviceHeight = ViewUtils.dpToPx(activity, height);
		if (getLayoutParams().height == deviceHeight) return;
		getLayoutParams().height = deviceHeight;
		if (!isFs) normalHeight = deviceHeight;
		requestLayout();
	}

}
