package com.nitsutech.sleepremote.wear;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;

import java.util.Arrays;

/**
 * Caches synced phone state locally on the Watch for instant UI and Tile rendering.
 */
public class WatchPrefsManager {

    private static final String PREF_NAME = "sleep_remote_wear_prefs";
    private static final String KEY_STATE_JSON = "state_json";
    private static final Gson GSON = new Gson();

    private final SharedPreferences prefs;

    public WatchPrefsManager(Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public void saveStateJson(String json) {
        if (json == null || json.isEmpty()) return;
        prefs.edit().putString(KEY_STATE_JSON, json).apply();
    }

    public void saveState(WearProtocol.StatePayload payload) {
        if (payload == null) return;
        saveStateJson(GSON.toJson(payload));
    }

    public WearProtocol.StatePayload getState() {
        String raw = prefs.getString(KEY_STATE_JSON, null);
        WearProtocol.StatePayload state = null;
        if (raw != null && !raw.isEmpty()) {
            try {
                state = GSON.fromJson(raw, WearProtocol.StatePayload.class);
            } catch (Exception ignored) {
            }
        }
        if (state == null) {
            state = new WearProtocol.StatePayload();
        }
        if (state.napPresets == null || state.napPresets.isEmpty()) {
            state.napPresets = Arrays.asList(20, 30, 45, 60, 90);
        }
        if (state.defaultSnoozeMinutes <= 0) {
            state.defaultSnoozeMinutes = 10;
        }
        return state;
    }
}
