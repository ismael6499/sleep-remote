package com.nitsutech.sleepremote.ui;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.InputDeviceCompat;
import androidx.wear.widget.SwipeDismissFrameLayout;

import com.google.gson.Gson;
import com.nitsutech.sleepremote.R;
import com.nitsutech.sleepremote.service.WatchWearListenerService;
import com.nitsutech.sleepremote.wear.WatchDataClient;
import com.nitsutech.sleepremote.wear.WatchHapticHelper;
import com.nitsutech.sleepremote.wear.WatchPrefsManager;
import com.nitsutech.sleepremote.wear.WearProtocol;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

/**
 * Galaxy Watch 8 Classic UI for controlling Sleep as Android remotely:
 * rotating bezel scroll, full back-stack navigation, real alarm toggles,
 * alarm creator, 1-tap nap presets, and sleep tracking controls.
 */
public class WearMainActivity extends AppCompatActivity {

    private static final int SCREEN_HOME = 0;
    private static final int SCREEN_ALARMS = 1;
    private static final int SCREEN_NAPS = 2;
    private static final int SCREEN_CREATE = 3;
    private static final int SCREEN_CONTROLS = 4;

    private static final Gson GSON = new Gson();

    private WatchPrefsManager prefs;
    private WearProtocol.StatePayload state;

    private SwipeDismissFrameLayout swipeRoot;
    private ScrollView mainScroll;
    private LinearLayout screenHome;
    private LinearLayout screenAlarms;
    private LinearLayout screenNaps;
    private LinearLayout screenCreate;
    private LinearLayout screenControls;

    private TextView tvNextAlarm;
    private TextView tvPhoneStatus;
    private Button btnOpenAlarms;
    private LinearLayout llAlarmsList;
    private LinearLayout llNapsList;

    private TextView tvCreateHour;
    private TextView tvCreateMin;
    private TextView tvCreateLabel;
    private Button btnDayMon;
    private Button btnDayTue;
    private Button btnDayWed;
    private Button btnDayThu;
    private Button btnDayFri;
    private Button btnDaySat;
    private Button btnDaySun;
    private Button btnCreateVibrate;
    private Button btnCtrlSnooze;

    private GestureDetector backSwipeDetector;
    private final Deque<Integer> backStack = new ArrayDeque<>();
    private int currentScreen = SCREEN_HOME;

    private int createHour = 8;
    private int createMinute = 0;
    private int createDaysMask = 0;
    private boolean createVibrate = true;
    private String createLabel = "";

