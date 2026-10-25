package com.fondi.photosync;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

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

        Button chooseFolderButton = new Button(this);
        chooseFolderButton.setText(R.string.button_choose_folder);
        chooseFolderButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                // Phase 2b will open a directory browser here.
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
}