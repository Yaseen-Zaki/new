package com.fondi.photosync;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class UploadDatabase extends SQLiteOpenHelper {

    private static final String DB_NAME = "fondi.db";
    private static final int DB_VERSION = 1;

    public static final String TABLE_PHOTOS = "photos";
    public static final String TABLE_QUEUE  = "queue";

    // photos columns
    public static final String COL_ID         = "_id";
    public static final String COL_PATH       = "path";
    public static final String COL_SIZE       = "size";
    public static final String COL_MTIME      = "mtime";
    public static final String COL_SHA256     = "sha256";
    public static final String COL_FIRST_SEEN = "first_seen";
    public static final String COL_LAST_SEEN  = "last_seen";

    // queue columns
    public static final String COL_PHOTO_ID    = "photo_id";
    public static final String COL_STATUS      = "status";
    public static final String COL_ATTEMPTS    = "attempts";
    public static final String COL_LAST_ATTEMPT= "last_attempt";
    public static final String COL_LAST_ERROR  = "last_error";
    public static final String COL_REMOTE_KEY  = "remote_key";

    // queue status values
    public static final int STATUS_DISCOVERED = 0;
    public static final int STATUS_PENDING    = 1;
    public static final int STATUS_UPLOADING  = 2;
    public static final int STATUS_UPLOADED   = 3;
    public static final int STATUS_RETRY      = 4;
    public static final int STATUS_FAILED     = 5;

    public UploadDatabase(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(
                "CREATE TABLE " + TABLE_PHOTOS + " (" +
                COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_PATH + " TEXT NOT NULL, " +
                COL_SIZE + " INTEGER NOT NULL, " +
                COL_MTIME + " INTEGER NOT NULL, " +
                COL_SHA256 + " TEXT, " +
                COL_FIRST_SEEN + " INTEGER NOT NULL, " +
                COL_LAST_SEEN + " INTEGER NOT NULL, " +
                "UNIQUE(" + COL_PATH + ")" +
                ")"
        );
        db.execSQL(
                "CREATE TABLE " + TABLE_QUEUE + " (" +
                COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_PHOTO_ID + " INTEGER NOT NULL, " +
                COL_STATUS + " INTEGER NOT NULL, " +
                COL_ATTEMPTS + " INTEGER NOT NULL DEFAULT 0, " +
                COL_LAST_ATTEMPT + " INTEGER, " +
                COL_LAST_ERROR + " TEXT, " +
                COL_REMOTE_KEY + " TEXT, " +
                "UNIQUE(" + COL_PHOTO_ID + "), " +
                "FOREIGN KEY(" + COL_PHOTO_ID + ") REFERENCES " + TABLE_PHOTOS + "(" + COL_ID + ") ON DELETE CASCADE" +
                ")"
        );
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // No migrations yet. Version 1 is the first schema.
        // A future version must ALTER or migrate; never DROP user data silently.
    }

    /** Summary returned to the caller after a scan pass. */
    public static class ScanSummary {
        public int discoveredNew;   // newly inserted into photos
        public int seenAgain;       // already present, unchanged
        public int modified;        // same path, different size/mtime
        public int skippedUnreadable;
    }

    /**
     * Records a single discovered file. Must be called on a background thread.
     * Returns:
     *   0 = new row (also inserted into queue as DISCOVERED)
     *   1 = seen again, unchanged
     *   2 = path exists but size/mtime differ (sha256 cleared)
     *   3 = unreadable, ignored
     */
    public synchronized int recordFile(File file) {
        if (file == null || !file.isFile() || !file.canRead()) {
            return 3;
        }
        long size = file.length();
        if (size <= 0) {
            return 3;
        }
        long mtime = file.lastModified();
        long now = System.currentTimeMillis();
        String path = file.getAbsolutePath();

        SQLiteDatabase db = getWritableDatabase();
        Cursor c = null;
        try {
            c = db.query(TABLE_PHOTOS,
                    new String[]{COL_ID, COL_SIZE, COL_MTIME},
                    COL_PATH + "=?",
                    new String[]{path},
                    null, null, null, "1");
            if (c.moveToFirst()) {
                long id = c.getLong(0);
                long oldSize = c.getLong(1);
                long oldMtime = c.getLong(2);
                boolean changed = (oldSize != size) || (oldMtime != mtime);

                ContentValues update = new ContentValues();
                update.put(COL_LAST_SEEN, now);
                update.put(COL_SIZE, size);
                update.put(COL_MTIME, mtime);
                if (changed) {
                    update.putNull(COL_SHA256);
                }
                db.update(TABLE_PHOTOS, update, COL_ID + "=?", new String[]{String.valueOf(id)});

                if (changed) {
                    // Option A: re-promote the queue row so the modified file is uploaded again.
                    long qid = queueIdForPhoto(id);
                    if (qid > 0) {
                        markPending(qid);
                    }
                    return 2;
                }
                return 1;
            }

            ContentValues insert = new ContentValues();
            insert.put(COL_PATH, path);
            insert.put(COL_SIZE, size);
            insert.put(COL_MTIME, mtime);
            insert.put(COL_FIRST_SEEN, now);
            insert.put(COL_LAST_SEEN, now);
            long photoId = db.insert(TABLE_PHOTOS, null, insert);
            if (photoId < 0) {
                return 3;
            }

            ContentValues q = new ContentValues();
            q.put(COL_PHOTO_ID, photoId);
            q.put(COL_STATUS, STATUS_DISCOVERED);
            db.insert(TABLE_QUEUE, null, q);

            return 0;
        } finally {
            if (c != null) c.close();
        }
    }

    /** Counts queue entries by status. Used by the UI to show totals. */
    public synchronized int countByStatus(int status) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = null;
        try {
            c = db.rawQuery(
                    "SELECT COUNT(*) FROM " + TABLE_QUEUE + " WHERE " + COL_STATUS + "=?",
                    new String[]{String.valueOf(status)});
            if (c.moveToFirst()) return c.getInt(0);
            return 0;
        } finally {
            if (c != null) c.close();
        }
    }

    public synchronized int countPhotos() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = null;
        try {
            c = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_PHOTOS, null);
            if (c.moveToFirst()) return c.getInt(0);
            return 0;
        } finally {
            if (c != null) c.close();
        }
    }

    /** For debugging only: returns paths in queue order. Use sparingly. */
    public synchronized List<String> firstQueuedPaths(int limit) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = null;
        List<String> out = new ArrayList<String>();
        try {
            c = db.rawQuery(
                    "SELECT p." + COL_PATH + " FROM " + TABLE_QUEUE + " q " +
                    "JOIN " + TABLE_PHOTOS + " p ON p." + COL_ID + " = q." + COL_PHOTO_ID + " " +
                    "ORDER BY q." + COL_ID + " ASC LIMIT ?",
                    new String[]{String.valueOf(limit)});
            while (c.moveToNext()) {
                out.add(c.getString(0));
            }
            return out;
        } finally {
            if (c != null) c.close();
        }
    }

    /** Moves every DISCOVERED row to PENDING. Returns row count updated. */
    public synchronized int promoteDiscoveredToPending() {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues v = new ContentValues();
        v.put(COL_STATUS, STATUS_PENDING);
        return db.update(TABLE_QUEUE, v,
                COL_STATUS + "=?", new String[]{String.valueOf(STATUS_DISCOVERED)});
    }

    /** Re-promotes a UPLOADED row for a modified file back to PENDING. Returns true if changed. */
    public synchronized boolean markPending(long queueId) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues v = new ContentValues();
        v.put(COL_STATUS, STATUS_PENDING);
        v.putNull(COL_REMOTE_KEY);
        v.putNull(COL_LAST_ERROR);
        int n = db.update(TABLE_QUEUE, v, COL_ID + "=?", new String[]{String.valueOf(queueId)});
        return n > 0;
    }

    /** Number of PENDING rows. */
    public synchronized int countPending() {
        return countByStatus(STATUS_PENDING);
    }

    /** Returns the queue row id for a given photo id, or -1 if none. */
    public synchronized long queueIdForPhoto(long photoId) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = null;
        try {
            c = db.query(TABLE_QUEUE, new String[]{COL_ID},
                    COL_PHOTO_ID + "=?", new String[]{String.valueOf(photoId)},
                    null, null, null, "1");
            if (c.moveToFirst()) return c.getLong(0);
            return -1L;
        } finally {
            if (c != null) c.close();
        }
    }

    /** One queue entry ready to upload. */
    public static final class QueueItem {
        public long queueId;
        public long photoId;
        public String path;
    }

    /** Fetches the first PENDING row as a QueueItem, or null if empty. */
    public synchronized QueueItem takeNextPending() {
        SQLiteDatabase db = getWritableDatabase();
        Cursor c = null;
        try {
            c = db.rawQuery(
                    "SELECT q." + COL_ID + ", p." + COL_ID + ", p." + COL_PATH +
                    " FROM " + TABLE_QUEUE + " q JOIN " + TABLE_PHOTOS + " p " +
                    "ON p." + COL_ID + " = q." + COL_PHOTO_ID +
                    " WHERE q." + COL_STATUS + "=? ORDER BY q." + COL_ID + " ASC LIMIT 1",
                    new String[]{String.valueOf(STATUS_PENDING)});
            if (!c.moveToFirst()) return null;
            QueueItem item = new QueueItem();
            item.queueId = c.getLong(0);
            item.photoId = c.getLong(1);
            item.path = c.getString(2);
            return item;
        } finally {
            if (c != null) c.close();
        }
    }

    /** Marks a queue row UPLOADING only if it was PENDING. Returns true on successful transition. */
    public synchronized boolean markUploading(long queueId) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues v = new ContentValues();
        v.put(COL_STATUS, STATUS_UPLOADING);
        v.put(COL_LAST_ATTEMPT, System.currentTimeMillis());
        int n = db.update(TABLE_QUEUE, v,
                COL_ID + "=? AND " + COL_STATUS + "=?",
                new String[]{String.valueOf(queueId), String.valueOf(STATUS_PENDING)});
        return n > 0;
    }

    /** Marks a row UPLOADED. Records the server id and clears the last error. */
    public synchronized void markUploaded(long queueId, String remoteKey) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues v = new ContentValues();
        v.put(COL_STATUS, STATUS_UPLOADED);
        v.put(COL_REMOTE_KEY, remoteKey);
        v.putNull(COL_LAST_ERROR);
        db.update(TABLE_QUEUE, v, COL_ID + "=?", new String[]{String.valueOf(queueId)});
    }

    /** Marks a row RETRY, increments attempts, stores the error. */
    public synchronized void markRetry(long queueId, String message) {
        SQLiteDatabase db = getWritableDatabase();
        db.execSQL(
                "UPDATE " + TABLE_QUEUE + " SET " +
                COL_STATUS + "=?, " +
                COL_ATTEMPTS + "=" + COL_ATTEMPTS + "+1, " +
                COL_LAST_ERROR + "=? WHERE " + COL_ID + "=?",
                new Object[]{STATUS_RETRY, message, queueId});
    }

    /** Marks a row FAILED, increments attempts, stores the error. */
    public synchronized void markFailed(long queueId, String message) {
        SQLiteDatabase db = getWritableDatabase();
        db.execSQL(
                "UPDATE " + TABLE_QUEUE + " SET " +
                COL_STATUS + "=?, " +
                COL_ATTEMPTS + "=" + COL_ATTEMPTS + "+1, " +
                COL_LAST_ERROR + "=? WHERE " + COL_ID + "=?",
                new Object[]{STATUS_FAILED, message, queueId});
    }

}
