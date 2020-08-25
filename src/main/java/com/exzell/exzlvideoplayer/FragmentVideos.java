package com.exzell.exzlvideoplayer;

import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ConfigurationInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
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
import androidx.core.content.res.ConfigurationHelper;
import androidx.core.os.ConfigurationCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.SavedStateViewModelFactory;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.selection.DefaultSelectionTracker;
import androidx.recyclerview.selection.ItemDetailsLookup;
import androidx.recyclerview.selection.SelectionPredicates;
import androidx.recyclerview.selection.SelectionTracker;
import androidx.recyclerview.selection.StorageStrategy;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import com.exzell.exzlvideoplayer.adapter.VideoFileAdapter;
import com.exzell.exzlvideoplayer.adapter.tracker.MediaKeyProvider;
import com.exzell.exzlvideoplayer.listeners.OnFileChangedListener;
import com.exzell.exzlvideoplayer.utils.DialogUtils;
import com.exzell.exzlvideoplayer.utils.MediaUtils;
import com.exzell.exzlvideoplayer.utils.ObserverUtils;
import com.exzell.exzlvideoplayer.utils.ThumbnailUtils;
import com.exzell.exzlvideoplayer.viewmodels.MainViewModel;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FragmentVideos extends Fragment implements OnFileChangedListener {

    private final String TAG = this.getClass().getSimpleName();
    private MainViewModel mViewModel;
    private Map<MediaFile, List<MediaFile>> mFileInfos;

    private RecyclerView mRecyclerView;
    private GridLayoutManager mGridManager;
    private LinearLayoutManager mLinearManager;
    private VideoFileAdapter mAdapter;

    private boolean isForVideos;
    private ExecutorService mExecutor;
    private SelectionTracker mTracker;
    private ExtendedFloatingActionButton mExFab;
    private SelectionTracker.SelectionObserver mObserver;
    private ActionMode mActionMode;

    private boolean startDialogComplete = false;


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
        mExecutor = Executors.newFixedThreadPool(6);
        MediaFile.submitThumbnailDir(requireContext().getExternalCacheDir());
        mViewModel = new ViewModelProvider(requireActivity(),
                new SavedStateViewModelFactory(requireActivity().getApplication(), requireActivity()))
                .get(MainViewModel.class);
        ObserverUtils.getInstance().setObserverListeners(FragmentVideos.this);

        unravelBundle();
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
        mRecyclerView.setHasFixedSize(true);

        mGridManager = new GridLayoutManager(requireContext(), 2, GridLayoutManager.VERTICAL, false);
        mLinearManager = new LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false);

        if (mViewModel.getLayoutManager()) mRecyclerView.setLayoutManager(mLinearManager);
        else mRecyclerView.setLayoutManager(mGridManager);

        readUsingUri();

        mAdapter.setHasStableIds(true);
        mRecyclerView.setAdapter(mAdapter);

        mExFab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean b = sendFileToPlayer(null, (LinkedList<String>) mViewModel.getSelectedFiles().getValue());
                if (!b)
                    Toast.makeText(requireContext(), "Previously Selected File not Found", Toast.LENGTH_SHORT).show();
            }
        });

        buildTracker(savedInstanceState);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        mTracker.onSaveInstanceState(outState);
