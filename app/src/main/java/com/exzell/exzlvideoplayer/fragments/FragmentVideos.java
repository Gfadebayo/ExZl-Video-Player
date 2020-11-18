package com.exzell.exzlvideoplayer.fragments;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.ResultReceiver;
import android.provider.MediaStore;
import android.util.Log;
import android.view.ActionMode;
import android.view.ActionMode.Callback;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewPropertyAnimator;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.selection.DefaultSelectionTracker;
import androidx.recyclerview.selection.ItemDetailsLookup;
import androidx.recyclerview.selection.SelectionPredicates;
import androidx.recyclerview.selection.SelectionTracker;
import androidx.recyclerview.selection.StorageStrategy;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.exzell.exzlvideoplayer.MediaFile;
import com.exzell.exzlvideoplayer.MediaItemAnimator;
import com.exzell.exzlvideoplayer.PlaybackActivity;
import com.exzell.exzlvideoplayer.R;
import com.exzell.exzlvideoplayer.adapter.VideoFileAdapter;
import com.exzell.exzlvideoplayer.adapter.tracker.MediaKeyProvider;
import com.exzell.exzlvideoplayer.services.MediaObserverService;
import com.exzell.exzlvideoplayer.utils.CursorUtilsKt;
import com.exzell.exzlvideoplayer.utils.FileUtilsKt;
import com.exzell.exzlvideoplayer.utils.BuildUtilsKt;
import com.exzell.exzlvideoplayer.utils.MediaUtils;
import com.exzell.exzlvideoplayer.utils.ViewUtilsKt;
import com.exzell.exzlvideoplayer.viewmodels.MainViewModel;
import com.exzell.exzlvideoplayer.viewmodels.MediaViewModelFactory;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import org.jetbrains.annotations.NotNull;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import static com.exzell.exzlvideoplayer.MediaFile.Sort.*;

public class FragmentVideos extends SelectionFragment {

    private final String TAG = this.getClass().getSimpleName();
    private MainViewModel mViewModel;

    private RecyclerView mRecyclerView;
    private GridLayoutManager mGridManager;
    private LinearLayoutManager mLinearManager;
    private VideoFileAdapter mAdapter;

    private boolean isForVideos;
    private ExecutorService mExecutor;
    private SelectionTracker<Long> mTracker;
    private ExtendedFloatingActionButton mExFab;

    public static final String RECEIVER = "receiverr";
    public static final String URI = "mUri";


    public static FragmentVideos newInstance(int type) {
        FragmentVideos vids = new FragmentVideos();
        Bundle extras = new Bundle(1);
        extras.putInt("TAB", type);
        vids.setArguments(extras);

        return vids;
    }

    private void unravelBundle() {
        Bundle args = getArguments();
        if (args == null) return;

        int decision = args.getInt("TAB");

        isForVideos = decision == R.string.video;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
        setHasOptionsMenu(true);

        unravelBundle();

        mExecutor = Executors.newFixedThreadPool(3);

        String[] mediaCols = {MediaStore.MediaColumns.DATA, MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DATE_ADDED,
                MediaStore.MediaColumns.MIME_TYPE, MediaStore.MediaColumns.DURATION};

        Uri ur = isForVideos ? MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                : MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;

        ViewModelProvider.Factory fact = new MediaViewModelFactory(this, requireActivity().getApplication(), mediaCols, ur);

        mViewModel = new ViewModelProvider(this, fact).get(MainViewModel.class);
        mViewModel.registerPrefObservers(this);



        Intent watchIntent = new Intent(requireActivity(), MediaObserverService.class);

        Bundle bund = new Bundle(2);
        bund.putParcelable(RECEIVER, new MediaReceiver());
        bund.putString(URI, ur.toString());

        watchIntent.putExtra(RECEIVER, new MediaReceiver());
        watchIntent.putExtra(URI, ur.toString());

//        requireActivity().startService(watchIntent);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_videos, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {

        mExFab = requireActivity().findViewById(R.id.fab_current_file);
        mRecyclerView = view.findViewById(R.id.list_file);

        mGridManager = new GridLayoutManager(requireContext(), 2, GridLayoutManager.VERTICAL, false);
        mLinearManager = new LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false);

        mRecyclerView.setLayoutManager(mViewModel.getLayoutManager() ? mLinearManager : mGridManager);

