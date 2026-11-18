package com.nitsutech.sleepremote.ui;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.nitsutech.sleepremote.R;
import com.nitsutech.sleepremote.SleepRemoteApp;
import com.nitsutech.sleepremote.data.PrefsManager;
import com.nitsutech.sleepremote.sleep.SleepAlarmRepository;
import com.nitsutech.sleepremote.sleep.SleepCommandController;
import com.nitsutech.sleepremote.sleep.SleepConstants;
import com.nitsutech.sleepremote.wear.WearDataLayerManager;
import com.nitsutech.sleepremote.wear.WearProtocol;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;

/**
 * Phone companion screen for SleepRemote: permission checks, Intent API guide,
 * Watch Data Layer sync, configurable nap presets, and live Sleep alarm overview.
 */
public class MainActivity extends AppCompatActivity {

    private PrefsManager prefs;
    private AlarmsAdapter alarmsAdapter;

    private TextView tvSleepStatus;
    private TextView tvReadPermStatus;
    private TextView tvOverlayPermStatus;
    private TextView tvWatchStatus;
    private TextView tvAlarmsEmpty;
    private Button btnGrantRead;
    private Button btnGrantOverlay;
    private Button btnSnooze5;
    private Button btnSnooze10;
    private Button btnSnooze15;
    private EditText etNewPreset;
    private ChipGroup cgNapPresets;
    private RecyclerView rvAlarms;

