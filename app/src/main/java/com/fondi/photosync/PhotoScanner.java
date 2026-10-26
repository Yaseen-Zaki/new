package com.fondi.photosync;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Scans a folder for JPEG/PNG files and records them in the DB.
 * Contains no UI, no Activity reference, and no threading. The caller
 * decides where to run it (AsyncTask, service thread, etc).
 */
public final class PhotoScanner {

    public interface Listener {
        void onScanProgress(int filesSeen);
        void onScanDone(Result result);
    }

    public static final class Result {
        public int total;
        public int newlyDiscovered;
        public int seenAgain;
        public int modified;
        public int skippedUnreadable;
        public List<String> shownNames = new ArrayList<String>();
        public String error;
    }

    private final UploadDatabase db;
    private final int maxNamesShown;

    public PhotoScanner(UploadDatabase db, int maxNamesShown) {
        this.db = db;
        this.maxNamesShown = maxNamesShown;
    }

    /** Synchronous. Caller runs it on a background thread. */
    public Result scan(String folderPath, Listener listener) {
        Result result = new Result();
        if (folderPath == null) {
            result.error = "no_folder";
            return result;
        }
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
        int newly = 0;
        int seen = 0;
        int modified = 0;
        int skipped = 0;
        for (File c : children) {
            if (!c.isFile()) continue;
            if (!FolderBrowser.isImage(c.getName())) continue;
            long size = c.length();
            if (size <= 0) { skipped++; continue; }
            total++;
            int r = db.recordFile(c);
            if (r == 0) newly++;
            else if (r == 1) seen++;
            else if (r == 2) modified++;
            else skipped++;
            if (names.size() < maxNamesShown) {
                names.add(c.getName() + "  (" + size + " B)");
            }
            if (listener != null && (total % 25) == 0) {
                listener.onScanProgress(total);
            }
        }
        Collections.sort(names);
        result.total = total;
        result.newlyDiscovered = newly;
        result.seenAgain = seen;
        result.modified = modified;
        result.skippedUnreadable = skipped;
        result.shownNames = names;
        return result;
    }
}
