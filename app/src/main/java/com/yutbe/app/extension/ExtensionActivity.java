package com.yutbe.app.extension;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.yutbe.app.R;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import android.widget.LinearLayout;

import com.google.android.material.slider.Slider;
import com.yutbe.app.filter.BlockedChannel;
import com.yutbe.app.filter.ContentFilters;
import com.yutbe.app.util.ViewUtils;

import java.util.ArrayList;

import android.net.Uri;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;
import com.yutbe.app.backup.SettingsBackup;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import com.yutbe.app.player.common.SleepTimer;
import com.yutbe.app.player.queue.QueueItem;
import com.yutbe.app.player.queue.QueueRepository;
import com.yutbe.app.util.ToastUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.concurrent.Executor;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Screen that shows extension settings as categorized pages.
 */
@AndroidEntryPoint
public class ExtensionActivity extends AppCompatActivity {
	private static final int TYPE_NAV = 0;
	private static final int TYPE_TOGGLE = 1;
	private static final int TYPE_VALUE = 2;
	@Inject
	ExtensionManager manager;
	@Inject
	ContentFilters contentFilters;
	@Inject
	SleepTimer sleepTimer;
	@Inject
	QueueRepository queueRepository;
	@Inject
	SettingsBackup settingsBackup;
	@Inject
	Executor executor;
	private final ActivityResultLauncher<String> exportLauncher = registerForActivityResult(
					new ActivityResultContracts.CreateDocument("application/json"), this::exportTo);
	private final ActivityResultLauncher<String[]> importLauncher = registerForActivityResult(
					new ActivityResultContracts.OpenDocument(), this::importFrom);
	private final Deque<Extension> stack = new ArrayDeque<>();
	private final Adapter adapter = new Adapter();
	private Extension page;
	private MaterialToolbar toolbar;

	public static Intent intent(@NonNull android.content.Context context) {
		return new Intent(context, ExtensionActivity.class);
	}

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		EdgeToEdge.enable(this);
		setContentView(R.layout.activity_extension);

