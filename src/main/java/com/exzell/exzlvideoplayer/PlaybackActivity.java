package com.exzell.exzlvideoplayer;

import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.exzell.exzlvideoplayer.viewmodels.MainViewModel;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

public class PlaybackActivity extends AppCompatActivity {

    public static final String TAG = "PlaybackActivity";
    private final String KEY_COMPLETE = "completed";
    private final String KEY_PENDING = "pending";
    private MainViewModel mViewModel;
//    private File mVideoPlaying;
    private CustomPlayer mCustomPlayer;
    private Set<File> mFiles;
    private Map<String, LinkedList<String>> mPlayList;
//    private AppBarLayout mDefaultToolbar;
//    private WindowManager.LayoutParams defaultParams;
//    private Runnable mExitRunnable;
//    private boolean isExiting;
    private Toolbar mToolbar;
    private AlertDialog mDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_playback);
        mToolbar = findViewById(R.id.toolbar1);
        setSupportActionBar(mToolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        mPlayList = new HashMap<>();
        mPlayList.put(KEY_COMPLETE, new LinkedList<String>());
        mPlayList.put(KEY_PENDING, readReceivedFiles());

        mCustomPlayer = new CustomPlayer(this);
        initVideo(null);
        nextAndPrevious();
        playbackRateDialog();
        this.setVolumeControlStream(AudioManager.STREAM_MUSIC);

        mCustomPlayer.setOnVideoCompletedListener(new CustomPlayer.OnVideoCompletedListener() {
            @Override
            public void onVideoCompleted() {
                nextFile();
            }
        });
    }

    private LinkedList<String> readReceivedFiles() {
        ArrayList<String> vids = new ArrayList<>();

        Uri uri = getIntent().getData();
        if (uri != null) {
            URI urii = null;
            try {
                urii = new URI(uri.getScheme(), uri.getAuthority(), uri.getPath(), uri.getQuery(), uri.getFragment());
            } catch (URISyntaxException urix) {
                urix.printStackTrace();
            }

            vids.add(new File(urii).getPath());

        } else vids = getIntent().getStringArrayListExtra(TAG);

        return new LinkedList<>(vids);
    }

    private void initVideo(String name) {
        String currentFile;

        if (name != null) {
            mPlayList.get(KEY_PENDING).removeFirstOccurrence(name);
            mPlayList.get(KEY_COMPLETE).offerLast(name);
        }

        currentFile = mPlayList.get(KEY_PENDING).peekFirst();

        if (currentFile == null) {
            finish();
            return;
        } else {
            mCustomPlayer.setUpPlayer(new File(currentFile));
            if(name != null) mCustomPlayer.reconfigureCodec();
            mCustomPlayer.startPlaying();
        }

        Drawable drawable = getDrawable(R.drawable.ic_arrow_back_black_24dp);
        drawable.setTint(Color.WHITE);
        mToolbar.setNavigationIcon(drawable);

        String fileName = new File(mPlayList.get(KEY_PENDING).peekFirst()).getName();
        mToolbar.setTitle(fileName);

    }


    @Override
    protected void onStop() {
        super.onStop();
        mCustomPlayer.pause();
    }

    @Override
    public void onBackPressed() {
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mCustomPlayer.exitPlayer();
    }

    //    private void modifyConfig(){
//
//        AppBarLayout toolBarLay = (AppBarLayout) getLayoutInflater().inflate(R.layout.activity_main, null, false).findViewById(R.id.toolbar).getParent();
//
//        ((Toolbar) toolBarLay.getChildAt(0)).setTitle(fileName);
//        mDefaultToolbar.setBackground(null);
//    }

    private void nextAndPrevious() {
        final View.OnClickListener next = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                nextFile();
            }
        };

        View.OnClickListener previous = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mCustomPlayer.getCurrentPosition() >= 3 * Math.pow(10, 6))
                    mCustomPlayer.seekBarSeek(0);
                else {
                    mCustomPlayer.stop();
                    if (mPlayList.get(KEY_COMPLETE).isEmpty()) return;
                    String lastFile = mPlayList.get(KEY_COMPLETE).pollLast();
                    mPlayList.get(KEY_PENDING).offerFirst(lastFile);
                    initVideo(null);
                }
            }
        };

        mCustomPlayer.setNextPrevListeners(next, previous);
    }

    private void nextFile(){
        mCustomPlayer.stop();
        String currentFile = mCustomPlayer.getFilePath();
        if (mPlayList.get(KEY_PENDING).isEmpty()) return;
//        mCustomPlayer.reconfigureCodec();
        initVideo(currentFile);
    }

    private void playbackRateDialog() {

        mDialog = new MaterialAlertDialogBuilder(this)
                .create();
        mDialog.getWindow().setGravity(Gravity.BOTTOM | Gravity.END);
        mDialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialog) {

                dialogListeners();
                ((EditText) PlaybackActivity.this.mDialog.findViewById(R.id.edit_text_speed)).setText(editSpeed(mCustomPlayer.getSpeed()));
            }
        });

        mToolbar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mDialog.show();
                mDialog.setContentView(R.layout.view_playback_speed);
            }
        });
//        dialogListeners();
    }

    private void dialogListeners() {
        View.OnClickListener onClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (v.getId() == R.id.image_add) {
                    mCustomPlayer.increaseSpeed(true);
                } else mCustomPlayer.increaseSpeed(false);
                ((EditText) mDialog.findViewById(R.id.edit_text_speed)).setText(editSpeed(mCustomPlayer.getSpeed()));
            }
        };


        mDialog.findViewById(R.id.image_add).setOnClickListener(onClick);
        mDialog.findViewById(R.id.image_subtract).setOnClickListener(onClick);
    }

    private <T> String editSpeed(T texts) {
        String text = texts.toString();
        StringBuilder textBuild = new StringBuilder(text);
        int startIndex = 3;
        if (text.charAt(2) == '0') startIndex = 1;

        return textBuild.delete(startIndex, text.length()).append("x").toString();
    }
}