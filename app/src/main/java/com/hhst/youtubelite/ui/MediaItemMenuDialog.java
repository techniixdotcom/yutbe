package com.hhst.youtubelite.ui;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.media3.common.util.UnstableApi;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.hhst.youtubelite.R;
import com.hhst.youtubelite.browser.MediaItemMenuPayload;
import com.hhst.youtubelite.downloader.ui.DownloadDialog;
import com.hhst.youtubelite.extractor.YoutubeExtractor;
import com.hhst.youtubelite.gallery.GalleryActivity;
import com.hhst.youtubelite.player.LitePlayer;
import com.hhst.youtubelite.player.queue.QueueItem;
import com.hhst.youtubelite.player.queue.QueueRepository;
import com.hhst.youtubelite.sync.BlockedChannels;
import com.hhst.youtubelite.sync.WatchSyncManager;
import com.hhst.youtubelite.util.ToastUtils;
import com.squareup.picasso.Picasso;

import java.util.ArrayList;

/**
 * Builds the media item action dialog.
 */
@UnstableApi
public final class MediaItemMenuDialog {
	@NonNull
	private final Context context;
	@NonNull
	private final MediaItemMenuPayload item;
	@NonNull
	private final YoutubeExtractor extractor;
	@NonNull
	private final QueueRepository queue;
	@NonNull
	private final LitePlayer player;
	@Nullable
	private final WatchSyncManager watchSyncManager;
	@NonNull
	private final BlockedChannels blockedChannels = new BlockedChannels();

	public MediaItemMenuDialog(@NonNull Context context,
	                           @NonNull MediaItemMenuPayload item,
	                           @NonNull YoutubeExtractor extractor,
	                           @NonNull QueueRepository queue,
	                           @NonNull LitePlayer player) {
		this(context, item, extractor, queue, player, null);
	}

	public MediaItemMenuDialog(@NonNull Context context,
	                           @NonNull MediaItemMenuPayload item,
	                           @NonNull YoutubeExtractor extractor,
	                           @NonNull QueueRepository queue,
	                           @NonNull LitePlayer player,
	                           @Nullable WatchSyncManager watchSyncManager) {
		this.context = context;
		this.item = item;
		this.extractor = extractor;
		this.queue = queue;
		this.player = player;
		this.watchSyncManager = watchSyncManager;
	}

	public void show() {
		// Bind the preview image and action handlers.
		View view = LayoutInflater.from(context).inflate(R.layout.dialog_media_item_menu, null, false);
		AlertDialog dialog = new MaterialAlertDialogBuilder(context)
						.setView(view)
						.create();
		ImageView thumb = view.findViewById(R.id.media_item_thumb);
		TextView title = view.findViewById(R.id.media_item_title);
		TextView author = view.findViewById(R.id.media_item_author);
		String thumbUrl = item.thumbnailUrl();

		title.setText(item.title());
		author.setText(item.author());
		author.setVisibility(item.author() != null && !item.author().isBlank() ? View.VISIBLE : View.GONE);

		Picasso.get()
						.load(thumbUrl)
						.placeholder(R.drawable.ic_thumbnail_placeholder)
						.error(R.drawable.ic_thumbnail_placeholder)
						.into(thumb);

		thumb.setOnClickListener(v -> openGallery(thumbUrl));
		thumb.setEnabled(thumbUrl != null && !thumbUrl.isBlank());

		view.findViewById(R.id.media_item_close).setOnClickListener(v -> dialog.dismiss());
		view.findViewById(R.id.action_queue).setOnClickListener(v -> {
			addToQueue(false);
			dialog.dismiss();
		});
		view.findViewById(R.id.action_queue_next).setOnClickListener(v -> {
			addToQueue(true);
			dialog.dismiss();
		});
		view.findViewById(R.id.action_share).setOnClickListener(v -> {
			share();
			dialog.dismiss();
		});
		view.findViewById(R.id.action_download).setOnClickListener(v -> {
			dialog.dismiss();
			new DownloadDialog(item.videoUrl(), context, extractor).show();
		});
		View markWatched = view.findViewById(R.id.action_mark_watched);
		boolean watched = isWatched();
		if (markWatched instanceof TextView markWatchedText) {
			markWatchedText.setText(watched ? R.string.mark_as_unwatched : R.string.mark_as_watched);
		}
		markWatched.setOnClickListener(v -> {
			if (watched) {
				unmarkWatched();
			} else {
				markWatched();
			}
			dialog.dismiss();
		});
		View blockChannel = view.findViewById(R.id.action_block_channel);
		boolean hasAuthor = item.author() != null && !item.author().isBlank();
		blockChannel.setVisibility(hasAuthor ? View.VISIBLE : View.GONE);
		blockChannel.setOnClickListener(v -> {
			blockedChannels.block(item.author());
			ToastUtils.show(context, context.getString(R.string.channel_blocked, item.author()));
			dialog.dismiss();
		});

		dialog.show();
	}

	private void markWatched() {
		if (watchSyncManager == null) return;
		// A full-position entry marks the video watched (100%) in the ledger:
		// it is grayed out in feeds and skipped by autoplay-next.
		watchSyncManager.recordWatch(item.videoId(), item.title(), item.author(),
						item.thumbnailUrl(), 1L, 1L);
		ToastUtils.show(context, R.string.marked_as_watched);
	}

	private boolean isWatched() {
		try {
			if (watchSyncManager == null) return false;
			for (String id : watchSyncManager.getWatchedVideoIds()) {
				if (id.equals(item.videoId())) return true;
			}
		} catch (Exception ignored) {
		}
		return false;
	}

	private void unmarkWatched() {
		if (watchSyncManager == null) return;
		watchSyncManager.markUnwatched(item.videoId(), item.title(), item.author(), item.thumbnailUrl());
		ToastUtils.show(context, R.string.marked_as_unwatched);
	}

	private void addToQueue(boolean playNext) {
		// Reject queue items that are missing core metadata.
		QueueItem queueItem = item.toQueueItem();
		String videoId = queueItem.getVideoId();
		if (queueItem.getVideoUrl() == null || videoId == null || videoId.isBlank()
						|| queueItem.getTitle() == null || queueItem.getTitle().isBlank()) {
			ToastUtils.show(context, R.string.queue_item_unavailable);
			return;
		}
		if (!queue.isEnabled()) {
			queue.setEnabled(true);
		}
		if (playNext) {
			queue.addNext(queueItem, player.getActiveVideoId());
		} else {
			queue.add(queueItem);
		}
		player.refreshQueueNav();
		ToastUtils.show(context, playNext ? R.string.queue_item_added_next : R.string.queue_item_added);
	}

	private void share() {
		// Share the original video URL as plain text.
		Intent send = new Intent(Intent.ACTION_SEND);
		send.putExtra(Intent.EXTRA_TEXT, item.videoUrl());
		send.setType("text/plain");
		context.startActivity(Intent.createChooser(send, context.getString(R.string.share)));
	}

	private void openGallery(@Nullable String thumbUrl) {
		// Reuse the thumbnail list for the gallery screen.
		if (thumbUrl == null || thumbUrl.isBlank()) return;
		ArrayList<String> urls = new ArrayList<>();
		urls.add(thumbUrl);
		Intent intent = new Intent(context, GalleryActivity.class);
		intent.putStringArrayListExtra("thumbnails", urls);
		intent.putExtra("filename", item.title());
		context.startActivity(intent);
	}
}
