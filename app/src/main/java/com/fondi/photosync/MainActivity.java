package com.fondi.photosync;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.lang.ref.WeakReference;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

public class MainActivity extends Activity {

    private static final int STATE_STOPPED = 0;
    private static final int STATE_SCANNING = 1;
    private static final int STATE_UPLOADING = 2;
    private static final int STATE_PAUSED = 3;

    private static final String PREFS_NAME = "fondi_prefs";
    private static final String KEY_FOLDER = "selected_folder";
    private static final String KEY_SCAN_TOTAL = "last_scan_total";
    private static final String KEY_SCAN_TIME = "last_scan_time";
    private static final String KEY_TOKEN = "worker_token";
    private static final String WORKER_UPLOAD_URL = "https://restless-fire-7680.yaseenzaki99.workers.dev/upload";
    private static final int MAX_NAMES_SHOWN = 50;

    private TextView statusView;
    private TextView countersView;
    private TextView folderView;
    private TextView lastErrorView;
    private Button startButton;
    private Button pauseButton;
    private Button stopButton;
    private Button chooseFolderButton;
    private Button setTokenButton;
    private Button checkTokenButton;
    private Button sendOnePhotoButton;

    private int currentState = STATE_STOPPED;
    private int pendingCount = 0;
    private int uploadedCount = 0;
    private int failedCount = 0;
    private String lastError = null;
    private String selectedFolder = null;
    private ScanTask currentScan = null;
    private StringBuilder scanOutput = new StringBuilder();
    private CheckTokenTask currentCheckTask;
    private UploadDatabase db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        loadPreferences();
        db = new UploadDatabase(this);
        db.resetUploadingToPending();
        showTokenDialogIfMissing();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32, 32, 32, 32);

        TextView title = new TextView(this);
        title.setText(R.string.app_name);
        title.setTextSize(20f);
        title.setPadding(0, 0, 0, 16);
        root.addView(title);

        statusView = new TextView(this);
        statusView.setTextSize(16f);
        statusView.setPadding(0, 0, 0, 8);
        root.addView(statusView);

        countersView = new TextView(this);
        countersView.setTextSize(14f);
        countersView.setPadding(0, 0, 0, 16);
        root.addView(countersView);

        folderView = new TextView(this);
        folderView.setTextSize(14f);
        folderView.setPadding(0, 0, 0, 8);
        root.addView(folderView);

        chooseFolderButton = new Button(this);
        chooseFolderButton.setText(R.string.button_choose_folder);
        chooseFolderButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                openFolderBrowser();
            }
        });
        root.addView(chooseFolderButton);

        setTokenButton = new Button(this);
        setTokenButton.setText(R.string.button_set_token);
        setTokenButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showTokenDialog(); }
        });
        root.addView(setTokenButton);

        checkTokenButton = new Button(this);
        checkTokenButton.setText(R.string.button_check_token);
        checkTokenButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { onCheckTokenClicked(); }
        });
        root.addView(checkTokenButton);

        sendOnePhotoButton = new Button(this);
        sendOnePhotoButton.setText(R.string.button_send_one_photo);
        sendOnePhotoButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { onSendOnePhotoClicked(); }
        });
        root.addView(sendOnePhotoButton);

        Button settingsButton = new Button(this);
        settingsButton.setText(R.string.settings_title);
        settingsButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openSettings(); }
        });
        root.addView(settingsButton);

        LinearLayout buttonRow = new LinearLayout(this);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonRow.setPadding(0, 16, 0, 16);

        startButton = new Button(this);
        startButton.setText(R.string.button_start);
        startButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { onStartClicked(); }
        });
        buttonRow.addView(startButton);

        pauseButton = new Button(this);
        pauseButton.setText(R.string.button_pause);
        pauseButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { onPauseClicked(); }
        });
        buttonRow.addView(pauseButton);

        stopButton = new Button(this);
        stopButton.setText(R.string.button_stop);
        stopButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { onStopClicked(); }
        });
        buttonRow.addView(stopButton);

        root.addView(buttonRow);

        ScrollView scroll = new ScrollView(this);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        scroll.setLayoutParams(scrollParams);

        lastErrorView = new TextView(this);
        lastErrorView.setTextSize(12f);
        scroll.addView(lastErrorView);

        root.addView(scroll);

        setContentView(root);

        render();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (db != null) db.close();
    }

    private void onStartClicked() {
        if (selectedFolder == null) {
            lastError = "Choose a folder before starting.";
            render();
            return;
        }
        if (currentScan != null) {
            return;
        }
        if (SyncState.serviceRunning) {
            lastError = getString(R.string.sync_busy);
            render();
            return;
        }
        currentState = STATE_SCANNING;
        lastError = null;
        scanOutput.setLength(0);
        scanOutput.append(getString(R.string.scan_started));
        render();
        SyncScheduler.enable(this);
        SyncState.manualRunActive = true;
        currentScan = new ScanTask(this, db, selectedFolder);
        currentScan.execute();
    }

    private void onPauseClicked() {
        if (currentState == STATE_UPLOADING && !uploadPaused) {
            // Pause: takes effect at the next row boundary inside UploadTask.
            // Clear the manual-run flag: the alarm is free to run the
            // background service while the user has stepped away from the loop.
            uploadPaused = true;
            SyncState.manualRunActive = false;
            currentState = STATE_PAUSED;
            render();
            return;
        }
        if (currentState == STATE_PAUSED) {
            onResumeClicked();
        }
    }

    private void onResumeClicked() {
        if (!uploadPaused) return;
        uploadPaused = false;
        SyncState.manualRunActive = true;
        currentState = STATE_UPLOADING;
        render();
        startUploadLoop(true);
    }

    private void onStopClicked() {
        uploadStopped = true;
        SyncState.manualRunActive = false;
        SyncScheduler.disable(this);
        currentState = STATE_STOPPED;
        render();
    }

    private void render() {
        switch (currentState) {
            case STATE_SCANNING:  statusView.setText(R.string.status_scanning);  break;
            case STATE_UPLOADING: statusView.setText(R.string.status_uploading); break;
            case STATE_PAUSED:    statusView.setText(R.string.status_paused);    break;
            case STATE_STOPPED:
            default:              statusView.setText(R.string.status_stopped);   break;
        }

        countersView.setText(getString(
                R.string.counters_fmt, pendingCount, uploadedCount, failedCount));

        if (selectedFolder == null) {
            folderView.setText(R.string.folder_none);
        } else {
            folderView.setText(getString(R.string.folder_fmt, selectedFolder));
        }

        // Start is available when fully Stopped (fresh scan) or from Paused
        // (abandon the paused batch and start over). While actively Uploading
        // or Scanning it is disabled.
        startButton.setEnabled(currentState == STATE_STOPPED || currentState == STATE_PAUSED);
        pauseButton.setEnabled(currentState == STATE_UPLOADING || currentState == STATE_PAUSED);
        pauseButton.setText(currentState == STATE_PAUSED ? R.string.button_resume : R.string.button_pause);
        stopButton.setEnabled(currentState != STATE_STOPPED);

        if (lastError == null) {
            lastErrorView.setText(R.string.no_errors);
        } else {
            lastErrorView.setText(getString(R.string.last_error_fmt, lastError));
        }

        if (scanOutput.length() > 0) {
            lastErrorView.setText(scanOutput.toString());
        }
    }

    private void openFolderBrowser() {
        java.util.List<File> roots = FolderBrowser.candidateRoots();
        if (roots.isEmpty()) {
            showDirectoryChooser(new File("/"));
        } else {
            showDirectoryChooser(roots.get(0));
        }
    }

    private void showDirectoryChooser(final File dir) {
        final java.util.List<File> entries = new java.util.ArrayList<File>();
        final java.util.List<String> labels = new java.util.ArrayList<String>();

        if (dir.getParentFile() != null) {
            entries.add(dir.getParentFile());
            labels.add(getString(R.string.folder_pick_up));
        }

        java.util.List<File> subs = FolderBrowser.subdirectories(dir);
        for (File sub : subs) {
            int count = FolderBrowser.countImages(sub);
            String label = sub.getName() + "  (" +
                    getResources().getQuantityString(R.plurals.folder_preview, count, count) + ")";
            entries.add(sub);
            labels.add(label);
        }

        final String[] items = labels.toArray(new String[labels.size()]);

        new android.app.AlertDialog.Builder(this)
                .setTitle(R.string.folder_pick_title)
                .setItems(items, new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int which) {
                        showDirectoryChooser(entries.get(which));
                    }
                })
                .setPositiveButton(R.string.folder_pick_use, new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) {
                        selectFolder(dir);
                    }
                })
                .setNegativeButton(R.string.folder_pick_cancel, null)
                .show();
    }

    private void selectFolder(File dir) {
        if (dir == null || !dir.isDirectory() || !dir.canRead()) {
            lastError = "Selected folder is not readable: " + (dir == null ? "null" : dir.getAbsolutePath());
            render();
            return;
        }
        selectedFolder = dir.getAbsolutePath();
        lastError = null;
        saveFolderPreference(selectedFolder);
        render();
    }

    private void loadPreferences() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String saved = prefs.getString(KEY_FOLDER, null);
        if (saved == null || saved.length() == 0) {
            return;
        }
        File dir = new File(saved);
        if (dir.isDirectory() && dir.canRead()) {
            selectedFolder = saved;
        } else {
            prefs.edit().remove(KEY_FOLDER).apply();
            selectedFolder = null;
            lastError = getString(R.string.folder_unavailable, saved);
        }
    }

    private void saveFolderPreference(String path) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit().putString(KEY_FOLDER, path).apply();
    }

    // AsyncTask is deprecated on API 30+, but present and correct on API 19.
    // Static + WeakReference to avoid leaking the Activity.
    @SuppressWarnings("deprecation")
    private static class ScanTask extends AsyncTask<Void, Void, PhotoScanner.Result> {
        private final WeakReference<MainActivity> activityRef;
        private final UploadDatabase db;
        private final String folderPath;

        ScanTask(MainActivity activity, UploadDatabase db, String folderPath) {
            this.activityRef = new WeakReference<MainActivity>(activity);
            this.db = db;
            this.folderPath = folderPath;
        }

        @Override
        protected PhotoScanner.Result doInBackground(Void... params) {
            PhotoScanner scanner = new PhotoScanner(db, MAX_NAMES_SHOWN);
            return scanner.scan(folderPath, null);
        }

        @Override
        protected void onPostExecute(PhotoScanner.Result result) {
            MainActivity activity = activityRef.get();
            if (activity == null) return;
            activity.finishScanFromScanner(result);
        }
    }


    // Called from ScanTask.onPostExecute via WeakReference. Runs on the UI thread.
    private void finishScanFromScanner(PhotoScanner.Result result) {
        currentScan = null;
        currentState = STATE_STOPPED;
        if (result.error != null) {
            SyncState.manualRunActive = false;
            lastError = getString(R.string.scan_failed, result.error);
            scanOutput.setLength(0);
        } else {
            lastError = null;
            scanOutput.setLength(0);
            scanOutput.append(getResources().getQuantityString(
                    R.plurals.scan_result, result.total, result.total, result.shownNames.size()))
                    .append("\n");
            scanOutput.append(getString(R.string.scan_db_summary,
                    result.newlyDiscovered, result.seenAgain, result.modified, result.skippedUnreadable))
                    .append("\n\n");
            for (String name : result.shownNames) {
                scanOutput.append(name).append("\n");
            }
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .edit()
                    .putInt(KEY_SCAN_TOTAL, result.total)
                    .putLong(KEY_SCAN_TIME, System.currentTimeMillis())
                    .apply();
        }
        render();
        if (result.error == null) {
            startUploadLoop(false);
        }
    }

    // -------- token helpers --------

    private String getToken() {
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(KEY_TOKEN, null);
    }

    private void saveToken(String raw) {
        String token = stripQuotesAndWhitespace(raw);
        if (token.length() == 0) return;
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit().putString(KEY_TOKEN, token).apply();
    }

    private static String stripQuotesAndWhitespace(String raw) {
        if (raw == null) return "";
        String v = raw.trim();
        while (v.length() >= 2) {
            char a = v.charAt(0);
            char b = v.charAt(v.length() - 1);
            if ((a == '"' && b == '"') || (a == '\'' && b == '\'')) {
                v = v.substring(1, v.length() - 1).trim();
            } else {
                break;
            }
        }
        return v;
    }

    private void showTokenDialog() {
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setHint(R.string.token_hint);
        String existing = getToken();
        if (existing != null) input.setText(existing);
        new android.app.AlertDialog.Builder(this)
                .setTitle(R.string.token_dialog_title)
                .setMessage(R.string.token_dialog_message)
                .setView(input)
                .setPositiveButton(R.string.token_dialog_save, new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) {
                        String raw = input.getText() == null ? "" : input.getText().toString();
                        saveToken(raw);
                        lastError = getToken() == null ? getString(R.string.token_missing) : getString(R.string.token_saved);
                        render();
                    }
                })
                .setNegativeButton(R.string.token_dialog_cancel, null)
                .show();
    }

    private void onCheckTokenClicked() {
        String token = getToken();
        if (token == null || token.length() == 0) {
            lastError = getString(R.string.token_missing);
            render();
            return;
        }
        if (currentCheckTask != null) return;
        lastError = null;
        scanOutput.setLength(0);
        scanOutput.append("Checking token…");
        render();
        currentCheckTask = new CheckTokenTask(this, token);
        currentCheckTask.execute();
    }

    private void onCheckTokenDone(String message) {
        currentCheckTask = null;
        scanOutput.setLength(0);
        lastError = null;
        scanOutput.append(message);
        render();
    }

    @SuppressWarnings("deprecation")
    private static class CheckTokenTask extends AsyncTask<Void, Void, String> {
        private final WeakReference<MainActivity> activityRef;
        private final String token;
        CheckTokenTask(MainActivity activity, String token) {
            this.activityRef = new WeakReference<MainActivity>(activity);
            this.token = token;
        }
        @Override
        protected String doInBackground(Void... params) {
            MainActivity act = activityRef.get();
            if (act == null) return null;
            HttpURLConnection conn = null;
            try {
                URL url = new URL(WORKER_UPLOAD_URL);
                conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(20000);
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setFixedLengthStreamingMode(0);
                conn.setRequestProperty("Authorization", "Bearer " + token);
                conn.setRequestProperty("Content-Length", "0");
                OutputStream out = conn.getOutputStream();
                out.flush();
                out.close();
                int code = conn.getResponseCode();
                String body = readStream(code >= 200 && code < 400
                        ? conn.getInputStream() : conn.getErrorStream());
                if (code == 401) {
                    return act.getString(R.string.token_rejected);
                }
                if (code == 400 && body.contains("bad_hash")) {
                    return act.getString(R.string.token_accepted);
                }
                return act.getString(R.string.token_check_unexpected, code, body);
            } catch (IOException e) {
                return act.getString(R.string.token_check_net_error,
                        e.getClass().getSimpleName() + ": " + e.getMessage());
            } finally {
                if (conn != null) conn.disconnect();
            }
        }
        @Override
        protected void onPostExecute(String message) {
            MainActivity act = activityRef.get();
            if (act == null || message == null) return;
            act.onCheckTokenDone(message);
        }
        private static String readStream(InputStream in) throws IOException {
            if (in == null) return "";
            BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[1024];
            int n;
            try {
                while ((n = r.read(buf)) != -1) {
                    sb.append(buf, 0, n);
                    if (sb.length() > 4096) break;
                }
            } finally {
                try { r.close(); } catch (IOException ignored) {}
            }
            return sb.toString();
        }
    }

    private void showTokenDialogIfMissing() {
        String t = getToken();
        if (t == null || t.length() == 0) showTokenDialog();
    }

    // ------------------------------------------------------------------
    // Step 5B — send one specific photo (no DB, no queue)
    // ------------------------------------------------------------------

    private void onSendOnePhotoClicked() {
        if (selectedFolder == null) {
            lastError = getString(R.string.send_one_no_folder);
            render();
            return;
        }
        String token = getToken();
        if (token == null || token.length() == 0) {
            lastError = getString(R.string.token_missing);
            render();
            return;
        }
        File dir = new File(selectedFolder);
        File[] children = dir.listFiles();
        if (children == null) {
            lastError = getString(R.string.send_one_no_photos);
            render();
            return;
        }
        final java.util.List<File> images = new java.util.ArrayList<File>();
        final java.util.List<String> labels = new java.util.ArrayList<String>();
        for (File f : children) {
            if (!f.isFile()) continue;
            if (!FolderBrowser.isImage(f.getName())) continue;
            if (f.length() <= 0) continue;
            images.add(f);
            labels.add(f.getName() + "  (" + f.length() + " B)");
        }
        if (images.isEmpty()) {
            lastError = getString(R.string.send_one_no_photos);
            render();
            return;
        }
        final String[] items = labels.toArray(new String[labels.size()]);
        new android.app.AlertDialog.Builder(this)
                .setTitle(R.string.send_one_pick_title)
                .setItems(items, new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int which) {
                        startSendOnePhoto(images.get(which));
                    }
                })
                .setNegativeButton(R.string.token_dialog_cancel, null)
                .show();
    }

    private void startSendOnePhoto(File file) {
        if (currentSendOne != null) return;
        String token = getToken();
        if (token == null || token.length() == 0) return;
        scanOutput.setLength(0);
        scanOutput.append(getString(R.string.send_one_uploading, file.getName()));
        lastError = null;
        render();
        currentSendOne = new SendOneTask(this, token, file);
        currentSendOne.execute();
    }

    private void onSendOneDone(HttpUploader.Result result, String fileName) {
        currentSendOne = null;
        scanOutput.setLength(0);
        if (result.error != null) {
            lastError = getString(R.string.send_one_failed_pre, result.error);
            render();
            return;
        }
        lastError = null;
        scanOutput.append("File: ").append(fileName).append("\n");
        scanOutput.append(getString(R.string.send_one_result, result.httpCode, result.rawBody));
        render();
    }

    private SendOneTask currentSendOne;
    private UploadTask currentUpload;
    private boolean uploadPaused;
    private boolean uploadStopped;

    @SuppressWarnings("deprecation")
    private static class SendOneTask extends AsyncTask<Void, Void, HttpUploader.Result> {
        private final WeakReference<MainActivity> activityRef;
        private final String token;
        private final File file;
        SendOneTask(MainActivity activity, String token, File file) {
            this.activityRef = new WeakReference<MainActivity>(activity);
            this.token = token;
            this.file = file;
        }
        @Override
        protected HttpUploader.Result doInBackground(Void... params) {
            return HttpUploader.upload(token, file);
        }
        @Override
        protected void onPostExecute(HttpUploader.Result r) {
            MainActivity activity = activityRef.get();
            if (activity == null) return;
            activity.onSendOneDone(r, file.getName());
        }
    }

    // ------------------------------------------------------------------
    // Upload loop (5C)
    // ------------------------------------------------------------------

    private void startUploadLoop(boolean isResume) {
        String token = getToken();
        if (token == null || token.length() == 0) {
            lastError = getString(R.string.token_missing);
            render();
            return;
        }
        if (currentUpload != null) return;

        if (!isResume) {
            db.promoteDiscoveredToPending();
        }
        // 6C: promote RETRY rows whose backoff has expired. Runs for both
        // fresh Start and Resume so resumed sessions also pick up eligible
        // retries that became ready while the user was paused.
        db.promoteDueRetries();
        int pending = db.countPending();
        if (pending == 0) {
            // Nothing to do — stay in Stopped and leave the scan output visible.
            // Clear the manual-run flag so the background service can run
            // on the next alarm; otherwise every subsequent alarm is swallowed
            // by the SyncService guard.
            SyncState.manualRunActive = false;
            currentState = STATE_STOPPED;
            render();
            return;
        }
        uploadPaused = false;
        uploadStopped = false;
        currentState = STATE_UPLOADING;
        pendingCount = pending;
        render();
        currentUpload = new UploadTask(this, db, token);
        currentUpload.execute();
    }

    private void onUploadProgress(int done, int total) {
        pendingCount = total - done;
        uploadedCount = db.countByStatus(UploadDatabase.STATUS_UPLOADED);
        failedCount = db.countByStatus(UploadDatabase.STATUS_FAILED);
        if (currentState == STATE_UPLOADING) {
            scanOutput.setLength(0);
            scanOutput.append(getString(R.string.upload_progress_fmt, done, total));
        }
        render();
    }

    private void onUploadDone(int uploaded, int failed, boolean stoppedByUser, boolean paused, String lastFailure) {
        SyncState.manualRunActive = false;
        currentUpload = null;
        if (paused) {
            currentState = STATE_PAUSED;
        } else {
            currentState = STATE_STOPPED;
        }
        pendingCount = db.countPending();
        uploadedCount = db.countByStatus(UploadDatabase.STATUS_UPLOADED);
        failedCount = db.countByStatus(UploadDatabase.STATUS_FAILED);
        scanOutput.setLength(0);
        if (paused) {
            scanOutput.append(getString(R.string.upload_paused_fmt,
                    pendingCount, uploaded, failed));
        } else if (stoppedByUser) {
            scanOutput.append(getString(R.string.upload_stopped));
        } else {
            scanOutput.append(getString(R.string.upload_done_fmt, uploaded, failed));
        }
        if (lastFailure != null) {
            scanOutput.append("\n").append(lastFailure);
        }
        render();
    }

    @SuppressWarnings("deprecation")
    private static class UploadTask extends AsyncTask<Void, int[], PhotoUploader.Result> {
        private final WeakReference<MainActivity> activityRef;
        private final UploadDatabase db;
        private final String token;

        UploadTask(MainActivity activity, UploadDatabase db, String token) {
            this.activityRef = new WeakReference<MainActivity>(activity);
            this.db = db;
            this.token = token;
        }

        @Override
        protected PhotoUploader.Result doInBackground(Void... params) {
            PhotoUploader uploader = new PhotoUploader(db, token);
            return uploader.run(new PhotoUploader.StopCheck() {
                @Override public boolean shouldStop() {
                    MainActivity a = activityRef.get();
                    return a == null || a.uploadStopped || a.uploadPaused;
                }
            }, new PhotoUploader.Listener() {
                @Override public void onUploadProgress(int done, int total) {
                    publishProgress(new int[]{done, total});
                }
                @Override public void onUploadDone(PhotoUploader.Result r) { /* handled in onPostExecute */ }
            });
        }

        @Override
        protected void onProgressUpdate(int[]... values) {
            MainActivity activity = activityRef.get();
            if (activity == null) return;
            int[] v = values[0];
            activity.onUploadProgress(v[0], v[1]);
        }

        @Override
        protected void onPostExecute(PhotoUploader.Result r) {
            MainActivity activity = activityRef.get();
            if (activity == null) return;
            boolean paused = activity.uploadPaused;
            boolean stopped = activity.uploadStopped;
            activity.onUploadDone(r.uploaded, r.failed, stopped, paused, r.lastFailure);
        }
    }

    private void openSettings() {
        android.content.Intent i = new android.content.Intent(this, SettingsActivity.class);
        startActivity(i);
    }

}