    private final ActivityResultLauncher<String> readPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted && getApplication() instanceof SleepRemoteApp app) {
                    app.tryRegisterAlarmObserver();
                }
                refreshAndSync();
            });

    private final ActivityResultLauncher<Intent> overlaySettingsLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> refreshAndSync());

    private final BroadcastReceiver stateUpdatedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            renderUi();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = new PrefsManager(this);
        bindViews();
        setupListeners();
        renderUi();
        WearDataLayerManager.syncStateAsync(this);
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter(WearDataLayerManager.ACTION_LOCAL_STATE_UPDATED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(stateUpdatedReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(stateUpdatedReceiver, filter);
        }
        refreshAndSync();
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            unregisterReceiver(stateUpdatedReceiver);
        } catch (IllegalArgumentException ignored) {
        }
    }

    private void bindViews() {
        tvSleepStatus = findViewById(R.id.tv_sleep_status);
        tvReadPermStatus = findViewById(R.id.tv_read_perm_status);
        tvOverlayPermStatus = findViewById(R.id.tv_overlay_perm_status);
        tvWatchStatus = findViewById(R.id.tv_watch_status);
        tvAlarmsEmpty = findViewById(R.id.tv_alarms_empty);
        btnGrantRead = findViewById(R.id.btn_grant_read);
        btnGrantOverlay = findViewById(R.id.btn_grant_overlay);
        btnSnooze5 = findViewById(R.id.btn_snooze_5);
        btnSnooze10 = findViewById(R.id.btn_snooze_10);
        btnSnooze15 = findViewById(R.id.btn_snooze_15);
        etNewPreset = findViewById(R.id.et_new_nap_preset);
        cgNapPresets = findViewById(R.id.cg_nap_presets);
        rvAlarms = findViewById(R.id.rv_alarms);

        alarmsAdapter = new AlarmsAdapter(new AlarmsAdapter.OnAlarmActionListener() {
            @Override
            public void onToggle(WearProtocol.AlarmDto alarm, boolean targetEnabled) {
                SleepCommandController.CommandResult res =
                        SleepCommandController.toggleAlarm(MainActivity.this, alarm.id, alarm.label, targetEnabled);
                Toast.makeText(MainActivity.this, res.detail, Toast.LENGTH_SHORT).show();
                WearDataLayerManager.syncStateDelayedAsync(MainActivity.this, 450);
            }

            @Override
            public void onEditAlias(WearProtocol.AlarmDto alarm) {
                showAlarmAliasDialog(alarm);
            }
        });
        rvAlarms.setLayoutManager(new LinearLayoutManager(this));
        rvAlarms.setNestedScrollingEnabled(false);
        rvAlarms.setAdapter(alarmsAdapter);
    }

    private void setupListeners() {
        btnGrantRead.setOnClickListener(v ->
                readPermissionLauncher.launch(SleepConstants.PERMISSION_SLEEP_READ));

        btnGrantOverlay.setOnClickListener(v -> {
            Intent intent = new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())
            );
            overlaySettingsLauncher.launch(intent);
        });

        findViewById(R.id.btn_open_sleep_app).setOnClickListener(v -> {
            Intent launch = getPackageManager().getLaunchIntentForPackage(SleepConstants.SLEEP_PACKAGE);
            if (launch != null) {
                startActivity(launch);
            } else {
                Toast.makeText(this, R.string.err_sleep_not_installed, Toast.LENGTH_SHORT).show();
            }
        });

        findViewById(R.id.btn_sync_now).setOnClickListener(v -> {
            WearDataLayerManager.syncStateAsync(this);
            Toast.makeText(this, R.string.toast_sync_triggered, Toast.LENGTH_SHORT).show();
        });

        findViewById(R.id.btn_add_nap_preset).setOnClickListener(v -> {
            String raw = etNewPreset.getText() != null ? etNewPreset.getText().toString().trim() : "";
            if (TextUtils.isEmpty(raw)) return;
            try {
                int mins = Integer.parseInt(raw);
                if (mins < 1 || mins > 720) {
                    Toast.makeText(this, R.string.toast_invalid_preset, Toast.LENGTH_SHORT).show();
                    return;
                }
                prefs.addNapPreset(mins);
                etNewPreset.setText("");
                refreshAndSync();
            } catch (NumberFormatException e) {
                Toast.makeText(this, R.string.toast_invalid_preset, Toast.LENGTH_SHORT).show();
            }
        });

        findViewById(R.id.btn_reset_nap_presets).setOnClickListener(v -> {
            prefs.resetDefaultNapPresets();
            refreshAndSync();
        });

        btnSnooze5.setOnClickListener(v -> updateDefaultSnooze(5));
        btnSnooze10.setOnClickListener(v -> updateDefaultSnooze(10));
        btnSnooze15.setOnClickListener(v -> updateDefaultSnooze(15));

        findViewById(R.id.btn_phone_start_track).setOnClickListener(v ->
                runSleepAction(WearProtocol.ACTION_START_TRACK));
        findViewById(R.id.btn_phone_start_ideal).setOnClickListener(v ->
                runSleepAction(WearProtocol.ACTION_START_IDEAL));
        findViewById(R.id.btn_phone_stop_track).setOnClickListener(v ->
                runSleepAction(WearProtocol.ACTION_STOP_TRACK));
        findViewById(R.id.btn_phone_skip_next).setOnClickListener(v ->
                runSleepAction(WearProtocol.ACTION_SKIP_NEXT));
    }

    private void updateDefaultSnooze(int mins) {
        prefs.setDefaultSnoozeMinutes(mins);
        refreshAndSync();
    }

    private void runSleepAction(String action) {
        SleepCommandController.CommandResult res =
                SleepCommandController.executeSleepAction(this, action, prefs.getDefaultSnoozeMinutes());
        Toast.makeText(this, res.detail, Toast.LENGTH_SHORT).show();
        WearDataLayerManager.syncStateDelayedAsync(this, 450);
    }

    private void refreshAndSync() {
        renderUi();
        WearDataLayerManager.syncStateAsync(this);
    }

    private void renderUi() {
        String version = SleepAlarmRepository.getSleepVersionName(this);
        boolean installed = !TextUtils.isEmpty(version);
        tvSleepStatus.setText(installed
                ? getString(R.string.status_sleep_installed, version)
                : getString(R.string.status_sleep_missing));
        tvSleepStatus.setTextColor(ContextCompat.getColor(this,
                installed ? R.color.sr_success : R.color.sr_danger));

        boolean hasRead = SleepAlarmRepository.hasSleepReadPermission(this);
        tvReadPermStatus.setText(hasRead
                ? getString(R.string.status_perm_granted)
                : getString(R.string.status_perm_missing));
        tvReadPermStatus.setTextColor(ContextCompat.getColor(this,
                hasRead ? R.color.sr_success : R.color.sr_warn));
        btnGrantRead.setVisibility(hasRead ? View.GONE : View.VISIBLE);

        boolean hasOverlay = SleepAlarmRepository.hasOverlayPermission(this);
        tvOverlayPermStatus.setText(hasOverlay
                ? getString(R.string.status_perm_granted)
                : getString(R.string.status_perm_overlay_missing));
        tvOverlayPermStatus.setTextColor(ContextCompat.getColor(this,
                hasOverlay ? R.color.sr_success : R.color.sr_warn));
        btnGrantOverlay.setVisibility(hasOverlay ? View.GONE : View.VISIBLE);

        String watchName = WearDataLayerManager.getLastConnectedWatchName();
        long syncMs = prefs.getLastSyncMs();
        String syncTime = syncMs > 0
                ? DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(syncMs))
                : getString(R.string.sync_never);
        if (!TextUtils.isEmpty(watchName)) {
            tvWatchStatus.setText(getString(R.string.status_watch_connected, watchName, syncTime));
            tvWatchStatus.setTextColor(ContextCompat.getColor(this, R.color.sr_teal));
        } else {
            tvWatchStatus.setText(getString(R.string.status_watch_waiting, syncTime));
            tvWatchStatus.setTextColor(ContextCompat.getColor(this, R.color.sr_text_muted));
        }

        renderNapPresets();
        renderSnoozeButtons();
        renderAlarms(hasRead);
    }

    private void renderNapPresets() {
        cgNapPresets.removeAllViews();
        List<Integer> presets = prefs.getNapPresets();
        for (Integer mins : presets) {
            Chip chip = new Chip(this);
            chip.setText(getString(R.string.nap_preset_chip_fmt, mins));
            chip.setCloseIconVisible(presets.size() > 1);
            chip.setChipBackgroundColorResource(R.color.sr_surface_alt);
            chip.setTextColor(ContextCompat.getColor(this, R.color.sr_text));
            chip.setCloseIconTintResource(R.color.sr_text_muted);
            chip.setOnCloseIconClickListener(v -> {
                prefs.removeNapPreset(mins);
                refreshAndSync();
            });
            chip.setOnClickListener(v -> {
                SleepCommandController.CommandResult res =
                        SleepCommandController.createNapAlarm(this, mins, null);
                Toast.makeText(this, res.detail, Toast.LENGTH_SHORT).show();
                WearDataLayerManager.syncStateDelayedAsync(this, 600);
            });
            cgNapPresets.addView(chip);
        }
    }

    private void renderSnoozeButtons() {
        int active = prefs.getDefaultSnoozeMinutes();
        styleSnoozeBtn(btnSnooze5, active == 5);
        styleSnoozeBtn(btnSnooze10, active == 10);
        styleSnoozeBtn(btnSnooze15, active == 15);
    }

    private void styleSnoozeBtn(Button btn, boolean selected) {
        btn.setBackgroundTintList(ContextCompat.getColorStateList(this,
                selected ? R.color.sr_primary : R.color.sr_surface_alt));
    }

    private void renderAlarms(boolean hasRead) {
        if (!hasRead) {
            alarmsAdapter.submitList(null, null);
            tvAlarmsEmpty.setText(R.string.alarms_need_permission);
            tvAlarmsEmpty.setVisibility(View.VISIBLE);
            return;
        }
        List<WearProtocol.AlarmDto> alarms = SleepAlarmRepository.loadAlarms(this);
        alarmsAdapter.submitList(alarms, prefs.getAlarmAliases());
        if (alarms.isEmpty()) {
            tvAlarmsEmpty.setText(R.string.alarms_empty);
            tvAlarmsEmpty.setVisibility(View.VISIBLE);
        } else {
            tvAlarmsEmpty.setVisibility(View.GONE);
        }
    }

    private void showAlarmAliasDialog(WearProtocol.AlarmDto alarm) {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        String current = prefs.getAlarmAliases().get(alarm.id);
        input.setText(current != null ? current : alarm.label);
        input.setHint(R.string.hint_alarm_alias);

        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_alarm_alias_title)
                .setMessage(R.string.dialog_alarm_alias_note)
                .setView(input)
                .setPositiveButton(R.string.btn_save, (d, w) -> {
                    String val = input.getText() != null ? input.getText().toString().trim() : "";
                    prefs.setAlarmAlias(alarm.id, val);
                    refreshAndSync();
                })
                .setNeutralButton(R.string.btn_open_sleep_app, (d, w) -> {
                    Intent launch = getPackageManager().getLaunchIntentForPackage(SleepConstants.SLEEP_PACKAGE);
                    if (launch != null) {
                        startActivity(launch);
                    }
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }
}