//        mViewModel.cacheFiles(mAdapter.getFiles(), isForVideos);
    }

    private void buildTracker(final Bundle savedState) {
        requireActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                String id = "track" + mAdapter.getItemId(0);
                mTracker = new DefaultSelectionTracker.Builder<>(id, mRecyclerView,
                        new MediaKeyProvider(mRecyclerView), setLookup(), StorageStrategy.createLongStorage())
                        .withSelectionPredicate(SelectionPredicates.<Long>createSelectAnything())
                        .build();

                mAdapter.passTracker(mTracker);

                registerObserver();

                mTracker.addObserver(mObserver);

                if (savedState != null) mTracker.onRestoreInstanceState(savedState);
            }
        });
    }

    private ItemDetailsLookup<Long> setLookup() {
        return new ItemDetailsLookup<Long>() {
            @Nullable
            @Override
            public ItemDetails<Long> getItemDetails(@NonNull MotionEvent e) {
                View v = mRecyclerView.findChildViewUnder(e.getX(), e.getY());

                VideoFileAdapter.ViewHolder holder = (VideoFileAdapter.ViewHolder) mRecyclerView.findContainingViewHolder(v);

                if (holder != null) return holder.setDetails();

                return null;
            }
        };
    }

    @SuppressLint("SetTextI18n")
    private void registerObserver() {
        mObserver = new SelectionTracker.SelectionObserver() {

            @Override
            public void onItemStateChanged(@NonNull Object key, boolean selected) {
                int count = mRecyclerView.getAdapter().getItemCount();
                int size = mTracker.getSelection().size();
                if (size >= 1) {
                    if (size == 1 && mActionMode == null)
                        mActionMode = requireActivity().startActionMode(startActionMode());

                    String title = size + "/" + count + " Selected";
                    mActionMode.setTitle(title);
                    mExFab.setText(Integer.toString(size));

                    String filePath = ((VideoFileAdapter.ViewHolder) mRecyclerView.findViewHolderForItemId((Long) key)).filePath;
                    selectFiles(filePath);
                } else if (size == 0 && !selected) mActionMode.finish();
            }
        };

    }

    @Override
    public void onPause() {
        super.onPause();
        if (mActionMode != null) mActionMode.finish();
    }

//    private void switchRatios() {
//
//        if (isForVideos) return;
//
//        CircularRevealCardView cv = requireActivity().findViewById(R.id.card_image);
//        int width = cv.getWidth();
//        int hei = cv.getHeight();
//
//        ViewGroup.LayoutParams layoutParams = cv.getLayoutParams();
//        layoutParams.width = hei;
//        layoutParams.height = (int) requireContext().getResources().getDimension(R.dimen.image_height);
//        cv.requestLayout();
//    }

    @Override
    public void onCreateOptionsMenu(Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.menu_videos, menu);

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
                order = MediaFile.Sort.NAME;
                ret = restartFrag = true;
                break;
            case R.id.action_date:
                item.setChecked(true);
                order = MediaFile.Sort.DATE;
                ret = restartFrag = true;
                break;
            case R.id.action_size:
                item.setChecked(true);
                order = MediaFile.Sort.SIZE;
                ret = restartFrag = true;
                break;
            case R.id.action_duration:
                item.setChecked(true);
                order = MediaFile.Sort.DURATION;
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
            List<MediaFile> files = mAdapter.getFiles();
            MediaUtils.sort(mViewModel.getSortOrder(), mViewModel.isAscending(), files);
            mAdapter.notifyItemRangeChanged(0, files.size());
        }

        return ret;
    }

    public void getFileName(@NonNull View v) {

        final VideoFileAdapter.ViewHolder holder = (VideoFileAdapter.ViewHolder) mRecyclerView.getChildViewHolder(v);
        requireActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                MediaFile checker = new MediaFile(holder.filePath);
                if (!mFileInfos.keySet().contains(checker)) {
                    String pat = holder.filePath;
                    sendFileToPlayer(pat, null);

                } else {
                    List<MediaFile> files = mFileInfos.get(checker);
                    MediaUtils.sort(mViewModel.getSortOrder(), mViewModel.isAscending(), files);
                    mViewModel.cacheFiles(mAdapter.getFiles(), isForVideos);
                    mAdapter.setNewFiles(files);

                    onBackPressed();

                    ((Toolbar) requireActivity().findViewById(R.id.toolbar)).setTitle(holder.mTextName.getText().toString());

//            mViewModel.setCurrentAdapter(newAdapter, isForVideos);
//            ((FragmentPager) getParentFragment()).destroyAndRecreate();
                }
            }
        });
    }

    private void readUsingUri() {
        requireActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Uri volumeExternal;
                if (isForVideos) volumeExternal = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                else volumeExternal = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;

                String[] requiredColumns = {MediaStore.Audio.Media.DISPLAY_NAME,
                        MediaStore.Audio.Media.DATA, MediaStore.Audio.Media.SIZE};

                List<MediaFile> file = mViewModel.getCachedFile(isForVideos);

//                if(viewModelAdapter != null) mAdapter = viewModelAdapter;
                if (file != null)
                    mAdapter = new VideoFileAdapter(file, requireActivity(), mViewModel.getLayoutManager());
                else {
                    List<Bundle> media = mViewModel.cursorQuery(volumeExternal, null, MediaStore.Audio.Media.DISPLAY_NAME, requiredColumns);

                    unwrapBundleAndCreateMediaInfos(media);
                    ArrayList<MediaFile> parents = new ArrayList<>(mFileInfos.keySet());
                    MediaUtils.sort(mViewModel.getSortOrder(), mViewModel.isAscending(), parents);
                    mAdapter = new VideoFileAdapter(requireActivity(), parents, mViewModel.getLayoutManager());
                }

                onBackPressed();
            }
        });
