package com.nitsutech.sleepremote.sleep;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.provider.Settings;
import android.text.TextUtils;

import androidx.core.content.ContextCompat;

import com.nitsutech.sleepremote.wear.WearProtocol;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Reads configured alarms and permission/installation state from Sleep as Android.
 */
public class SleepAlarmRepository {

    public static boolean isSleepInstalled(Context context) {
        return !TextUtils.isEmpty(getSleepVersionName(context));
    }

    public static String getSleepVersionName(Context context) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(SleepConstants.SLEEP_PACKAGE, 0);
            return info != null && info.versionName != null ? info.versionName : "installed";
        } catch (PackageManager.NameNotFoundException e) {
            return "";
        }
    }

    public static boolean hasSleepReadPermission(Context context) {
        return ContextCompat.checkSelfPermission(context, SleepConstants.PERMISSION_SLEEP_READ)
                == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean hasOverlayPermission(Context context) {
        return Settings.canDrawOverlays(context);
    }

    public static List<WearProtocol.AlarmDto> loadAlarms(Context context) {
        List<WearProtocol.AlarmDto> list = new ArrayList<>();
        if (!isSleepInstalled(context) || !hasSleepReadPermission(context)) {
            return list;
        }

        final long nowMs = System.currentTimeMillis();
        try (Cursor cursor = context.getContentResolver().query(
                SleepConstants.ALARM_CONTENT_URI,
                null,
                null,
                null,
                null
        )) {
            if (cursor == null) {
                return list;
            }
            int idxId = cursor.getColumnIndex(SleepConstants.COL_ID);
            int idxHour = cursor.getColumnIndex(SleepConstants.COL_HOUR);
            int idxMin = cursor.getColumnIndex(SleepConstants.COL_MINUTES);
            int idxDays = cursor.getColumnIndex(SleepConstants.COL_DAYS_OF_WEEK);
            int idxEnabled = cursor.getColumnIndex(SleepConstants.COL_ENABLED);
            int idxMessage = cursor.getColumnIndex(SleepConstants.COL_MESSAGE);
            int idxLabel = cursor.getColumnIndex(SleepConstants.COL_LABEL);
            int idxSmart = cursor.getColumnIndex(SleepConstants.COL_NDS_WAKEUP_WINDOW);

            while (cursor.moveToNext()) {
                WearProtocol.AlarmDto dto = new WearProtocol.AlarmDto();
                dto.id = idxId >= 0 ? cursor.getLong(idxId) : 0L;
                dto.hour = idxHour >= 0 ? cursor.getInt(idxHour) : 0;
                dto.minutes = idxMin >= 0 ? cursor.getInt(idxMin) : 0;
                dto.daysOfWeek = idxDays >= 0 ? cursor.getInt(idxDays) : 0;
                dto.enabled = readBooleanColumn(cursor, idxEnabled);
                dto.alarmTime = WearProtocol.computeNextAlarmMillis(
                        dto.hour, dto.minutes, dto.daysOfWeek, nowMs);
                if (idxSmart >= 0) {
                    int rawSmart = cursor.getInt(idxSmart);
                    dto.smartPeriodMinutes = rawSmart > 180 ? Math.round(rawSmart / 60000f) : Math.max(0, rawSmart);
                }

                String msg = idxMessage >= 0 ? cursor.getString(idxMessage) : "";
                String lbl = idxLabel >= 0 ? cursor.getString(idxLabel) : "";
                dto.label = !TextUtils.isEmpty(msg) ? msg.trim() : (!TextUtils.isEmpty(lbl) ? lbl.trim() : "");
                list.add(dto);
            }
        } catch (SecurityException | IllegalArgumentException ignored) {
            return list;
        }

        // Match Sleep as Android ordering: enabled first by soonest next trigger time, then disabled.
        list.sort(Comparator
                .comparing((WearProtocol.AlarmDto a) -> !a.enabled)
                .thenComparingLong(a -> a.alarmTime)
                .thenComparingLong(a -> a.id));
        return list;
    }

    private static boolean readBooleanColumn(Cursor cursor, int colIndex) {
        if (colIndex < 0) return false;
        int type = cursor.getType(colIndex);
        if (type == Cursor.FIELD_TYPE_INTEGER) {
            return cursor.getInt(colIndex) != 0;
        }
        String raw = cursor.getString(colIndex);
        return "1".equals(raw) || "true".equalsIgnoreCase(raw);
    }
}
