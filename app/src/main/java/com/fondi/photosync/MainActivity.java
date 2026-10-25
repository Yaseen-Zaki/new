package com.fondi.photosync;

import android.app.Activity;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends Activity {

    // Public HTTPS endpoint with a valid certificate.
    // Replace with your own Cloudflare-hosted URL when ready.
    private static final String TEST_URL = "https://httpbin.org/get";

    private static final int CONNECT_TIMEOUT_MS = 15000;
    private static final int READ_TIMEOUT_MS = 15000;
    private static final int MAX_BODY_CHARS = 4096;

    private TextView statusView;
    private TextView responseView;
    private Button fetchButton;
    private FetchTask currentTask;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(48, 48, 48, 48);

        TextView title = new TextView(this);
        title.setText("Fondi Photo Sync");
        title.setTextSize(20f);
        root.addView(title);

        statusView = new TextView(this);
        statusView.setText("Idle.");
        statusView.setTextSize(16f);
        statusView.setPadding(0, 24, 0, 24);
        root.addView(statusView);

        fetchButton = new Button(this);
        fetchButton.setText("Fetch test URL");
        fetchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startFetch();
            }
        });
        root.addView(fetchButton);

        ScrollView scroll = new ScrollView(this);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
        );
        scroll.setLayoutParams(scrollParams);

        responseView = new TextView(this);
        responseView.setTextSize(12f);
        responseView.setText("");
        scroll.addView(responseView);

        root.addView(scroll);

        setContentView(root);
    }

    private void startFetch() {
        if (currentTask != null) {
            statusView.setText("A request is already in progress.");
            return;
        }
        fetchButton.setEnabled(false);
        statusView.setText("Requesting " + TEST_URL + " ...");
        responseView.setText("");
        currentTask = new FetchTask();
        currentTask.execute(TEST_URL);
    }

    private class FetchTask extends AsyncTask<String, Void, FetchResult> {

        @Override
        protected FetchResult doInBackground(String... params) {
            String urlString = params[0];
            FetchResult result = new FetchResult();
            HttpURLConnection conn = null;
            InputStream in = null;
            BufferedReader reader = null;
            try {
                URL url = new URL(urlString);
                conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
                conn.setReadTimeout(READ_TIMEOUT_MS);
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/json");
                conn.setInstanceFollowRedirects(true);

                int code = conn.getResponseCode();
                result.httpCode = code;
                result.httpMessage = conn.getResponseMessage();

                in = (code >= 200 && code < 400)
                        ? conn.getInputStream()
                        : conn.getErrorStream();

                if (in != null) {
                    reader = new BufferedReader(new InputStreamReader(in, "UTF-8"));
                    StringBuilder sb = new StringBuilder();
                    char[] buf = new char[1024];
                    int read;
                    while ((read = reader.read(buf)) != -1) {
                        sb.append(buf, 0, read);
                        if (sb.length() >= MAX_BODY_CHARS) {
                            sb.append("\n... [truncated]");
                            break;
                        }
                    }
                    result.body = sb.toString();
                } else {
                    result.body = "(empty response body)";
                }
            } catch (IOException e) {
                result.error = e.getClass().getSimpleName() + ": " + e.getMessage();
            } catch (Exception e) {
                result.error = e.getClass().getSimpleName() + ": " + e.getMessage();
            } finally {
                if (reader != null) {
                    try { reader.close(); } catch (IOException ignored) { }
                }
                if (in != null) {
                    try { in.close(); } catch (IOException ignored) { }
                }
                if (conn != null) {
                    conn.disconnect();
                }
            }
            return result;
        }

        @Override
        protected void onPostExecute(FetchResult result) {
            currentTask = null;
            fetchButton.setEnabled(true);

            if (result.error != null) {
                statusView.setText("Request failed: " + result.error);
                responseView.setText("");
                return;
            }

            statusView.setText("HTTP " + result.httpCode
                    + " " + (result.httpMessage != null ? result.httpMessage : ""));
            responseView.setText(result.body != null ? result.body : "");
        }
    }

    private static class FetchResult {
        int httpCode;
        String httpMessage;
        String body;
        String error;
    }
}
