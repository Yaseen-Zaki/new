package com.fondi.photosync;

/**
 * Process-wide coordination between the manual UI and the background service.
 * Intentionally simple: two volatile booleans, reset on app process start.
 * Not persisted. Force-stop clears it, which is correct.
 */
public final class SyncState {
    public static volatile boolean serviceRunning = false;
    public static volatile boolean manualRunActive = false;

    private SyncState() {}
}
