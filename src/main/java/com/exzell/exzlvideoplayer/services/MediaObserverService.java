package com.exzell.exzlvideoplayer.services;

import android.app.IntentService;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.provider.MediaStore;
import android.util.Log;

import androidx.annotation.Nullable;

import com.exzell.exzlvideoplayer.listeners.OnFileChangedListener;
import com.exzell.exzlvideoplayer.utils.ObserverUtils;

public class MediaObserverService extends IntentService {
    private final String TAG = this.getClass().getSimpleName();

    /**
     * Creates an IntentService.  Invoked by your subclass's constructor.
     *
     * @param name Used to name the worker thread, important only for debugging.
     */
    public MediaObserverService(String name) {
        super(name);
    }

    public MediaObserverService() {
        this("OK");
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        Uri ex = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
        Uri exAud = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        Handler han = new Handler(getMainLooper());
        getContentResolver().registerContentObserver(ex, true, new MediaObserver(han));
        getContentResolver().registerContentObserver(exAud, true, new MediaObserver(han));

    }

    private class MediaObserver extends ContentObserver {

        final OnFileChangedListener[] ofc = ObserverUtils.getInstance().getListeners();

        /**
         * Creates a content observer.
         *
         * @param handler The handler to run {@link #onChange} on, or null if none.
         */
        MediaObserver(Handler handler) {
            super(handler);
            Log.i(TAG, "Observer started");
        }

        @Override
        public boolean deliverSelfNotifications() {
            return true;
        }

        @Override
        public void onChange(boolean selfChange) {
            Log.i(TAG, "Normal OnChange called");
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {

            String check = readLastModifiedFile(uri);

            if (uri.toString().contains("video")) ofc[0].onFileChanged(check);
            else ofc[1].onFileChanged(check);

        }

        private String readLastModifiedFile(Uri uri) {
            String order = MediaStore.MediaColumns.DATE_MODIFIED + " DESC";
            String pathOfChangedFile = "";
            String[] proj = {MediaStore.Video.Media.DATA};
            try {
                ContentResolver r = getContentResolver();
                Cursor cursor = r.query(uri, proj, null, null, order);

                if (cursor.moveToNext()) {
                    pathOfChangedFile = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA));
                }

                cursor.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
            return pathOfChangedFile;
        }
    }
}
