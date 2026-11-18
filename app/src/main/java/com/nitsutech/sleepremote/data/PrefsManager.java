package com.nitsutech.sleepremote.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Persists configurable nap presets, snooze duration, local alarm aliases, and sync metadata.
 */
public class PrefsManager {

    private static final String PREF_NAME = "sleep_remote_prefs";
    private static final String KEY_NAP_PRESETS = "nap_presets_json";
    private static final String KEY_ALARM_ALIASES = "alarm_aliases_json";
    private static final String KEY_ALARM_SEQ_ID = "alarm_seq_id";
    private static final String KEY_SNOOZE_MINUTES = "default_snooze_minutes";
    private static final String KEY_LAST_SYNC_MS = "last_sync_ms";
    private static final String KEY_TRACKING_ACTIVE = "tracking_active";

    private static final List<Integer> DEFAULT_NAPS = Arrays.asList(20, 30, 45, 60, 90);
    private static final Gson GSON = new Gson();
    private static final Type INT_LIST_TYPE = new TypeToken<List<Integer>>() {
    }.getType();
    private static final Type ALIAS_MAP_TYPE = new TypeToken<Map<Long, String>>() {
    }.getType();

    private final SharedPreferences prefs;

    public PrefsManager(Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public List<Integer> getNapPresets() {
        String raw = prefs.getString(KEY_NAP_PRESETS, null);
        if (raw == null || raw.isEmpty()) {
            return new ArrayList<>(DEFAULT_NAPS);
        }
        try {
            List<Integer> parsed = GSON.fromJson(raw, INT_LIST_TYPE);
            if (parsed == null || parsed.isEmpty()) {
                return new ArrayList<>(DEFAULT_NAPS);
            }
            return normalizePresets(parsed);
        } catch (Exception e) {
            return new ArrayList<>(DEFAULT_NAPS);
        }
    }

    public void saveNapPresets(List<Integer> presets) {
        List<Integer> clean = normalizePresets(presets);
        if (clean.isEmpty()) {
            clean = new ArrayList<>(DEFAULT_NAPS);
        }
        prefs.edit().putString(KEY_NAP_PRESETS, GSON.toJson(clean)).apply();
    }

    public void addNapPreset(int minutes) {
        if (minutes <= 0 || minutes > 720) return;
        List<Integer> current = getNapPresets();
        current.add(minutes);
        saveNapPresets(current);
    }

    public void removeNapPreset(int minutes) {
        List<Integer> current = getNapPresets();
        current.remove(Integer.valueOf(minutes));
        if (!current.isEmpty()) {
            saveNapPresets(current);
        }
    }

    public void resetDefaultNapPresets() {
        saveNapPresets(DEFAULT_NAPS);
    }

    public int getDefaultSnoozeMinutes() {
        return prefs.getInt(KEY_SNOOZE_MINUTES, 10);
    }

    public void setDefaultSnoozeMinutes(int minutes) {
        int clamped = Math.max(1, Math.min(60, minutes));
        prefs.edit().putInt(KEY_SNOOZE_MINUTES, clamped).apply();
    }

    public long getLastSyncMs() {
        return prefs.getLong(KEY_LAST_SYNC_MS, 0L);
    }

    public void setLastSyncMs(long ts) {
        prefs.edit().putLong(KEY_LAST_SYNC_MS, ts).apply();
    }

    public boolean isTrackingActive() {
        return prefs.getBoolean(KEY_TRACKING_ACTIVE, false);
    }

    public void setTrackingActive(boolean active) {
        prefs.edit().putBoolean(KEY_TRACKING_ACTIVE, active).apply();
    }

    public synchronized int nextAlarmTagId() {
        int next = prefs.getInt(KEY_ALARM_SEQ_ID, 0) + 1;
        prefs.edit().putInt(KEY_ALARM_SEQ_ID, next).apply();
        return next;
    }

    public Map<Long, String> getAlarmAliases() {
        String raw = prefs.getString(KEY_ALARM_ALIASES, null);
        if (raw == null || raw.isEmpty()) {
            return new HashMap<>();
        }
        try {
            Map<Long, String> parsed = GSON.fromJson(raw, ALIAS_MAP_TYPE);
            return parsed != null ? new HashMap<>(parsed) : new HashMap<>();
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    public void setAlarmAlias(long alarmId, String alias) {
        Map<Long, String> map = getAlarmAliases();
        String trimmed = alias != null ? alias.trim() : "";
        if (trimmed.isEmpty()) {
            map.remove(alarmId);
        } else {
            map.put(alarmId, trimmed);
        }
        prefs.edit().putString(KEY_ALARM_ALIASES, GSON.toJson(map)).apply();
    }

    private static List<Integer> normalizePresets(List<Integer> input) {
        Set<Integer> unique = new LinkedHashSet<>();
        for (Integer val : input) {
            if (val != null && val > 0 && val <= 720) {
                unique.add(val);
            }
        }
        List<Integer> result = new ArrayList<>(unique);
        Collections.sort(result);
        return result;
    }
}
