package com.exzell.exzlvideoplayer.utils;

import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;

import androidx.annotation.NonNull;

import com.exzell.exzlvideoplayer.MediaFile;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

import static com.exzell.exzlvideoplayer.MediaFile.*;

public class MediaUtils {


//    public static Bitmap getFileThumbnail(final File thumbDir, final String file, final int width, final int height, ExecutorService exe) {
//
//        Bitmap thumb = null;
//
//        try {
//            thumb =
//                    exe.submit(() -> {
//                        File name = new File(thumbDir, new File(file).getName() + ".png");
//                        Bitmap fullBitmap;
//
//
//                        BitmapFactory.Options op = new BitmapFactory.Options();
//                        op.inJustDecodeBounds = true;
//                        BitmapFactory.decodeFile(name.getAbsolutePath(), op);
//                        op.inSampleSize = determineSampleSize(op, width, height);
//                        op.inJustDecodeBounds = false;
//
//                        fullBitmap = BitmapFactory.decodeFile(name.getAbsolutePath(), op);
//
//                        return fullBitmap;
//                    }).get();
//        } catch (InterruptedException | ExecutionException e) {
//            e.printStackTrace();
//        }
//
//        return thumb;
//    }
//
//    private static int determineSampleSize(BitmapFactory.Options op, int reqWidth, int reqHeight) {
//        int outWidth = op.outWidth;
//        int outHeight = op.outHeight;
//        int inSampleSize = 1;
//
//        if (outHeight > reqHeight || outWidth > reqWidth) {
//
//            final int halfHeight = outHeight / 2;
//            final int halfWidth = outWidth / 2;
//
//            while ((halfHeight / inSampleSize) >= reqHeight
//                    && (halfWidth / inSampleSize) >= reqWidth) {
//                inSampleSize *= 2;
//            }
//        }
//
//        return inSampleSize;
//    }

    public static String loadThumbIntoCache(File cache, String path, String time) {

            MediaMetadataRetriever mMedia = new MediaMetadataRetriever();
            File name = new File(cache, new File(path).getName() + ".png");
            if (name.exists() && time == null) return name.getPath();

            try {
                mMedia.setDataSource(path);

                if(mMedia.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) != null){

                    if (time == null)
                        saveFrame(name, mMedia.getFrameAtTime(), false);

                    else saveFrame(name, mMedia.getFrameAtTime(timeInUs(time)), true);

                }else saveAudioArt(name, mMedia);
            } finally {
                mMedia.release();
            }

