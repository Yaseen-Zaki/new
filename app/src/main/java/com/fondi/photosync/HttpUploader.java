package com.fondi.photosync;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Uploads a single photo to the Worker. Contains no UI, no DB, no state.
 * Every public method is pure: given inputs, returns outputs, and never throws
 * an unchecked exception across its boundary.
 */
public final class HttpUploader {

    public static final String ENDPOINT =
            "https://restless-fire-7680.yaseenzaki99.workers.dev/upload";
    public static final long MAX_UPLOAD_BYTES = 10L * 1024L * 1024L;
    private static final int CONNECT_TIMEOUT_MS = 20000;
    private static final int READ_TIMEOUT_MS = 60000;

    public static final class Result {
        public int httpCode;            // 0 if we never reached the server
        public String rawBody = "";     // server's response body, truncated for display
        public String error;            // non-null only when httpCode == 0
        public boolean ok;              // true for 200 or 201
        public boolean retryable;       // true for transient failures (5xx, IO, timeout)
        public boolean duplicate;       // value of "duplicate" when ok
        public String serverId;         // value of "id" when ok
    }

    private HttpUploader() {}

    public static Result upload(String token, File file) {
        Result r = new Result();
        if (token == null || token.length() == 0) {
            r.error = "no_token";
            return r;
        }
        if (file == null || !file.isFile() || !file.canRead()) {
            r.error = "unreadable";
            return r;
        }
        long size = file.length();
        if (size <= 0) {
            r.error = "empty_file";
            return r;
        }
        if (size > MAX_UPLOAD_BYTES) {
            r.error = "too_large";
            return r;
        }
        String contentType = detectContentType(file);
        if (contentType == null) {
            r.error = "unsupported_type";
            return r;
        }
        String sha256;
        try {
            sha256 = sha256Hex(file);
        } catch (IOException e) {
            r.error = "hash_io: " + e.getMessage();
            return r;
        } catch (NoSuchAlgorithmException e) {
            r.error = "hash_algorithm_missing";
            return r;
        }

        HttpURLConnection conn = null;
        OutputStream out = null;
        InputStream in = null;
        BufferedReader reader = null;
        try {
            URL url = new URL(ENDPOINT);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setFixedLengthStreamingMode((int) size);
            conn.setRequestProperty("Authorization", "Bearer " + token);
            conn.setRequestProperty("Content-Type", contentType);
            conn.setRequestProperty("X-Content-SHA256", sha256);
            conn.setRequestProperty("Accept", "application/json");

            out = conn.getOutputStream();
            FileInputStream fis = new FileInputStream(file);
            try {
                byte[] buf = new byte[16384];
                int n;
                while ((n = fis.read(buf)) != -1) out.write(buf, 0, n);
                out.flush();
            } finally {
                try { fis.close(); } catch (IOException ignored) {}
            }

            int code = conn.getResponseCode();
            r.httpCode = code;
            in = (code >= 200 && code < 400) ? conn.getInputStream() : conn.getErrorStream();
            if (in != null) {
                reader = new BufferedReader(new InputStreamReader(in, "UTF-8"));
                StringBuilder sb = new StringBuilder();
                char[] buf = new char[1024];
                int n;
                while ((n = reader.read(buf)) != -1) {
                    sb.append(buf, 0, n);
                    if (sb.length() > 4096) break;
                }
                r.rawBody = sb.toString();
            }
            r.ok = (code == 200 || code == 201);
            r.retryable = (code >= 500) || (code == 0);
            if (r.ok) {
                r.duplicate = jsonBool(r.rawBody, "duplicate");
                r.serverId = jsonString(r.rawBody, "id");
            }
            return r;
        } catch (IOException e) {
            r.error = e.getClass().getSimpleName() + ": " + e.getMessage();
            r.retryable = true;
            return r;
        } finally {
            if (reader != null) { try { reader.close(); } catch (IOException ignored) {} }
            if (in != null)     { try { in.close(); }     catch (IOException ignored) {} }
            if (out != null)    { try { out.close(); }    catch (IOException ignored) {} }
            if (conn != null)   conn.disconnect();
        }
    }

    /** Returns "image/jpeg", "image/png", or null. */
    public static String detectContentType(File file) {
        InputStream in = null;
        try {
            in = new FileInputStream(file);
            byte[] b = new byte[4];
            int n = in.read(b);
            if (n < 4) return null;
            if ((b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) return "image/jpeg";
            if ((b[0] & 0xFF) == 0x89 && b[1] == 0x50 && b[2] == 0x4E && b[3] == 0x47) return "image/png";
            return null;
        } catch (IOException e) {
            return null;
        } finally {
            if (in != null) { try { in.close(); } catch (IOException ignored) {} }
        }
    }

    /** Streaming SHA-256 as lowercase hex. */
    public static String sha256Hex(File file) throws IOException, NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        FileInputStream fis = new FileInputStream(file);
        try {
            byte[] buf = new byte[16384];
            int n;
            while ((n = fis.read(buf)) != -1) md.update(buf, 0, n);
        } finally {
            try { fis.close(); } catch (IOException ignored) {}
        }
        byte[] d = md.digest();
        StringBuilder sb = new StringBuilder(64);
        for (byte b : d) sb.append(String.format("%02x", b & 0xFF));
        return sb.toString();
    }

    private static boolean jsonBool(String body, String key) {
        String needle = "\"" + key + "\"";
        int i = body.indexOf(needle);
        if (i < 0) return false;
        int c = body.indexOf(':', i + needle.length());
        if (c < 0) return false;
        return body.substring(c + 1).trim().startsWith("true");
    }

    private static String jsonString(String body, String key) {
        String needle = "\"" + key + "\"";
        int i = body.indexOf(needle);
        if (i < 0) return null;
        int c = body.indexOf(':', i + needle.length());
        if (c < 0) return null;
        int q1 = body.indexOf('"', c + 1);
        if (q1 < 0) return null;
        int q2 = body.indexOf('"', q1 + 1);
        if (q2 < 0) return null;
        return body.substring(q1 + 1, q2);
    }
}
