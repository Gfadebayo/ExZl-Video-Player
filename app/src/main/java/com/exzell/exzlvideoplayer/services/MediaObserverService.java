package com.exzell.exzlvideoplayer.services;

import android.app.IntentService;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.ResultReceiver;
import android.provider.MediaStore;
import android.util.Log;

import androidx.annotation.Nullable;

import com.exzell.exzlvideoplayer.MediaFile;
import com.exzell.exzlvideoplayer.MediaStoreIdKt;
import com.exzell.exzlvideoplayer.fragments.FragmentVideos;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntPredicate;

import static com.exzell.exzlvideoplayer.BuildConfig.DEBUG;

public class MediaObserverService extends IntentService {
    private final String TAG = this.getClass().getSimpleName();
    private Set<MediaObserver> mObs = new HashSet<>();

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

        Handler han = new Handler(getMainLooper());


        ResultReceiver r = intent.getParcelableExtra(FragmentVideos.RECEIVER);
        Log.i("Service", "Result Reci=eiver gotten: " + r.toString());

        String ur = intent.getStringExtra(FragmentVideos.URI);
        Log.i("Service", "Uri found: " + ur);
        Uri uri = Uri.parse(ur);
        MediaObserver o = new MediaObserver(han, uri, r);

        mObs.add(o);
    }

    private class MediaObserver extends ContentObserver {

        private Uri mUri;
        private ResultReceiver mReceiver;


        /**
         * Creates a content observer.
         *
         * @param handler The handler to run {@link #onChange} on, or null if none.
         */
        MediaObserver(Handler handler, Uri uri, ResultReceiver listener) {
            super(handler);
            mUri = uri;
            mReceiver = listener;

            getContentResolver().registerContentObserver(uri, true, this);
            if(DEBUG) Log.i(TAG, "Observer started");
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

            String path = uri.getLastPathSegment();
            uri = path.codePoints().anyMatch(v -> !Character.isDigit(v)) ? mUri : uri;

            ArrayList<Integer> check = (ArrayList<Integer>) readLastModifiedFile(uri);

            if(check.isEmpty()) return;

            Bundle bu = new Bundle(1);
            bu.putIntegerArrayList(FragmentVideos.MediaReceiver.SEND, check);

            int result = uri.compareTo(mUri) == 0 ? FragmentVideos.MediaReceiver.PATH_UNKNOWN : FragmentVideos.MediaReceiver.PATH_KNOWN;
            mReceiver.send(result, bu);


            //TODO: Dont forget this oooo :)
        }

        private List<Integer> readLastModifiedFile(Uri uri) {

            List<MediaFile> files = new ArrayList<>();

            String order = MediaStore.MediaColumns.DISPLAY_NAME;
            List<Integer> items = new ArrayList<>();

            String storeTitle = "";

            String[] reqColumns = {MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.DATA};


            try {
                ContentResolver r = getContentResolver();
                Cursor cursor = r.query(uri, reqColumns, null, null, order);

                while(cursor.moveToNext()) {
                    storeTitle = cursor.getString(cursor.getColumnIndexOrThrow(reqColumns[1]));


                    int id = cursor.getInt(cursor.getColumnIndex(reqColumns[0]));
                    items.add(id);

                    String pname = cursor.getString(cursor.getColumnIndex(reqColumns[2]));
                    MediaFile f = new MediaFile(pname);
                    f.setDisplayName(storeTitle);
                    f.setId((id));

                    files.add(f);
                }

                if(DEBUG) MediaStoreIdKt.writeToFile(MediaObserverService.this, files, mUri.compareTo(MediaStore.Video.Media.EXTERNAL_CONTENT_URI) == 0
                        ? MediaStoreIdKt.AFTER_VIDEO : MediaStoreIdKt.AFTER_AUDIO);

                cursor.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
            return items;
        }
    }

    private void writeToFile(String name){

    }
}
