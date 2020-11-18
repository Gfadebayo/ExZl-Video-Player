package com.exzell.exzlvideoplayer.player;

import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;

import com.exzell.exzlvideoplayer.R;
import com.exzell.exzlvideoplayer.player.utils.AnimationExtKt;

import kotlin.Unit;


public class CustomPlayer extends FrameLayout {

    private Player mPlayer;

    private final String TAG = getClass().getSimpleName();
    private Context mContext;

    //Views
    private View mToolbar;
    private ImageView mPlayImage;
    private ImageView mNextImage;
    private ImageView mPrevImage;
    private ImageView mLoopImage;
    private SeekBar mSeekBar;
    private TextView mCurrentTime;
    private TextView mEndTime;
    private View mDecorView;

    private SurfaceView mSurfaceView;
    private Surface mSurface;


    //Thread Handlers
    private Handler mHandler;
    private Runnable mShowRunnable, mShowTimeoutRunnable;


    //Flags
    private boolean isLooping = false;
    private boolean isShowing = false;

    private Player.OnStreamCompletedListener mCompleteListener;


    private ObjectAnimator mSeekbarAnimator;


    public CustomPlayer(Context act) {
        super(act);
        initPlayer(act);
        resolveAttrs(act, null);
    }

    public CustomPlayer(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        initPlayer(context);
        resolveAttrs(context, attrs);
    }

    public CustomPlayer(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initPlayer(context);
        resolveAttrs(context, attrs);
    }

    public CustomPlayer(@NonNull Context act, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(act, attrs, defStyleAttr, defStyleRes);
        initPlayer(act);
        resolveAttrs(act, attrs);

    }

