package com.fondi.photosync;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.SystemClock;

/**
 * Registers and cancels the periodic scan alarm. Uses setRepeating with
 * RTC_WAKEUP so the alarm fires even with the screen off. On API 19+ the
 * scheduling is inexact; the OS may delay or batch it.
 */
public final class SyncScheduler {

    private static final int REQUEST_CODE = 0xF0D1;

    private SyncScheduler() {}

    private static PendingIntent pendingIntent(Context ctx) {
        Intent i = new Intent(ctx, SyncAlarmReceiver.class);
        // AGP 8 lint accepts this shape: conditional flag in a variable,
        // then one getBroadcast() call. Splitting the return into two
        // branches makes lint miss the SDK check (known false positive).
        int flags;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags = PendingIntent.FLAG_IMMUTABLE;
        } else {
            flags = 0;
        }
        flags |= PendingIntent.FLAG_UPDATE_CURRENT;
        return PendingIntent.getBroadcast(ctx, REQUEST_CODE, i, flags);
    }

    public static void enable(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        long interval = SyncConfig.SCAN_INTERVAL_MS;
        long first = SystemClock.elapsedRealtime() + interval;
        am.setRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, first, interval,
                pendingIntent(ctx));
    }

    public static void disable(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        am.cancel(pendingIntent(ctx));
    }

    // Lint's UnspecifiedImmutableFlag check flags FLAG_NO_CREATE calls even
    // though no PendingIntent is created (FLAG_NO_CREATE returns null if the
    // intent does not already exist). This is a documented false positive;
    // see https://googlesamples.github.io/android-custom-lint-rules/checks/UnspecifiedImmutableFlag.md.html
    @SuppressWarnings("UnspecifiedImmutableFlag")
    public static boolean isEnabled(Context ctx) {
        return PendingIntent.getBroadcast(
                ctx, REQUEST_CODE, new Intent(ctx, SyncAlarmReceiver.class),
                PendingIntent.FLAG_NO_CREATE) != null;
    }
}
