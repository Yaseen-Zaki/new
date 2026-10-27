package com.fondi.photosync;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;

public class DiagnosticsActivity extends Activity {

    private static final String PREFS_NAME = "fondi_prefs";

    private TextView output;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32, 32, 32, 32);

        TextView title = new TextView(this);
        title.setText(R.string.diag_title);
        title.setTextSize(20f);
        title.setPadding(0, 0, 0, 16);
        root.addView(title);

        Button refresh = new Button(this);
        refresh.setText(R.string.diag_refresh);
        refresh.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { render(); }
        });
        root.addView(refresh);

        ScrollView scroll = new ScrollView(this);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        scroll.setLayoutParams(sp);

        output = new TextView(this);
        output.setTextSize(12f);
        output.setPadding(0, 16, 0, 0);
        scroll.addView(output);
        root.addView(scroll);

        setContentView(root);
        render();
    }

    private void render() {
        StringBuilder b = new StringBuilder();

        // ---- App state ----
        b.append("App state\n");
        b.append("  service running: ").append(SyncState.serviceRunning).append("\n");
        b.append("  manual run active: ").append(SyncState.manualRunActive).append("\n");
        b.append("  alarm enabled: ").append(SyncScheduler.isEnabled(this)).append("\n");
        long lastAlarm = getSharedPreferences(PREFS_NAME, 0).getLong("last_alarm_ms", 0L);
        b.append("  last alarm: ").append(fmtTime(lastAlarm)).append("\n\n");

        // ---- Folder ----
        String folder = getSharedPreferences(PREFS_NAME, 0)
                .getString("selected_folder", null);
        b.append("Folder\n");
        if (folder == null || folder.length() == 0) {
            b.append("  (none selected)\n\n");
        } else {
            File f = new File(folder);
            boolean readable = f.isDirectory() && f.canRead();
            b.append("  ").append(folder).append("\n");
            b.append("  readable: ").append(readable).append("\n");
            int images = 0;
            if (readable) {
                File[] kids = f.listFiles();
                if (kids != null) {
                    for (File c : kids) {
                        if (c.isFile() && FolderBrowser.isImage(c.getName()) && c.length() > 0) {
                            images++;
                        }
                    }
                }
            }
            b.append("  images now: ").append(images).append("\n\n");
        }

        // ---- Queue ----
        UploadDatabase db = null;
        try {
            db = new UploadDatabase(this);
            b.append("Queue by status\n");
            b.append("  DISCOVERED: ").append(db.countByStatus(UploadDatabase.STATUS_DISCOVERED)).append("\n");
            b.append("  PENDING:    ").append(db.countByStatus(UploadDatabase.STATUS_PENDING)).append("\n");
            b.append("  UPLOADING:  ").append(db.countByStatus(UploadDatabase.STATUS_UPLOADING)).append("\n");
            b.append("  UPLOADED:   ").append(db.countByStatus(UploadDatabase.STATUS_UPLOADED)).append("\n");
            b.append("  RETRY:      ").append(db.countByStatus(UploadDatabase.STATUS_RETRY)).append("\n");
            b.append("  FAILED:     ").append(db.countByStatus(UploadDatabase.STATUS_FAILED)).append("\n\n");
        } finally {
            if (db != null) try { db.close(); } catch (Exception ignored) {}
        }

        // ---- Last scan ----
        long scanTime = getSharedPreferences(PREFS_NAME, 0).getLong("last_scan_time", 0L);
        int scanTotal = getSharedPreferences(PREFS_NAME, 0).getInt("last_scan_total", 0);
        b.append("Last scan\n");
        b.append("  total: ").append(scanTotal).append("\n");
        b.append("  at:    ").append(fmtTime(scanTime)).append("\n\n");

        // ---- Last upload ----
        long upTime = getSharedPreferences(PREFS_NAME, 0).getLong("last_upload_time", 0L);
        int upUploaded = getSharedPreferences(PREFS_NAME, 0).getInt("last_upload_uploaded", 0);
        int upFailed = getSharedPreferences(PREFS_NAME, 0).getInt("last_upload_failed", 0);
        String upFailure = getSharedPreferences(PREFS_NAME, 0).getString("last_upload_failure", null);
        b.append("Last upload\n");
        b.append("  uploaded: ").append(upUploaded).append("\n");
        b.append("  failed:   ").append(upFailed).append("\n");
        b.append("  at:       ").append(fmtTime(upTime)).append("\n");
        b.append("  last err: ").append(upFailure == null ? "(none)" : upFailure).append("\n");

        output.setText(b.toString());
    }

    private String fmtTime(long ms) {
        if (ms <= 0L) return "never";
        return android.text.format.DateFormat.getTimeFormat(this).format(new java.util.Date(ms));
    }
}
