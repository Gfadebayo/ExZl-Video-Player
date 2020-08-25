package com.exzell.exzlvideoplayer;

import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaSync;
import android.media.PlaybackParams;
import android.media.SyncParams;
import android.os.Handler;
import android.util.Log;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;

import com.exzell.exzlvideoplayer.utils.PlayerUtils;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


class CustomPlayer {
    private final SyncParams mSyncParams;
    private OnVideoCompletedListener mCompleteListener;


    //Player state
    private final int SYNC_PLAYING = 0;
    private final int SYNC_PAUSED = 1;
    private final int SYNC_STOPPED = 3;
    // --Commented out by Inspection (16/08/2020 4:44 PM):private final int SYNC_COMPLETED = 4;
    private final int SYNC_PAUSED_SEEKING = 5;
    private final String TAG = getClass().getSimpleName();
    private final Context mContext;


    //Views
    private final View videoPlayer;
    private final ImageView mPlayImage;
    private final ImageView mNextImage;
    private final ImageView mPrevImage;
    private final ImageView mLoopImage;
    private final SeekBar mSeekBar;
    private final TextView mCurrentTime;
    private final TextView mEndTime;
    private final View mDecorView;
    private final WindowManager mManager;
    private final ExecutorService mExecutor;
    private final PlaybackParams mPlaybackParams;
    private final long playbackRate = 0;
    private int CURRENT_SYNC_STATE = -1;
    private File mSourceFile;
    private SurfaceView mSurfaceView;
    private WindowManager.LayoutParams mDefaultDecorParams;


    //Thread Handlers
    private Handler mHandler;
    private Runnable mProgressRunnable, mShowRunnable, mShowTimeoutRunnable;


    //Media Classes
    private MediaExtractor mVideoExtractor;
    private MediaExtractor mAudioExtractor;
    private MediaCodec mVideoCodec;
    private MediaCodec mAudioCodec;
    private MediaSync mMediaSync;
    private ArrayList<Integer> trackIndexes;
    private Surface mSurface;
    private AudioTrack mAudioTrack;


    //Flags
    private boolean isFlushing;
    private boolean isLooping = false;
    private boolean isEOS = false;
    private boolean isShowing = false;
    private boolean hasVideo = true;
    private boolean hasAudio = true;
    private long seekTimeout = 0;
    private long currentPausePosition;
    private Long renderDifference;
    private float frameRate;
    private int audioSampleRate;
    private long prevSampleTime = 0;
    private float sleepRate = 1f;


    public CustomPlayer(Activity act) {
        mContext = act;
        Window mWindow = act.getWindow();
        mDecorView = mWindow.getDecorView();
        mManager = act.getWindowManager();
        videoPlayer = mDecorView.findViewById(R.id.frame_player);
        mPlayImage = videoPlayer.findViewById(R.id.image_play);
        mNextImage = videoPlayer.findViewById(R.id.image_next);
        mPrevImage = videoPlayer.findViewById(R.id.image_previous);
        mSeekBar = videoPlayer.findViewById(R.id.seek_progress);
        mCurrentTime = videoPlayer.findViewById(R.id.text_time_start);
        mEndTime = videoPlayer.findViewById(R.id.text_time_end);
        mLoopImage = videoPlayer.findViewById(R.id.image_loop);
        mHandler = new Handler();
        mExecutor = Executors.newCachedThreadPool();
        mSyncParams = new SyncParams();
        mPlaybackParams = new PlaybackParams();


        addSurface();
        surfaceCallback();
        childListeners();
        runnables();
        decorListener();
    }