//        switchRatios();
    }

    private void unwrapBundleAndCreateMediaInfos(final List<Bundle> mediaFiles) {
        Map<MediaFile, List<MediaFile>> parents = new HashMap<>();

        try {
            parents =
                    mExecutor.submit(new Callable<Map<MediaFile, List<MediaFile>>>() {
                        final Map<MediaFile, List<MediaFile>> in = new HashMap<>();

                        @Override
                        public Map<MediaFile, List<MediaFile>> call() throws Exception {
                            final String data = MediaStore.MediaColumns.DATA;
                            final String size = MediaStore.MediaColumns.SIZE;
                            final String add = MediaStore.MediaColumns.DATE_ADDED;

                            for (Bundle b : mediaFiles) {
                                String path = new File(b.getString(data)).getParent();
                                final MediaFile chi = new MediaFile(b.getString(data), b.getInt(size), b.getInt(add));
//                                new Thread(new Runnable() {
//                                    @Override
//                                    public void run() {
//                                        Bitmap thumbnail = MediaUtils.getFileThumbnail(requireContext().getExternalCacheDir(), chi.getDisplayName(), maxImageWidth, maxImageHeight);
//                                        long duration = Long.parseLong(MediaUtils.getMediaDuration(chi.getPath())) * 1000;
//
//                                        if (thumbnail != null) chi.setThumbnail(thumbnail);
//                                        chi.setDuration(duration);
//
//                                    }
//                                }).start();
                                MediaFile parent = new MediaFile(path);

                                List<MediaFile> paren = in.get(parent);
                                if (paren == null) paren = new ArrayList<>(mediaFiles.size());
                                paren.add(chi);
                                in.put(parent, paren);
                            }

                            return in;
                        }
                    }).get();
        } catch (ExecutionException | InterruptedException | CancellationException e) {
            e.printStackTrace();
        }

        parentFileSize(parents);
        mFileInfos = parents;
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {

        super.onConfigurationChanged(newConfig);
        Log.i(TAG, "Configuration Changed");
        changeConfig(newConfig);
    }

    private void changeConfig(Configuration newOrientation){
        int orientation = newOrientation.orientation;
        if (mRecyclerView.getLayoutManager() instanceof GridLayoutManager) {
            if (orientation == Configuration.ORIENTATION_LANDSCAPE)
                mGridManager.setSpanCount(3);
            else mGridManager.setSpanCount(2);
        }
    }

    private boolean sendFileToPlayer(final String pat, final LinkedList<String> pats) {
        final ArrayList<Boolean> ret = new ArrayList<>(1);
        ret.add(true);
        requireActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Intent play = new Intent();
                play.setClass(requireActivity(), PlaybackActivity.class);

                if (pat != null) {
                    LinkedList<String> file = new LinkedList<>();
                    file.add(pat);
//                        mViewModel.setSelectedFile(file);
                    play.putExtra(PlaybackActivity.TAG, new ArrayList<>(file));
                } else if (pats != null && !pats.isEmpty()) {
//                        mViewModel.setSelectedFile(pats);
                    play.putExtra(PlaybackActivity.TAG, new ArrayList<>(pats));
                } else {
                    ret.set(0, false);
                }
//                    mViewModel.setCurrentAdapter(mAdapter, isForVideos);
                if (ret.get(0)) startActivity(play);
            }
        });
        return ret.get(0);
    }

    private void selectFiles(String path) {
        if (isForVideos) {
            mViewModel.getSelectedFiles().getValue().offerLast(path);
            fabWiggle();
        } else mViewModel.getSelectedFiles().getValue().remove(path);
    }

    public void onBackPressed() {
        requireActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toolbar bar = requireActivity().findViewById(R.id.toolbar);
//                int adaptersSize = mViewModel.usedAdaptersSize(isForVideos);
                int adaptersSize = mViewModel.cachedFilesSize(isForVideos);
                if (adaptersSize >= 1) {
                    bar.setNavigationIcon(com.google.android.material.R.drawable.abc_ic_ab_back_material);
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
        });

    }

    public List<MediaFile> initPreviousAdapter() {

        //by default this method returns a videofileadapter, but that is being changed to accommodate a selection tracker
//        VideoFileAdapter currentAdapter = mViewModel.getCurrentAdapter(isForVideos);
        List<MediaFile> file = mViewModel.getCachedFile(isForVideos);
        if (file == null) return null;

        mAdapter.setNewFiles(file);

        onBackPressed();

        return file;

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
                if(layout) {
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

                mAdapter.setManager(!layout);
                mViewModel.setLayoutManager(!layout);
//                changeConfig();
            }
        });
    }

    private void parentFileSize(final Map<MediaFile, List<MediaFile>> file) {

        for (MediaFile f : file.keySet()) {

            long size = 0;
            List<MediaFile> child = file.get(f);
            if (child == null) continue;
            for (MediaFile fi : child) {
                size += fi.getSize();
            }
            f.setSize(size);
        }
    }

    @Override
    public void onFileChanged(String changedFile) {

        if (!changedFile.equals("")) {
            //a new file has been added or renamed
            File fi = new File(changedFile);
            List<MediaFile> childs = mFileInfos.get(new MediaFile(fi.getParent()));
            int changedChild = MediaUtils.changedFileIndex(childs);

            if (changedChild != -1) {
                //if the stray thumbnail is null, we simply use the thumbnail in the path that is now invalid
                String strayThumb = ThumbnailUtils.getUtils().getThumbnailDir();
                if (strayThumb == null) strayThumb = childs.get(changedChild).getThumbnailPath();

                if (strayThumb != null) MediaUtils.renameFile(null, strayThumb, fi.getName());

                MediaFile up = childs.get(changedChild);

                up.updateFilePath(changedFile);

                MediaUtils.sort(mViewModel.getSortOrder(), mViewModel.isAscending(), childs);
            } else {
                MediaFile added = new MediaFile(fi.getPath());
                childs.add(added);

            }
        } else {
            //a file has been deleted or renamed
            MediaFile parent = MediaUtils.changedParentIndex(mFileInfos);
            if (parent == null) return;
            List<MediaFile> children = mFileInfos.get(parent);
            int changedIndex = MediaUtils.changedFileIndex(children);
            MediaFile toBeRemoved = children.get(changedIndex);
            ThumbnailUtils.getUtils().setThumbnailDir(toBeRemoved.getThumbnailPath());
            children.remove(changedIndex);
            ThumbnailUtils.getUtils().removeThumbnail(new Handler());

        }

        mAdapter.notifyDataSetChanged();
    }

    private Callback startActionMode() {
        return new Callback() {
            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                mExFab.extend();
                return true;
            }

            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                return false;
            }

            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                return false;
            }

            @Override
            public void onDestroyActionMode(ActionMode mode) {
                Log.i(TAG, "Action mode destroyed");
                mTracker.clearSelection();
                mExFab.shrink();
                mExFab.setText("0");
                mActionMode = null;

            }
        };
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if(!requireActivity().isFinishing()) mViewModel.cacheFiles(mAdapter.getFiles(), isForVideos);
    }
}

