package com.exzell.exzlvideoplayer.viewmodels;

import android.app.Application;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.Observer;
import androidx.lifecycle.SavedStateHandle;

import com.exzell.exzlvideoplayer.BuildConfig;
import com.exzell.exzlvideoplayer.LocalPersistenceManager;
import com.exzell.exzlvideoplayer.MediaFile;
import com.exzell.exzlvideoplayer.MediaStoreIdKt;
import com.exzell.exzlvideoplayer.utils.BuildUtilsKt;
import com.exzell.exzlvideoplayer.utils.CursorUtilsKt;
import com.exzell.exzlvideoplayer.utils.FileUtilsKt;
import com.exzell.exzlvideoplayer.utils.MediaUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class MainViewModel extends AndroidViewModel {

    private ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    //Keys
    private final String KEY_SORT_ORDER = "sort_order";
    private final String KEY_ASCENDING = "ascending";
    private final String KEY_FILES = "selected_files";
    private final String KEY_MANAGER = "layout_manager";

    private Context mContext;
    private SavedStateHandle mSavedHandle;
    private LocalPersistenceManager mManager;

    private List<MediaFile> toBeRemoved = new ArrayList<>();

    private Observer[] mPrefObservers = new Observer[3];

    private List<MediaFile> mCurrent = new ArrayList<>();
    private List<MediaFile> mNext = new ArrayList<>();
    private List<MediaFile> mPrevious = new ArrayList<>();
    private String mMediaFilter;

    public final String[] mCursorColumns;
    public final Uri mUri;


    public MainViewModel(@NonNull Application application, SavedStateHandle handle, String[] colns, Uri uri) {
        super(application);
        mSavedHandle = handle;
        mManager = new LocalPersistenceManager(application.getSharedPreferences
                (LocalPersistenceManager.PREF_NAME, Context.MODE_PRIVATE));
        mContext = application.getApplicationContext();

        mCursorColumns = colns;
        mUri = uri;
        initHandle();
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

    /**
     * @return Returns true if the layout manager is a linear layout manager
     */
    public boolean getLayoutManager() {
        return mSavedHandle.get(KEY_MANAGER);
    }

    public void setLayoutManager(boolean manager) {
        mSavedHandle.set(KEY_MANAGER, manager);
    }

    public LinkedList<MediaFile> getSelectedFiles() {
        return mSavedHandle.get(KEY_FILES);
    }

    public void setSelectedFiles(List<MediaFile> selectedFiles){
        mSavedHandle.set(KEY_FILES, selectedFiles);
    }

    public boolean hasPrevious(){
        return !mPrevious.isEmpty();
    }

    public void setMediaFilter(String filter){
        mMediaFilter = filter;
    }

    public List<MediaFile> currentFiles(){
        List<MediaFile> files = new File(mCurrent.get(0).getPath()).isDirectory() ? mCurrent : mCurrent.stream()
                .filter(p -> p.getParentPath().equals(mMediaFilter))
                .collect(Collectors.toList());

        MediaUtils.sort(getSortOrder(), isAscending(), files, mExecutor);
        return files;
    }

    public List<MediaFile> getAllFiles(){
        List<MediaFile> all = new ArrayList<>(mCurrent);
        all.addAll(mNext);
        all.addAll(mPrevious);
        return all;
    }

    //TODO: Update this documentation
    /**
     * This method should be called when a file is clicked
     *so it will prepare the previous data and update the current data
     * call this before
     */
    public void preparePreviousMediaData(){

        if(mNext.isEmpty()) return;

        mPrevious.clear();
        mPrevious.addAll(mCurrent);

        mCurrent.clear();
        mCurrent.addAll(mNext);

        mNext.clear();
    }

    /**
     * This method should be called when the up button is clicked
     * so it will prepare the next data and update the current data
     */
    public void prepareNextMedias(){

        if(mPrevious.isEmpty()) return;

        mNext.clear();
        mNext.addAll(mCurrent);

        mCurrent.clear();
        mCurrent.addAll(mPrevious);

        mPrevious.clear();
    }

    public void prepareData() {

        if(!mCurrent.isEmpty()) return;

        List<Bundle> media = CursorUtilsKt.queryMediaStore(mContext, mUri, null, null, MediaStore.Audio.Media.DISPLAY_NAME, mCursorColumns);

        List<MediaFile> all = createMediaItemsFromBundle(media);

        mCurrent.addAll(all.stream().filter(p -> new File(p.getPath()).isDirectory()).collect(Collectors.toList()));
        mNext.addAll(all.stream().filter(p -> new File(p.getPath()).isFile()).collect(Collectors.toList()));
        updateOrCreateParents(mNext);


        if(BuildConfig.DEBUG) MediaStoreIdKt.writeToFile(mContext, mNext, mUri.compareTo(MediaStore.Video.Media.EXTERNAL_CONTENT_URI) == 0
                ? MediaStoreIdKt.DEFAULT_VIDEO : MediaStoreIdKt.DEFAULT_AUDIO);
    }

    /**
     * Unwraps the bundle and creates the necessary media files as well as media files of the parents
     */
    public List<MediaFile> createMediaItemsFromBundle(final List<Bundle> mediaFiles) {
        List<MediaFile> files = new ArrayList<>();

            try {
                files =
                mExecutor.submit(() -> {

                    Set<MediaFile> parentContainers = new HashSet<>();
                    List<MediaFile> childContainers = new ArrayList<>();


                    for (Bundle b : mediaFiles) {
                        String path = b.getString(mCursorColumns[0]);

                        MediaFile.Type mime = b.getString(mCursorColumns[4]).contains("audio")
                                ? MediaFile.Type.AUDIO
                                : MediaFile.Type.VIDEO;
                        long duration = b.getInt(mCursorColumns[5]) * 1000;

                        MediaFile chi = new MediaFile(path, b.getInt(mCursorColumns[1]), b.getInt(mCursorColumns[3]), b.getInt(mCursorColumns[2]), mime);
                        chi.setDuration(duration);
//                        MediaUtils.setMediaDuration(chi, mExecutor);
                        File thumbPath = new File(BuildUtilsKt.thumbnailPath(mContext), chi.getDisplayName() + ".png");
                        if(thumbPath.exists()) chi.setThumbnailPath(thumbPath.getPath());

                        childContainers.add(chi);
                        MediaFile parent = new MediaFile(new File(path).getParent());

                        parentContainers.add(parent);
                    }

                    childContainers.addAll(parentContainers);

                    return childContainers;
                }).get();
            } catch (ExecutionException | CancellationException | InterruptedException e) {
                e.printStackTrace();
            }

            return files;
    }

    public void updateMedia(MediaFile mediaFile, int change){
        String select = MediaStore.MediaColumns._ID + "=" + change;

        Bundle b = CursorUtilsKt.queryMediaStore(mContext, mUri, select, null, MediaStore.MediaColumns.DATA, mCursorColumns).get(0);
        String path = b.getString(MediaStore.MediaColumns.DATA);
        File changeFile = new File(path);


        mediaFile.setDisplayName(changeFile.getName());
        mediaFile.setParentPath(changeFile.getParent());
        mediaFile.setPath(changeFile.getPath());

        String newPath = FileUtilsKt.updateThumbnailPath(mediaFile.getThumbnailPath(), changeFile.getName());
        mediaFile.setThumbnailPath(newPath);
    }

    public void cacheMedias(MediaFile probation){
        toBeRemoved.add(probation);
    }

    public List<MediaFile> getCache(){
        return toBeRemoved;
    }

    public void removeMedia(MediaFile gone){

            if (mCurrent.contains(gone)) mCurrent.remove(gone);
            else if (mPrevious.contains(gone)) mPrevious.remove(gone);
            else mNext.remove(gone);

    }

    /**
     * Adds a new Media File to the list, identifying which of them it belongs to
     * @return Returns true if the currently shown list is where the file is added to
     */
    public boolean addToList(MediaFile newFile) {
        boolean ret = false;
        if(mCurrent.stream().anyMatch(me -> new File(me.getPath()).isFile()) && new File(newFile.getPath()).isFile()){
            mCurrent.add(newFile);
            ret = true;
        }else if(mPrevious.stream().anyMatch(me -> me.isDir() == newFile.isDir())){
            mPrevious.add(newFile);
        }else{
            mNext.add(newFile);
        }
        updateOrCreateParents(Collections.singletonList(newFile));

        return ret;
    }

    public void updateOrCreateParents(List<MediaFile> files){
        List<String> parentPaths = files.stream().filter(p -> new File(p.getPath()).isFile()).map(f -> f.getParentPath()).distinct().collect(Collectors.toList());

        for(String parentPath : parentPaths) {

            List<MediaFile> dirs;
            long sum = getAllFiles().stream().filter(f -> f.getParentPath().equals(parentPath)).mapToLong(l -> l.getSize()).sum();

            if (new File(mCurrent.get(0).getPath()).isDirectory()) dirs = mCurrent;
            else if (new File(mPrevious.get(0).getPath()).isDirectory()) dirs = mPrevious;
            else dirs = mNext;

            Optional<MediaFile> first = dirs.stream().filter(p -> p.getPath().equals(parentPath)).findFirst();
            if (first.isPresent()) {
                MediaFile parent = first.get();
                parent.setSize(sum);
            } else {
                MediaFile parent = new MediaFile(parentPath);
                parent.setSize(sum);
                dirs.add(parent);
            }
        }
    }

    public void registerPrefObservers(final LifecycleOwner owner) {

        if(mPrefObservers[1] == null){
            mPrefObservers[0] = (Observer<Boolean>) b -> mManager.setAscendingOrder(b);
            mPrefObservers[1] = (Observer<MediaFile.Sort>) sort -> mManager.setSortValue(sort);
            mPrefObservers[2] = (Observer<Boolean>) manager -> mManager.setLayoutManager(manager);
        }

        mSavedHandle.getLiveData(KEY_ASCENDING).observe(owner, mPrefObservers[0]);
        mSavedHandle.getLiveData(KEY_SORT_ORDER).observe(owner, mPrefObservers[1]);
        mSavedHandle.getLiveData(KEY_MANAGER).observe(owner, mPrefObservers[2]);
    }

    @Override
    protected void onCleared() {
        mSavedHandle.getLiveData(KEY_ASCENDING).removeObserver(mPrefObservers[0]);
        mSavedHandle.getLiveData(KEY_SORT_ORDER).removeObserver(mPrefObservers[1]);
        mSavedHandle.getLiveData(KEY_MANAGER).removeObserver(mPrefObservers[2]);
    }
}
