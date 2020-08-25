package com.exzell.exzlvideoplayer.utils;

import android.os.Handler;

import java.io.File;
import java.lang.ref.WeakReference;

public class ThumbnailUtils {
    private static ThumbnailUtils sThumb;
    private String thumbnailDir;

    private ThumbnailUtils() {
    }

    public static ThumbnailUtils getUtils() {
        if (sThumb == null) sThumb = new ThumbnailUtils();
        return sThumb;
    }

    public String getThumbnailDir() {
        return thumbnailDir;
    }

    public void setThumbnailDir(String dir) {
        thumbnailDir = dir;
    }

    public void removeThumbnail(Handler hand) {

        new Thread(new Runnable() {
            @Override
            public void run() {

                try{
                    Thread.sleep(10000);
                }catch(InterruptedException ie){ie.printStackTrace();}
                File f = new File(thumbnailDir);
                if (!f.exists()) return;
                MediaUtils.deleteFile(null, thumbnailDir);
                thumbnailDir = null;
            }
        });
    }
}
