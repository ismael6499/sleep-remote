package com.nitsutech.sleepremote.wear;

import androidx.annotation.NonNull;

import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;
import com.google.gson.Gson;
import com.nitsutech.sleepremote.sleep.SleepCommandController;

import java.nio.charset.StandardCharsets;

/**
 * Receives commands from the Galaxy Watch 8 Classic and dispatches them to Sleep as Android.
 */
public class PhoneWearListenerService extends WearableListenerService {

    private static final Gson GSON = new Gson();

    @Override
    public void onMessageReceived(@NonNull MessageEvent messageEvent) {
        String path = messageEvent.getPath();
        String json = new String(messageEvent.getData(), StandardCharsets.UTF_8);

        if (WearProtocol.PATH_REQUEST_SYNC.equals(path)) {
            WearDataLayerManager.syncStateAsync(this);
            return;
        }

        if (WearProtocol.PATH_TOGGLE_ALARM.equals(path)) {
            WearProtocol.ToggleAlarmRequest req = GSON.fromJson(json, WearProtocol.ToggleAlarmRequest.class);
            if (req != null) {
                SleepCommandController.CommandResult res =
                        SleepCommandController.toggleAlarm(this, req.id, req.label, req.enabled);
                WearDataLayerManager.sendActionResultAsync(this, res.ok, path, res.detail);
                WearDataLayerManager.syncStateDelayedAsync(this, 450);
                WearDataLayerManager.syncStateDelayedAsync(this, 1200);
            }
            return;
        }

        if (WearProtocol.PATH_CREATE_ALARM.equals(path)) {
            WearProtocol.CreateAlarmRequest req = GSON.fromJson(json, WearProtocol.CreateAlarmRequest.class);
            if (req != null) {
                SleepCommandController.CommandResult res = SleepCommandController.createAlarm(
                        this, req.hour, req.minutes, req.label, req.daysOfWeekMask, req.vibrate);
                WearDataLayerManager.sendActionResultAsync(this, res.ok, path, res.detail);
                WearDataLayerManager.syncStateDelayedAsync(this, 600);
                WearDataLayerManager.syncStateDelayedAsync(this, 1500);
            }
            return;
        }

        if (WearProtocol.PATH_CREATE_NAP.equals(path)) {
            WearProtocol.CreateNapRequest req = GSON.fromJson(json, WearProtocol.CreateNapRequest.class);
            if (req != null) {
                SleepCommandController.CommandResult res =
                        SleepCommandController.createNapAlarm(this, req.minutes, req.label);
                WearDataLayerManager.sendActionResultAsync(this, res.ok, path, res.detail);
                WearDataLayerManager.syncStateDelayedAsync(this, 600);
                WearDataLayerManager.syncStateDelayedAsync(this, 1500);
            }
            return;
        }

        if (WearProtocol.PATH_SLEEP_ACTION.equals(path)) {
            WearProtocol.SleepActionRequest req = GSON.fromJson(json, WearProtocol.SleepActionRequest.class);
            if (req != null) {
                SleepCommandController.CommandResult res =
                        SleepCommandController.executeSleepAction(this, req.action, req.snoozeMinutes);
                WearDataLayerManager.sendActionResultAsync(this, res.ok, req.action, res.detail);
                WearDataLayerManager.syncStateDelayedAsync(this, 500);
                WearDataLayerManager.syncStateDelayedAsync(this, 1400);
            }
        }
    }
}