    private void addSurface() {
        mSurfaceView = new SurfaceView(mContext);
        mSurfaceView.setBackground(null);
        mSurfaceView.setZOrderMediaOverlay(false);

        WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams();
        layoutParams.height = layoutParams.width = WindowManager.LayoutParams.MATCH_PARENT;
        layoutParams.flags = WindowManager.LayoutParams.FLAG_FULLSCREEN | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        layoutParams.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING;
        layoutParams.format = PixelFormat.TRANSLUCENT;


        mManager.addView(mSurfaceView, layoutParams);
        mDecorView.setBackground(null);
        try {
            mManager.removeView(mDecorView);
        } catch (Exception e) {
            e.printStackTrace();
        }

        mDecorView.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
                initDecorParams();
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
            }
        });


        mSurfaceView.setOnTouchListener(new View.OnTouchListener() {

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                int action = event.getActionMasked();
                if (action != MotionEvent.ACTION_DOWN) return false;
                mHandler.post(mShowRunnable);
                return true;
            }

        });
    }

    private void initDecorParams() {
        if (mDefaultDecorParams != null) return;

        mDefaultDecorParams = (WindowManager.LayoutParams) mDecorView.getLayoutParams();
        mDefaultDecorParams.format = PixelFormat.TRANSLUCENT;
        mDefaultDecorParams.flags = WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
                | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
                | WindowManager.LayoutParams.FLAG_LAYOUT_ATTACHED_IN_DECOR
                | WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR;

        mDefaultDecorParams.type = WindowManager.LayoutParams.TYPE_APPLICATION;

        Toolbar bar = mDecorView.findViewById(R.id.toolbar1);
        if (bar.getTitle().toString().equals(mSourceFile.getName())) return;
        bar.setTitle(mSourceFile.getName());
    }

    private void runnables() {

        mProgressRunnable = new Runnable() {
            @Override
            public void run() {
                updateControls();
            }
        };

        mShowRunnable = new Runnable() {
            @Override
            public void run() {
                if (isShowing) hide();
                else show();
            }
        };

        mShowTimeoutRunnable = new Runnable() {
            @Override
            public void run() {
                if (isShowing) hide();
            }
        };
    }

    public String getFilePath() {
        return mSourceFile.getPath();
    }

    public void setUpPlayer(final File videoFile) {
        mSourceFile = videoFile;
        mMediaSync = new MediaSync();
        trackIndexes = PlayerUtils.createExtractor(videoFile.getAbsolutePath());
        //video always occupy index 0, so a value of -1(default value) means there isnt a video
        if (trackIndexes.get(0) == -1) hasVideo = false;
        if (trackIndexes.get(1) == -1) hasAudio = false;

        if (hasAudio) {
            try {
                mAudioExtractor = new MediaExtractor();
                mAudioExtractor.setDataSource(videoFile.getAbsolutePath());
                mAudioExtractor.selectTrack(trackIndexes.get(1));
                mAudioTrack = PlayerUtils.makeAudioTrack(mAudioExtractor.getTrackFormat(trackIndexes.get(1)));
                mMediaSync.setAudioTrack(mAudioTrack);
                audioSampleRate = mAudioTrack.getSampleRate();
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        }

        if (hasVideo) {
            try {
                mVideoExtractor = new MediaExtractor();
                mVideoExtractor.setDataSource(videoFile.getAbsolutePath());
                mVideoExtractor.selectTrack(trackIndexes.get(0));
                frameRate = mVideoExtractor.getTrackFormat(trackIndexes.get(0)).getInteger(MediaFormat.KEY_FRAME_RATE);
                mSyncParams.setFrameRate(frameRate).setTolerance(1 / frameRate);
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        }
        mMediaSync.setPlaybackParams(mPlaybackParams.setSpeed(0f));

        setSync();
    }

    public void startPlaying() {

        mExecutor.submit(new Runnable() {
            @Override
            public void run() {

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    ie.printStackTrace();
                }

                changeSpeed(1.f);
                CURRENT_SYNC_STATE = SYNC_PLAYING;

                if (hasAudio) mAudioCodec.start();
                if (hasVideo) mVideoCodec.start();


                updateControls();

//                seekBarAnimate();
//                mHandler.post(mShowRunnable);
//                updateControls();
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        animateProgress();
                    }
                }, 1000);
            }
        });

        mPlayImage.setImageDrawable(mContext.getDrawable(R.drawable.ic_pause_black_24dp));