    private final BroadcastReceiver syncReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            state = prefs.getState();
            renderCurrentScreen();
        }
    };

    private final BroadcastReceiver actionResultReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String raw = intent.getStringExtra(WatchWearListenerService.EXTRA_RESULT_JSON);
            if (TextUtils.isEmpty(raw)) return;
            try {
                WearProtocol.ActionResultPayload res =
                        GSON.fromJson(raw, WearProtocol.ActionResultPayload.class);
                if (res != null && !res.ok && !TextUtils.isEmpty(res.detail)) {
                    Toast.makeText(WearMainActivity.this, res.detail, Toast.LENGTH_SHORT).show();
                }
            } catch (Exception ignored) {
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wear_main);

        prefs = new WatchPrefsManager(this);
        state = prefs.getState();
        initDefaultCreateTime();

        bindViews();
        setupNavigationBackStack();
        setupListeners();
        renderCurrentScreen();
        WatchDataClient.requestSync(this);
    }

    private void initDefaultCreateTime() {
        Calendar now = Calendar.getInstance();
        now.add(Calendar.HOUR_OF_DAY, 1);
        createHour = now.get(Calendar.HOUR_OF_DAY);
        createMinute = (now.get(Calendar.MINUTE) / 5) * 5;
        createLabel = getString(R.string.preset_label_alarm);
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter syncFilter = new IntentFilter(WatchWearListenerService.ACTION_STATE_SYNCED);
        IntentFilter resFilter = new IntentFilter(WatchWearListenerService.ACTION_RESULT_RECEIVED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(syncReceiver, syncFilter, Context.RECEIVER_NOT_EXPORTED);
            registerReceiver(actionResultReceiver, resFilter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(syncReceiver, syncFilter);
            registerReceiver(actionResultReceiver, resFilter);
        }
        state = prefs.getState();
        renderCurrentScreen();
        WatchDataClient.requestSync(this);
        if (mainScroll != null) {
            mainScroll.requestFocus();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            unregisterReceiver(syncReceiver);
            unregisterReceiver(actionResultReceiver);
        } catch (IllegalArgumentException ignored) {
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (backSwipeDetector != null) {
            backSwipeDetector.onTouchEvent(ev);
        }
        return super.dispatchTouchEvent(ev);
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_SCROLL
                && event.isFromSource(InputDeviceCompat.SOURCE_ROTARY_ENCODER)) {
            float delta = -event.getAxisValue(MotionEvent.AXIS_SCROLL) * 140f;
            if (mainScroll != null) {
                mainScroll.smoothScrollBy(0, Math.round(delta));
                return true;
            }
        }
        return super.onGenericMotionEvent(event);
    }

    private void bindViews() {
        swipeRoot = findViewById(R.id.wear_swipe_root);
        mainScroll = findViewById(R.id.wear_main_scroll);
        screenHome = findViewById(R.id.screen_home);
        screenAlarms = findViewById(R.id.screen_alarms);
        screenNaps = findViewById(R.id.screen_naps);
        screenCreate = findViewById(R.id.screen_create);
        screenControls = findViewById(R.id.screen_controls);

        tvNextAlarm = findViewById(R.id.tv_wear_next_alarm);
        tvPhoneStatus = findViewById(R.id.tv_wear_phone_status);
        btnOpenAlarms = findViewById(R.id.btn_wear_open_alarms);
        llAlarmsList = findViewById(R.id.ll_wear_alarms_list);
        llNapsList = findViewById(R.id.ll_wear_naps_list);

        tvCreateHour = findViewById(R.id.tv_create_hour);
        tvCreateMin = findViewById(R.id.tv_create_min);
        tvCreateLabel = findViewById(R.id.tv_create_label);
        btnDayMon = findViewById(R.id.btn_day_mon);
        btnDayTue = findViewById(R.id.btn_day_tue);
        btnDayWed = findViewById(R.id.btn_day_wed);
        btnDayThu = findViewById(R.id.btn_day_thu);
        btnDayFri = findViewById(R.id.btn_day_fri);
        btnDaySat = findViewById(R.id.btn_day_sat);
        btnDaySun = findViewById(R.id.btn_day_sun);
        btnCreateVibrate = findViewById(R.id.btn_create_vibrate);
        btnCtrlSnooze = findViewById(R.id.btn_ctrl_snooze);
    }

    private void setupNavigationBackStack() {
        swipeRoot.addCallback(new SwipeDismissFrameLayout.Callback() {
            @Override
            public void onDismissed(SwipeDismissFrameLayout layout) {
                finish();
            }
        });

        backSwipeDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (e1 == null || e2 == null) return false;
                float dx = e2.getX() - e1.getX();
                float dy = Math.abs(e2.getY() - e1.getY());
                if (currentScreen != SCREEN_HOME && dx > 75f && dx > dy * 1.4f) {
                    navigateBack();
                    return true;
                }
                return false;
            }
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (navigateBack()) {
                    return;
                }
                setEnabled(false);
                getOnBackPressedDispatcher().onBackPressed();
            }
        });
    }

    private boolean navigateBack() {
        if (!backStack.isEmpty()) {
            currentScreen = backStack.pop();
            renderCurrentScreen();
            mainScroll.post(() -> mainScroll.scrollTo(0, 0));
            return true;
        }
        if (currentScreen != SCREEN_HOME) {
            currentScreen = SCREEN_HOME;
            renderCurrentScreen();
            mainScroll.post(() -> mainScroll.scrollTo(0, 0));
            return true;
        }
        return false;
    }

    private void navigateTo(int targetScreen) {
        if (currentScreen != targetScreen) {
            backStack.push(currentScreen);
        }
        currentScreen = targetScreen;
        renderCurrentScreen();
        mainScroll.post(() -> mainScroll.scrollTo(0, 0));
    }

    private void setupListeners() {
        btnOpenAlarms.setOnClickListener(v -> navigateTo(SCREEN_ALARMS));
        findViewById(R.id.btn_wear_open_naps).setOnClickListener(v -> navigateTo(SCREEN_NAPS));
        findViewById(R.id.btn_wear_open_create).setOnClickListener(v -> navigateTo(SCREEN_CREATE));
        findViewById(R.id.btn_wear_open_controls).setOnClickListener(v -> navigateTo(SCREEN_CONTROLS));
        findViewById(R.id.btn_wear_sync_now).setOnClickListener(v -> {
            WatchHapticHelper.pulseTap(this);
            WatchDataClient.requestSync(this);
            Toast.makeText(this, R.string.wear_toast_syncing, Toast.LENGTH_SHORT).show();
        });

        findViewById(R.id.btn_back_alarms).setOnClickListener(v -> navigateBack());
        findViewById(R.id.btn_back_naps).setOnClickListener(v -> navigateBack());
        findViewById(R.id.btn_back_create).setOnClickListener(v -> navigateBack());
        findViewById(R.id.btn_back_controls).setOnClickListener(v -> navigateBack());

        findViewById(R.id.btn_wear_refresh_alarms).setOnClickListener(v -> {
            WatchHapticHelper.pulseTap(this);
            WatchDataClient.requestSync(this);
            Toast.makeText(this, R.string.wear_toast_syncing, Toast.LENGTH_SHORT).show();
        });

        // Create Alarm Time Pickers
        findViewById(R.id.btn_hour_minus).setOnClickListener(v -> adjustHour(-1));
        findViewById(R.id.btn_hour_plus).setOnClickListener(v -> adjustHour(1));
        findViewById(R.id.btn_min_minus5).setOnClickListener(v -> adjustMinute(-5));
        findViewById(R.id.btn_min_minus1).setOnClickListener(v -> adjustMinute(-1));
        findViewById(R.id.btn_min_plus1).setOnClickListener(v -> adjustMinute(1));
        findViewById(R.id.btn_min_plus5).setOnClickListener(v -> adjustMinute(5));

        // Optional Day Toggles
        btnDayMon.setOnClickListener(v -> toggleDayBit(WearProtocol.BIT_MON));
        btnDayTue.setOnClickListener(v -> toggleDayBit(WearProtocol.BIT_TUE));
        btnDayWed.setOnClickListener(v -> toggleDayBit(WearProtocol.BIT_WED));
        btnDayThu.setOnClickListener(v -> toggleDayBit(WearProtocol.BIT_THU));
        btnDayFri.setOnClickListener(v -> toggleDayBit(WearProtocol.BIT_FRI));
        btnDaySat.setOnClickListener(v -> toggleDayBit(WearProtocol.BIT_SAT));
        btnDaySun.setOnClickListener(v -> toggleDayBit(WearProtocol.BIT_SUN));

        // Label Presets & Custom Input
        findViewById(R.id.btn_lbl_alarm).setOnClickListener(v -> setCreateLabel(getString(R.string.preset_label_alarm)));
        findViewById(R.id.btn_lbl_work).setOnClickListener(v -> setCreateLabel(getString(R.string.preset_label_work)));
        findViewById(R.id.btn_lbl_wake).setOnClickListener(v -> setCreateLabel(getString(R.string.preset_label_wake)));
        findViewById(R.id.btn_lbl_custom).setOnClickListener(v -> showLabelInputDialog());

        btnCreateVibrate.setOnClickListener(v -> {
            WatchHapticHelper.pulseTap(this);
            createVibrate = !createVibrate;
            renderCreateScreen();
        });

        findViewById(R.id.btn_create_save).setOnClickListener(v -> {
            WatchHapticHelper.pulseConfirm(this);
            WatchDataClient.createAlarm(
                    this, createHour, createMinute, createLabel, createDaysMask, createVibrate);
            String timeStr = String.format(Locale.US, "%02d:%02d", createHour, createMinute);
            Toast.makeText(this, getString(R.string.wear_toast_alarm_created, timeStr), Toast.LENGTH_SHORT).show();
            navigateBack();
        });

        // Quick Sleep Controls
        findViewById(R.id.btn_ctrl_start_track).setOnClickListener(v ->
                triggerSleepAction(WearProtocol.ACTION_START_TRACK, R.string.wear_toast_track_started, true));
        findViewById(R.id.btn_ctrl_start_ideal).setOnClickListener(v ->
                triggerSleepAction(WearProtocol.ACTION_START_IDEAL, R.string.wear_toast_track_ideal, true));
        findViewById(R.id.btn_ctrl_stop_track).setOnClickListener(v ->
                triggerSleepAction(WearProtocol.ACTION_STOP_TRACK, R.string.wear_toast_track_stopped, false));
        btnCtrlSnooze.setOnClickListener(v ->
                triggerSleepAction(WearProtocol.ACTION_SNOOZE, R.string.wear_toast_snoozed, null));
        findViewById(R.id.btn_ctrl_skip_next).setOnClickListener(v ->
                triggerSleepAction(WearProtocol.ACTION_SKIP_NEXT, R.string.wear_toast_skipped, null));
        findViewById(R.id.btn_ctrl_dismiss).setOnClickListener(v ->
                triggerSleepAction(WearProtocol.ACTION_DISMISS, R.string.wear_toast_dismissed, null));
    }

    private void triggerSleepAction(String action, int toastResId, Boolean trackingState) {
        WatchHapticHelper.pulseConfirm(this);
        if (trackingState != null) {
            state.trackingActive = trackingState;
            prefs.saveState(state);
        }
        WatchDataClient.sendSleepAction(this, action, state.defaultSnoozeMinutes);
        Toast.makeText(this, toastResId, Toast.LENGTH_SHORT).show();
        renderCurrentScreen();
    }

    private void adjustHour(int delta) {
        WatchHapticHelper.pulseTap(this);
        createHour = ((createHour + delta) % 24 + 24) % 24;
        renderCreateScreen();
    }

    private void adjustMinute(int delta) {
        WatchHapticHelper.pulseTap(this);
        createMinute = ((createMinute + delta) % 60 + 60) % 60;
        renderCreateScreen();
    }

    private void toggleDayBit(int bit) {
        WatchHapticHelper.pulseTap(this);
        createDaysMask ^= bit;
        renderCreateScreen();
    }

    private void setCreateLabel(String label) {
        WatchHapticHelper.pulseTap(this);
        createLabel = label;
        renderCreateScreen();
    }

    private void showLabelInputDialog() {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setText(createLabel);
        input.setHint(R.string.wear_hint_alarm_label);
        new AlertDialog.Builder(this)
                .setTitle(R.string.wear_dialog_label_title)
                .setView(input)
                .setPositiveButton(R.string.wear_ok, (d, w) -> {
                    String val = input.getText() != null ? input.getText().toString().trim() : "";
                    createLabel = TextUtils.isEmpty(val) ? getString(R.string.preset_label_alarm) : val;
                    renderCreateScreen();
                })
                .setNegativeButton(R.string.wear_cancel, null)
                .show();
    }

    private void renderCurrentScreen() {
        swipeRoot.setSwipeable(currentScreen == SCREEN_HOME);

        screenHome.setVisibility(currentScreen == SCREEN_HOME ? View.VISIBLE : View.GONE);
        screenAlarms.setVisibility(currentScreen == SCREEN_ALARMS ? View.VISIBLE : View.GONE);
        screenNaps.setVisibility(currentScreen == SCREEN_NAPS ? View.VISIBLE : View.GONE);
        screenCreate.setVisibility(currentScreen == SCREEN_CREATE ? View.VISIBLE : View.GONE);
        screenControls.setVisibility(currentScreen == SCREEN_CONTROLS ? View.VISIBLE : View.GONE);

        if (currentScreen == SCREEN_HOME) {
            renderHomeScreen();
        } else if (currentScreen == SCREEN_ALARMS) {
            renderAlarmsScreen();
        } else if (currentScreen == SCREEN_NAPS) {
            renderNapsScreen();
        } else if (currentScreen == SCREEN_CREATE) {
            renderCreateScreen();
        } else if (currentScreen == SCREEN_CONTROLS) {
            renderControlsScreen();
        }
    }

    private List<WearProtocol.AlarmDto> getSortedAlarms() {
        List<WearProtocol.AlarmDto> alarms = state.alarms != null
                ? new ArrayList<>(state.alarms)
                : new ArrayList<>();
        final long nowMs = System.currentTimeMillis();
        alarms.sort(Comparator
                .comparing((WearProtocol.AlarmDto a) -> !a.enabled)
                .thenComparingLong(a -> WearProtocol.computeNextAlarmMillis(
                        a.hour, a.minutes, a.daysOfWeek, nowMs))
                .thenComparingLong(a -> a.id));
        return alarms;
    }

    private String resolveDisplayLabel(WearProtocol.AlarmDto alarm) {
        if (!TextUtils.isEmpty(alarm.label)) {
            return alarm.label;
        }
        if (state.alarmAliases != null) {
            String alias = state.alarmAliases.get(alarm.id);
            if (!TextUtils.isEmpty(alias)) {
                return alias;
            }
        }
        return getString(R.string.alarm_default_id_fmt, alarm.id);
    }

    private void renderHomeScreen() {
        List<WearProtocol.AlarmDto> alarms = getSortedAlarms();
        int enabledCount = 0;
        WearProtocol.AlarmDto nextEnabled = null;
        for (WearProtocol.AlarmDto a : alarms) {
            if (a.enabled) {
                enabledCount++;
                if (nextEnabled == null) {
                    nextEnabled = a;
                }
            }
        }

        if (nextEnabled != null) {
            String time = String.format(Locale.US, "%02d:%02d", nextEnabled.hour, nextEnabled.minutes);
            tvNextAlarm.setText(getString(R.string.wear_next_alarm_fmt, time, resolveDisplayLabel(nextEnabled)));
        } else {
            tvNextAlarm.setText(R.string.wear_no_active_alarms);
        }

        if (!state.sleepInstalled && state.syncedAtMs > 0) {
            tvPhoneStatus.setText(R.string.wear_status_sleep_missing);
        } else if (state.syncedAtMs > 0 && !state.readPermissionGranted) {
            tvPhoneStatus.setText(R.string.wear_status_perm_needed);
        } else if (state.trackingActive) {
            tvPhoneStatus.setText(R.string.wear_status_tracking_on);
        } else if (state.syncedAtMs > 0) {
            tvPhoneStatus.setText(getString(R.string.wear_status_synced,
                    TextUtils.isEmpty(state.phoneName) ? "Phone" : state.phoneName));
        } else {
            tvPhoneStatus.setText(R.string.wear_status_waiting_sync);
        }

        btnOpenAlarms.setText(getString(R.string.wear_btn_alarms_count, enabledCount, alarms.size()));
    }

    private void renderAlarmsScreen() {
        llAlarmsList.removeAllViews();
        List<WearProtocol.AlarmDto> alarms = getSortedAlarms();

        if (alarms.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(!state.readPermissionGranted && state.syncedAtMs > 0
                    ? R.string.wear_alarms_need_perm
                    : R.string.wear_alarms_empty);
            empty.setTextColor(ContextCompat.getColor(this, R.color.wear_text_muted));
            empty.setGravity(Gravity.CENTER);
            empty.setTextSize(13f);
            empty.setPadding(8, 16, 8, 16);
            llAlarmsList.addView(empty);
            return;
        }

        int padH = dp(12);
        int padV = dp(10);
        for (WearProtocol.AlarmDto alarm : alarms) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setBackgroundResource(R.drawable.bg_wear_card);
            card.setPadding(padH, padV, padH, padV);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            lp.bottomMargin = dp(8);
            card.setLayoutParams(lp);

            LinearLayout left = new LinearLayout(this);
            left.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams leftLp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            );
            left.setLayoutParams(leftLp);

            TextView tvTime = new TextView(this);
            tvTime.setText(String.format(Locale.US, "%02d:%02d", alarm.hour, alarm.minutes));
            tvTime.setTextColor(ContextCompat.getColor(this,
                    alarm.enabled ? R.color.wear_text : R.color.wear_text_muted));
            tvTime.setTextSize(18f);
            tvTime.setTypeface(null, android.graphics.Typeface.BOLD);

            TextView tvLbl = new TextView(this);
            tvLbl.setText(resolveDisplayLabel(alarm));
            tvLbl.setTextColor(ContextCompat.getColor(this,
                    alarm.enabled ? R.color.wear_teal : R.color.wear_text_muted));
            tvLbl.setTextSize(12f);

            String daysMeta = formatDaysCompact(alarm.daysOfWeek);
            if (alarm.smartPeriodMinutes > 0) {
                daysMeta = daysMeta + " · " + getString(R.string.wear_smart_period_fmt, alarm.smartPeriodMinutes);
            }
            TextView tvDays = new TextView(this);
            tvDays.setText(daysMeta);
            tvDays.setTextColor(ContextCompat.getColor(this, R.color.wear_text_muted));
            tvDays.setTextSize(11f);

            left.addView(tvTime);
            left.addView(tvLbl);
            left.addView(tvDays);

            TextView tvBadge = new TextView(this);
            tvBadge.setText(alarm.enabled ? R.string.wear_state_on : R.string.wear_state_off);
            tvBadge.setTextColor(ContextCompat.getColor(this,
                    alarm.enabled ? R.color.wear_teal : R.color.wear_text_muted));
            tvBadge.setTextSize(12f);
            tvBadge.setTypeface(null, android.graphics.Typeface.BOLD);
            tvBadge.setPadding(dp(8), dp(4), dp(4), dp(4));

            card.addView(left);
            card.addView(tvBadge);

            card.setOnClickListener(v -> {
                WatchHapticHelper.pulseConfirm(this);
                boolean target = !alarm.enabled;
                alarm.enabled = target;
                prefs.saveState(state);
                WatchDataClient.toggleAlarm(this, alarm.id, alarm.label, target);
                renderAlarmsScreen();
            });

            llAlarmsList.addView(card);
        }
    }

    private void renderNapsScreen() {
        llNapsList.removeAllViews();
        List<Integer> presets = state.napPresets;
        for (Integer mins : presets) {
            Calendar target = Calendar.getInstance();
            target.add(Calendar.MINUTE, mins);
            String wakeStr = String.format(Locale.US, "%02d:%02d",
                    target.get(Calendar.HOUR_OF_DAY), target.get(Calendar.MINUTE));

            Button btn = new Button(this);
            btn.setText(getString(R.string.wear_nap_btn_fmt, mins, wakeStr));
            btn.setTextColor(ContextCompat.getColor(this, R.color.wear_text));
            btn.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.wear_primary));
            btn.setAllCaps(false);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            lp.bottomMargin = dp(6);
            btn.setLayoutParams(lp);
            btn.setOnClickListener(v -> {
                WatchHapticHelper.pulseConfirm(this);
                String label = getString(R.string.nap_alarm_label_fmt, mins);
                WatchDataClient.createNap(this, mins, label);
                Toast.makeText(this, getString(R.string.wear_toast_nap_created, mins, wakeStr),
                        Toast.LENGTH_SHORT).show();
            });
            llNapsList.addView(btn);
        }
    }

    private void renderCreateScreen() {
        tvCreateHour.setText(String.format(Locale.US, "%02d", createHour));
        tvCreateMin.setText(String.format(Locale.US, "%02d", createMinute));
        tvCreateLabel.setText(getString(R.string.wear_label_preview_fmt, createLabel));

        styleDayBtn(btnDayMon, (createDaysMask & WearProtocol.BIT_MON) != 0);
        styleDayBtn(btnDayTue, (createDaysMask & WearProtocol.BIT_TUE) != 0);
        styleDayBtn(btnDayWed, (createDaysMask & WearProtocol.BIT_WED) != 0);
        styleDayBtn(btnDayThu, (createDaysMask & WearProtocol.BIT_THU) != 0);
        styleDayBtn(btnDayFri, (createDaysMask & WearProtocol.BIT_FRI) != 0);
        styleDayBtn(btnDaySat, (createDaysMask & WearProtocol.BIT_SAT) != 0);
        styleDayBtn(btnDaySun, (createDaysMask & WearProtocol.BIT_SUN) != 0);

        btnCreateVibrate.setText(createVibrate
                ? R.string.wear_vibrate_on
                : R.string.wear_vibrate_off);
        btnCreateVibrate.setBackgroundTintList(ContextCompat.getColorStateList(this,
                createVibrate ? R.color.wear_card_active : R.color.wear_chip_off));
    }

    private void renderControlsScreen() {
        int snooze = state.defaultSnoozeMinutes > 0 ? state.defaultSnoozeMinutes : 10;
        btnCtrlSnooze.setText(getString(R.string.wear_ctrl_snooze_fmt, snooze));
    }

    private void styleDayBtn(Button btn, boolean active) {
        btn.setBackgroundTintList(ContextCompat.getColorStateList(this,
                active ? R.color.wear_primary : R.color.wear_chip_off));
    }

    private String formatDaysCompact(int mask) {
        if (mask == 0) return getString(R.string.days_one_time);
        if (mask == 0x7f) return getString(R.string.days_every_day);
        if (mask == (WearProtocol.BIT_MON | WearProtocol.BIT_TUE | WearProtocol.BIT_WED
                | WearProtocol.BIT_THU | WearProtocol.BIT_FRI)) {
            return getString(R.string.days_weekdays);
        }
        if (mask == (WearProtocol.BIT_SAT | WearProtocol.BIT_SUN)) {
            return getString(R.string.days_weekends);
        }
        List<String> parts = new ArrayList<>();
        if ((mask & WearProtocol.BIT_MON) != 0) parts.add(getString(R.string.day_short_mon));
        if ((mask & WearProtocol.BIT_TUE) != 0) parts.add(getString(R.string.day_short_tue));
        if ((mask & WearProtocol.BIT_WED) != 0) parts.add(getString(R.string.day_short_wed));
        if ((mask & WearProtocol.BIT_THU) != 0) parts.add(getString(R.string.day_short_thu));
        if ((mask & WearProtocol.BIT_FRI) != 0) parts.add(getString(R.string.day_short_fri));
        if ((mask & WearProtocol.BIT_SAT) != 0) parts.add(getString(R.string.day_short_sat));
        if ((mask & WearProtocol.BIT_SUN) != 0) parts.add(getString(R.string.day_short_sun));
        return TextUtils.join(" ", parts);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
