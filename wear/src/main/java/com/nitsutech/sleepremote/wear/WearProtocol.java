package com.nitsutech.sleepremote.wear;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared Wearable Data Layer contract between Phone (:app) and Galaxy Watch (:wear).
 */
public final class WearProtocol {

    private WearProtocol() {
    }

    public static final String PATH_STATE_DATA = "/sleepremote/state";
    public static final String PATH_SYNC_MSG = "/sleepremote/sync_state";
    public static final String PATH_ACTION_RESULT = "/sleepremote/action_result";

    public static final String PATH_REQUEST_SYNC = "/sleepremote/request_sync";
    public static final String PATH_TOGGLE_ALARM = "/sleepremote/toggle_alarm";
    public static final String PATH_CREATE_ALARM = "/sleepremote/create_alarm";
    public static final String PATH_CREATE_NAP = "/sleepremote/create_nap";
    public static final String PATH_SLEEP_ACTION = "/sleepremote/sleep_action";

    public static final String ACTION_START_TRACK = "START_TRACK";
    public static final String ACTION_START_IDEAL = "START_IDEAL";
    public static final String ACTION_STOP_TRACK = "STOP_TRACK";
    public static final String ACTION_SNOOZE = "SNOOZE";
    public static final String ACTION_SKIP_NEXT = "SKIP_NEXT";
    public static final String ACTION_DISMISS = "DISMISS";

    public static final int BIT_MON = 1;
    public static final int BIT_TUE = 2;
    public static final int BIT_WED = 4;
    public static final int BIT_THU = 8;
    public static final int BIT_FRI = 16;
    public static final int BIT_SAT = 32;
    public static final int BIT_SUN = 64;

    public static final String EXTRA_STATE_JSON = "state_json";
    public static final String EXTRA_TS = "ts";

    public static class AlarmDto {
        public long id;
        public int hour;
        public int minutes;
        public int daysOfWeek;
        public boolean enabled;
        public String label = "";
        public long alarmTime;
        public int smartPeriodMinutes;
    }

    public static class StatePayload {
        public List<AlarmDto> alarms = new ArrayList<>();
        public List<Integer> napPresets = new ArrayList<>();
        public Map<Long, String> alarmAliases = new HashMap<>();
        public int defaultSnoozeMinutes = 10;
        public boolean sleepInstalled;
        public String sleepVersion = "";
        public boolean readPermissionGranted;
        public boolean overlayPermissionGranted;
        public boolean trackingActive;
        public long syncedAtMs;
        public String phoneName = "";
    }

    public static class ToggleAlarmRequest {
        public long id;
        public String label = "";
        public boolean enabled;
    }

    public static class CreateAlarmRequest {
        public int hour;
        public int minutes;
        public String label = "";
        public int daysOfWeekMask;
        public boolean vibrate = true;
    }

    public static class CreateNapRequest {
        public int minutes;
        public String label = "";
    }

    public static class SleepActionRequest {
        public String action = "";
        public int snoozeMinutes = 10;
    }

    public static class ActionResultPayload {
        public boolean ok;
        public String action = "";
        public String detail = "";
    }

    public static ArrayList<Integer> toCalendarDays(int mask) {
        ArrayList<Integer> days = new ArrayList<>();
        if ((mask & BIT_MON) != 0) days.add(Calendar.MONDAY);
        if ((mask & BIT_TUE) != 0) days.add(Calendar.TUESDAY);
        if ((mask & BIT_WED) != 0) days.add(Calendar.WEDNESDAY);
        if ((mask & BIT_THU) != 0) days.add(Calendar.THURSDAY);
        if ((mask & BIT_FRI) != 0) days.add(Calendar.FRIDAY);
        if ((mask & BIT_SAT) != 0) days.add(Calendar.SATURDAY);
        if ((mask & BIT_SUN) != 0) days.add(Calendar.SUNDAY);
        return days;
    }

    /**
     * Computes the next trigger epoch millis for an alarm so alarms sort identically
     * to Sleep as Android (soonest upcoming occurrence first).
     */
    public static long computeNextAlarmMillis(int hour, int minutes, int daysOfWeekMask, long nowMs) {
        Calendar candidate = Calendar.getInstance();
        candidate.setTimeInMillis(nowMs);
        candidate.set(Calendar.HOUR_OF_DAY, hour);
        candidate.set(Calendar.MINUTE, minutes);
        candidate.set(Calendar.SECOND, 0);
        candidate.set(Calendar.MILLISECOND, 0);

        if (daysOfWeekMask == 0) {
            if (candidate.getTimeInMillis() <= nowMs) {
                candidate.add(Calendar.DAY_OF_YEAR, 1);
            }
            return candidate.getTimeInMillis();
        }

        for (int offset = 0; offset <= 7; offset++) {
            Calendar check = (Calendar) candidate.clone();
            check.add(Calendar.DAY_OF_YEAR, offset);
            int bit = calendarDayToBit(check.get(Calendar.DAY_OF_WEEK));
            if ((daysOfWeekMask & bit) != 0 && check.getTimeInMillis() > nowMs) {
                return check.getTimeInMillis();
            }
        }
        return candidate.getTimeInMillis();
    }

    private static int calendarDayToBit(int calendarDayOfWeek) {
        switch (calendarDayOfWeek) {
            case Calendar.MONDAY:
                return BIT_MON;
            case Calendar.TUESDAY:
                return BIT_TUE;
            case Calendar.WEDNESDAY:
                return BIT_WED;
            case Calendar.THURSDAY:
                return BIT_THU;
            case Calendar.FRIDAY:
                return BIT_FRI;
            case Calendar.SATURDAY:
                return BIT_SAT;
            case Calendar.SUNDAY:
                return BIT_SUN;
            default:
                return 0;
        }
    }
}