//        if (mSurface != null) reconfigureCodec();
    }

    public void reconfigureCodec() {
        if (hasAudio) mAudioCodec = PlayerUtils.initCodec(mAudioExtractor.getTrackFormat
                (trackIndexes.get(1)), setCodecCallback(), mHandler, null);

        if (hasVideo) mVideoCodec = PlayerUtils.initCodec(mVideoExtractor.getTrackFormat(
                trackIndexes.get(0)), setCodecCallback(), mHandler, mSurface);
    }

    public void pause() throws IllegalStateException {

        if (!hasVideo) {
            mAudioTrack.pause();
            mAudioTrack.flush();
            mMediaSync.flush();
            Log.i(TAG, "Wrong method called");
        } else {
            mExecutor.submit(new Runnable() {
                @Override
                public void run() {
//                    if (CURRENT_SYNC_STATE != SYNC_PLAYING && CURRENT_SYNC_STATE != SYNC_PAUSED_SEEKING) throw new IllegalStateException("Wrong State");
                    currentPausePosition = mMediaSync.getTimestamp().getAnchorMediaTimeUs();

                    isFlushing = true;
                    if (hasAudio) {
                        mAudioExtractor.seekTo(currentPausePosition, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
                        mAudioCodec.flush();
                    }

                    if (hasVideo) {
                        mVideoExtractor.seekTo(currentPausePosition, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
                        mVideoCodec.flush();
                    }

                    mMediaSync.flush();
                }
            });
        }

        CURRENT_SYNC_STATE = SYNC_PAUSED;
    }

    public void resume() throws IllegalStateException {

//        if (!hasVideo) {
////            mAudioTrack.play();
////            mAudioCodec.start();
////            isFlushing = false;
//            seekBarSeek(-1);
//        } else {
            Log.i(TAG, "Current State is " + CURRENT_SYNC_STATE);
            mExecutor.submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        Thread.sleep(20);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    if (hasAudio) mAudioCodec.start();
                    if (hasVideo) mVideoCodec.start();
                    isFlushing = false;
                }
            });
