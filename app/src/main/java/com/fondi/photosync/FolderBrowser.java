package com.fondi.photosync;

import android.os.Environment;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class FolderBrowser {

    /** Returns plausible starting roots, filtered to those that exist and are readable. */
    public static List<File> candidateRoots() {
        String external = Environment.getExternalStorageDirectory() != null
                ? Environment.getExternalStorageDirectory().getAbsolutePath()
                : null;
        String legacy = "/mnt/" + "sdcard";
        java.util.List<String> pathsList = new ArrayList<String>();
        if (external != null) {
            pathsList.add(external);
            pathsList.add(external + "/DCIM");
            pathsList.add(external + "/Pictures");
        }
        pathsList.add(legacy);
        pathsList.add("/storage/" + "sdcard0");
        pathsList.add("/storage/" + "emulated/0");
        String[] paths = pathsList.toArray(new String[pathsList.size()]);
        List<File> roots = new ArrayList<File>();
        for (String p : paths) {
            File f = new File(p);
            if (f.isDirectory() && f.canRead()) {
                roots.add(f);
            }
        }
        return roots;
    }

    /** Direct subdirectories of `dir`, alphabetical, readable, non-hidden. Never null. */
    public static List<File> subdirectories(File dir) {
        List<File> out = new ArrayList<File>();
        if (dir == null || !dir.isDirectory() || !dir.canRead()) {
            return out;
        }
        File[] children = dir.listFiles();
        if (children == null) {
            return out;
        }
        for (File c : children) {
            if (c.isDirectory() && c.canRead() && !c.getName().startsWith(".")) {
                out.add(c);
            }
        }
        Collections.sort(out, new Comparator<File>() {
            @Override public int compare(File a, File b) {
                return a.getName().compareToIgnoreCase(b.getName());
            }
        });
        return out;
    }

    /** How many JPEG/PNG files sit directly in `dir` (non-recursive). For preview. */
    public static int countImages(File dir) {
        if (dir == null || !dir.isDirectory() || !dir.canRead()) return 0;
        File[] children = dir.listFiles();
        if (children == null) return 0;
        int n = 0;
        for (File c : children) {
            if (c.isFile() && isImage(c.getName())) n++;
        }
        return n;
    }

    public static boolean isImage(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png");
    }
}