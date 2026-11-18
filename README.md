# SleepRemote

Android phone + Wear OS remote controller for **Sleep as Android** (`com.urbandroid.sleep`) built in Java 17 using the Play Services Wearable Data Layer.

## Modules

- **`:app`** — Phone companion (`com.nitsutech.sleepremote`):
  - Queries Sleep as Android alarms via `content://com.urbandroid.sleep.alarmclock/alarm` (`com.urbandroid.sleep.READ`).
  - Creates alarms silently via `android.intent.action.SET_ALARM` with `EXTRA_SKIP_UI = true`.
  - Toggles existing labeled alarms via `com.urbandroid.sleep.alarmclock.ALARM_STATE_CHANGE`.
  - Triggers sleep tracking & alarm actions (`START_SLEEP_TRACK`, `START_SLEEP_TRACK_WITH_IDEAL_ALARM_ACTION`, `STOP_SLEEP_TRACK`, `ALARM_SNOOZE`, `SKIP_NEXT_ALARM_ACTION`, `ALARM_DISMISS_CAPTCHA`).
  - Provides permission checks (`com.urbandroid.sleep.READ`, `SYSTEM_ALERT_WINDOW`), Intent API setup guide, configurable nap presets, and Wearable Data Layer sync.
- **`:wear`** — Wear OS watch app & Tile (`com.nitsutech.sleepremote`):
  - Rotating bezel scroll support and full back-stack navigation.
  - Real-time synced alarm list with 1-tap ON/OFF toggle.
  - Alarm creator (hour, minute, optional repeat days, label).
  - 1-tap configurable Quick Nap buttons.
  - Sleep tracking controls & native Wear OS Tile (`SleepRemoteTileService`).