//        }
        CURRENT_SYNC_STATE = SYNC_PLAYING;
    }

    public void stop() {
        mMediaSync.flush();
        mMediaSync.release();

        if (hasVideo) {
            mVideoCodec.reset();
            mVideoExtractor.release();
        }
        if (hasAudio) {
            mAudioCodec.reset();
            mAudioTrack.pause();
            mAudioTrack.flush();
            mAudioTrack.release();
            mAudioExtractor.release();
        }

        CURRENT_SYNC_STATE = SYNC_STOPPED;
    }

    private void loop() {

        Log.i(TAG, "Loop reached" + mPlaybackParams.getSpeed());
        if (hasVideo) mVideoExtractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
        if (hasAudio) mAudioExtractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);

        isEOS = false;

        flushAndRestart();
    }

    private void show() {
        try {
            mManager.removeView(mDecorView);
        } catch (Exception e) {
            Log.d(TAG, "failed to remove view: " + e);
        }

        mManager.addView(mDecorView, mDefaultDecorParams);
        Log.i(TAG, "Added Decor view");
        isShowing = true;
        updateControls();

        mHandler.postDelayed(mShowTimeoutRunnable, 5000);

    }

    public void hide() {
        mManager.removeView(mDecorView);
        mHandler.removeCallbacks(mProgressRunnable);
        mHandler.removeCallbacks(mShowRunnable);
        mHandler.removeCallbacks(mShowTimeoutRunnable);
        isShowing = false;
    }

    private void updateControls() {
        if (mMediaSync == null) return;
        MediaFormat trackFormat;

        if (hasVideo) trackFormat = mVideoExtractor.getTrackFormat(trackIndexes.get(0));
        else trackFormat = mAudioExtractor.getTrackFormat(trackIndexes.get(1));
        long duration = trackFormat.getLong(MediaFormat.KEY_DURATION) / 1000;

        //convert from micro to milli
        long position;
        if (hasVideo) position = mVideoExtractor.getSampleTime() / 1000;
        else position = mAudioExtractor.getSampleTime() / 1000;

        if (position >= duration) return;

        if (duration >= 0) {
            long seekProgress = mSeekBar.getMax() * position / duration;
            mSeekBar.setProgress((int) seekProgress);
            mCurrentTime.setText(timeFormat(position, false));
            mEndTime.setText(timeFormat(duration - position, true));
        }
        animateProgress();

        if (hasAudio) mHandler.postDelayed(mProgressRunnable, 1000);
        else mHandler.postDelayed(mProgressRunnable, 37);
    }

    private void animateProgress(){
        int p = mSeekBar.getProgress();
        ObjectAnimator.ofInt(mSeekBar, "progress", mSeekBar.getMax()).setDuration(mVideoExtractor.getTrackFormat(trackIndexes.get(0)).getLong(MediaFormat.KEY_DURATION) / 1000).start();
    }

    public void setOnVideoCompletedListener(OnVideoCompletedListener ovcl){
        mCompleteListener = ovcl;
    }

    @SuppressLint("DefaultLocale")
    private String timeFormat(final long milli, final boolean hasMinus) {
        String time = null;

        try {
            time = mExecutor.submit(new Callable<String>() {

                @Override
                public String call() throws Exception {
                    try {
                        float seconds = milli / 1000;
                        int secs = (int) seconds % 60;

                        int minute = (int) (seconds / 60) % 60;
                        int hours = (int) seconds / (3600);

                        String res;
                        if (hours > 0) res = String.format("%d:%02d:%02d", hours, minute, secs);
                        else res = String.format("%02d:%02d", minute, secs);

                        if (hasMinus) return new StringBuilder(res).insert(0, '-').toString();
                        else return res;
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    return null;
                }
            }).get();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return time;
    }

    void seekBarSeek(int prog) {

        long dur;
        if (hasVideo) dur = mVideoExtractor.getTrackFormat(trackIndexes.get(0))
                .getLong(MediaFormat.KEY_DURATION);

        else dur = mAudioExtractor.getTrackFormat(trackIndexes.get(1))
                .getLong(MediaFormat.KEY_DURATION);

        long currProgInMicro;
        if (prog != -1) currProgInMicro = dur * prog / mSeekBar.getMax();
            //in case playback rate is changed this can be used to resynchronize
        else currProgInMicro = mAudioExtractor.getSampleTime();

        //so we can view the video progress as we seek even in a paused state
        if (CURRENT_SYNC_STATE == SYNC_PAUSED || CURRENT_SYNC_STATE == SYNC_PAUSED_SEEKING && hasVideo) {
            isFlushing = false;
            currentPausePosition = currProgInMicro;
            CURRENT_SYNC_STATE = SYNC_PAUSED_SEEKING;
//            mVideoExtractor.advance();
        }

        if (hasVideo) mVideoExtractor.seekTo(currProgInMicro, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
        if (hasAudio) mAudioExtractor.seekTo(currProgInMicro, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);

        flushAndRestart();
    }

    private void flushAndRestart() {
        isFlushing = true;

        if (hasVideo) mVideoCodec.flush();
        if (hasAudio) {
            mAudioCodec.flush();
            mAudioTrack.pause();
            mAudioTrack.flush();
        }
        mMediaSync.flush();

        mExecutor.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(100 + seekTimeout);
                    Log.i(TAG, "Start thread sleeping");
                } catch (InterruptedException ie) {
                    ie.printStackTrace();
                }


//                if(CURRENT_SYNC_STATE == SYNC_PAUSED) {
                if (hasVideo) mVideoCodec.start();
                if (hasAudio) mAudioCodec.start();
                isFlushing = false;
                Log.e(TAG, " Invalid End of start thread");
                //                }
            }
        });

        seekTimeout += 10;
//        if (seekTimeout > 1000) seekTimeout = 0;
//        if(CURRENT_SYNC_STATE != SYNC_PAUSED) CURRENT_SYNC_STATE = SYNC_PLAYING;
    }

    public long getCurrentPosition() {
        return mMediaSync.getTimestamp().getAnchorMediaTimeUs();
    }

    private void changePlayButtonState() {

        if (CURRENT_SYNC_STATE == SYNC_PAUSED || CURRENT_SYNC_STATE == SYNC_PAUSED_SEEKING) {
            resume();
            mPlayImage.setImageDrawable(mContext.getDrawable(R.drawable.ic_pause_black_24dp));
            updateControls();
            mHandler.post(mShowRunnable);

        } else {
            pause();
            mPlayImage.setImageDrawable(mContext.getDrawable(R.drawable.ic_play_arrow_black_24dp));
            mHandler.removeCallbacks(mProgressRunnable);
        }
    }

    private void childListeners() {

        mPlayImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                changePlayButtonState();
            }
        });

        mLoopImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                int color = mContext.getColor(R.color.colorHighlight);
                if (!isLooping) {
                    isLooping = true;
                    v.setBackgroundTintList(ColorStateList.valueOf(color));
                } else {
                    isLooping = false;
                    v.setBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));
                }

            }
        });

        mSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    Log.i(TAG, "Time to seek");
