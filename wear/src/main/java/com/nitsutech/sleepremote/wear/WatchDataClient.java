package com.nitsutech.sleepremote.wear;

import android.content.Context;

import com.google.android.gms.tasks.Tasks;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataItemBuffer;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.Wearable;
import com.google.gson.Gson;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Sends SleepRemote commands from the Galaxy Watch 8 Classic to the paired phone.
 */
public class WatchDataClient {

    private static final ExecutorService IO_EXEC = Executors.newSingleThreadExecutor();
    private static final Gson GSON = new Gson();

    public static void requestSync(Context context) {
        sendToPhoneAsync(context, WearProtocol.PATH_REQUEST_SYNC, new byte[0]);
        loadCachedDataItemAsync(context);
    }

    public static void toggleAlarm(Context context, long alarmId, String label, boolean enabled) {
        WearProtocol.ToggleAlarmRequest req = new WearProtocol.ToggleAlarmRequest();
        req.id = alarmId;
        req.label = label != null ? label : "";
        req.enabled = enabled;
        sendToPhoneAsync(context, WearProtocol.PATH_TOGGLE_ALARM, GSON.toJson(req).getBytes(StandardCharsets.UTF_8));
    }

    public static void createAlarm(
            Context context, int hour, int minutes, String label, int daysMask, boolean vibrate) {
        WearProtocol.CreateAlarmRequest req = new WearProtocol.CreateAlarmRequest();
        req.hour = hour;
        req.minutes = minutes;
        req.label = label != null ? label : "";
        req.daysOfWeekMask = daysMask;
        req.vibrate = vibrate;
        sendToPhoneAsync(context, WearProtocol.PATH_CREATE_ALARM, GSON.toJson(req).getBytes(StandardCharsets.UTF_8));
    }

    public static void createNap(Context context, int minutes, String label) {
        WearProtocol.CreateNapRequest req = new WearProtocol.CreateNapRequest();
        req.minutes = minutes;
        req.label = label != null ? label : "";
        sendToPhoneAsync(context, WearProtocol.PATH_CREATE_NAP, GSON.toJson(req).getBytes(StandardCharsets.UTF_8));
    }

    public static void sendSleepAction(Context context, String action, int snoozeMinutes) {
        WearProtocol.SleepActionRequest req = new WearProtocol.SleepActionRequest();
        req.action = action;
        req.snoozeMinutes = snoozeMinutes;
        sendToPhoneAsync(context, WearProtocol.PATH_SLEEP_ACTION, GSON.toJson(req).getBytes(StandardCharsets.UTF_8));
    }

    private static void sendToPhoneAsync(Context context, String path, byte[] data) {
        final Context appCtx = context.getApplicationContext();
        IO_EXEC.execute(() -> {
            try {
                List<Node> nodes = Tasks.await(Wearable.getNodeClient(appCtx).getConnectedNodes());
                if (nodes != null) {
                    for (Node node : nodes) {
                        Tasks.await(Wearable.getMessageClient(appCtx).sendMessage(node.getId(), path, data));
                    }
                }
            } catch (Exception ignored) {
            }
        });
    }

    private static void loadCachedDataItemAsync(Context context) {
        final Context appCtx = context.getApplicationContext();
        IO_EXEC.execute(() -> {
            try {
                DataItemBuffer buffer = Tasks.await(Wearable.getDataClient(appCtx).getDataItems());
                if (buffer != null) {
                    try {
                        for (DataItem item : buffer) {
                            if (WearProtocol.PATH_STATE_DATA.equals(item.getUri().getPath())) {
                                String json = DataMapItem.fromDataItem(item)
                                        .getDataMap()
                                        .getString(WearProtocol.EXTRA_STATE_JSON);
                                if (json != null && !json.isEmpty()) {
                                    new WatchPrefsManager(appCtx).saveStateJson(json);
                                    com.nitsutech.sleepremote.service.WatchWearListenerService
                                            .notifyStateRefreshed(appCtx);
                                }
                            }
                        }
                    } finally {
                        buffer.release();
                    }
                }
            } catch (Exception ignored) {
            }
        });
    }
}
