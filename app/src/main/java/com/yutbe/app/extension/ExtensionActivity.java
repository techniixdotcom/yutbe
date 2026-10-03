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

	private boolean onMenuItemClick(@NonNull MenuItem item) {
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
			if (item.isPercent() || item.isAction()) return TYPE_VALUE;
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

	private boolean isLockedByMinimize(@NonNull Extension item) {
		return manager.isEnabled(Constant.GESTURE_SWIPE_DOWN_MINIMIZE)
						&& item.controlsAny(Constant.MINIMIZE_DISABLED_KEYS);
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
