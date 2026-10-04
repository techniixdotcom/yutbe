package com.yutbe.app.history;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.squareup.picasso.Picasso;
import com.yutbe.app.Constant;
import com.yutbe.app.R;
import com.yutbe.app.ui.MainActivity;
import com.yutbe.app.util.ToastUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Full-screen list of the videos played in YuTbe, grouped by day like YouTube's History page.
 */
@AndroidEntryPoint
public final class LocalHistoryActivity extends AppCompatActivity {
	private static final int TYPE_HEADER = 0;
	private static final int TYPE_VIDEO = 1;

	@Inject
	WatchHistory watchHistory;

	private final Adapter adapter = new Adapter();
	private TextView empty;

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		EdgeToEdge.enable(this);
		setContentView(R.layout.activity_local_history);

		View root = findViewById(R.id.root);
		ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
			var bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
			v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
			return insets;
		});

		MaterialToolbar toolbar = findViewById(R.id.toolbar);
		toolbar.setNavigationOnClickListener(v -> finish());
		toolbar.getMenu().add(R.string.watch_history_clear)
						.setOnMenuItemClickListener(item -> {
							confirmClear();
							return true;
						});

		empty = findViewById(R.id.empty);
		RecyclerView list = findViewById(R.id.recyclerView);
		list.setLayoutManager(new LinearLayoutManager(this));
		list.setAdapter(adapter);
	}

	@Override
	protected void onResume() {
		super.onResume();
		reload();
	}

	private void reload() {
		adapter.submit(watchHistory.entries());
		empty.setVisibility(adapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
	}

	private void confirmClear() {
		new MaterialAlertDialogBuilder(this)
						.setTitle(R.string.watch_history_clear)
						.setMessage(R.string.watch_history_clear_confirm)
						.setPositiveButton(R.string.watch_history_clear, (d, w) -> {
							watchHistory.clear();
							reload();
						})
						.setNegativeButton(R.string.cancel, null)
						.show();
	}

	private void play(@NonNull String videoId) {
		Intent intent = new Intent(Intent.ACTION_VIEW,
						Uri.parse(Constant.HOME_URL + "/watch?v=" + videoId),
						this, MainActivity.class);
		intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
		startActivity(intent);
		finish();
	}

	private void showItemMenu(@NonNull View anchor, @NonNull WatchHistory.Entry entry) {
		PopupMenu menu = new PopupMenu(this, anchor);
		menu.getMenu().add(R.string.local_history_remove).setOnMenuItemClickListener(item -> {
			watchHistory.remove(entry.videoId());
			ToastUtils.show(this, R.string.local_history_removed);
			reload();
			return true;
		});
		menu.show();
	}

	/**
	 * Rows: a day header followed by that day's videos.
	 */
	private final class Adapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
		private final List<Object> rows = new ArrayList<>();

		void submit(@NonNull List<WatchHistory.Entry> entries) {
			rows.clear();
			ZoneId zone = ZoneId.systemDefault();
			LocalDate today = LocalDate.now(zone);
			DateTimeFormatter dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM);
			LocalDate currentDay = null;
			for (WatchHistory.Entry entry : entries) {
				LocalDate day = Instant.ofEpochMilli(entry.watchedAt()).atZone(zone).toLocalDate();
				if (!day.equals(currentDay)) {
					currentDay = day;
					rows.add(dayLabel(day, today, zone, dateFormat));
				}
				rows.add(entry);
			}
			notifyDataSetChanged();
		}

		@NonNull
		private String dayLabel(@NonNull LocalDate day,
		                        @NonNull LocalDate today,
		                        @NonNull ZoneId zone,
		                        @NonNull DateTimeFormatter dateFormat) {
			if (!day.isBefore(today.minusDays(1))) {
				// "Today" / "Yesterday" in the phone's language.
				long start = day.atStartOfDay(zone).toInstant().toEpochMilli();
				long now = today.atStartOfDay(zone).toInstant().toEpochMilli();
				return String.valueOf(DateUtils.getRelativeTimeSpanString(start, now, DateUtils.DAY_IN_MILLIS));
			}
			return day.format(dateFormat);
		}

		@Override
		public int getItemViewType(int position) {
			return rows.get(position) instanceof String ? TYPE_HEADER : TYPE_VIDEO;
		}

		@Override
		public int getItemCount() {
			return rows.size();
		}

		@NonNull
		@Override
		public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
			LayoutInflater inflater = LayoutInflater.from(parent.getContext());
			if (viewType == TYPE_HEADER) {
				return new HeaderHolder(inflater.inflate(R.layout.item_local_history_header, parent, false));
			}
			return new VideoHolder(inflater.inflate(R.layout.item_local_history, parent, false));
		}

		@Override
		public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
			Object row = rows.get(position);
			if (holder instanceof HeaderHolder header && row instanceof String text) {
				header.text.setText(text);
			} else if (holder instanceof VideoHolder video && row instanceof WatchHistory.Entry entry) {
				video.bind(entry);
			}
		}
	}

	private static final class HeaderHolder extends RecyclerView.ViewHolder {
		private final TextView text;

		HeaderHolder(@NonNull View itemView) {
			super(itemView);
			text = itemView.findViewById(R.id.header);
		}
	}

	private final class VideoHolder extends RecyclerView.ViewHolder {
		private final ImageView thumbnail;
		private final TextView title;
		private final TextView author;
		private final TextView when;
		private final ImageButton more;

		VideoHolder(@NonNull View itemView) {
			super(itemView);
			thumbnail = itemView.findViewById(R.id.thumbnail);
			title = itemView.findViewById(R.id.title);
			author = itemView.findViewById(R.id.author);
			when = itemView.findViewById(R.id.when);
			more = itemView.findViewById(R.id.more);
		}

		void bind(@NonNull WatchHistory.Entry entry) {
			String name = entry.title() == null || entry.title().isBlank() ? entry.videoId() : entry.title();
			title.setText(name);
			boolean hasAuthor = entry.author() != null && !entry.author().isBlank();
			author.setText(hasAuthor ? entry.author() : "");
			author.setVisibility(hasAuthor ? View.VISIBLE : View.GONE);
			when.setText(DateUtils.getRelativeTimeSpanString(entry.watchedAt(), System.currentTimeMillis(),
							DateUtils.MINUTE_IN_MILLIS));
			Picasso.get()
							.load("https://i.ytimg.com/vi/" + entry.videoId() + "/mqdefault.jpg")
							.fit()
							.centerCrop()
							.into(thumbnail);
			itemView.setOnClickListener(v -> play(entry.videoId()));
			more.setOnClickListener(v -> showItemMenu(v, entry));
		}
	}
}
