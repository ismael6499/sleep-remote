# SleepRemote

Native **Android + Wear OS** remote controller for **Sleep as Android** (`com.urbandroid.sleep`), built in **Java 17** with bi-directional **Play Services Wearable Data Layer** sync and a native **Wear OS Tile**.

## Features

### Watch (`:wear`)
- **Live Alarm List**: Reads alarms from Sleep as Android on the phone, sorted chronologically by next trigger time (enabled first, disabled at the bottom).
- **1-Tap Alarm Toggle**: Enables or disables any alarm remotely via `ALARM_STATE_CHANGE` (supporting both labeled alarms and unlabeled alarms via `alarm_id`, with local display aliases).
- **Alarm Creator with Wheels & Bezel**: Side-by-side `NumberPicker` hour (`00–23`) and minute (`00–59`) wheels with optional **Bezel Lock** (`Bezel: Hour` / `Bezel: Min`) for physical rotating-bezel control, optional repeat days, preset/custom labels (auto-tagged with unique `#ID`), and vibration toggle.
- **Quick Nap Presets**: 1-tap configurable nap buttons (e.g. `+20m`, `+30m`, `+45m`, `+60m`, `+90m`) with live wake-up time preview.
- **Sleep Tracking & Alarm Controls**: Start Tracking, Start with Ideal Alarm, Stop Tracking, Snooze, Skip Next Alarm, and Dismiss Alarm.
- **Native Wear OS Tile**: Quick-access Tile for 1-tap naps, tracking toggle, skip next alarm, and launching the app.
- **Wear Navigation**: `windowSwipeToDismiss=false`, `SwipeDismissFrameLayout` only on root, physical rotating bezel scrolling, and full screen back-stack.

### Phone Companion (`:app`)
- **Permission & Setup Checker**: Verifies Sleep as Android installation, `com.urbandroid.sleep.READ` runtime permission, `SYSTEM_ALERT_WINDOW` (for background silent `SET_ALARM`), and includes an Intent API setup guide.
- **Configurable Presets**: Customize quick nap durations and default Watch snooze duration with automatic Data Layer sync.
- **Alarm Overview & Local Aliases**: View, toggle, and assign local display aliases for synced Sleep as Android alarms.
- **Full Localization**: English default (`values/strings.xml`) and Spanish (`values-es/strings.xml`) with zero hardcoded layout strings.

## Architecture

```text
+---------------------------+        Wearable Data Layer        +---------------------------+
|   Wear OS Watch (:wear)   | <==============================> |    Android Phone (:app)   |
|                           |   DataClient (/sleepremote/state) |                           |
| - WearMainActivity        |   MessageClient (RPC commands)    | - MainActivity            |
| - SleepRemoteTileService  |                                   | - PhoneWearListenerService|
| - WatchWearListenerService|                                   | - SleepAlarmRepository    |
+---------------------------+                                   | - SleepCommandController  |
                                                                +-------------+-------------+
                                                                              |
                                                           ContentProvider + Intent API
                                                                              v
                                                                +---------------------------+
                                                                |  Sleep as Android         |
                                                                |  (com.urbandroid.sleep)   |
                                                                +---------------------------+
```

## Setup

1. Install `:app` on your Android phone and `:wear` on your Wear OS watch (both share `applicationId = "com.nitsutech.sleepremote"`).
2. Open **Sleep as Android** → **Settings** → **Services** → **Automation** → enable **Intent API**.
3. Open **SleepRemote** on the phone and grant:
   - `com.urbandroid.sleep.READ` (to sync your alarms)
   - **Display over other apps** (`SYSTEM_ALERT_WINDOW`, required on Android 10+ for silent background alarm creation from the Watch)
4. Add the **SleepRemote** Tile on your watch for one-turn quick controls.

## Verification

```bash
# Virtual DOM XML & EN/ES i18n parity audit
node test-ui-audit.js

# Unit tests & debug APKs
./gradlew testDebugUnitTest assembleDebug
```
