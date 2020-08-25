package com.exzell.exzlvideoplayer.viewmodels;

import android.app.Application;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.SavedStateHandle;

import com.exzell.exzlvideoplayer.LocalPersistenceManager;
import com.exzell.exzlvideoplayer.MediaFile;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class MainViewModel extends AndroidViewModel {

    //Keys
    private final String KEY_SORT_ORDER = "sort_order";
    private final String KEY_ASCENDING = "ascending";
    private final String KEY_FILES = "selected_files";
    private final String KEY_MANAGER = "layout_manager";
    private Context mContext;
    private SavedStateHandle mSavedHandle;
    private LocalPersistenceManager mManager;
    private final LinkedList<List<MediaFile>> cachedVideos = new LinkedList<>();
    private final LinkedList<List<MediaFile>> cachedAudios = new LinkedList<>();


    public MainViewModel(@NonNull Application application, SavedStateHandle handle) {
        super(application);
        mSavedHandle = handle;
        mManager = new LocalPersistenceManager(application.getSharedPreferences
                (LocalPersistenceManager.PREF_NAME, Context.MODE_PRIVATE));
        mContext = application.getApplicationContext();
    }

    public void initHandle() {
        if (mSavedHandle.get(KEY_ASCENDING) == null)
            mSavedHandle.set(KEY_ASCENDING, mManager.getAscendingOrder());
        if (mSavedHandle.get(KEY_SORT_ORDER) == null)
            mSavedHandle.set(KEY_SORT_ORDER, mManager.getSortValue());
        if (mSavedHandle.get(KEY_MANAGER) == null)
            mSavedHandle.set(KEY_MANAGER, mManager.getLayoutManager());
        if (mSavedHandle.get(KEY_FILES) == null)
            mSavedHandle.set(KEY_FILES, new LinkedList<String>());
    }

    public MediaFile.Sort getSortOrder() {
        return mSavedHandle.get(KEY_SORT_ORDER);
    }

    public void setSortOrder(MediaFile.Sort order) {
        mSavedHandle.set(KEY_SORT_ORDER, order);
    }

    public boolean isAscending() {
        return mSavedHandle.get(KEY_ASCENDING);
    }

    public void setAscending(boolean ascend) {
        mSavedHandle.set(KEY_ASCENDING, ascend);
    }

    public boolean getLayoutManager() {
        return mSavedHandle.get(KEY_MANAGER);
    }

    public void setLayoutManager(boolean manager) {
        mSavedHandle.set(KEY_MANAGER, manager);
    }

    public List<Bundle> cursorQuery(final Uri queriedUri, final String selection, final String sortOrder, final String... columnsToBeSelected) {
        Cursor query;
        final List<Bundle> resultBundle = new ArrayList<>();


        try {

            query = mContext.getContentResolver().query(queriedUri, columnsToBeSelected, selection, null, sortOrder);

            if (query == null) return null;

            while (query.moveToNext()) {
                Bundle bund = new Bundle(columnsToBeSelected.length);

                for (int i = 0; i < columnsToBeSelected.length; i++) {
                    //since the column type may not be known
                    //it is better to get it ourselves
                    switch (query.getType(i)) {
                        case Cursor.FIELD_TYPE_STRING:
                            bund.putString(columnsToBeSelected[i], query.getString(i));
                            break;
                        case Cursor.FIELD_TYPE_INTEGER:
                            bund.putInt(columnsToBeSelected[i], query.getInt(i));
                            break;
                        case Cursor.FIELD_TYPE_FLOAT:
                            bund.putFloat(columnsToBeSelected[i], query.getFloat(i));
                            break;
                    }
                }

                resultBundle.add(bund);
            }

            query.close();
        } catch (Exception n) {
            n.printStackTrace();
        }

        return resultBundle;
    }

    public MutableLiveData<LinkedList> getSelectedFiles() {
        return mSavedHandle.getLiveData(KEY_FILES);
    }

    public List<MediaFile> getCachedFile(boolean which) {
        List<MediaFile> cachedFile;

        if (which) cachedFile = cachedVideos.pollFirst();
        else cachedFile = cachedAudios.pollFirst();

        return cachedFile;
    }

    public void cacheFiles(List<MediaFile> files, boolean which) {
        if (which) {
            cachedVideos.offerFirst(files);
        } else {
            cachedAudios.offerFirst(files);
        }
    }

    public int cachedFilesSize(boolean which) {
        if (which) return cachedVideos.size();
        else return cachedAudios.size();
    }

    public void registerPrefObservers(final LifecycleOwner owner, Handler backHandler) {


        backHandler.post(new Runnable() {
            @Override
            public void run() {
                MutableLiveData<Boolean> bo = mSavedHandle.getLiveData(KEY_ASCENDING);
                bo.observe(owner, new Observer<Boolean>() {
                    @Override
                    public void onChanged(Boolean aBoolean) {
                        mManager.setAscendingOrder(aBoolean);
                    }
                });

                MutableLiveData<MediaFile.Sort> so = mSavedHandle.getLiveData(KEY_SORT_ORDER);
                so.observe(owner, new Observer<MediaFile.Sort>() {
                    @Override
                    public void onChanged(MediaFile.Sort sort) {
                        mManager.setSortValue(sort);
                    }
                });

                MutableLiveData<Boolean> boo = mSavedHandle.getLiveData(KEY_MANAGER);
                boo.observe(owner, new Observer<Boolean>() {
                    @Override
                    public void onChanged(Boolean aBoolean) {
                        mManager.setLayoutManager(aBoolean);
                    }
                });
            }
        });
    }
}
