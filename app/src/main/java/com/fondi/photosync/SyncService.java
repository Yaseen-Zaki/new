package com.fondi.photosync;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Foreground service woken by SyncAlarmReceiver. Runs one scan + upload pass
 * and stops itself. Coordinates with the manual UI via SyncState so the two
 * never run their upload loops at the same time.
 */
public class SyncService extends Service {

    private static final String TAG = "FondiSyncService";
    private static final String PREFS_NAME = "fondi_prefs";
    private static final String KEY_FOLDER = "selected_folder";
    private static final String KEY_TOKEN = "worker_token";
    private static final int NOTIFICATION_ID = 0xF0D2;
    private static final int MAX_NAMES_SHOWN = 50;

    private Thread worker;
    private volatile boolean stopRequested = false;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Guard: manual run in progress → do nothing.
        if (SyncState.manualRunActive) {
            Log.d(TAG, "manual run active, service not starting");
            stopSelf();
            return START_NOT_STICKY;
        }
        // Guard: another service instance already running → do nothing.
        if (SyncState.serviceRunning) {
            Log.d(TAG, "service already running, ignoring duplicate alarm");
            stopSelf();
            return START_NOT_STICKY;
        }

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        final String folder = prefs.getString(KEY_FOLDER, null);
        final String token = prefs.getString(KEY_TOKEN, null);
        if (folder == null || folder.length() == 0 || token == null || token.length() == 0) {
            Log.d(TAG, "no folder or token set, service stopping");
            stopSelf();
            return START_NOT_STICKY;
        }

        SyncState.serviceRunning = true;
        startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.sync_notif_scanning)));

        worker = new Thread(new Runnable() {
            @Override
            public void run() {
                UploadDatabase db = null;
                try {
                    db = new UploadDatabase(getApplicationContext());
                    // 1. scan
                    PhotoScanner scanner = new PhotoScanner(db, MAX_NAMES_SHOWN);
                    PhotoScanner.Result scan = scanner.scan(folder, null);
                    if (scan.error != null) {
                        Log.w(TAG, "scan error: " + scan.error);
                        return;
                    }
                    // 2. promote discoveries and due retries
                    db.promoteDiscoveredToPending();
                    db.promoteDueRetries();
                    int pending = db.countPending();
                    if (pending == 0) {
                        Log.d(TAG, "nothing to upload");
                        return;
                    }
                    // 3. update notification to "uploading"
                    updateNotification(getString(R.string.sync_notif_uploading));
                    // 4. upload loop
                    PhotoUploader uploader = new PhotoUploader(db, token);
                    uploader.setContext(SyncService.this);
                    PhotoUploader.Result r = uploader.run(
                            new PhotoUploader.StopCheck() {
                                @Override public boolean shouldStop() {
                                    return stopRequested;
                                }
                            },
                            new PhotoUploader.Listener() {
                                @Override public void onUploadProgress(int done, int total) { /* no UI */ }
                                @Override public void onUploadDone(PhotoUploader.Result result) { /* no UI */ }
                            });
                    Log.d(TAG, "service upload pass done: uploaded=" + r.uploaded
                            + " failed=" + r.failed
                            + " stopped=" + r.stopped
                            + (r.lastFailure != null ? " last=" + r.lastFailure : ""));
                } catch (Exception e) {
                    Log.e(TAG, "service pass failed", e);
                } finally {
                    if (db != null) try { db.close(); } catch (Exception ignored) {}
                    SyncState.serviceRunning = false;
                    stopForeground(true);
                    stopSelf();
                }
            }
        }, "fondi-sync-worker");
        worker.start();

        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopRequested = true;
        SyncState.serviceRunning = false;
        if (worker != null) {
            try { worker.join(2000); } catch (InterruptedException ignored) {}
            worker = null;
        }
    }

    // ---------------- notification helpers ----------------

    private Notification buildNotification(String text) {
        Intent open = new Intent(this, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pi = PendingIntent.getActivity(this, 0, open,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        } else {
            pi = PendingIntent.getActivity(this, 0, open,
                    PendingIntent.FLAG_UPDATE_CURRENT);
        }
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            b = new Notification.Builder(this, "fondi_sync");
        } else {
            b = new Notification.Builder(this);
        }
        // Use a framework raster drawable (API 19 has no vector
        // notification icon support). stat_sys_upload is the
        // standard "uploading" status bar icon on every API 19+
        // device, and satisfies NotificationIconCompatibility.
        return b.setSmallIcon(android.R.drawable.stat_sys_upload)
                .setContentTitle(getString(R.string.sync_notif_title))
                .setContentText(text)
                .setOngoing(true)
                .setContentIntent(pi)
                .build();
    }

    private void updateNotification(String text) {
        android.app.NotificationManager nm =
                (android.app.NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(NOTIFICATION_ID, buildNotification(text));
        }
    }
}
