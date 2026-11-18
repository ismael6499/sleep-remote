package com.nitsutech.sleepremote.service;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.wear.tiles.TileService;

import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;
import com.nitsutech.sleepremote.tile.SleepRemoteTileService;
import com.nitsutech.sleepremote.wear.WatchPrefsManager;
import com.nitsutech.sleepremote.wear.WearProtocol;

import java.nio.charset.StandardCharsets;

/**
 * Receives synced Sleep as Android state and command acknowledgments from the phone.
 */
public class WatchWearListenerService extends WearableListenerService {

    public static final String ACTION_STATE_SYNCED = "com.nitsutech.sleepremote.WEAR_STATE_SYNCED";
    public static final String ACTION_RESULT_RECEIVED = "com.nitsutech.sleepremote.WEAR_ACTION_RESULT";
    public static final String EXTRA_RESULT_JSON = "result_json";

    @Override
    public void onDataChanged(@NonNull DataEventBuffer dataEvents) {
        for (DataEvent event : dataEvents) {
            if (event.getType() == DataEvent.TYPE_CHANGED
                    && WearProtocol.PATH_STATE_DATA.equals(event.getDataItem().getUri().getPath())) {
                String json = DataMapItem.fromDataItem(event.getDataItem())
                        .getDataMap()
                        .getString(WearProtocol.EXTRA_STATE_JSON);
                if (json != null && !json.isEmpty()) {
                    new WatchPrefsManager(this).saveStateJson(json);
                    notifyStateRefreshed(this);
                }
            }
        }
    }

    @Override
    public void onMessageReceived(@NonNull MessageEvent messageEvent) {
        String path = messageEvent.getPath();
        String json = new String(messageEvent.getData(), StandardCharsets.UTF_8);

        if (WearProtocol.PATH_SYNC_MSG.equals(path)) {
            new WatchPrefsManager(this).saveStateJson(json);
            notifyStateRefreshed(this);
        } else if (WearProtocol.PATH_ACTION_RESULT.equals(path)) {
            Intent intent = new Intent(ACTION_RESULT_RECEIVED);
            intent.setPackage(getPackageName());
            intent.putExtra(EXTRA_RESULT_JSON, json);
            sendBroadcast(intent);
        }
    }

    public static void notifyStateRefreshed(Context context) {
        Intent intent = new Intent(ACTION_STATE_SYNCED);
        intent.setPackage(context.getPackageName());
        context.sendBroadcast(intent);
        try {
            TileService.getUpdater(context).requestUpdate(SleepRemoteTileService.class);
        } catch (Exception ignored) {
        }
    }
}
