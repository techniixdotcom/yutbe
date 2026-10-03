package com.yutbe.app.di;

import com.yutbe.app.downloader.core.YuTbeDownloader;
import com.yutbe.app.downloader.core.StreamDownloader;
import com.yutbe.app.downloader.core.impl.YuTbeDownloaderImpl;
import com.yutbe.app.downloader.core.impl.StreamDownloaderImpl;

import javax.inject.Singleton;

import dagger.Binds;
import dagger.Module;
import dagger.hilt.InstallIn;
import dagger.hilt.components.SingletonComponent;

/**
 * Hilt module that wires download dependencies.
 */
@Module
@InstallIn(SingletonComponent.class)
public abstract class DownloaderModule {

	@Binds
	@Singleton
	public abstract YuTbeDownloader bindYuTbeDownloader(YuTbeDownloaderImpl impl);

	@Binds
	@Singleton
	public abstract StreamDownloader bindStreamDownloader(StreamDownloaderImpl impl);

}