//                    if(hasVideo) currentPausePosition = mVideoExtractor.getSampleTime();
//                    else currentPausePosition = mAudioExtractor.getSampleTime();
                    seekBarSeek(progress);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                    mHandler.removeCallbacks(mProgressRunnable);
                    mHandler.removeCallbacks(mShowTimeoutRunnable);
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                mHandler.post(mShowTimeoutRunnable);
                seekTimeout = 0;
                updateControls();
            }
        });

    }

    private void changeSpeed(float speed) {
        mPlaybackParams.setSpeed(speed);
        mMediaSync.setPlaybackParams(mPlaybackParams);
    }

    public void exitPlayer() {
        isFlushing = true;
//        mVideoCodec.signalEndOfInputStream();

        new Thread(new Runnable() {
            @Override
            public void run() {
//                mHandler.removeCallbacks(mProgressRunnable);
//                mHandler.removeCallbacks(mShowRunnable);
//                mHandler.removeCallbacks(mShowTimeoutRunnable);
                mHandler.removeCallbacksAndMessages(null);
                mHandler = null;

//                    mMediaSync.setAudioTrack(null);
                mMediaSync.flush();
                mMediaSync.release();
                mMediaSync = null;
                Log.i(TAG, "Media Sync released");
            }
        }).start();


        if (hasVideo) {

            new Thread(new Runnable() {
                @Override
                public void run() {

                    mVideoExtractor.release();
                    mVideoExtractor = null;
                    Log.i(TAG, "Video Extractor released");

//                    mVideoCodec.flush();
//                    mVideoCodec.stop();
                    mVideoCodec.release();
                    mVideoCodec = null;
                    Log.i(TAG, "Video Codec released");

                    mSurface.release();
                    mSurface = null;
                    Log.i(TAG, "Surface released");
                }
            }).start();
        }

        if (mAudioExtractor != null) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    mAudioTrack.release();
                    mAudioTrack = null;
                    Log.i(TAG, "Audiotrack released");

                    mAudioExtractor.unselectTrack(1);
                    mAudioExtractor.release();
                    mAudioExtractor = null;
                    Log.i(TAG, "Audio Extractor released");

                    mAudioCodec.stop();
                    mAudioCodec.release();
                    mAudioCodec = null;
                    Log.i(TAG, "Audio Codec released");
                }
            }).start();
        }

        try {
            mManager.removeViewImmediate(mSurfaceView);
            mManager.addView(mDecorView, mDefaultDecorParams);
        }catch(Exception e){e.printStackTrace();}

    }

    private void surfaceCallback() {
        mExecutor.submit(new Runnable() {

            @Override
            public void run() {
                mSurfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        holder.setFormat(PixelFormat.TRANSPARENT);
                        mMediaSync.setSurface(holder.getSurface());
                        holder.setKeepScreenOn(true);

                        initSurfaceWaiters();
                    }

                    @Override
                    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                    }

                    @Override
                    public void surfaceDestroyed(SurfaceHolder holder) {
                    }
                });
            }
        });
    }

    private void initSurfaceWaiters() {
        if (mSurface != null) return;

        mExecutor.submit(new Runnable() {
            @Override
            public void run() {
                mSurface = mMediaSync.createInputSurface();
                if (hasAudio)
                    mAudioCodec = PlayerUtils.initCodec(mAudioExtractor.getTrackFormat(trackIndexes.get(1)), setCodecCallback(), mHandler, null);
                if (hasVideo) {
                    mVideoCodec = PlayerUtils.initCodec(mVideoExtractor.getTrackFormat(trackIndexes.get(0)), setCodecCallback(), mHandler, mSurface);
                    mMediaSync.setSyncParams(mSyncParams.setSyncSource(SyncParams.SYNC_SOURCE_VSYNC).setAudioAdjustMode(SyncParams.AUDIO_ADJUST_MODE_STRETCH));
                } else
                    mMediaSync.setSyncParams(mSyncParams.setSyncSource(SyncParams.SYNC_SOURCE_AUDIO).setAudioAdjustMode(SyncParams.AUDIO_ADJUST_MODE_STRETCH));


                mVideoCodec.setOnFrameRenderedListener(new MediaCodec.OnFrameRenderedListener() {
                    @Override
                    public void onFrameRendered(@NonNull MediaCodec codec, long presentationTimeUs, long nanoTime) {
                        if (isEOS) mHandler.removeCallbacks(mProgressRunnable);
                        if (isEOS) {
                            if(isLooping) loop();
                            else mCompleteListener.onVideoCompleted();
                        }
                    }
                }, mHandler);
            }
        });
    }

    public void setNextPrevListeners(View.OnClickListener next, View.OnClickListener previous) {
        mPrevImage.setOnClickListener(previous);
        mNextImage.setOnClickListener(next);
    }

    private void decorListener() {
        View.OnClickListener mDecorListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mHandler.post(mShowRunnable);
            }
        };

        mDecorView.findViewById(R.id.constraint_player).setOnClickListener(mDecorListener);
    }

    private void setSync() {
        mMediaSync.setCallback(new MediaSync.Callback() {
            @Override
            public void onAudioBufferConsumed(@NonNull MediaSync sync, @NonNull ByteBuffer audioBuffer, int bufferId) {
                if (isFlushing) return;
                if (CURRENT_SYNC_STATE != SYNC_PLAYING) return;

                if (hasAudio) mAudioCodec.releaseOutputBuffer(bufferId, false);

            }
        }, mHandler);
    }

    private MediaCodec.Callback setCodecCallback() {

        return new MediaCodec.Callback() {
            @Override
            public void onInputBufferAvailable(@NonNull MediaCodec codec, int index) {
                readBuffers(codec, index);
            }

            @Override
            public void onOutputBufferAvailable(@NonNull MediaCodec codec, int index, @NonNull MediaCodec.BufferInfo info) {
                getOutputBuffers(codec, index, info);
            }

            @Override
            public void onError(@NonNull MediaCodec codec, @NonNull MediaCodec.CodecException e) {
            }

            @Override
            public void onOutputFormatChanged(@NonNull MediaCodec codec, @NonNull MediaFormat format) {
            }
        };

    }

    private void readBuffers(final MediaCodec code, final int index) {

        if (isFlushing || CURRENT_SYNC_STATE == SYNC_PAUSED_SEEKING) {
            Log.i(TAG, "Invalid Flushing error for input " + code.getName());
            return;
        }
        if (currentPausePosition == 0) {

            try {
                MediaFormat inputFormat = code.getInputFormat();
                String mime = inputFormat.getString(MediaFormat.KEY_MIME);

                ByteBuffer buff = code.getInputBuffer(index);
                MediaExtractor me;

                if (mime.startsWith("video/")) {
                    me = mVideoExtractor;
                } else {
                    me = mAudioExtractor;
                }
                int sampleSize = me.readSampleData(buff, 0);

                if (sampleSize < 0) {
                    try {
                        Log.i(TAG, "End of stream reached for " + mime);
                        isEOS = true;
                        code.signalEndOfInputStream();
                        return;
                    } catch (Exception e) {
                        e.printStackTrace();
                        return;
                    }
                } else {
                    code.queueInputBuffer(index, 0, sampleSize, me.getSampleTime(), 0);
                }
                if (mime.startsWith("video")) setRenderDifference(me.getSampleTime());

                me.advance();
            } catch (Exception e) {
                e.printStackTrace();
                Log.d(TAG, "Error in Input Buffer Computation");
            }
        } else {
            Log.i(TAG, "Reached in Input with a pause position of " + currentPausePosition + " sample time of " + mVideoExtractor.getSampleTime());
            ByteBuffer inputBuffer = code.getInputBuffer(index);
            int samSize = mVideoExtractor.readSampleData(inputBuffer, 0);
            if (samSize < 0) return;
            code.queueInputBuffer(index, 0, samSize, mVideoExtractor.getSampleTime(), 0);
            mVideoExtractor.advance();
            if (mVideoExtractor.getSampleTime() >= currentPausePosition) {
                currentPausePosition = 0;
                Log.i(TAG, "Pause position updated");
            }
        }

    }

    private void getOutputBuffers(final MediaCodec code, final int index, final MediaCodec.BufferInfo info) {
        if (isFlushing) {
            Log.i(TAG, "Invalid Flushing error in output for " + code.getName());
            return;
        }
        if (currentPausePosition == 0) {
            try {
                String mime = code.getOutputFormat(index).getString(MediaFormat.KEY_MIME);

                if (!hasAudio) {
                    try {
                        if (renderDifference == null) Thread.sleep(0);
                        else Thread.sleep(37 - (int) ((sleepRate - 1) * 37));
                    } catch (InterruptedException ie) {
                        ie.printStackTrace();
                    }
                }

                ByteBuffer outBuff = code.getOutputBuffer(index);

                long presentationTimeUs = info.presentationTimeUs;

                if (mime.startsWith("video/"))
                    code.releaseOutputBuffer(index, presentationTimeUs * (1000 + playbackRate));
                else mMediaSync.queueAudio(outBuff, index, presentationTimeUs);
            } catch (IllegalStateException e) {
                e.printStackTrace();
            }
        } else {
            try {
                code.releaseOutputBuffer(index, false);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void setRenderDifference(long currentSampleTime) {
        if (currentSampleTime == 0) return;
        if (renderDifference == null) renderDifference = 0L;

        renderDifference = Math.abs(currentSampleTime - prevSampleTime);
        prevSampleTime = currentSampleTime;
    }

    public void increaseSpeed(boolean increment) {
        float currentMultiplier;

        try {
            if (hasAudio)
                currentMultiplier = (float) mAudioTrack.getPlaybackRate() / audioSampleRate;
            else currentMultiplier = sleepRate;

            if (increment) currentMultiplier += 0.1;
            else currentMultiplier -= 0.1;

            if (checkRateValidity(currentMultiplier)) return;

            if (hasAudio) {
                int newRate = (int) (currentMultiplier * audioSampleRate);
                Log.i(TAG, "New rate is" + Float.valueOf(newRate).intValue());
                mAudioTrack.setPlaybackRate(newRate);
                Log.i(TAG, "New speed is " + currentMultiplier * 44100);
            } else {
                sleepRate = currentMultiplier;
            }
        } catch (Exception e) {
            Log.d(TAG, "Could not change " + e);
        }
    }

    private void makePlaybackToast(boolean type) {
        if (type) Toast.makeText(mContext, "Max speed reached", Toast.LENGTH_SHORT).show();
        else Toast.makeText(mContext, "Min speed reached", Toast.LENGTH_SHORT).show();
    }

    private boolean checkRateValidity(float curr) {
        boolean shouldReturn = false;
        if (hasAudio) {
            int maxRate = AudioTrack.getNativeOutputSampleRate(AudioTrack.MODE_STREAM);
            if (curr * audioSampleRate > 2 * maxRate) {
                makePlaybackToast(true);
                shouldReturn = true;
            }
        } else {
            if (curr >= 2) makePlaybackToast(true);
            if (curr < 0) makePlaybackToast(false);
            if (curr >= 2 || curr < 0) shouldReturn = true;
        }

        return shouldReturn;
    }

    public float getSpeed() {
        float currSpeed;
        if (hasAudio) {
            currSpeed = (float) mAudioTrack.getPlaybackRate() / audioSampleRate;
        } else currSpeed = sleepRate;
        Log.i(TAG, "Speed is " + currSpeed + "and sleep rate " + sleepRate);
        return (currSpeed);
    }


    interface OnVideoCompletedListener{
        void onVideoCompleted();
    }

}
