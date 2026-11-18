package com.nitsutech.sleepremote;

import android.app.Application;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Looper;

import com.nitsutech.sleepremote.sleep.SleepAlarmRepository;
import com.nitsutech.sleepremote.sleep.SleepConstants;
import com.nitsutech.sleepremote.wear.WearDataLayerManager;

/**
 * Application entry point that watches Sleep as Android's AlarmProvider for live changes.
 */
public class SleepRemoteApp extends Application {

    private boolean observerRegistered;
    private ContentObserver alarmObserver;

    @Override
    public void onCreate() {
        super.onCreate();
        tryRegisterAlarmObserver();
        WearDataLayerManager.syncStateAsync(this);
    }

    public void tryRegisterAlarmObserver() {
        if (observerRegistered || !SleepAlarmRepository.hasSleepReadPermission(this)) {
            return;
        }
        try {
            alarmObserver = new ContentObserver(new Handler(Looper.getMainLooper())) {
                @Override
                public void onChange(boolean selfChange) {
                    WearDataLayerManager.syncStateDelayedAsync(SleepRemoteApp.this, 250);
                }
            };
            getContentResolver().registerContentObserver(
                    SleepConstants.ALARM_CONTENT_URI,
                    true,
                    alarmObserver
            );
            observerRegistered = true;
        } catch (SecurityException ignored) {
        }
    }
}
