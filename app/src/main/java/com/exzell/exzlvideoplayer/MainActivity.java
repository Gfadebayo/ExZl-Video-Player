package com.exzell.exzlvideoplayer;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.SearchManager;
import android.app.SearchableInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.util.TypedValue;
import android.view.ActionMode.Callback;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.SavedStateViewModelFactory;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.widget.ViewPager2;

import com.exzell.exzlvideoplayer.adapter.SectionsPagerAdapter;
import com.exzell.exzlvideoplayer.fragments.FragmentVideos;
import com.exzell.exzlvideoplayer.utils.BuildUtilsKt;
import com.exzell.exzlvideoplayer.utils.CursorUtilsKt;
import com.exzell.exzlvideoplayer.utils.MediaUtils;
import com.exzell.exzlvideoplayer.viewmodels.MainViewModel;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.schedulers.Schedulers;

@SuppressLint("InflateParams")
public class MainActivity extends AppCompatActivity {
    private final String TAG = this.getClass().getSimpleName();
    private SectionsPagerAdapter mAdapter;
    private ViewPager2 mPager;
//    private MainViewModel mViewModel;
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
        setSupportActionBar(toolbar);

        if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
        } else start();
    }

    //Granting permissions is required to start any operation
    //if the user denies, nothing will be done
    private void start(){
        mLPM = new LocalPersistenceManager(getSharedPreferences(
                LocalPersistenceManager.PREF_NAME, Context.MODE_PRIVATE));

//        if (mLPM.getDialogComplete()) {
//        }

        initViews();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if(requestCode == 1){
            for(int i = 0; i < permissions.length; i++){
                if(permissions[i].equals(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        && grantResults[i] == PackageManager.PERMISSION_GRANTED) start();
            }
        }
    }

    private void initViews() {
        mExFab = findViewById(R.id.fab_current_file);

        mAdapter = new SectionsPagerAdapter(MainActivity.this);

        mPager = findViewById(R.id.view_pager);
        mTabs = findViewById(R.id.tabs);

        TabLayoutMediator.TabConfigurationStrategy stra = (tab, position) -> {
            if (position == 0) tab.setText(R.string.video);
            else tab.setText(R.string.audio);
        };

        mTabMed = new TabLayoutMediator(mTabs, mPager, stra);

        setUpPager();
    }

    private void setUpPager() {
        mPager.setOffscreenPageLimit(2);
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

        mTabMed.attach();

//        mPager.setCurrentItem(1);
//        mPager.setCurrentItem(0);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable("adapter", mAdapter.saveState());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_videos, menu);

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {

        TypedValue tv = new TypedValue();
        getTheme().resolveAttribute(R.attr.headerColor, tv, true);
        ColorStateList color = ColorStateList.valueOf(tv.data);

        for(int i = 0; i < menu.size(); i++){

            Drawable icon = menu.getItem(i).getIcon();
            if(icon != null) icon.setTintList(color);
        }
//        final android.widget.SearchView searchView = (android.widget.SearchView) menu.findItem(R.id.action_search).getActionView();
//        searchView.setIconified(true);
//
//        searchView.setOnSearchClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                searchView.setIconified(true);
//                startActionMode(performSearch());
////                SearchView sv = (SearchView) searchMode.getCustomView();
//
//            }
//        });
        return super.onPrepareOptionsMenu(menu);
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

    @Override
    public void onBackPressed() {
        if (mCurrentFragment == null) mCurrentFragment = (FragmentVideos) mAdapter
                .getCurrentFragment(mPager.getCurrentItem());

        MainViewModel model = new ViewModelProvider(mCurrentFragment, new SavedStateViewModelFactory(getApplication(), this)).get(MainViewModel.class);
        if (!model.hasPrevious()) finish();
        else mCurrentFragment.initPreviousAdapter();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
//        if (isFinishing()) stopService(mServiceIntent);
    }
}
