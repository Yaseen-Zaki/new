package com.fondi.photosync;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Receives the periodic alarm. In 6D.2 this only logs. In 6D.3 it will
 * start SyncService.
 */
public class SyncAlarmReceiver extends BroadcastReceiver {
    private static final String TAG = "FondiSyncAlarm";

    private static final String PREFS_NAME = "fondi_prefs";
    private static final String KEY_LAST_ALARM = "last_alarm_ms";

    @Override
    public void onReceive(Context context, Intent intent) {
        long now = System.currentTimeMillis();
        Log.d(TAG, "Alarm fired at " + now);
        // Record the fire time for the settings screen status line.
        try {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit().putLong(KEY_LAST_ALARM, now).apply();
        } catch (Exception ignored) {}
        // 6D.3: start the foreground service to run one scan+upload pass.
        try {
            Intent svc = new Intent(context, SyncService.class);
            context.startService(svc);
        } catch (Exception e) {
            Log.e(TAG, "failed to start SyncService", e);
        }
    }
}
