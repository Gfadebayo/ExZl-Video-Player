package com.exzell.exzlvideoplayer.utils;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaCodecList;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Handler;
import android.view.Surface;

import java.io.IOException;
import java.util.ArrayList;

public class PlayerUtils {


    public static ArrayList<Integer> createExtractor(String fileUri) {
        MediaExtractor ex = new MediaExtractor();
        try {
            ex.setDataSource(fileUri);
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }

        ArrayList<Integer> index = new ArrayList<>(3);
        for (int i = 0; i < 3; i++) index.add(-1);

        int trackCount = ex.getTrackCount();
        for (int i = 0; i < trackCount; i++) {
            MediaFormat trackFormat = ex.getTrackFormat(i);
            String mime = trackFormat.getString(MediaFormat.KEY_MIME);

            if (mime.startsWith("video/")) index.set(0, i);
            else index.add(1, i);
        }

        ex.release();

        return index;
    }

    public static MediaCodec initCodec(MediaFormat form, MediaCodec.Callback callback, Handler handle, Surface face) {
        MediaCodec codec = null;
        String decoderForFormat = new MediaCodecList(MediaCodecList.ALL_CODECS).findDecoderForFormat(form);
        try {
            if (decoderForFormat != null) codec = MediaCodec.createByCodecName(decoderForFormat);
            else codec = MediaCodec.createDecoderByType(form.getString(MediaFormat.KEY_MIME));
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }

        codec.setCallback(callback, handle);

        codec.configure(form, face, null, 0);
        if (face != null) codec.setVideoScalingMode(MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT);

        return codec;
    }

    private static AudioFormat getAudioFormat(MediaFormat mf) {
        AudioFormat.Builder build = new AudioFormat.Builder();

        if (mf.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
            build.setSampleRate(mf.getInteger(MediaFormat.KEY_SAMPLE_RATE));
        } else build.setSampleRate(44100);

        if (mf.containsKey(MediaFormat.KEY_CHANNEL_MASK)) {
            build.setChannelMask(mf.getInteger(MediaFormat.KEY_CHANNEL_MASK));
        } else build.setChannelMask(AudioFormat.CHANNEL_OUT_STEREO);

        if (mf.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
            build.setEncoding(mf.getInteger(MediaFormat.KEY_PCM_ENCODING));
        } else build.setEncoding(AudioFormat.ENCODING_PCM_16BIT);


        return build.build();
    }

    public static AudioTrack makeAudioTrack(MediaFormat mf) {
        AudioFormat af = getAudioFormat(mf);
        AudioAttributes at = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                .setLegacyStreamType(AudioManager.STREAM_MUSIC)
                .build();

        int minBuff = AudioTrack.getMinBufferSize(af.getSampleRate(), af.getChannelMask(), af.getEncoding());
        AudioTrack.Builder build = new AudioTrack.Builder();

        build.setAudioAttributes(at)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setAudioFormat(af)
                .setSessionId(AudioManager.AUDIO_SESSION_ID_GENERATE)
                .setBufferSizeInBytes(minBuff);

        return build.build();
    }


}