    private void resolveAttrs(Context ctx, AttributeSet attrs){
        TypedArray ta = ctx.obtainStyledAttributes(attrs, R.styleable.CustomPlayer);

        String resVal = ta.getString(R.styleable.CustomPlayer_hiddenViewIds);
        ta.recycle();
        if(resVal == null) return;

        String packageName = ctx.getResources().getResourcePackageName(R.attr.toolbarId);
        int id = ctx.getResources().getIdentifier(resVal, "id", packageName);

        mDecorView.addOnAttachStateChangeListener(new OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
                mToolbar = v.findViewById(id);
                mDecorView.removeOnAttachStateChangeListener(this);
            }

            @Override
            public void onViewDetachedFromWindow(View v) {

            }
        });
    }

    private void initPlayer(Context act){
        mContext = act;

        inflate(act, R.layout.exzl_player, this);

        mDecorView = ((Activity) act).getWindow().getDecorView();
        mPlayImage = findViewById(R.id.image_play);
        mNextImage = findViewById(R.id.image_next);
        mPrevImage = findViewById(R.id.image_previous);
        mSeekBar = findViewById(R.id.seek_progress);
        mCurrentTime = findViewById(R.id.text_time_start);
        mEndTime = findViewById(R.id.text_time_end);
        mLoopImage = findViewById(R.id.image_loop);
        mHandler = new Handler();


        addSurface();
        controlListeners();
        runnables();
    }

    private void addSurface() {
        mSurfaceView = findViewById(R.id.surface_player);
        mSurfaceView.setBackground(null);
        mSurfaceView.setZOrderMediaOverlay(true);
        mSurfaceView.setClickable(false);

        findViewById(R.id.control_root).setClickable(true);

        setOnClickListener((v) -> {
            if(isShowing) {
                hideControls();
                Toast.makeText(mContext, "Decor View hidden", Toast.LENGTH_SHORT).show();
                Log.i(TAG, "Decor View hidden");
            } else {
                showControls();
                Toast.makeText(mContext, "Decor View shown", Toast.LENGTH_SHORT).show();
                Log.i(TAG, "Decor View shown");
            }
        });

        surfaceCallback();
    }

    public void setPlayerCompleteListener(Player.OnStreamCompletedListener listener){
        mCompleteListener = listener;
    }

    private void runnables() {

        mShowTimeoutRunnable = () -> { if (isShowing) hideControls(); };
    }

    public void setUpPlayer(Player newPlayer) {
        mPlayer = newPlayer;
        mSeekBar.setMax((int) newPlayer.getMMedia().getDuration());
        if(mSurface != null) {
            mPlayer.setSurface(mSurface);
            mPlayer.setupPlayer();
            start();
        }
    }

    public Player getPlayer(){
        return mPlayer;
    }

    private void hideControls() {

        int uiOptions = getSystemUiVisibility();
        int newOptions = uiOptions;
        newOptions |= View.SYSTEM_UI_FLAG_FULLSCREEN;
        newOptions |= View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
        newOptions |= View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
        newOptions |= View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
        newOptions |= View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
        newOptions |= View.SYSTEM_UI_FLAG_IMMERSIVE;
//        newOptions |= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        mDecorView.setSystemUiVisibility(newOptions);

        findViewById(R.id.control_root).setVisibility(View.GONE);
        mToolbar.setVisibility(View.GONE);

        isShowing = false;
        mSeekbarAnimator.end();
        mHandler.removeCallbacks(mShowTimeoutRunnable);
    }

    public void showControls() {

        int uiOptions = getSystemUiVisibility();
        int newOptions = uiOptions;
        newOptions &= ~View.SYSTEM_UI_FLAG_FULLSCREEN;
        newOptions &= ~View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
        newOptions &= ~View.SYSTEM_UI_FLAG_IMMERSIVE;
        newOptions &= ~View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        mDecorView.setSystemUiVisibility(newOptions);

        findViewById(R.id.control_root).setVisibility(View.VISIBLE);
        mToolbar.setVisibility(View.VISIBLE);

        isShowing = true;

        updateControls();

        mHandler.postDelayed(mShowTimeoutRunnable, 60000);
    }

    private void updateControls() {
        if(mPlayer == null || !isShowing) return;


        long duration = mPlayer.getMMedia().getDuration() / 1000;

        //convert from micro to milli
        long position = mPlayer.getCurrentTime() / 1000;

        if (position >= duration) return;

        if (duration >= 0) {
            long seekProgress = (mSeekBar.getMax() * position) / duration;

            mCurrentTime.setText(timeFormat(position, false));
            mEndTime.setText(timeFormat(duration - position, true));
            mSeekbarAnimator.setIntValues((int) seekProgress);
            mSeekbarAnimator.start();
        }
    }

    public void start(){
        mPlayImage.setSelected(true);
        mPlayer.setOnStreamComplete(mCompleteListener);
        mPlayer.start();

        if(mSeekbarAnimator == null){
            mSeekbarAnimator = AnimationExtKt.animateSeekbar(mSeekBar, () -> {
                updateControls();
                return Unit.INSTANCE;
            });
        }

        ((Toolbar) mToolbar).setTitle(mPlayer.getMMedia().getDisplayName());

        hideControls();
    }

    public void pause(){
        mPlayImage.setSelected(false);
        mPlayer.stop();

        mSeekbarAnimator.pause();
    }

    public void resume() {
        mPlayImage.setSelected(true);
        mPlayer.resume();
        mSeekbarAnimator.start();
    }

    @SuppressLint("DefaultLocale")
    private String timeFormat(final long milli, final boolean hasMinus) {

                        float seconds = milli / 1000;
                        int secs = (int) seconds % 60;

                        int minute = (int) (seconds / 60) % 60;
                        int hours = (int) seconds / (3600);

                        String res;
                        if (hours > 0) res = String.format("%d:%02d:%02d", hours, minute, secs);
                        else res = String.format("%02d:%02d", minute, secs);

                        if (hasMinus) return new StringBuilder(res).insert(0, '-').toString();
                        else return res;
    }

    public void seekBarSeek(int prog) {
        long time = (prog * mPlayer.getMMedia().getDuration()) / mSeekBar.getMax();
        mPlayer.seek(time);
    }

    private void controlListeners() {

        mPlayImage.setOnClickListener(v -> {
            if(!mPlayer.getState().equals(PlayerState.PLAYING)) resume();
            else pause();
        });

        mLoopImage.setOnClickListener(v -> {
            isLooping = !isLooping;
            mPlayer.setLoop(isLooping);
            v.setEnabled(isLooping);
        });

        mSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    seekBarSeek(progress);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                    mHandler.removeCallbacks(mShowTimeoutRunnable);
                    mSeekbarAnimator.end();
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                mHandler.post(mShowTimeoutRunnable);
                updateControls();
            }
        });
    }

    public void setNextPrevListeners(View.OnClickListener next, View.OnClickListener previous) {
        mPrevImage.setOnClickListener(previous);
        mNextImage.setOnClickListener(next);

    }

    public void exitPlayer() {
        mPlayer.close();
        mSurface.release();
    }

    private void surfaceCallback() {

            mSurfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
                @Override
                public void surfaceCreated(SurfaceHolder holder) {

                    if(mSurface != null) return;

                    mSurface = holder.getSurface();
                    holder.setFormat(PixelFormat.TRANSPARENT);

                    mPlayer.setSurface(mSurface);
                    mPlayer.setupPlayer();

                    start();
                    holder.setKeepScreenOn(true);
                }

                @Override
                public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}

                @Override
                public void surfaceDestroyed(SurfaceHolder holder) {}
            });
    }
}