        mViewModel.prepareData();

        mAdapter = new VideoFileAdapter(mViewModel.currentFiles(), requireActivity(), mViewModel.getLayoutManager());
        mRecyclerView.setAdapter(mAdapter);

        ViewUtilsKt.removeBehaviour(mExFab);
        mExFab.setOnClickListener(v -> {

            if(mTracker.hasSelection()) {
                List<MediaFile> files = new ArrayList<>();

                mTracker.getSelection().forEach(c -> {
                    int pos = mRecyclerView.findViewHolderForItemId(c).getAbsoluteAdapterPosition();
                    MediaFile f = mAdapter.getFiles().get(pos);
                    files.add(f);
                });

                files.get(0).setSelected(true);
                mViewModel.setSelectedFiles(files);
            }


            boolean b = sendFileToPlayer(mViewModel.getSelectedFiles());
            if (!b)
                Toast.makeText(requireContext(), "Previously Selected File not Found", Toast.LENGTH_SHORT).show();
        });

        mTracker = initTracker(mRecyclerView);

        mAdapter.setListener(getFileName());
        mAdapter.setTracker(mTracker);

        onBackPressed();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, @NonNull MenuInflater inflater) {

        //Ensure the icon is correct
        MenuItem item = menu.findItem(R.id.action_layout);

        if (mViewModel.getLayoutManager()) {
            item.setTitle("Grid");
            item.setIcon(R.drawable.ic_view_module_black_24dp);
            mRecyclerView.setLayoutManager(mLinearManager);
        } else {
            item.setTitle("List");
            item.setIcon(R.drawable.ic_view_list_black_24dp);
            mRecyclerView.setLayoutManager(mGridManager);
        }
        mAdapter.setManager(mViewModel.getLayoutManager());
    }

    @Override
    public void onPrepareOptionsMenu(@NonNull Menu menu) {

        switch (mViewModel.getSortOrder()) {
            case NAME:
                menu.findItem(R.id.action_title).setChecked(true);
                break;
            case DATE:
                menu.findItem(R.id.action_date).setChecked(true);
                break;
            case SIZE:
                menu.findItem(R.id.action_size).setChecked(true);
                break;
            case DURATION:
                menu.findItem(R.id.action_duration).setChecked(true);
                break;
        }

        menu.findItem(R.id.action_ascending).setChecked(mViewModel.isAscending());
//        changeConfig();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();
        boolean ret = false;
        boolean restartFrag = false;
        MediaFile.Sort order = null;

        switch (itemId) {
            case R.id.action_title:
                item.setChecked(true);
                order = NAME;
                ret = restartFrag = true;
                break;
            case R.id.action_date:
                item.setChecked(true);
                order = DATE;
                ret = restartFrag = true;
                break;
            case R.id.action_size:
                item.setChecked(true);
                order = SIZE;
                ret = restartFrag = true;
                break;
            case R.id.action_duration:
                item.setChecked(true);
                order = DURATION;
                ret = restartFrag = true;
                break;
            case R.id.action_ascending:
                boolean checked = !item.isChecked();
                mViewModel.setAscending(checked);
                item.setChecked(checked);
                restartFrag = true;
                ret = true;
                break;
            case R.id.action_layout:
                changeLayout(item);
                ret = true;
                break;
        }


        if (order != null) mViewModel.setSortOrder(order);
        if (restartFrag) {
            //Since the order of the files in the view model wont change, its better to sort directly
            //from here
            MediaUtils.sort(mViewModel.getSortOrder(), mViewModel.isAscending(), mAdapter.getFiles(), mExecutor);
            mAdapter.notifyItemRangeChanged(0, mAdapter.getItemCount());
        }

        return ret;
    }

    public View.OnClickListener getFileName() {

        return (view) -> {
            VideoFileAdapter.ViewHolder holder = (VideoFileAdapter.ViewHolder) mRecyclerView.getChildViewHolder(view);
            int position = holder.getBindingAdapterPosition();
            MediaFile mediaFile = mAdapter.getFiles().get(position);

            if (new File(mediaFile.getPath()).isFile()) {
                mediaFile.setSelected(true);
                sendFileToPlayer(Collections.singletonList(mediaFile));

            } else {

                mViewModel.setMediaFilter(mediaFile.getPath());
                mViewModel.preparePreviousMediaData();


                mAdapter.setNewFiles(mViewModel.currentFiles());

                onBackPressed();

                ((Toolbar) requireActivity().findViewById(R.id.toolbar))
                        .setTitle(mediaFile.getDisplayName());
            }
        };
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {

        super.onConfigurationChanged(newConfig);
        Log.i(TAG, "Configuration Changed");
        changeConfig(newConfig);
    }

    private void changeConfig(Configuration newOrientation) {
        int orientation = newOrientation.orientation;
        if (mRecyclerView.getLayoutManager() instanceof GridLayoutManager) {
            if (orientation == Configuration.ORIENTATION_LANDSCAPE)
                mGridManager.setSpanCount(3);
            else mGridManager.setSpanCount(2);
        }
    }

    private boolean sendFileToPlayer(final List<MediaFile> pats) {

        //TODO: Fucking change this method so it instead sends a media file

//                    mViewModel.setCurrentAdapter(mAdapter, isForVideos);

                if(pats == null || pats.isEmpty()) return false;

                Intent newIntent = new Intent(requireActivity(), PlaybackActivity.class);
                newIntent.putExtra(PlaybackActivity.TAG, new ArrayList<>(pats));
                startActivity(newIntent);

        return true;
    }

    public void onBackPressed() {
        if(mTracker.hasSelection()) closeActionMode();
        else {
            Toolbar bar = requireActivity().findViewById(R.id.toolbar);
//                int adaptersSize = mViewModel.usedAdaptersSize(isForVideos);
//                int adaptersSize = mViewModel.cachedFilesSize(isForVideos);
            if (mViewModel.hasPrevious()) {
                bar.setNavigationIcon(R.drawable.ic_arrow_back_black_24dp);
                bar.setTitle(mAdapter.getFileTitle());
                bar.setNavigationOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        initPreviousAdapter();
                    }
                });
            } else {
                bar.setNavigationIcon(null);
                bar.setTitle(R.string.files);
            }
        }
    }

    public void initPreviousAdapter() {

        mViewModel.prepareNextMedias();
        //by default this method returns a videofileadapter, but that is being changed to accommodate a selection tracker
//        VideoFileAdapter currentAdapter = mViewModel.getCurrentAdapter(isForVideos);
//        List<MediaFile> file = mViewModel.releaseCachedMedia(isForVideos);
//        if (file == null) return null;

        mAdapter.setNewFiles(mViewModel.currentFiles());

        onBackPressed();
    }

    private void fabWiggle() {
        requireActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ExtendedFloatingActionButton fab = requireActivity().findViewById(R.id.fab_current_file);
                final float alp = 50;

                final ViewPropertyAnimator animate = fab.animate();

                animate.setDuration(1000)
                        .translationZBy(alp)
                        .withEndAction(new Runnable() {
                            @Override
                            public void run() {
                                animate.translationZ(0).setDuration(1000).start();
                            }
                        })
                        .start();
            }
        });
    }

    private void changeLayout(final MenuItem item) {

        requireActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                boolean layout = mViewModel.getLayoutManager();
                if (layout) {
                    Drawable newicon = requireContext().getDrawable(R.drawable.ic_view_list_black_24dp);
                    item.setIcon(newicon);
                    item.setTitle("List");
                    mRecyclerView.setLayoutManager(mGridManager);
                } else {
                    Drawable newIcon = requireContext().getDrawable(R.drawable.ic_view_module_black_24dp);
                    item.setIcon(newIcon);
                    item.setTitle("Grid");
                    mRecyclerView.setLayoutManager(mLinearManager);
                }

                requireActivity().invalidateOptionsMenu();
                mAdapter.setManager(!layout);
                mViewModel.setLayoutManager(!layout);
//                changeConfig();
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
//            mViewModel.cacheFiles(mAdapter.getCurrentList(), isForVideos);
    }

    @Override
    public boolean onActionItemClicked(@NotNull MenuItem item) {
        if(item.getItemId() == R.id.action_cab_delete){
            mTracker.getSelection().forEach(c -> {
                VideoFileAdapter.ViewHolder vh = (VideoFileAdapter.ViewHolder) mRecyclerView.findViewHolderForItemId(c);
                int pos = vh.getBindingAdapterPosition();

                MediaFile file = mAdapter.getFiles().get(pos);

                MediaUtils.deleteFile(requireActivity(), file);
            });
        }

        return true;
    }

    //FAILURE: If you havent given up on this project, try to fix it
    public class MediaReceiver extends ResultReceiver {

        public static final String SEND = "send_info";
        public static final int PATH_KNOWN = 0;
        public static final int PATH_UNKNOWN = 1;


        public MediaReceiver() {
            super(new Handler());
        }

        @Override
        protected void onReceiveResult(int resultCode, Bundle resultData) {
            /*In the media store, when a file is renamed, it is first removed
             * then a new entry for it is created*/
            //A list of the ids of the affected media item or all media items is sent

            List<Integer> changedFile = resultData.getIntegerArrayList(SEND);
            boolean valid = resultCode == PATH_KNOWN;

            List<Integer> fileIds = mViewModel.getAllFiles().stream()
                    .filter(p -> new File(p.getPath()).isFile())
                    .map(f -> f.getId()).collect(Collectors.toList());

            if(!valid) {
                if (changedFile.size() < fileIds.size()) {
                    //A file has been removed from the media store
                    fileIds.removeAll(changedFile);

                    //filePaths has been filtered to show only the invalid media store id
                    //We cache the media file in case the file was renamed instead of deleted from the device

                    List<MediaFile> invalids = mViewModel.getAllFiles().stream()
                            .filter(p -> fileIds.contains(p.getId())).collect(Collectors.toList());

                    invalids.forEach(remove -> {
                        mViewModel.cacheMedias(remove);
                        mViewModel.removeMedia(remove);

                        mAdapter.removeFile(remove);
                    });

                } else if (changedFile.size() > fileIds.size()) {
                    //A file has been added to the media store
                    changedFile.removeAll(fileIds);
                    updateFromMediaStore(changedFile.get(0));
                }
            }else{
                int id = changedFile.get(0);
                updateFromMediaStore(id);
            }
        }

        private void updateFromMediaStore(int change){
            new Thread(() -> {
                //changedFile now contains only the new paths in the media store with a size of 1
                //we check our cache to see if those files are identical to the ones there

                if(mViewModel.getAllFiles().stream().anyMatch(p -> p.getId() == change)){

                    //The id exists so we only need to change the data ie the path, the name and the thumbnail path
                    MediaFile media = mViewModel.getAllFiles().stream().filter(p -> p.getId() == change).findFirst().get();
                    mViewModel.updateMedia(media, change);

                    int index = mAdapter.getFiles().indexOf(media);

                    requireActivity().runOnUiThread(() -> {if(index != -1) mAdapter.notifyItemChanged(index);});

                }else if(!mViewModel.getCache().isEmpty()) {


                    MediaFile mediaFile = null;

                    for (int i = 0; i < mViewModel.getCache().size(); i++) {
                        if (MediaUtils.isSameMedia(requireActivity(), mViewModel.mUri, change, mViewModel.getCache().get(i).getId())) {
                            mediaFile = mViewModel.getCache().get(i);
                            mViewModel.getCache().remove(mediaFile);
                            break;
                        }
                    }

                    if(mediaFile != null) {
                        mViewModel.updateMedia(mediaFile, change);

                        mViewModel.addToList(mediaFile);
                        requireActivity().runOnUiThread(() -> mAdapter.setNewFiles(mViewModel.currentFiles()));
                    }else createMedia(change);

                }else createMedia(change);

            }).start();
        }

        private void createMedia(int change){
            //An entirely new file
            String select = MediaStore.MediaColumns._ID + "=" + change;
            List<Bundle> bundles = CursorUtilsKt.queryMediaStore(requireActivity(), mViewModel.mUri, select, null, null, mViewModel.mCursorColumns);
            List<MediaFile> fromBundle = mViewModel.createMediaItemsFromBundle(bundles);

            MediaUtils.loadThumbIntoCache(BuildUtilsKt.thumbnailPath(requireActivity()), fromBundle.get(0).getPath(), null);
            mViewModel.addToList(fromBundle.get(0));

            requireActivity().runOnUiThread(() -> mAdapter.setNewFiles(mViewModel.currentFiles()));
        }
    }
}

