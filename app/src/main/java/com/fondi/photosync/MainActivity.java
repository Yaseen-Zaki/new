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
    private static final int MAX_NAMES_SHOWN = 50;

    private TextView statusView;
    private TextView countersView;
    private TextView folderView;
    private TextView lastErrorView;
    private Button startButton;
    private Button pauseButton;
    private Button stopButton;
    private Button chooseFolderButton;

    private int currentState = STATE_STOPPED;
    private int pendingCount = 0;
    private int uploadedCount = 0;
    private int failedCount = 0;
    private String lastError = null;
    private String selectedFolder = null;
    private ScanTask currentScan = null;
    private StringBuilder scanOutput = new StringBuilder();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        loadPreferences();

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

    private void onStartClicked() {
        if (selectedFolder == null) {
            lastError = "Choose a folder before starting.";
            render();
            return;
        }
        if (currentScan != null) {
            return;
        }
        currentState = STATE_SCANNING;
        lastError = null;
        scanOutput.setLength(0);
        scanOutput.append(getString(R.string.scan_started));
        render();
        currentScan = new ScanTask(this, selectedFolder);
        currentScan.execute();
    }

    private void onPauseClicked() {
        if (currentState == STATE_SCANNING || currentState == STATE_UPLOADING) {
            currentState = STATE_PAUSED;
            render();
        }
    }

    private void onStopClicked() {
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

        startButton.setEnabled(currentState == STATE_STOPPED || currentState == STATE_PAUSED);
        pauseButton.setEnabled(currentState == STATE_SCANNING || currentState == STATE_UPLOADING);
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
    private static class ScanTask extends AsyncTask<Void, Void, ScanResult> {
        private final WeakReference<MainActivity> activityRef;
        private final String folderPath;

        ScanTask(MainActivity activity, String folderPath) {
            this.activityRef = new WeakReference<MainActivity>(activity);
            this.folderPath = folderPath;
        }

        @Override
        protected ScanResult doInBackground(Void... params) {
            ScanResult result = new ScanResult();
            File dir = new File(folderPath);
            if (!dir.isDirectory() || !dir.canRead()) {
                result.error = "Folder is not readable: " + folderPath;
                return result;
            }
            File[] children = dir.listFiles();
            if (children == null) {
                result.error = "Folder listing returned null.";
                return result;
            }
            List<String> names = new ArrayList<String>();
            int total = 0;
            for (File c : children) {
                if (!c.isFile()) continue;
                if (!FolderBrowser.isImage(c.getName())) continue;
                long size = c.length();
                if (size <= 0) continue;
                total++;
                if (names.size() < MAX_NAMES_SHOWN) {
                    names.add(c.getName() + "  (" + size + " B)");
                }
            }
            Collections.sort(names);
            result.total = total;
            result.shown = names;
            return result;
        }

        @Override
        protected void onPostExecute(ScanResult result) {
            MainActivity activity = activityRef.get();
            if (activity == null) return;
            activity.finishScan(result);
        }
    }

    private static class ScanResult {
        int total;
        List<String> shown;
        String error;
    }

    // Called from ScanTask.onPostExecute via WeakReference. Runs on the UI thread.
    private void finishScan(ScanResult result) {
        currentScan = null;
        currentState = STATE_STOPPED;
        if (result.error != null) {
            lastError = getString(R.string.scan_failed, result.error);
            scanOutput.setLength(0);
        } else {
            lastError = null;
            scanOutput.setLength(0);
            scanOutput.append(getResources().getQuantityString(
                    R.plurals.scan_result, result.total, result.total, result.shown.size()))
                    .append("\n\n");
            for (String name : result.shown) {
                scanOutput.append(name).append("\n");
            }
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .edit()
                    .putInt(KEY_SCAN_TOTAL, result.total)
                    .putLong(KEY_SCAN_TIME, System.currentTimeMillis())
                    .apply();
        }
        render();
    }
}
