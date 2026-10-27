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

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "Alarm fired at " + System.currentTimeMillis());
        // 6D.3: start the foreground service to run one scan+upload pass.
        // startService from a BroadcastReceiver is safe here because the
        // service calls startForeground() as its first action, which gives
        // it foreground priority and prevents the OS from killing it
        // mid-upload.
        try {
            Intent svc = new Intent(context, SyncService.class);
            context.startService(svc);
        } catch (Exception e) {
            Log.e(TAG, "failed to start SyncService", e);
        }
    }
}
