package com.nitsutech.sleepremote.wear;

import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.google.android.gms.tasks.Tasks;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;
import com.google.gson.Gson;
import com.nitsutech.sleepremote.data.PrefsManager;
import com.nitsutech.sleepremote.sleep.SleepAlarmRepository;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Bidirectional Wearable Data Layer coordinator on the phone (:app).
 */
public class WearDataLayerManager {

    public static final String ACTION_LOCAL_STATE_UPDATED = "com.nitsutech.sleepremote.LOCAL_STATE_UPDATED";

    private static final ScheduledExecutorService EXEC = Executors.newSingleThreadScheduledExecutor();
    private static final Gson GSON = new Gson();
    private static volatile String lastConnectedWatchName = "";

    public static String getLastConnectedWatchName() {
        return lastConnectedWatchName;
    }

    public static void syncStateAsync(Context context) {
        final Context appCtx = context.getApplicationContext();
        EXEC.execute(() -> syncStateBlocking(appCtx));
    }

    public static void syncStateDelayedAsync(Context context, long delayMs) {
        final Context appCtx = context.getApplicationContext();
        EXEC.schedule(() -> syncStateBlocking(appCtx), delayMs, TimeUnit.MILLISECONDS);
    }

    public static WearProtocol.StatePayload buildStatePayload(Context context) {
        PrefsManager prefs = new PrefsManager(context);
        WearProtocol.StatePayload state = new WearProtocol.StatePayload();
        state.alarms = SleepAlarmRepository.loadAlarms(context);
        state.napPresets = prefs.getNapPresets();
        state.alarmAliases = prefs.getAlarmAliases();
        state.defaultSnoozeMinutes = prefs.getDefaultSnoozeMinutes();
        state.sleepInstalled = SleepAlarmRepository.isSleepInstalled(context);
        state.sleepVersion = SleepAlarmRepository.getSleepVersionName(context);
        state.readPermissionGranted = SleepAlarmRepository.hasSleepReadPermission(context);
        state.overlayPermissionGranted = SleepAlarmRepository.hasOverlayPermission(context);
        state.trackingActive = prefs.isTrackingActive();
        state.syncedAtMs = System.currentTimeMillis();
        state.phoneName = Build.MODEL != null ? Build.MODEL : "Android";
        return state;
    }

    public static void syncStateBlocking(Context context) {
        try {
            WearProtocol.StatePayload state = buildStatePayload(context);
            PrefsManager prefs = new PrefsManager(context);
            prefs.setLastSyncMs(state.syncedAtMs);

            String json = GSON.toJson(state);
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);

            PutDataMapRequest dataReq = PutDataMapRequest.create(WearProtocol.PATH_STATE_DATA);
            dataReq.getDataMap().putString(WearProtocol.EXTRA_STATE_JSON, json);
            dataReq.getDataMap().putLong(WearProtocol.EXTRA_TS, state.syncedAtMs);
            PutDataRequest putReq = dataReq.asPutDataRequest().setUrgent();
            Tasks.await(Wearable.getDataClient(context).putDataItem(putReq));

            List<Node> nodes = Tasks.await(Wearable.getNodeClient(context).getConnectedNodes());
            if (nodes != null && !nodes.isEmpty()) {
                lastConnectedWatchName = nodes.get(0).getDisplayName();
                for (Node node : nodes) {
                    Tasks.await(Wearable.getMessageClient(context)
                            .sendMessage(node.getId(), WearProtocol.PATH_SYNC_MSG, bytes));
                }
            } else {
                lastConnectedWatchName = "";
            }

            notifyLocalUi(context);
        } catch (Exception ignored) {
        }
    }

    public static void sendActionResultAsync(Context context, boolean ok, String action, String detail) {
        final Context appCtx = context.getApplicationContext();
        EXEC.execute(() -> {
            try {
                WearProtocol.ActionResultPayload payload = new WearProtocol.ActionResultPayload();
                payload.ok = ok;
                payload.action = action != null ? action : "";
                payload.detail = detail != null ? detail : "";
                byte[] bytes = GSON.toJson(payload).getBytes(StandardCharsets.UTF_8);

                List<Node> nodes = Tasks.await(Wearable.getNodeClient(appCtx).getConnectedNodes());
                if (nodes != null && !nodes.isEmpty()) {
                    lastConnectedWatchName = nodes.get(0).getDisplayName();
                    for (Node node : nodes) {
                        Tasks.await(Wearable.getMessageClient(appCtx)
                                .sendMessage(node.getId(), WearProtocol.PATH_ACTION_RESULT, bytes));
                    }
                }
            } catch (Exception ignored) {
            }
        });
    }

    private static void notifyLocalUi(Context context) {
        Intent intent = new Intent(ACTION_LOCAL_STATE_UPDATED);
        intent.setPackage(context.getPackageName());
        context.sendBroadcast(intent);
    }
}
