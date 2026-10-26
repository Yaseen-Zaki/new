package com.fondi.photosync;

import java.io.File;

/**
 * Runs the upload loop against the DB queue. Synchronous; the caller runs it
 * on a background thread. No Activity reference. The caller implements StopCheck
 * (e.g. "is Pause pressed or service stopping?") so the loop exits promptly.
 */
public final class PhotoUploader {

    public interface StopCheck {
        /** Return true to stop the loop before the next row. */
        boolean shouldStop();
    }

    public interface Listener {
        void onUploadProgress(int done, int total);
        void onUploadDone(Result result);
    }

    public static final class Result {
        public int uploaded;
        public int failed;
        public boolean stopped;
        public String lastFailure;
    }

    private final UploadDatabase db;
    private final String token;

    public PhotoUploader(UploadDatabase db, String token) {
        this.db = db;
        this.token = token;
    }

    public Result run(StopCheck stop, Listener listener) {
        Result result = new Result();
        if (token == null || token.length() == 0) {
            result.lastFailure = "no_token";
            return result;
        }
        int total = db.countPending();
        int done = 0;
        int failed = 0;
        while (true) {
            if (stop != null && stop.shouldStop()) {
                result.stopped = true;
                break;
            }
            UploadDatabase.QueueItem item = db.takeNextPending();
            if (item == null) break;
            if (!db.markUploading(item.queueId)) continue;

            File f = new File(item.path);
            HttpUploader.Result r = HttpUploader.upload(token, f);
            if (r.ok) {
                db.markUploaded(item.queueId, r.serverId == null ? "" : r.serverId);
                done++;
            } else if (r.retryable) {
                db.markRetry(item.queueId, r.error);
                failed++;
                result.lastFailure = "Retry: " + r.error;
            } else {
                db.markFailed(item.queueId, r.error);
                failed++;
                result.lastFailure = "Failed: " + r.error;
            }
            if (listener != null) {
                listener.onUploadProgress(done, total);
            }
        }
        result.uploaded = done;
        result.failed = failed;
        return result;
    }
}
