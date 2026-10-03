package com.yutbe.app.extractor.potoken;

import androidx.annotation.Nullable;

import org.schabi.newpipe.extractor.services.youtube.PoTokenProvider;
import org.schabi.newpipe.extractor.services.youtube.PoTokenResult;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Provider that feeds PoToken data into extraction.
 */
@Singleton
public final class YuTbePoTokenProvider implements PoTokenProvider {
	private final PoTokenCoordinator coordinator;

	@Inject
	public YuTbePoTokenProvider(PoTokenCoordinator coordinator) {
		this.coordinator = coordinator;
	}

	@Override
	@Nullable
	public PoTokenResult getWebClientPoToken(String videoId) {
		return coordinator.getWebClientPoToken(videoId);
	}
}
