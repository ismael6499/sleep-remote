package com.nitsutech.sleepremote.sleep;

import android.net.Uri;

/**
 * Verified package, ContentProvider, permission, and Intent API constants for Sleep as Android.
 */
public final class SleepConstants {

    private SleepConstants() {
    }

    public static final String SLEEP_PACKAGE = "com.urbandroid.sleep";
    public static final String PERMISSION_SLEEP_READ = "com.urbandroid.sleep.READ";

    public static final Uri ALARM_CONTENT_URI = Uri.parse("content://com.urbandroid.sleep.alarmclock/alarm");

    public static final String COMPONENT_ADD_ALARM = "com.urbandroid.sleep.alarmclock.AddAlarmActivity";
    public static final String COMPONENT_ALARM_MODIFICATION_RECEIVER =
            "com.urbandroid.sleep.alarmclock.AlarmModificationReceiver";

    public static final String ACTION_ALARM_STATE_CHANGE =
            "com.urbandroid.sleep.alarmclock.ALARM_STATE_CHANGE";
    public static final String EXTRA_ALARM_ID = "alarm_id";
    public static final String EXTRA_ALARM_LABEL = "alarm_label";
    public static final String EXTRA_ALARM_ENABLED = "alarm_enabled";

    public static final String ACTION_START_SLEEP_TRACK =
            "com.urbandroid.sleep.alarmclock.START_SLEEP_TRACK";
    public static final String ACTION_START_SLEEP_TRACK_IDEAL =
            "com.urbandroid.sleep.alarmclock.START_SLEEP_TRACK_WITH_IDEAL_ALARM_ACTION";
    public static final String ACTION_STOP_SLEEP_TRACK =
            "com.urbandroid.sleep.alarmclock.STOP_SLEEP_TRACK";
    public static final String ACTION_ALARM_SNOOZE =
            "com.urbandroid.sleep.alarmclock.ALARM_SNOOZE";
    public static final String EXTRA_SNOOZE_TIME = "extra_snooze_time";
    public static final String ACTION_SKIP_NEXT_ALARM =
            "com.urbandroid.sleep.alarmclock.SKIP_NEXT_ALARM_ACTION";
    public static final String ACTION_ALARM_DISMISS_CAPTCHA =
            "com.urbandroid.sleep.alarmclock.ALARM_DISMISS_CAPTCHA";

    public static final String COL_ID = "_id";
    public static final String COL_HOUR = "hour";
    public static final String COL_MINUTES = "minutes";
    public static final String COL_DAYS_OF_WEEK = "daysofweek";
    public static final String COL_ALARM_TIME = "alarmtime";
    public static final String COL_ENABLED = "enabled";
    public static final String COL_MESSAGE = "message";
    public static final String COL_LABEL = "label";
    public static final String COL_NDS_WAKEUP_WINDOW = "ndswakeupwindow";
}
