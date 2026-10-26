package com.fondi.photosync;

/**
 * Central tuning constants. Change SCAN_INTERVAL_MS to adjust how often the
 * alarm fires. Note: on API 19+ AlarmManager.setRepeating is inexact; the OS
 * will batch and delay these, especially with the screen off. 60s is a
 * request, not a guarantee.
 */
public final class SyncConfig {
    public static final long SCAN_INTERVAL_MS = 60L * 1000L; // 1 minute

    private SyncConfig() {}
}
