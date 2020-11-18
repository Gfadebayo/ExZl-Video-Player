package com.exzell.exzlvideoplayer;

import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

//import com.exzell.exzlvideoplayer.player.CustomPlayer;
import com.exzell.exzlvideoplayer.player.CustomPlayer;
import com.exzell.exzlvideoplayer.player.Player;
import com.exzell.exzlvideoplayer.viewmodels.MainViewModel;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PlaybackActivity extends AppCompatActivity {

    public static final String TAG = "PlaybackActivity";
    private final String KEY_COMPLETE = "completed";
    private final String KEY_PENDING = "pending";
//    private File mVideoPlaying;
    private CustomPlayer mCustomPlayer;
    private Player mPlayer;
    private Map<String, LinkedList<MediaFile>> mPlayList;

    private Toolbar mToolbar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_playback);
        mToolbar = findViewById(R.id.toolbar1);
        setSupportActionBar(mToolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        mToolbar.setNavigationOnClickListener(v -> finish());

        mPlayList = new HashMap<>();
        mPlayList.put(KEY_COMPLETE, new LinkedList<>());
        mPlayList.put(KEY_PENDING, readReceivedFiles());

        mCustomPlayer = findViewById(R.id.player);

        initVideo(null);
        nextAndPrevious();
        this.setVolumeControlStream(AudioManager.STREAM_MUSIC);

//        mCustomPlayer.setOnVideoCompletedListener(new CustomPlayer.OnVideoCompletedListener() {
//            @Override
//            public void onVideoCompleted() {
//                nextFile();
//            }
//        });
    }

    private LinkedList<MediaFile> readReceivedFiles() {
        ArrayList<MediaFile> vids = new ArrayList<>();

        Uri uri = getIntent().getData();
        if (uri != null) {
            URI urii = null;
            try {
                urii = new URI(uri.getScheme(), uri.getAuthority(), uri.getPath(), uri.getQuery(), uri.getFragment());
            } catch (URISyntaxException urix) {
                urix.printStackTrace();
            }

            MediaFile f = new MediaFile(new File(urii).getPath());
            vids.add(f);

        } else vids = getIntent().getParcelableArrayListExtra(TAG);

        return new LinkedList<>(vids);
    }

    private void initVideo(MediaFile oldFile) {
        MediaFile currentFile;

        if (oldFile != null) {
            oldFile.setSelected(false);
            mPlayList.get(KEY_PENDING).removeFirstOccurrence(oldFile);
            mPlayList.get(KEY_COMPLETE).offerLast(oldFile);
        }

        currentFile = mPlayList.get(KEY_PENDING).peekFirst();
        currentFile.setSelected(true);

        if (currentFile == null) {
            finish();
        } else {
            if(mPlayer != null) mPlayer.close();
            mPlayer = new Player(currentFile);
            mCustomPlayer.setUpPlayer(mPlayer);
            mToolbar.setTitle(currentFile.getDisplayName());
        }
    }


    @Override
    protected void onStop() {
        super.onStop();
        mCustomPlayer.pause();
    }

    @Override
    protected void onResume() {
        super.onResume();
//        mCustomPlayer.resume();
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



    private void nextAndPrevious() {
        View.OnClickListener next = v -> nextFile();

        View.OnClickListener previous = v -> {
            if (mCustomPlayer.getPlayer().getCurrentTime() >= 3 * Math.pow(10, 6))
                mCustomPlayer.seekBarSeek(0);

            else {
                mPlayer.stop();
                if (mPlayList.get(KEY_COMPLETE).isEmpty()) return;

                MediaFile lastFile = mPlayList.get(KEY_COMPLETE).pollLast();
                mPlayList.get(KEY_PENDING).offerFirst(lastFile);
                initVideo(null);
            }
        };

        mCustomPlayer.setNextPrevListeners(next, previous);
    }

    private void nextFile(){
//        mCustomPlayer.stop();
        MediaFile currentFile = mCustomPlayer.getPlayer().getMMedia();
        if (mPlayList.get(KEY_PENDING).isEmpty()) return;
//        mCustomPlayer.reconfigureCodec();
        initVideo(currentFile);
    }
}