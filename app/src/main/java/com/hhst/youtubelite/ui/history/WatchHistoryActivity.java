package com.hhst.youtubelite.ui.history;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
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
import com.hhst.youtubelite.R;
import com.hhst.youtubelite.sync.WatchLedgerEntry;
import com.hhst.youtubelite.sync.WatchSyncManager;
import com.hhst.youtubelite.ui.MainActivity;
import com.hhst.youtubelite.util.ImageUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Local watch history aggregated from the synced ledger (all devices).
 */
@AndroidEntryPoint
public class WatchHistoryActivity extends AppCompatActivity {

	@Inject
	WatchSyncManager syncManager;

	private final Adapter adapter = new Adapter();
	private TextView emptyView;

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		EdgeToEdge.enable(this);
		setContentView(R.layout.activity_watch_history);

		View root = findViewById(R.id.root);
		ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
			var bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
			v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
			return insets;
		});

		MaterialToolbar toolbar = findViewById(R.id.toolbar);
		toolbar.setNavigationOnClickListener(v -> finish());
		toolbar.inflateMenu(R.menu.watch_history_actions);
		toolbar.setOnMenuItemClickListener(item -> {
			if (item.getItemId() != R.id.action_clear) return false;
			new MaterialAlertDialogBuilder(this)
							.setTitle(R.string.clear_history)
							.setMessage(R.string.clear_history_message)
							.setPositiveButton(R.string.confirm, (d, w) -> {
								syncManager.clearHistory();
								refresh();
							})
							.setNegativeButton(R.string.cancel, null)
							.show();
			return true;
		});

		emptyView = findViewById(R.id.empty);
		RecyclerView list = findViewById(R.id.recyclerView);
		list.setLayoutManager(new LinearLayoutManager(this));
		list.setAdapter(adapter);
	}

	@Override
	protected void onResume() {
		super.onResume();
		refresh();
	}

	private void refresh() {
		List<WatchLedgerEntry> history = syncManager.getHistory();
		adapter.submit(history);
		emptyView.setVisibility(history.isEmpty() ? View.VISIBLE : View.GONE);
	}

	private void open(@NonNull WatchLedgerEntry entry) {
		Intent intent = new Intent(Intent.ACTION_VIEW,
						Uri.parse("https://m.youtube.com/watch?v=" + entry.getVideoId()),
						this, MainActivity.class);
		startActivity(intent);
	}

	private void confirmRemove(@NonNull WatchLedgerEntry entry) {
		new MaterialAlertDialogBuilder(this)
						.setTitle(R.string.remove_from_history)
						.setPositiveButton(R.string.confirm, (d, w) -> {
							syncManager.removeHistoryEntry(entry.getVideoId());
							refresh();
						})
						.setNegativeButton(R.string.cancel, null)
						.show();
	}

	private final class Adapter extends RecyclerView.Adapter<Adapter.Holder> {
		@NonNull
		private List<WatchLedgerEntry> items = new ArrayList<>();

		void submit(@NonNull List<WatchLedgerEntry> items) {
			this.items = items;
			notifyDataSetChanged();
		}

		@Override
		public int getItemCount() {
			return items.size();
		}

		@NonNull
		@Override
		public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
			View view = LayoutInflater.from(parent.getContext())
							.inflate(R.layout.item_watch_history, parent, false);
			return new Holder(view);
		}

		@Override
		public void onBindViewHolder(@NonNull Holder holder, int position) {
			holder.bind(items.get(position));
		}

		final class Holder extends RecyclerView.ViewHolder {
			private final ImageView thumbnail;
			private final TextView title;
			private final TextView subtitle;

			Holder(@NonNull View itemView) {
				super(itemView);
				thumbnail = itemView.findViewById(R.id.thumbnail);
				title = itemView.findViewById(R.id.title);
				subtitle = itemView.findViewById(R.id.subtitle);
			}

			void bind(@NonNull WatchLedgerEntry entry) {
				ImageUtils.loadThumb(thumbnail, entry.getThumbnailUrl());
				title.setText(entry.getTitle() == null ? entry.getVideoId() : entry.getTitle());
				String when = DateUtils.getRelativeTimeSpanString(entry.getTimestamp()).toString();
				String meta = (entry.getAuthor() == null ? "" : entry.getAuthor() + " · ")
								+ when + " · "
								+ String.format(Locale.getDefault(), "%d%%", entry.percentWatched());
				subtitle.setText(meta);
				itemView.setOnClickListener(v -> open(entry));
				itemView.setOnLongClickListener(v -> {
					confirmRemove(entry);
					return true;
				});
			}
		}
	}
}
