package com.nitsutech.sleepremote.sleep;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.provider.AlarmClock;
import android.text.TextUtils;

import com.nitsutech.sleepremote.R;
import com.nitsutech.sleepremote.data.PrefsManager;
import com.nitsutech.sleepremote.wear.WearProtocol;

import java.util.ArrayList;
import java.util.Calendar;

/**
 * Executes Sleep as Android Intent API commands on the phone.
 */
public class SleepCommandController {

    public static class CommandResult {
        public final boolean ok;
        public final String detail;

        public CommandResult(boolean ok, String detail) {
            this.ok = ok;
            this.detail = detail;
        }
    }

    public static CommandResult createAlarm(Context context, int hour, int minutes, String label, int daysMask) {
        return createAlarm(context, hour, minutes, label, daysMask, true);
    }

    public static CommandResult createAlarm(
            Context context, int hour, int minutes, String label, int daysMask, boolean vibrate) {
        if (!SleepAlarmRepository.isSleepInstalled(context)) {
            return new CommandResult(false, context.getString(R.string.err_sleep_not_installed));
        }

        int safeHour = ((hour % 24) + 24) % 24;
        int safeMinute = ((minutes % 60) + 60) % 60;
        String effectiveLabel = TextUtils.isEmpty(label)
                ? context.getString(R.string.default_alarm_label)
                : label.trim();

        Intent intent = new Intent(AlarmClock.ACTION_SET_ALARM);
        intent.setComponent(new ComponentName(SleepConstants.SLEEP_PACKAGE, SleepConstants.COMPONENT_ADD_ALARM));
        intent.putExtra(AlarmClock.EXTRA_HOUR, safeHour);
        intent.putExtra(AlarmClock.EXTRA_MINUTES, safeMinute);
        intent.putExtra(AlarmClock.EXTRA_MESSAGE, effectiveLabel);
        intent.putExtra(AlarmClock.EXTRA_VIBRATE, vibrate);
        intent.putExtra(AlarmClock.EXTRA_SKIP_UI, true);

        ArrayList<Integer> days = WearProtocol.toCalendarDays(daysMask);
        if (!days.isEmpty()) {
            intent.putIntegerArrayListExtra(AlarmClock.EXTRA_DAYS, days);
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        try {
            context.startActivity(intent);
            return new CommandResult(true, String.format("%02d:%02d (%s)", safeHour, safeMinute, effectiveLabel));
        } catch (Exception e) {
            return new CommandResult(false, e.getMessage() != null ? e.getMessage() : "startActivity failed");
        }
    }

    public static CommandResult createNapAlarm(Context context, int offsetMinutes, String customLabel) {
        int safeOffset = Math.max(1, Math.min(720, offsetMinutes));
        Calendar target = Calendar.getInstance();
        target.add(Calendar.MINUTE, safeOffset);

        int hour = target.get(Calendar.HOUR_OF_DAY);
        int minute = target.get(Calendar.MINUTE);
        String label = !TextUtils.isEmpty(customLabel)
                ? customLabel.trim()
                : context.getString(R.string.nap_alarm_label_fmt, safeOffset);

        return createAlarm(context, hour, minute, label, 0);
    }

    public static CommandResult toggleAlarm(Context context, long alarmId, String label, boolean enabled) {
        if (!SleepAlarmRepository.isSleepInstalled(context)) {
            return new CommandResult(false, context.getString(R.string.err_sleep_not_installed));
        }

        boolean hasId = alarmId > 0;
        String cleanLabel = label != null ? label.trim() : "";
        if (!hasId && TextUtils.isEmpty(cleanLabel)) {
            return new CommandResult(false, context.getString(R.string.err_label_required_for_toggle));
        }

        Intent intent = new Intent(SleepConstants.ACTION_ALARM_STATE_CHANGE);
        intent.setComponent(new ComponentName(
                SleepConstants.SLEEP_PACKAGE,
                SleepConstants.COMPONENT_ALARM_MODIFICATION_RECEIVER
        ));
        if (hasId) {
            intent.putExtra(SleepConstants.EXTRA_ALARM_ID, (int) alarmId);
        } else {
            intent.putExtra(SleepConstants.EXTRA_ALARM_LABEL, cleanLabel);
        }
        intent.putExtra(SleepConstants.EXTRA_ALARM_ENABLED, enabled);

        context.sendBroadcast(intent);
        String display = !TextUtils.isEmpty(cleanLabel)
                ? cleanLabel
                : context.getString(R.string.alarm_default_id_fmt, alarmId);
        return new CommandResult(true, display + " -> " + (enabled ? "ON" : "OFF"));
    }

    public static CommandResult executeSleepAction(Context context, String action, int snoozeMinutes) {
        if (!SleepAlarmRepository.isSleepInstalled(context)) {
            return new CommandResult(false, context.getString(R.string.err_sleep_not_installed));
        }

        PrefsManager prefs = new PrefsManager(context);
        Intent intent = new Intent();
        intent.setPackage(SleepConstants.SLEEP_PACKAGE);

        if (WearProtocol.ACTION_START_TRACK.equals(action)) {
            intent.setAction(SleepConstants.ACTION_START_SLEEP_TRACK);
            prefs.setTrackingActive(true);
        } else if (WearProtocol.ACTION_START_IDEAL.equals(action)) {
            intent.setAction(SleepConstants.ACTION_START_SLEEP_TRACK_IDEAL);
            prefs.setTrackingActive(true);
        } else if (WearProtocol.ACTION_STOP_TRACK.equals(action)) {
            intent.setAction(SleepConstants.ACTION_STOP_SLEEP_TRACK);
            prefs.setTrackingActive(false);
        } else if (WearProtocol.ACTION_SNOOZE.equals(action)) {
            intent.setAction(SleepConstants.ACTION_ALARM_SNOOZE);
            int mins = snoozeMinutes > 0 ? snoozeMinutes : prefs.getDefaultSnoozeMinutes();
            intent.putExtra(SleepConstants.EXTRA_SNOOZE_TIME, mins);
        } else if (WearProtocol.ACTION_SKIP_NEXT.equals(action)) {
            intent.setAction(SleepConstants.ACTION_SKIP_NEXT_ALARM);
        } else if (WearProtocol.ACTION_DISMISS.equals(action)) {
            intent.setAction(SleepConstants.ACTION_ALARM_DISMISS_CAPTCHA);
        } else {
            return new CommandResult(false, "Unknown action: " + action);
        }

        context.sendBroadcast(intent);
        return new CommandResult(true, action);
    }
}
