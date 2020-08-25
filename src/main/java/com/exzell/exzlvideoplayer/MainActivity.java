package com.exzell.exzlvideoplayer;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.SearchManager;
import android.app.SearchableInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.util.Log;
import android.view.ActionMode.Callback;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.SavedStateViewModelFactory;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.widget.ViewPager2;

import com.exzell.exzlvideoplayer.adapter.SectionsPagerAdapter;
import com.exzell.exzlvideoplayer.services.MediaObserverService;
import com.exzell.exzlvideoplayer.utils.DialogUtils;
import com.exzell.exzlvideoplayer.utils.MediaUtils;
import com.exzell.exzlvideoplayer.viewmodels.MainViewModel;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@SuppressLint("InflateParams")
public class MainActivity extends AppCompatActivity {
    private final String TAG = this.getClass().getSimpleName();
    private SectionsPagerAdapter mAdapter;
    private ViewPager2 mPager;
    private MainViewModel mViewModel;
    private final Thread thumbThread = new Thread(new ThumbThread());
    private Intent mServiceIntent;
    private FragmentVideos mCurrentFragment;
    private ExtendedFloatingActionButton mExFab;
    private TabLayout mTabs;
    private TabLayoutMediator mTabMed;
    private LocalPersistenceManager mLPM;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle("Files");
        setSupportActionBar(toolbar);

        while (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
        }

        initHelpers();

        if (mLPM.getDialogComplete()) {
            thumbThread.start();
            DialogUtils.startDialog(this, onShowDialog());
        }

        initViews();

    }

    private DialogInterface.OnShowListener onShowDialog() {
        return new DialogInterface.OnShowListener() {
            @Override
            public void onShow(final DialogInterface dialog) {

                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        while (true) {
                            if (mLPM.getDialogComplete()) {
                                dialog.cancel();
                                break;
                            }
                        }
                    }
                }).start();
            }
        };
    }

    private void initHelpers() {
        mServiceIntent = new Intent();
        mServiceIntent.setClass(this, MediaObserverService.class);
        startService(mServiceIntent);

        mViewModel = new ViewModelProvider(this, new SavedStateViewModelFactory
                (getApplication(), this))
                .get(MainViewModel.class);
        mViewModel.initHandle();
        mViewModel.registerPrefObservers(this, new Handler());

        mLPM = new LocalPersistenceManager(getSharedPreferences(
                LocalPersistenceManager.PREF_NAME, Context.MODE_PRIVATE));
    }

    private void initViews() {
        mExFab = findViewById(R.id.fab_current_file);
        mExFab.shrink();

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mAdapter = new SectionsPagerAdapter(MainActivity.this);

                mPager = findViewById(R.id.view_pager);
                mTabs = findViewById(R.id.tabs);
                TabLayoutMediator.TabConfigurationStrategy stra = new TabLayoutMediator.TabConfigurationStrategy() {
                    @Override
                    public void onConfigureTab(@NonNull TabLayout.Tab tab, int position) {
                        if (position == 0) tab.setText(R.string.video);
                        else tab.setText(R.string.audio);
                    }
                };
                mTabMed = new TabLayoutMediator(mTabs, mPager, true, stra);

                setUpPager();
            }
        });

    }

    private void setUpPager() {
        mPager.setAdapter(mAdapter);
        mPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {

            @Override
            public void onPageSelected(int position) {

                FragmentVideos frag = (FragmentVideos) mAdapter.getCurrentFragment(position);

                if (frag == null) return;

                mCurrentFragment = frag;
                if (frag.isAdded()) frag.onBackPressed();
            }
        });

//        mPager.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                mTabMed.detach();
//                mTabMed.attach();
//            }
//        });
        mTabMed.attach();

        mPager.setCurrentItem(1);
        mPager.setCurrentItem(0);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable("adapter", mAdapter.saveState());
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {

        final android.widget.SearchView searchView = (android.widget.SearchView) menu.findItem(R.id.action_search).getActionView();
        searchView.setIconified(true);

        searchView.setOnSearchClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                searchView.setIconified(true);
                startActionMode(performSearch());
//                SearchView sv = (SearchView) searchMode.getCustomView();

            }
        });
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() != R.id.action_download) return false;

         EditText ed = (EditText) getLayoutInflater().inflate(R.layout.switch_item,
                null, false);
        DialogUtils.downloadLinkDialog(this, ed);
        return true;
    }

    private Callback performSearch() {

        return new android.view.ActionMode.Callback() {
            androidx.appcompat.widget.SearchView sView = (androidx.appcompat.widget.SearchView)
                    getLayoutInflater().inflate(R.layout.action_search,
                            null, false);

            @Override
            public boolean onCreateActionMode(android.view.ActionMode mode, Menu menu) {
                mode.setCustomView(sView);
                SearchManager searchM = getSystemService(SearchManager.class);
                ComponentName cn = new ComponentName(MainActivity.this, SearchActivity.class);
                SearchableInfo searchInfo = searchM.getSearchableInfo(cn);

                sView.setSearchableInfo(searchInfo);

                return true;
            }

            @Override
            public boolean onPrepareActionMode(android.view.ActionMode mode, Menu menu) {
                sView.setIconified(false);
                LinearLayout edit = sView.findViewById(androidx.appcompat.R.id.search_edit_frame);
                View icon = edit.findViewById(androidx.appcompat.R.id.search_mag_icon);
                edit.removeView(icon);
                return true;
            }

            @Override
            public boolean onActionItemClicked(android.view.ActionMode mode, MenuItem item) {
                return false;
            }

            @Override
            public void onDestroyActionMode(android.view.ActionMode mode) {
            }
        };
    }

    public void getFileName(View view) {

        if (mCurrentFragment == null)
            mCurrentFragment = (FragmentVideos) mAdapter.getCurrentFragment(mPager.getCurrentItem());
        int no = Integer.parseInt(mExFab.getText().toString());
        if (no == 0) {
            try {
                mCurrentFragment.getFileName(view);
            } catch (Exception e) {
                e.printStackTrace();
                Log.d(TAG, "Failed again");
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (mCurrentFragment == null) mCurrentFragment = (FragmentVideos) mAdapter
                .getCurrentFragment(mPager.getCurrentItem());
        if (mCurrentFragment.initPreviousAdapter() == null) finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isFinishing()) stopService(mServiceIntent);
    }

    private class ThumbThread implements Runnable {

        private List<Bundle> mediaFiles = new ArrayList<>(100);

        @Override
        public void run() {
            getFiles();
            loadThumbnails();
        }

        private void loadThumbnails() {
            for (Bundle file : mediaFiles) {
                File media = new File(file.getString(MediaStore.Audio.Media.DATA));

                MediaUtils.loadThumbIntoCache(MainActivity.this.getExternalCacheDir(), media.getPath(), null);
            }

            mLPM.setStartDialogComplete(true);
        }

        private void getFiles() {
            Uri vidUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
            Uri audioUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
            String query = MediaStore.Audio.Media.DATA;

            mediaFiles.addAll(mViewModel.cursorQuery(vidUri, null, query, query));
            mediaFiles.addAll(mViewModel.cursorQuery(audioUri, null, query, query));

        }
    }

}
