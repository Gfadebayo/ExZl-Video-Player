package com.exzell.exzlvideoplayer;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.util.Log;

public class LocalPersistenceManager {

    public static final String PREF_NAME = "local_persistence";
    public static final String KEY_SORT = "sort";
    public static final String KEY_MANAGER = "layout_manager";
    public static final String KEY_ASCENDING = "ascending";
    public static final String KEY_DIALOG = "dialog_complete";
    private final SharedPreferences mPreference;
    private final SharedPreferences.Editor mEditor;


    @SuppressLint("CommitPrefEdits")
    public LocalPersistenceManager(SharedPreferences pref) {
        mPreference = pref;
        mEditor = pref.edit();
    }

    public MediaFile.Sort getSortValue() {
        String sort = mPreference.getString(KEY_SORT, MediaFile.Sort.NAME.toString());
        return MediaFile.Sort.valueOf(sort);
    }

    public void setSortValue(MediaFile.Sort val) {
        Log.i("Manager", "Sort value changed");
        mEditor.putString(KEY_SORT, val.toString());
        mEditor.commit();
    }

    public boolean getAscendingOrder() {
        return mPreference.getBoolean(KEY_ASCENDING, true);
    }

    public void setAscendingOrder(boolean ascend) {
        mEditor.putBoolean(KEY_ASCENDING, ascend);
        mEditor.commit();
    }

    public void setStartDialogComplete(boolean isDone) {
        mEditor.putBoolean(KEY_DIALOG, isDone);
        mEditor.commit();
    }

    public boolean getDialogComplete() {
        return mPreference.getBoolean(KEY_DIALOG, false);
    }

    public boolean getLayoutManager() {
        return mPreference.getBoolean(KEY_MANAGER, true);
    }

    public void setLayoutManager(boolean which) {
        mEditor.putBoolean(KEY_MANAGER, which);
        mEditor.commit();
    }
}
