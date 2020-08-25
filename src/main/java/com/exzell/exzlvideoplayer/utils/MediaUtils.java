package com.exzell.exzlvideoplayer.utils;

import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.provider.MediaStore;

import com.exzell.exzlvideoplayer.MediaFile;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MediaUtils {

    private static final ExecutorService mExecutor = Executors.newCachedThreadPool();


    public static Bitmap getFileThumbnail(final File thumbDir, final String file, final int width, final int height) {

        Bitmap thumb = null;

        try {
            thumb =
                    mExecutor.submit(new Callable<Bitmap>() {
                        @Override
                        public Bitmap call() throws Exception {
                            File name = new File(thumbDir, new File(file).getName() + ".png");
                            Bitmap fullBitmap;


                            BitmapFactory.Options op = new BitmapFactory.Options();
                            op.inJustDecodeBounds = true;
                            BitmapFactory.decodeFile(name.getAbsolutePath(), op);
                            op.inSampleSize = determineSampleSize(op, width, height);
                            op.inJustDecodeBounds = false;

                            fullBitmap = BitmapFactory.decodeFile(name.getAbsolutePath(), op);

                            return fullBitmap;
                        }
                    }).get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }

        return thumb;
    }

    private static int determineSampleSize(BitmapFactory.Options op, int reqWidth, int reqHeight) {
        int outWidth = op.outWidth;
        int outHeight = op.outHeight;
        int inSampleSize = 1;

        if (outHeight > reqHeight || outWidth > reqWidth) {

            final int halfHeight = outHeight / 2;
            final int halfWidth = outWidth / 2;

            while ((halfHeight / inSampleSize) >= reqHeight
                    && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }

        return inSampleSize;
    }

    public static void loadThumbIntoCache(File cache, String path, String time) {
        MediaMetadataRetriever mMedia = new MediaMetadataRetriever();
        File name = new File(cache, new File(path).getName() + ".png");
        if (name.exists() && time == null) return;
        Bitmap fullBitmap;
        boolean newBit = false;

        try {
            mMedia.setDataSource(path);

            if (time == null)
                fullBitmap = mMedia.getFrameAtTime(); // mMedia.getFrameAtTime(0, MediaMetadataRetriever.OPTION_PREVIOUS_SYNC);
            else {
                fullBitmap = mMedia.getFrameAtTime(timeInUs(time));
                newBit = true;
            }
            if (fullBitmap == null) fullBitmap = getAudioArt(mMedia);
            saveFrame(name, fullBitmap, newBit);
        } catch (Exception ee) {
            ee.printStackTrace();
        } finally {
            mMedia.release();
        }
    }

    public static long timeInUs(String time) {
        String[] times = time.split(":");
        long totalTime = 0;
        for (int i = 0; i < times.length - 1; i++) {
            int tim = Integer.parseInt(times[i]);
            if (times.length == 3 && i == 0) tim *= 3600;
            else tim *= 60;

            totalTime += tim;
        }
        totalTime += Integer.parseInt(times[times.length - 1]);

        return totalTime * 1000000;
    }

    private static Bitmap scaleBitmapDown(Bitmap map, int width, int height) {
        int scaledWidth = map.getWidth() / 2;
        int scaledHeight = map.getHeight() / 2;

        if (scaledWidth > width) scaledWidth = width;
        if (scaledHeight > (height)) scaledHeight = height;

        return Bitmap.createScaledBitmap(map, scaledWidth, scaledHeight, true);
    }

    private static Bitmap getAudioArt(MediaMetadataRetriever mMedia) {

        byte[] embeddedPicture = mMedia.getEmbeddedPicture();

        return BitmapFactory.decodeByteArray(embeddedPicture, 0, embeddedPicture.length);
    }

    private static void saveFrame(final File name, final Bitmap frame, boolean newBit) {
        if (frame == null || name.exists() && !newBit) return;
        try {
            name.createNewFile();
            final FileOutputStream streamOut = new FileOutputStream(name);

            mExecutor.submit(new Runnable() {
                @Override
                public void run() {
                    frame.compress(Bitmap.CompressFormat.PNG, 100, streamOut);
                    try {
                        streamOut.flush();
                        streamOut.close();
                    } catch (IOException ioe) {
                        ioe.printStackTrace();
                    }
                }
            });
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    public static MediaOrigins checkFileType(File file) {
        MediaMetadataRetriever meta = new MediaMetadataRetriever();
//        FileInputStream fd;
        try {
            FileInputStream fd = new FileInputStream(file);
            meta.setDataSource(file.getPath());
            fd.close();
        } catch (Exception ioe) {
            ioe.printStackTrace();
        }

        MediaOrigins ans;
        int videoMeta = MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO;
        int audioMeta = MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO;

        if (meta.extractMetadata(videoMeta) != null && meta.extractMetadata(audioMeta) != null)
            ans = MediaOrigins.AUDIOVIDEO;
        else if (meta.extractMetadata(videoMeta) == null && meta.extractMetadata(audioMeta) != null)
            ans = MediaOrigins.AUDIO;
        else if (meta.extractMetadata(videoMeta) != null && meta.extractMetadata(audioMeta) == null)
            ans = MediaOrigins.VIDEO;
        else ans = MediaOrigins.NOTMEDIA;

        meta.release();
        return ans;
    }

    public static boolean deleteFile(Context res, String path) {
        File pathFile = new File(path);
        Uri contentUri;

        MediaOrigins type = checkFileType(pathFile);
        if (type.equals(MediaOrigins.AUDIOVIDEO) || type.equals(MediaOrigins.VIDEO))
            contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
        else contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;


        boolean delete = pathFile.delete();
        if (delete && res != null)
            res.getContentResolver().delete(contentUri, MediaStore.MediaColumns.DATA + "=?", new String[]{pathFile.getAbsolutePath()});
        return delete;
    }

    public static boolean renameFile(Context res, String path, String newName) {
        boolean successful;
        String extension = path.substring(path.lastIndexOf("."));
        if (!newName.endsWith(extension)) newName.concat(extension);

        File pathFile = new File(path);

        File dest = new File(pathFile.getParent(), newName);

        successful = pathFile.renameTo(dest);
        if (successful && res != null) {
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.MediaColumns.DATA, dest.getPath());
            cv.put(MediaStore.MediaColumns.DISPLAY_NAME, dest.getName());
            long l = System.currentTimeMillis();
            cv.put(MediaStore.MediaColumns.DATE_MODIFIED, l);

            Uri contentUri;

            MediaOrigins type = checkFileType(dest);
            if (type.equals(MediaOrigins.AUDIOVIDEO) || type.equals(MediaOrigins.VIDEO))
                contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
            else contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;

            res.getContentResolver().update(contentUri, cv, MediaStore.MediaColumns.DATA + "=?", new String[]{pathFile.getPath()});
        }
        return successful;
    }

    public static int changedFileIndex(List<MediaFile> files) {
        int invalidFileIndex = -1;
        for (int f = 0; f < files.size(); f++) {
            File check = new File(files.get(f).getPath());
            if (!check.exists()) {
                invalidFileIndex = f;
                break;
            }
        }

        return invalidFileIndex;
    }

    public static MediaFile changedParentIndex(Map<MediaFile, List<MediaFile>> files) {
        int changedFile = -1;
        MediaFile mf = null;
        for (MediaFile f : files.keySet()) {
            changedFile++;
            int ind = changedFileIndex(files.get(f));
            if (ind != -1) {
                mf = f;
                break;
            }
        }

        return mf;
    }

    public static void sort(final MediaFile.Sort sortKey, final Boolean ascend, final List<MediaFile> media) {

        mExecutor.submit(new Runnable() {
            @Override
            public void run() {
                if (sortKey != null) {
                    Collections.sort(media, new Comparator<MediaFile>() {
                        @Override
                        public int compare(MediaFile o1, MediaFile o2) {
                            if (sortKey.equals(MediaFile.Sort.NAME)) {
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
                            } else if (sortKey.equals(MediaFile.Sort.SIZE)) {
                                long oo1 = o1.getSize();
                                long oo2 = o2.getSize();

                                return Long.compare(oo1, oo2);
                            } else if (sortKey.equals(MediaFile.Sort.DATE)) {
                                int oo1 = o1.getDateAdded();
                                int oo2 = o2.getDateAdded();

                                return Integer.compare(oo1, oo2);
                            } else if (sortKey.equals(MediaFile.Sort.DURATION)) {
                                long oo1 = o1.getDuration();
                                long oo2 = o2.getDuration();

                                return Long.compare(oo1, oo2);
                            } else {
                                return 0;
                            }
                        }
                    });
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

                if (ascend && reverse > 0) Collections.reverse(media);
                else if (!ascend && reverse < 0) Collections.reverse(media);
            }
        });
    }

    public static long calculateTotalSize(final String path) {
        long size = -1;

        try {
            size =
                    mExecutor.submit(new Callable<Long>() {
                        @Override
                        public Long call() throws Exception {

                            File f = new File(path);
                            if (!f.exists()) return -1L;

                            else if (f.isDirectory()) {
                                long size = 0;
                                File[] dirs = f.listFiles(new FileFilter() {
                                    @Override
                                    public boolean accept(File pathname) {
                                        return pathname.isFile();
                                    }
                                });

                                for (File fi : dirs) {
                                    size += fi.length();
                                }

                                return size;
                            } else {
                                return f.length();
                            }
                        }
                    }).get();
        } catch (ExecutionException | CancellationException | InterruptedException e) {
            e.printStackTrace();
        }

        return size;
    }

    public static String getMediaDuration(final String path) {
        String dura;
        final MediaMetadataRetriever ret = new MediaMetadataRetriever();
//
        try {
            dura =
                    mExecutor.submit(new Callable<String>() {
                        @Override
                        public String call() throws Exception {
                            ret.setDataSource(path);
                            String duration = ret.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                            ret.release();
                            return duration;
                        }
                    }).get();
        } catch (Exception e) {
            e.printStackTrace();
            return "0";
        } finally {
            ret.release();
        }
//        dura = duration;
        return dura;
    }

    public static int[] microUsToTime(final long timeUs) {
        int[] time = new int[3];

        try {
            time =
                    mExecutor.submit(new Callable<int[]>() {
                        @Override
                        public int[] call() throws Exception {
                            int[] time = new int[3];
                            long secs = timeUs / 1000000;

                            time[2] = (int) secs % 60;
                            time[1] = (int) (secs / 60) % 60;
                            time[0] = (int) (secs / 3600) % 24;

                            return time;
                        }
                    }).get();
        } catch (CancellationException | InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }

        return time;

    }

    public enum MediaOrigins {
        AUDIO,
        VIDEO,
        AUDIOVIDEO,
        NOTMEDIA
    }
}
