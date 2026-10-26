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
        // 6D.3: context.startService(new Intent(context, SyncService.class));
    }
}
