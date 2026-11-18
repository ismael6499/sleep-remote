package com.nitsutech.sleepremote.tile;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.wear.protolayout.ActionBuilders;
import androidx.wear.protolayout.ColorBuilders;
import androidx.wear.protolayout.DimensionBuilders;
import androidx.wear.protolayout.LayoutElementBuilders;
import androidx.wear.protolayout.ModifiersBuilders;
import androidx.wear.protolayout.ResourceBuilders;
import androidx.wear.protolayout.TimelineBuilders;
import androidx.wear.tiles.RequestBuilders;
import androidx.wear.tiles.TileBuilders;
import androidx.wear.tiles.TileService;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.nitsutech.sleepremote.R;
import com.nitsutech.sleepremote.ui.WearMainActivity;
import com.nitsutech.sleepremote.wear.WatchDataClient;
import com.nitsutech.sleepremote.wear.WatchHapticHelper;
import com.nitsutech.sleepremote.wear.WatchPrefsManager;
import com.nitsutech.sleepremote.wear.WearProtocol;

import java.util.Locale;

/**
 * Native Wear OS Quick Tile on Galaxy Watch 8 Classic for 1-tap nap alarms,
 * sleep tracking start/stop, skip next alarm, and launching SleepRemote.
 */
public class SleepRemoteTileService extends TileService {

    private static final String RESOURCES_VERSION = "1";
    private static final String ID_NAP_1 = "id_nap_1";
    private static final String ID_NAP_2 = "id_nap_2";
    private static final String ID_TRACK_TOGGLE = "id_track_toggle";
    private static final String ID_SKIP_NEXT = "id_skip_next";

    @NonNull
    @Override
    protected ListenableFuture<TileBuilders.Tile> onTileRequest(@NonNull RequestBuilders.TileRequest requestParams) {
        WatchPrefsManager prefs = new WatchPrefsManager(this);
        WearProtocol.StatePayload state = prefs.getState();

        int nap1 = state.napPresets.size() > 0 ? state.napPresets.get(0) : 20;
        int nap2 = state.napPresets.size() > 1 ? state.napPresets.get(1) : 30;

        String clicked = requestParams.getCurrentState() != null
                ? requestParams.getCurrentState().getLastClickableId()
                : "";

        if (ID_NAP_1.equals(clicked)) {
            WatchDataClient.createNap(this, nap1, getString(R.string.nap_alarm_label_fmt, nap1));
            WatchHapticHelper.pulseConfirm(this);
        } else if (ID_NAP_2.equals(clicked)) {
            WatchDataClient.createNap(this, nap2, getString(R.string.nap_alarm_label_fmt, nap2));
            WatchHapticHelper.pulseConfirm(this);
        } else if (ID_TRACK_TOGGLE.equals(clicked)) {
            boolean nextTracking = !state.trackingActive;
            state.trackingActive = nextTracking;
            prefs.saveState(state);
            WatchDataClient.sendSleepAction(
                    this,
                    nextTracking ? WearProtocol.ACTION_START_TRACK : WearProtocol.ACTION_STOP_TRACK,
                    state.defaultSnoozeMinutes
            );
            WatchHapticHelper.pulseConfirm(this);
        } else if (ID_SKIP_NEXT.equals(clicked)) {
            WatchDataClient.sendSleepAction(this, WearProtocol.ACTION_SKIP_NEXT, state.defaultSnoozeMinutes);
            WatchHapticHelper.pulseConfirm(this);
        }

        String headerText = buildHeaderSummary(state);

        LayoutElementBuilders.Column root = new LayoutElementBuilders.Column.Builder()
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .addContent(buildHeaderText(headerText, state.trackingActive))
                .addContent(spacer(6f))
                .addContent(new LayoutElementBuilders.Row.Builder()
                        .addContent(buildTileButton(
                                ID_NAP_1,
                                getString(R.string.tile_nap_fmt, nap1),
                                0xFF3949AB
                        ))
                        .addContent(hSpacer(6f))
                        .addContent(buildTileButton(
                                ID_NAP_2,
                                getString(R.string.tile_nap_fmt, nap2),
                                0xFF00838F
                        ))
                        .build())
                .addContent(spacer(6f))
                .addContent(new LayoutElementBuilders.Row.Builder()
                        .addContent(buildTileButton(
                                ID_TRACK_TOGGLE,
                                state.trackingActive
                                        ? getString(R.string.tile_stop_sleep)
                                        : getString(R.string.tile_start_sleep),
                                state.trackingActive ? 0xFFC62828 : 0xFF2E7D32
                        ))
                        .addContent(hSpacer(6f))
                        .addContent(buildTileButton(
                                ID_SKIP_NEXT,
                                getString(R.string.tile_skip_next),
                                0xFF37474F
                        ))
                        .build())
                .addContent(spacer(6f))
                .addContent(buildOpenAppChip())
                .build();

        TileBuilders.Tile tile = new TileBuilders.Tile.Builder()
                .setResourcesVersion(RESOURCES_VERSION)
                .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(root))
                .build();

