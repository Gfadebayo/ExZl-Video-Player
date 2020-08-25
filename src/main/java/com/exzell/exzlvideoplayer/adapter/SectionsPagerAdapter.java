package com.exzell.exzlvideoplayer.adapter;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.exzell.exzlvideoplayer.FragmentVideos;
import com.exzell.exzlvideoplayer.R;

/**
 * A [FragmentPagerAdapter] that returns a fragment corresponding to
 * one of the sections/tabs/pages.
 */
public class SectionsPagerAdapter extends FragmentStateAdapter {

    @StringRes
    private static final int[] TAB_TITLES = new int[]{R.string.video, R.string.audio};
    private FragmentActivity mActivity;


    public SectionsPagerAdapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
        mActivity = fragmentActivity;
    }

    public Fragment getCurrentFragment(int position) {
        return mActivity.getSupportFragmentManager()
                .findFragmentByTag("f" + getItemId(position));
    }


    @NonNull
    @Override
    public Fragment createFragment(int position) {
        // getItem is called to instantiate the fragment for the given page.
        // Return a PlaceholderFragment (defined as a static inner class below).

        return FragmentVideos.newInstance(TAB_TITLES[position]);
    }

    @Override
    public int getItemCount() {
        // Show 2 total pages.
        return TAB_TITLES.length;
    }
}