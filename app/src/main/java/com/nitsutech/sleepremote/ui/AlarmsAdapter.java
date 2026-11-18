package com.nitsutech.sleepremote.ui;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.nitsutech.sleepremote.R;
import com.nitsutech.sleepremote.wear.WearProtocol;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Displays synced Sleep as Android alarms on the phone dashboard with remote toggle & alias support.
 */
public class AlarmsAdapter extends RecyclerView.Adapter<AlarmsAdapter.ViewHolder> {

    public interface OnAlarmActionListener {
        void onToggle(WearProtocol.AlarmDto alarm, boolean targetEnabled);
        void onEditAlias(WearProtocol.AlarmDto alarm);
    }

    private final List<WearProtocol.AlarmDto> items = new ArrayList<>();
    private final Map<Long, String> aliases = new HashMap<>();
    private final OnAlarmActionListener listener;

    public AlarmsAdapter(OnAlarmActionListener listener) {
        this.listener = listener;
    }

    public void submitList(List<WearProtocol.AlarmDto> alarms, Map<Long, String> alarmAliases) {
        items.clear();
        if (alarms != null) {
            items.addAll(alarms);
        }
        aliases.clear();
        if (alarmAliases != null) {
            aliases.putAll(alarmAliases);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_alarm, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        WearProtocol.AlarmDto item = items.get(position);
        Context ctx = holder.itemView.getContext();

        holder.tvTime.setText(String.format(Locale.US, "%02d:%02d", item.hour, item.minutes));
        holder.tvLabel.setText(resolveDisplayLabel(ctx, item, aliases));

        String daysText = formatDays(ctx, item.daysOfWeek);
        if (item.smartPeriodMinutes > 0) {
            daysText = daysText + " · " + ctx.getString(R.string.alarm_smart_period_fmt, item.smartPeriodMinutes);
        }
        holder.tvDays.setText(daysText);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onEditAlias(item);
            }
        });

        holder.switchEnabled.setOnCheckedChangeListener(null);
        holder.switchEnabled.setChecked(item.enabled);
        holder.switchEnabled.setEnabled(true);
        holder.switchEnabled.setAlpha(1.0f);
        holder.switchEnabled.setOnCheckedChangeListener((btn, isChecked) -> {
            if (listener != null) {
                listener.onToggle(item, isChecked);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public static String resolveDisplayLabel(
            Context ctx, WearProtocol.AlarmDto item, Map<Long, String> aliasMap) {
        if (!TextUtils.isEmpty(item.label)) {
            return item.label;
        }
        if (aliasMap != null) {
            String alias = aliasMap.get(item.id);
            if (!TextUtils.isEmpty(alias)) {
                return alias;
            }
        }
        return ctx.getString(R.string.alarm_default_id_fmt, item.id);
    }

    public static String formatDays(Context ctx, int mask) {
        if (mask == 0) {
            return ctx.getString(R.string.days_one_time);
        } else if (mask == 0x7f) {
            return ctx.getString(R.string.days_every_day);
        } else if (mask == (WearProtocol.BIT_MON | WearProtocol.BIT_TUE | WearProtocol.BIT_WED
                | WearProtocol.BIT_THU | WearProtocol.BIT_FRI)) {
            return ctx.getString(R.string.days_weekdays);
        } else if (mask == (WearProtocol.BIT_SAT | WearProtocol.BIT_SUN)) {
            return ctx.getString(R.string.days_weekends);
        } else {
            List<String> parts = new ArrayList<>();
            if ((mask & WearProtocol.BIT_MON) != 0) parts.add(ctx.getString(R.string.day_short_mon));
            if ((mask & WearProtocol.BIT_TUE) != 0) parts.add(ctx.getString(R.string.day_short_tue));
            if ((mask & WearProtocol.BIT_WED) != 0) parts.add(ctx.getString(R.string.day_short_wed));
            if ((mask & WearProtocol.BIT_THU) != 0) parts.add(ctx.getString(R.string.day_short_thu));
            if ((mask & WearProtocol.BIT_FRI) != 0) parts.add(ctx.getString(R.string.day_short_fri));
            if ((mask & WearProtocol.BIT_SAT) != 0) parts.add(ctx.getString(R.string.day_short_sat));
            if ((mask & WearProtocol.BIT_SUN) != 0) parts.add(ctx.getString(R.string.day_short_sun));
            return TextUtils.join(" ", parts);
        }
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvTime;
        final TextView tvLabel;
        final TextView tvDays;
        final SwitchCompat switchEnabled;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTime = itemView.findViewById(R.id.tv_item_alarm_time);
            tvLabel = itemView.findViewById(R.id.tv_item_alarm_label);
            tvDays = itemView.findViewById(R.id.tv_item_alarm_days);
            switchEnabled = itemView.findViewById(R.id.sw_item_alarm_enabled);
        }
    }
}
