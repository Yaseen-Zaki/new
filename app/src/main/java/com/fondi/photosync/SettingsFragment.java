package com.fondi.photosync;

import android.content.SharedPreferences;
import android.text.InputType;
import android.os.Bundle;
import android.widget.EditText;
import android.text.TextUtils;

import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

public class SettingsFragment extends PreferenceFragmentCompat
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String PREFS_NAME = "fondi_prefs";

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences, rootKey);
    }

    @Override
    public void onResume() {
        super.onResume();
        PreferenceManager.getDefaultSharedPreferences(requireContext())
                .registerOnSharedPreferenceChangeListener(this);
        refresh();
    }

    @Override
    public void onPause() {
        super.onPause();
        PreferenceManager.getDefaultSharedPreferences(requireContext())
                .unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        if ("interval_ms".equals(key)) {
            EditTextPreference ep = findPreference(key);
            if (ep != null) {
                long ms = parseSecondsToMs(ep.getText());
                // Persist as long so SyncScheduler reads it unchanged.
                requireContext().getSharedPreferences(PREFS_NAME, 0)
                        .edit().putLong("interval_ms", ms).apply();
                ep.setSummary(getString(R.string.pref_interval_summary_fmt, ms / 1000L));
            }
            SyncScheduler.disable(requireContext());
            SyncScheduler.enable(requireContext());
        }
        if ("worker_token".equals(key)) {
            EditTextPreference tp = findPreference(key);
            if (tp != null) {
                String stripped = stripQuotesAndWhitespace(tp.getText());
                requireContext().getSharedPreferences(PREFS_NAME, 0)
                        .edit().putString("worker_token", stripped).apply();
                tp.setSummary(TextUtils.isEmpty(stripped)
                        ? getString(R.string.pref_token_summary) : "(token set)");
            }
        }
        refresh();
    }

    private void refresh() {
        // interval summary (stored as long, displayed as seconds)
        EditTextPreference ep = findPreference("interval_ms");
        if (ep != null) {
            long ms = requireContext().getSharedPreferences(PREFS_NAME, 0)
                    .getLong("interval_ms", SyncConfig.SCAN_INTERVAL_MS);
            ep.setSummary(getString(R.string.pref_interval_summary_fmt, ms / 1000L));
            // Seed the EditText text so the dialog opens with the current value.
            if (ep.getText() == null || ep.getText().length() == 0) {
                ep.setText(String.valueOf(ms / 1000L));
            }
        }

        // token summary
        EditTextPreference tp = findPreference("worker_token");
        if (tp != null) {
            String v = tp.getText();
            tp.setSummary(TextUtils.isEmpty(v) ? getString(R.string.pref_token_summary) : "(token set)");
        }

        // folder summary
        Preference folderPref = findPreference("folder_display");
        if (folderPref != null) {
            String folder = requireContext().getSharedPreferences(PREFS_NAME, 0)
                    .getString("selected_folder", null);
            if (TextUtils.isEmpty(folder)) {
                folderPref.setSummary(R.string.pref_folder_none);
            } else {
                int slash = folder.lastIndexOf('/');
                String shown = slash >= 0 && slash < folder.length() - 1
                        ? folder.substring(slash + 1) : folder;
                folderPref.setSummary(folder.length() > 40 ? "..." + shown : folder);
            }
        }

        // alarm status
        Preference statusPref = findPreference("sync_status");
        if (statusPref != null) {
            boolean on = SyncScheduler.isEnabled(requireContext());
            long last = requireContext().getSharedPreferences(PREFS_NAME, 0)
                    .getLong("last_alarm_ms", 0L);
            String lastText = last == 0L
                    ? getString(R.string.pref_alarm_never)
                    : android.text.format.DateFormat.getTimeFormat(requireContext())
                            .format(new java.util.Date(last));
            statusPref.setSummary(getString(R.string.pref_status_fmt,
                    getString(on ? R.string.pref_alarm_on : R.string.pref_alarm_off),
                    lastText));
        }
    }

    /** Parses user-entered seconds, clamps to >= 15 s, returns milliseconds. */
    private long parseSecondsToMs(String raw) {
        long seconds = 60L; // fallback if parse fails
        try {
            if (raw != null) {
                String s = raw.trim();
                if (s.length() > 0) seconds = Long.parseLong(s);
            }
        } catch (NumberFormatException ignored) {}
        if (seconds < 15L) seconds = 15L;
        return seconds * 1000L;
    }

    /** Mirrors the old MainActivity.saveToken() stripping so the token
        stored from Settings behaves identically to the old dialog path. */
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

    @Override
    public void onViewCreated(android.view.View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        EditTextPreference ep = findPreference("interval_ms");
        if (ep != null) {
            ep.setOnBindEditTextListener(new androidx.preference.EditTextPreference.OnBindEditTextListener() {
                @Override public void onBindEditText(EditText editText) {
                    editText.setInputType(InputType.TYPE_CLASS_NUMBER);
                }
            });
        }
    }

}