		View root = findViewById(R.id.root);
		ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
			var bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
			v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
			return insets;
		});

		toolbar = findViewById(R.id.toolbar);
		toolbar.setNavigationOnClickListener(v -> navigateBack());
		toolbar.inflateMenu(R.menu.extension_actions);
		toolbar.setOnMenuItemClickListener(this::onMenuItemClick);

		RecyclerView list = findViewById(R.id.recyclerView);
		list.setLayoutManager(new LinearLayoutManager(this));
		list.setAdapter(adapter);
		if (list.getItemAnimator() instanceof SimpleItemAnimator animator) {
			animator.setSupportsChangeAnimations(false);
		}

		getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
			@Override
			public void handleOnBackPressed() {
				navigateBack();
			}
		});

		showPage(Extension.root(), false);
	}

	@Override
	protected void onResume() {
		super.onResume();
		// The sleep timer can switch itself off while this screen is in the background.
		adapter.notifyDataSetChanged();
	}

	private boolean onMenuItemClick(@NonNull MenuItem item) {
		if (item.getItemId() == R.id.action_export) {
			exportLauncher.launch("yutbe-backup-" + LocalDate.now() + ".json");
			return true;
		}
		if (item.getItemId() == R.id.action_import) {
			importLauncher.launch(new String[]{"application/json", "text/plain", "application/octet-stream"});
			return true;
		}
		if (item.getItemId() != R.id.action_reset) return false;
		new MaterialAlertDialogBuilder(this)
						.setTitle(R.string.reset_extension_title)
						.setMessage(R.string.reset_extension_message)
						.setPositiveButton(R.string.confirm, (d, w) -> {
							manager.resetToDefault();
							adapter.notifyDataSetChanged();
						})
						.setNegativeButton(R.string.cancel, null)
						.show();
		return true;
	}

	private void open(@NonNull Extension item) {
		if (!item.hasChildren()) return;
		showPage(item, true);
	}

	private void showPage(@NonNull Extension next, boolean push) {
		if (push && page != null) {
			stack.push(page);
		}
		page = next;
		toolbar.setTitle(next.title());
		adapter.submit(next.children());
	}

	private void navigateBack() {
		if (stack.isEmpty()) {
			finish();
			return;
		}
		page = stack.pop();
		toolbar.setTitle(page.title());
		adapter.submit(page.children());
	}

	private final class Adapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
		@NonNull
		private List<Extension> items = List.of();

		void submit(@NonNull List<Extension> items) {
			this.items = items;
			notifyDataSetChanged();
		}

		@Override
		public int getItemViewType(int position) {
			Extension item = items.get(position);
			if (item.hasChildren()) return TYPE_NAV;
			if (item.isPercent() || item.isAction() || item.isChoice()) return TYPE_VALUE;
			return TYPE_TOGGLE;
		}

		@Override
		public int getItemCount() {
			return items.size();
		}

		@NonNull
		@Override
		public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
			LayoutInflater inflater = LayoutInflater.from(parent.getContext());
			if (viewType == TYPE_NAV) {
				return new NavHolder(inflater.inflate(R.layout.item_extension_nav, parent, false));
			}
			if (viewType == TYPE_VALUE) {
				return new ValueHolder(inflater.inflate(R.layout.item_extension_nav, parent, false));
			}
			return new ToggleHolder(inflater.inflate(R.layout.item_extension_toggle, parent, false));
		}

		@Override
		public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
			Extension item = items.get(position);
			if (holder instanceof NavHolder nav) {
				nav.bind(item);
				return;
			}
			if (holder instanceof ValueHolder value) {
				value.bind(item);
				return;
			}
			((ToggleHolder) holder).bind(item);
		}
	}

	private final class NavHolder extends RecyclerView.ViewHolder {
		private final TextView title;
		private final TextView summary;
		private final ImageView chevron;
		private final ImageView icon;

		private NavHolder(@NonNull View itemView) {
			super(itemView);
			title = itemView.findViewById(R.id.title);
			summary = itemView.findViewById(R.id.summary);
			chevron = itemView.findViewById(R.id.chevron);
			icon = itemView.findViewById(R.id.icon);
		}

		private void bind(@NonNull Extension item) {
			title.setText(item.title());
			if (item.summary() == 0) {
				summary.setVisibility(View.GONE);
			} else {
				summary.setVisibility(View.VISIBLE);
				summary.setText(item.summary());
			}
			if (item.icon() != 0) {
				icon.setVisibility(View.VISIBLE);
				icon.setImageResource(item.icon());
			} else {
				icon.setVisibility(View.GONE);
			}
			chevron.setVisibility(View.VISIBLE);
			if (isLockedByMinimize(item)) {
				summary.setVisibility(View.VISIBLE);
				summary.setText(R.string.disabled_by_swipe_minimize);
				setLocked(itemView, true);
				itemView.setOnClickListener(null);
				return;
			}
			setLocked(itemView, false);
			itemView.setOnClickListener(v -> open(item));
		}
	}

	private void exportTo(@Nullable Uri uri) {
		if (uri == null) return;
		executor.execute(() -> {
			boolean ok;
			try (OutputStream out = getContentResolver().openOutputStream(uri, "wt")) {
				if (out == null) throw new IOException("Cannot open " + uri);
				settingsBackup.export(out);
				ok = true;
			} catch (IOException | RuntimeException e) {
				ok = false;
			}
			boolean success = ok;
			runOnUiThread(() -> ToastUtils.show(this, success ? R.string.backup_exported : R.string.backup_export_failed));
		});
	}

	private void importFrom(@Nullable Uri uri) {
		if (uri == null) return;
		executor.execute(() -> {
			SettingsBackup.Result result = null;
			String error = null;
			try (InputStream in = getContentResolver().openInputStream(uri)) {
				if (in == null) throw new IOException("Cannot open " + uri);
				result = settingsBackup.restore(in);
			} catch (IOException | RuntimeException e) {
				error = e.getMessage();
			}
			SettingsBackup.Result done = result;
			String failure = error;
			runOnUiThread(() -> {
				if (isFinishing() || isDestroyed()) return;
				adapter.notifyDataSetChanged();
				if (done == null) {
					new MaterialAlertDialogBuilder(this)
									.setTitle(R.string.backup_import)
									.setMessage(getString(R.string.backup_import_failed, failure == null ? "" : failure))
									.setPositiveButton(R.string.confirm, null)
									.show();
					return;
				}
				new MaterialAlertDialogBuilder(this)
								.setTitle(R.string.backup_import)
								.setMessage(getString(R.string.backup_imported,
												done.settings(), done.channels(), done.videos(), done.queued()))
								.setPositiveButton(R.string.confirm, null)
								.show();
			});
		});
	}

	@NonNull
	private String qualityLabel(@NonNull String value) {
		if (Constant.QUALITY_REMEMBERED.equals(value)) return getString(R.string.quality_remembered);
		if (Constant.QUALITY_BEST.equals(value)) return getString(R.string.quality_best);
		return value;
	}

	private void showQualityDialog(@NonNull Extension item) {
		List<String> values = Constant.QUALITY_CHOICES;
		String[] labels = new String[values.size()];
		for (int i = 0; i < values.size(); i++) {
			labels[i] = qualityLabel(values.get(i));
		}
		int checked = Math.max(0, values.indexOf(manager.getString(item.key())));
		new MaterialAlertDialogBuilder(this)
						.setTitle(item.title())
						.setSingleChoiceItems(labels, checked, (d, which) -> {
							manager.setString(item.key(), values.get(which));
							adapter.notifyDataSetChanged();
							d.dismiss();
						})
						.setNegativeButton(R.string.cancel, null)
						.show();
	}

	/**
	 * Lists the local queue; tapping a video removes it.
	 */
	private void showQueueDialog() {
		List<QueueItem> items = new ArrayList<>(queueRepository.getItems());
		if (items.isEmpty()) {
			new MaterialAlertDialogBuilder(this)
							.setTitle(R.string.queue_list)
							.setMessage(R.string.queue_list_empty)
							.setPositiveButton(R.string.confirm, null)
							.show();
			return;
		}
		List<String> labels = new ArrayList<>();
		for (QueueItem item : items) {
			labels.add(queueLabel(item));
		}
		ArrayAdapter<String> listAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, labels);
		ListView list = new ListView(this);
		list.setAdapter(listAdapter);
		androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
						.setTitle(R.string.queue_list_tap_to_remove)
						.setView(list)
						.setPositiveButton(R.string.confirm, null)
						.setNeutralButton(R.string.queue_list_clear, (d, w) -> {
							queueRepository.clear();
							adapter.notifyDataSetChanged();
							ToastUtils.show(this, R.string.queue_list_cleared);
						})
						.setOnDismissListener(d -> adapter.notifyDataSetChanged())
						.show();
		list.setOnItemClickListener((parent, view, position, id) -> {
			if (position < 0 || position >= items.size()) return;
			QueueItem removed = items.remove(position);
			if (removed.getVideoId() != null) {
				queueRepository.remove(removed.getVideoId());
			}
			labels.remove(position);
			listAdapter.notifyDataSetChanged();
			ToastUtils.show(this, R.string.queue_item_removed);
			if (items.isEmpty()) {
				dialog.dismiss();
			}
		});
	}

	@NonNull
	private String queueLabel(@NonNull QueueItem item) {
		String title = item.getTitle() == null || item.getTitle().isBlank() ? item.getVideoId() : item.getTitle();
		String author = item.getAuthor();
		return author == null || author.isBlank() ? String.valueOf(title) : title + " · " + author;
	}

	@NonNull
	private String sleepTimerSummary() {
		return switch (sleepTimer.mode()) {
			case END_OF_VIDEO -> getString(R.string.sleep_timer_end_of_video);
			case AT_TIME -> getString(R.string.sleep_timer_at, String.valueOf(sleepTimer.stopAt()));
			default -> getString(R.string.sleep_timer_off);
		};
	}

	private void showSleepTimerDialog() {
		String[] options = {
						getString(R.string.sleep_timer_off),
						getString(R.string.sleep_timer_end_of_video),
						getString(R.string.sleep_timer_pick_time)
		};
		new MaterialAlertDialogBuilder(this)
						.setTitle(R.string.sleep_timer)
						.setItems(options, (d, which) -> {
							if (which == 0) {
								sleepTimer.cancel();
								adapter.notifyDataSetChanged();
							} else if (which == 1) {
								sleepTimer.stopAfterThisVideo();
								adapter.notifyDataSetChanged();
							} else {
								showSleepTimePicker();
							}
						})
						.setNegativeButton(R.string.cancel, null)
						.show();
	}

	private void showSleepTimePicker() {
		LocalTime start = sleepTimer.stopAt() != null ? sleepTimer.stopAt() : LocalTime.now().plusHours(1);
		MaterialTimePicker picker = new MaterialTimePicker.Builder()
						.setTimeFormat(TimeFormat.CLOCK_24H)
						.setHour(start.getHour())
						.setMinute(start.getMinute())
						.setTitleText(R.string.sleep_timer_pick_time)
						.build();
		picker.addOnPositiveButtonClickListener(v -> {
			LocalTime time = LocalTime.of(picker.getHour(), picker.getMinute());
			sleepTimer.stopAt(time);
			adapter.notifyDataSetChanged();
			ToastUtils.show(this, getString(R.string.sleep_timer_at, time.toString()));
		});
		picker.show(getSupportFragmentManager(), "sleep_timer_time");
	}

	private boolean isLockedByMinimize(@NonNull Extension item) {
		return manager.isEnabled(Constant.GESTURE_SWIPE_DOWN_MINIMIZE)
						&& item.controlsOnly(Constant.MINIMIZE_DISABLED_KEYS);
	}

	private static void setLocked(@NonNull View itemView, boolean locked) {
		itemView.setAlpha(locked ? 0.38f : 1.0f);
		itemView.setEnabled(!locked);
		itemView.setClickable(!locked);
	}

	private void showPercentDialog(@NonNull Extension item) {
		int current = manager.getInt(item.key());
		int min = Constant.MIN_WATCHED_THRESHOLD_PERCENT;
		int max = Constant.MAX_WATCHED_THRESHOLD_PERCENT;
		int step = 5;
		int initial = Math.max(min, Math.min(max, Math.round(current / (float) step) * step));
		int pad = ViewUtils.dpToPx(this, 24);
		LinearLayout content = new LinearLayout(this);
		content.setOrientation(LinearLayout.VERTICAL);
		content.setPadding(pad, pad / 2, pad, 0);
		TextView valueText = new TextView(this);
		valueText.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium);
		valueText.setText(getString(R.string.watched_threshold_value, initial));
		Slider slider = new Slider(this);
		slider.setValueFrom(min);
		slider.setValueTo(max);
		slider.setStepSize(step);
		slider.setValue(initial);
		slider.setLabelFormatter(value -> Math.round(value) + "%");
		slider.addOnChangeListener((s, value, fromUser) ->
						valueText.setText(getString(R.string.watched_threshold_value, Math.round(value))));
		content.addView(valueText);
		content.addView(slider);
		new MaterialAlertDialogBuilder(this)
						.setTitle(item.title())
						.setView(content)
						.setPositiveButton(R.string.confirm, (d, w) -> {
							manager.setInt(item.key(), Math.round(slider.getValue()));
							adapter.notifyDataSetChanged();
						})
						.setNegativeButton(R.string.cancel, null)
						.show();
	}

	private void showBlockedChannelsDialog() {
		List<BlockedChannel> channels = contentFilters.blockedChannels();
		if (channels.isEmpty()) {
			new MaterialAlertDialogBuilder(this)
							.setTitle(R.string.blocked_channels)
							.setMessage(R.string.blocked_channels_empty)
							.setPositiveButton(R.string.confirm, null)
							.show();
			return;
		}
		String[] names = new String[channels.size()];
		for (int i = 0; i < channels.size(); i++) {
			names[i] = channels.get(i).name();
		}
		boolean[] checked = new boolean[channels.size()];
		new MaterialAlertDialogBuilder(this)
						.setTitle(R.string.blocked_channels_unblock_title)
						.setMultiChoiceItems(names, checked, (d, which, isChecked) -> checked[which] = isChecked)
						.setPositiveButton(R.string.unblock, (d, w) -> {
							List<BlockedChannel> remove = new ArrayList<>();
							for (int i = 0; i < checked.length; i++) {
								if (checked[i]) remove.add(channels.get(i));
							}
							contentFilters.unblockChannels(remove);
							adapter.notifyDataSetChanged();
						})
						.setNegativeButton(R.string.cancel, null)
						.show();
	}

	private final class ValueHolder extends RecyclerView.ViewHolder {
		private final TextView title;
		private final TextView summary;
		private final ImageView chevron;
		private final ImageView icon;

		private ValueHolder(@NonNull View itemView) {
			super(itemView);
			title = itemView.findViewById(R.id.title);
			summary = itemView.findViewById(R.id.summary);
			chevron = itemView.findViewById(R.id.chevron);
			icon = itemView.findViewById(R.id.icon);
		}

		private void bind(@NonNull Extension item) {
			title.setText(item.title());
			icon.setVisibility(View.GONE);
			summary.setVisibility(View.VISIBLE);
			if (item.isChoice()) {
				String description = item.summary() == 0 ? "" : getString(item.summary()) + "\n";
				summary.setText(description + qualityLabel(manager.getString(item.key())));
				chevron.setVisibility(View.GONE);
				itemView.setOnClickListener(v -> showQualityDialog(item));
				return;
			}
			if (Constant.ACTION_QUEUE.equals(item.key())) {
				String description = item.summary() == 0 ? "" : getString(item.summary()) + "\n";
				int count = queueRepository.getItems().size();
				summary.setText(description + getResources().getQuantityString(R.plurals.queue_list_count, count, count));
				chevron.setVisibility(View.VISIBLE);
				itemView.setOnClickListener(v -> showQueueDialog());
				return;
			}
			if (Constant.ACTION_SLEEP_TIMER.equals(item.key())) {
				String description = item.summary() == 0 ? "" : getString(item.summary()) + "\n";
				summary.setText(description + sleepTimerSummary());
				chevron.setVisibility(View.VISIBLE);
				itemView.setOnClickListener(v -> showSleepTimerDialog());
				return;
			}
			if (item.isPercent()) {
				String description = item.summary() == 0 ? "" : getString(item.summary()) + "\n";
				summary.setText(description + getString(R.string.watched_threshold_value, manager.getInt(item.key())));
				chevron.setVisibility(View.GONE);
				itemView.setOnClickListener(v -> showPercentDialog(item));
				return;
			}
			int count = contentFilters.blockedChannels().size();
			String description = item.summary() == 0 ? "" : getString(item.summary()) + "\n";
			summary.setText(description + getResources().getQuantityString(R.plurals.blocked_channels_count, count, count));
			chevron.setVisibility(View.VISIBLE);
			itemView.setOnClickListener(v -> showBlockedChannelsDialog());
		}
	}

	private final class ToggleHolder extends RecyclerView.ViewHolder {
		private final TextView title;
		private final TextView summary;
		private final SwitchMaterial toggle;

		private ToggleHolder(@NonNull View itemView) {
			super(itemView);
			title = itemView.findViewById(R.id.title);
			summary = itemView.findViewById(R.id.summary);
			toggle = itemView.findViewById(R.id.toggle);
		}

		private void bind(@NonNull Extension item) {
			title.setText(item.title());
			if (item.summary() == 0) {
				summary.setVisibility(View.GONE);
			} else {
				summary.setVisibility(View.VISIBLE);
				summary.setText(item.summary());
			}
			toggle.setOnCheckedChangeListener(null);
			toggle.setChecked(manager.isEnabled(item.key()));
			if (isLockedByMinimize(item)) {
				summary.setVisibility(View.VISIBLE);
				summary.setText(R.string.disabled_by_swipe_minimize);
				setLocked(itemView, true);
				toggle.setEnabled(false);
				itemView.setOnClickListener(null);
				return;
			}
			setLocked(itemView, false);
			toggle.setEnabled(true);
			toggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
				manager.setEnabled(item.key(), isChecked);
				if (Constant.GESTURE_SWIPE_DOWN_MINIMIZE.equals(item.key())) {
					itemView.post(adapter::notifyDataSetChanged);
				}
			});
			itemView.setOnClickListener(v -> toggle.toggle());
		}
	}
}
