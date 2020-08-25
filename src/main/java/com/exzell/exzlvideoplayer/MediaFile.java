package com.exzell.exzlvideoplayer;

import android.graphics.Bitmap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.exzell.exzlvideoplayer.utils.MediaUtils;

import java.io.File;

public class MediaFile {

    private static File thumbDir;

    private String path;

    private String thumbnailPath;

    private long size;

    private String displayName;

    private boolean isDir;

    private int dateAdded;

    private long duration;

    private Bitmap thumbnail;


    public MediaFile(String filePath, int fileSize, int dateAdded) {
        path = filePath;
        size = fileSize;
        isDir = new File(filePath).isDirectory();
        displayName = new File(filePath).getName();
        thumbnailPath = new File(thumbDir, displayName).getPath();
        this.dateAdded = dateAdded;
//        if(!isDir) setDuration();
    }

    public MediaFile(String path) {
        this(path, 0, 0);
        size = MediaUtils.calculateTotalSize(path);
        if (!(new File(thumbnailPath).exists()) && !isDir)
            MediaUtils.loadThumbIntoCache(thumbDir, path, null);
    }

    public static void submitThumbnailDir(File dir) {
        thumbDir = dir;
    }

    public String getPath() {
        return path;
    }

    public String getDisplayName() {
        return displayName;
    }

    private void updateDisplayName(String newName) {
        displayName = newName;
        thumbnailPath = new File(thumbDir, newName).getPath();
    }

    public void updateFilePath(String newPath) {
        path = newPath;
        File newF = new File(newPath);
        updateDisplayName(newF.getName());
    }

    public Bitmap getThumbnail() {
        return thumbnail;
    }

    public void setThumbnail(Bitmap map) {
        thumbnail = map;
    }

    public boolean isDir() {
        return isDir;
    }

    public String getThumbnailPath() {
        return thumbnailPath;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public int getDateAdded() {
        return dateAdded;
    }

    public long getDuration() {
        return duration;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (!(obj instanceof MediaFile)) return false;

        MediaFile challenger = (MediaFile) obj;

        return path.equals(challenger.getPath());
    }

    @Override
    public int hashCode() {
        int hash = 0;
        for (int i = 0; i < path.length(); i++) {
            hash += path.charAt(i);
        }

        return hash;
    }

    @NonNull
    @Override
    public String toString() {
        return path;
    }

    public enum Sort {
        NAME,
        SIZE,
        DATE,
        DURATION


    }

}
