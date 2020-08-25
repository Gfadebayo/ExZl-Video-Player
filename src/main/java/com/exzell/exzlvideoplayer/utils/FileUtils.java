package com.exzell.exzlvideoplayer.utils;

import android.annotation.SuppressLint;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FileUtils {

    private static final ExecutorService mExecutor = Executors.newCachedThreadPool();

    @SuppressLint("DefaultLocale")
    public static String sizeInMb(final long size) {
        String siz = null;

        try {
            siz =
                    mExecutor.submit(new Callable<String>() {
                        @Override
                        public String call() {
                            float integer = (float) size;
                            //size in kb
                            float mbSize = integer / 1000;

                            if (mbSize < 1024) return String.format("%3dKB", (int) mbSize);

                            else if (mbSize >= 1024 && mbSize < (1000 * 1000))
                                return String.format("%.2fMB", (mbSize / 1024));

                            else return String.format("%.2fGB", mbSize / (1024 * 1024));
                        }
                    }).get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }

        return siz;
    }
}
