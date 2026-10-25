package com.fondi.photosync;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;

public class MainActivity extends Activity {

    private static final int STATE_STOPPED = 0;
    private static final int STATE_SCANNING = 1;
    private static final int STATE_UPLOADING = 2;
    private static final int STATE_PAUSED = 3;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

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
        currentState = STATE_SCANNING;
        render();
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
        render();
    }
}