        return Futures.immediateFuture(tile);
    }

    @NonNull
    @Override
    protected ListenableFuture<ResourceBuilders.Resources> onTileResourcesRequest(
            @NonNull RequestBuilders.ResourcesRequest requestParams) {
        return Futures.immediateFuture(
                new ResourceBuilders.Resources.Builder()
                        .setVersion(RESOURCES_VERSION)
                        .build()
        );
    }

    private String buildHeaderSummary(WearProtocol.StatePayload state) {
        if (state.trackingActive) {
            return getString(R.string.tile_tracking_active);
        }
        if (state.alarms != null) {
            for (WearProtocol.AlarmDto alarm : state.alarms) {
                if (alarm.enabled) {
                    String time = String.format(Locale.US, "%02d:%02d", alarm.hour, alarm.minutes);
                    String label = alarm.label;
                    if (TextUtils.isEmpty(label) && state.alarmAliases != null) {
                        label = state.alarmAliases.get(alarm.id);
                    }
                    if (TextUtils.isEmpty(label)) {
                        label = getString(R.string.alarm_default_id_fmt, alarm.id);
                    }
                    return time + " · " + label;
                }
            }
        }
        return getString(R.string.app_name);
    }

    private LayoutElementBuilders.LayoutElement buildHeaderText(String text, boolean tracking) {
        return new LayoutElementBuilders.Text.Builder()
                .setText(text)
                .setMaxLines(1)
                .setFontStyle(new LayoutElementBuilders.FontStyle.Builder()
                        .setSize(DimensionBuilders.sp(13f))
                        .setColor(ColorBuilders.argb(tracking ? 0xFFFFCC80 : 0xFF80DEEA))
                        .build())
                .build();
    }

    private LayoutElementBuilders.LayoutElement buildTileButton(String id, String label, int bgColorArgb) {
        ModifiersBuilders.Clickable clickable = new ModifiersBuilders.Clickable.Builder()
                .setId(id)
                .setOnClick(new ActionBuilders.LoadAction.Builder().build())
                .build();

        ModifiersBuilders.Modifiers mods = new ModifiersBuilders.Modifiers.Builder()
                .setClickable(clickable)
                .setBackground(new ModifiersBuilders.Background.Builder()
                        .setColor(ColorBuilders.argb(bgColorArgb))
                        .setCorner(new ModifiersBuilders.Corner.Builder()
                                .setRadius(DimensionBuilders.dp(18f))
                                .build())
                        .build())
                .setPadding(new ModifiersBuilders.Padding.Builder()
                        .setStart(DimensionBuilders.dp(10f))
                        .setEnd(DimensionBuilders.dp(10f))
                        .setTop(DimensionBuilders.dp(8f))
                        .setBottom(DimensionBuilders.dp(8f))
                        .build())
                .build();

        return new LayoutElementBuilders.Box.Builder()
                .setModifiers(mods)
                .addContent(new LayoutElementBuilders.Text.Builder()
                        .setText(label)
                        .setFontStyle(new LayoutElementBuilders.FontStyle.Builder()
                                .setSize(DimensionBuilders.sp(12f))
                                .setColor(ColorBuilders.argb(0xFFFFFFFF))
                                .build())
                        .build())
                .build();
    }

    private LayoutElementBuilders.LayoutElement buildOpenAppChip() {
        ActionBuilders.LaunchAction launchAction = new ActionBuilders.LaunchAction.Builder()
                .setAndroidActivity(new ActionBuilders.AndroidActivity.Builder()
                        .setPackageName(getPackageName())
                        .setClassName(WearMainActivity.class.getName())
                        .build())
                .build();

        ModifiersBuilders.Modifiers mods = new ModifiersBuilders.Modifiers.Builder()
                .setClickable(new ModifiersBuilders.Clickable.Builder()
                        .setId("open_app")
                        .setOnClick(launchAction)
                        .build())
                .setBackground(new ModifiersBuilders.Background.Builder()
                        .setColor(ColorBuilders.argb(0xFF1E293B))
                        .setCorner(new ModifiersBuilders.Corner.Builder()
                                .setRadius(DimensionBuilders.dp(14f))
                                .build())
                        .build())
                .setPadding(new ModifiersBuilders.Padding.Builder()
                        .setStart(DimensionBuilders.dp(12f))
                        .setEnd(DimensionBuilders.dp(12f))
                        .setTop(DimensionBuilders.dp(6f))
                        .setBottom(DimensionBuilders.dp(6f))
                        .build())
                .build();

        return new LayoutElementBuilders.Box.Builder()
                .setModifiers(mods)
                .addContent(new LayoutElementBuilders.Text.Builder()
                        .setText(getString(R.string.tile_open_app))
                        .setFontStyle(new LayoutElementBuilders.FontStyle.Builder()
                                .setSize(DimensionBuilders.sp(11f))
                                .setColor(ColorBuilders.argb(0xFFB2EBF2))
                                .build())
                        .build())
                .build();
    }

    private LayoutElementBuilders.Spacer spacer(float dp) {
        return new LayoutElementBuilders.Spacer.Builder()
                .setHeight(DimensionBuilders.dp(dp))
                .build();
    }

    private LayoutElementBuilders.Spacer hSpacer(float dp) {
        return new LayoutElementBuilders.Spacer.Builder()
                .setWidth(DimensionBuilders.dp(dp))
                .build();
    }
}
