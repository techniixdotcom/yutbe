package com.yutbe.app.di;

import android.app.Activity;

import androidx.media3.common.util.UnstableApi;

import com.yutbe.app.R;
import com.yutbe.app.player.YuTbePlayerView;

import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.components.ActivityComponent;
import dagger.hilt.android.scopes.ActivityScoped;

/**
 * Hilt module that wires playback dependencies.
 */
@Module
@InstallIn(ActivityComponent.class)
@UnstableApi
public class PlayerModule {

	@Provides
	@ActivityScoped
	public static YuTbePlayerView provideYuTbePlayerView(Activity activity) {
		return activity.findViewById(R.id.playerView);
	}
}