            return name.getPath();
    }

    private static void saveAudioArt(File savePath, MediaMetadataRetriever mMedia) {
//        if(savePath.exists()) return;

        savePath.delete();
        byte[] embeddedPicture = mMedia.getEmbeddedPicture();

        if(embeddedPicture == null) return;

        try{
            savePath.createNewFile();
            FileOutputStream stream = new FileOutputStream(savePath);
            stream.write(embeddedPicture);

        }catch(IOException ioe){ioe.printStackTrace();}
    }

    private static void saveFrame(final File name, final Bitmap frame, boolean newFrame) {
        if (frame == null || (name.exists() && !newFrame)) return;
        name.delete();

        try {

            name.createNewFile();
            FileOutputStream streamOut = new FileOutputStream(name);

//            exe.submit((Runnable) () -> {
            frame.compress(Bitmap.CompressFormat.PNG, 100, streamOut);

            streamOut.flush();
            streamOut.close();

//            });
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
    }

    public static boolean deleteFile(@NonNull Context res, MediaFile file) {

        Uri contentUri;
        if (file.getType().equals(Type.AUDIO)) {
            contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        } else {
            contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
        }


        boolean delete = new File(file.getPath()).delete();
        if (delete)
            res.getContentResolver().delete(contentUri, MediaStore.MediaColumns._ID + "=?", new String[]{String.valueOf(file.getId())});
        return delete;
    }

    public static boolean renameFile(@NonNull Context res, MediaFile file, String newName) {
        boolean successful;

        String path = file.getPath();

        String extension = path.substring(path.lastIndexOf("."));
        if (!newName.endsWith(extension)) newName.concat(extension);

        File pathFile = new File(path);

        File dest = new File(pathFile.getParent(), newName);

        successful = pathFile.renameTo(dest);
        if (successful) {
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.MediaColumns.DATA, dest.getPath());
            cv.put(MediaStore.MediaColumns.DISPLAY_NAME, dest.getName());
            long l = System.currentTimeMillis();
            cv.put(MediaStore.MediaColumns.DATE_MODIFIED, l);

            Uri contentUri;

            if (file.getType().equals(Type.VIDEO)) contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
            else contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;

            contentUri = contentUri.buildUpon().appendEncodedPath(String.valueOf(file.getId())).build();

            res.getContentResolver().update(contentUri, cv, MediaStore.MediaColumns._ID + "=?", new String[]{String.valueOf(file.getId())});
        }
        return successful;
    }

    public static void sort(final Sort sortKey, final Boolean ascend, final List<MediaFile> media, ExecutorService exe) {

        //TODO: Check out synchronized and see if it can be applied here
        exe.submit(() -> {
            if (sortKey != null) {

                Collections.sort(media, ((o1, o2) -> {
                    if (sortKey.equals(Sort.NAME)) {
                        int ret = 0;
                        String oo1 = o1.getDisplayName().toLowerCase();
                        String oo2 = o2.getDisplayName().toLowerCase();

                        for (int i = 0; i < oo1.length(); i++) {
                            if (i == oo2.length()) {
                                ret = 1;
                                break;
                            }

                            ret = oo1.substring(i, i + 1).compareTo(oo2.substring(i, i + 1));
                            if (ret != 0) break;
                        }

                        return ret;
                    } else if (sortKey.equals(Sort.SIZE)) {
                        long oo1 = o1.getSize();
                        long oo2 = o2.getSize();

                        return Long.compare(oo1, oo2);
                    } else if (sortKey.equals(Sort.DATE)) {
                        int oo1 = o1.getDateAdded();
                        int oo2 = o2.getDateAdded();

                        return Integer.compare(oo1, oo2);
                    } else if (sortKey.equals(Sort.DURATION)) {
                        long oo1 = o1.getDuration();
                        long oo2 = o2.getDuration();

                        return Long.compare(oo1, oo2);
                    } else {
                        return 0;
                    }
                }));
            }


            if (ascend == null) return;
            //get the first entry and the last entry
            //compare them accordingly and use that to determine if a reverse is needed
            MediaFile first = media.get(0);
            MediaFile last = media.get(media.size() - 1);
            int reverse = 0;

            switch (sortKey) {
                case NAME:
                    reverse = first.getDisplayName().compareTo(last.getDisplayName());
                    break;
                case SIZE:
                    reverse = Long.compare(first.getSize(), last.getSize());
                    break;
                case DATE:
                    reverse = Long.compare(first.getDateAdded(), last.getDateAdded());
                    break;
            }

            //if we are to ascend and the first is greater than the last
            if (ascend && reverse > 0) Collections.reverse(media);

            //if we are to descend and the first is smaller than the last
            else if (!ascend && reverse < 0) Collections.reverse(media);
        });
    }

    public static int[] microUsToTime(final long timeUs) {


                        int[] time1 = new int[3];
                        long secs = timeUs / 1000000;

                        time1[2] = (int) secs % 60;
                        time1[1] = (int) (secs / 60) % 60;
                        time1[0] = (int) (secs / 3600) % 24;

                        return time1;
    }

    public static long timeInUs(String time) {
        String[] times = Arrays.stream(time.split(":")).filter(p -> Character.isDigit(p.charAt(0))).toArray(String[]::new);
        long totalTime = 0;
        for (int i = 0; i < times.length - 1; i++) {
            if(times[i].trim().isEmpty()) continue;
            int tim = Integer.parseInt(times[i]);
            if (times.length == 3 && i == 0) tim *= 3600;
            else tim *= 60;

            totalTime += tim;
        }
        totalTime += Integer.parseInt(times[times.length - 1]);

        return totalTime * 1000000;
    }

    public static boolean isSameMedia(Context context, Uri uri, int id1, int id2){
        boolean same = true;
        String[] columnsToCompare = {MediaStore.MediaColumns.DATE_ADDED, MediaStore.MediaColumns.DURATION,
                MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.HEIGHT, MediaStore.MediaColumns.WIDTH};
        String selection = MediaStore.MediaColumns._ID + "=?s";

        ArrayList<Bundle> cols = CursorUtilsKt.queryMediaStore(context, uri, selection,
                new String[]{String.valueOf(id1), String.valueOf(id2)}, null, columnsToCompare);

        Bundle b1 = cols.get(0);
        Bundle b2 = cols.get(1);

        for (String s : columnsToCompare) {
            if (b1.getInt(s) != b2.getInt(s)) {
                same = false;
                break;
            }
        }

        return same;
    }

//    @Deprecated
//    public enum MediaOrigins {
//        AUDIO,
//        VIDEO,
//        AUDIOVIDEO,
//        NOTMEDIA
//    }
//
    //    public static long calculateTotalSize(final String path, ExecutorService exe) {
//        long size = -1;
//
//        try {
//            size =
//                    exe.submit(new Callable<Long>() {
//                        @Override
//                        public Long call() throws Exception {
//
//                            File f = new File(path);
//                            if (!f.exists()) return -1L;
//
//                            else if (f.isDirectory()) {
//                                long size = 0;
//                                File[] dirs = f.listFiles(new FileFilter() {
//                                    @Override
//                                    public boolean accept(File pathname) {
//                                        return pathname.isFile();
//                                    }
//                                });
//
//                                for (File fi : dirs) {
//                                    size += fi.length();
//                                }
//
//                                return size;
//                            } else {
//                                return f.length();
//                            }
//                        }
//                    }).get();
//        } catch (ExecutionException | CancellationException | InterruptedException e) {
//            e.printStackTrace();
//        }
//
//        return size;
//    }
//
//    public static void setMediaDuration(MediaFile file, ExecutorService exe) {
//        MediaMetadataRetriever ret = new MediaMetadataRetriever();
////
//        try {
////                        exe.submit(() -> {
//                            ret.setDataSource(file.getPath());
//                            String duration = ret.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
//                            ret.release();
//
//                            if(duration == null) return;
//                            file.setDuration(Long.parseLong(duration) * 1000);
////                        });
//
//            } finally {
//            ret.release();
//        }
////        dura = duration;
//    }
}
