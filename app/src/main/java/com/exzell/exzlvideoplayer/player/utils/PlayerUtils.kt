package com.exzell.exzlvideoplayer.player.utils

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Handler
import android.view.Surface

import java.io.IOException

fun getExtractorTracks(fileUri: String): IntArray? {
        val ex = MediaExtractor()
        try {
            ex.setDataSource(fileUri)

            val arr = intArrayOf(-1, -1, -1)

            val trackCount = ex.trackCount
            for (i in 0 until trackCount) {
                val trackFormat = ex.getTrackFormat(i)
                val mime = trackFormat.getString(MediaFormat.KEY_MIME)

                if (mime!!.startsWith("video/")) {
                    arr[0] = i
                    continue
                }else if(mime.startsWith("audio/")){
                    arr[1] = i
                    continue
                }
            }

            ex.release()
            return arr

        }catch (ioe: IOException) {
            ioe.printStackTrace()
            return null
        }
    }

    fun initCodec(form: MediaFormat, callback: MediaCodec.Callback, face: Surface? = null, handle: Handler? = null): MediaCodec {
        var codec: MediaCodec? = null
        val decoderForFormat = MediaCodecList(MediaCodecList.ALL_CODECS).findDecoderForFormat(form)
        try {
            if (decoderForFormat != null)
                codec = MediaCodec.createByCodecName(decoderForFormat)
            else
                codec = MediaCodec.createDecoderByType(form.getString(MediaFormat.KEY_MIME)!!)
        } catch (ioe: IOException) {
            ioe.printStackTrace()
        }

        codec!!.setCallback(callback, handle)

        codec.configure(form, face, null, 0)

        return codec
    }

    private fun getAudioFormat(mf: MediaFormat): AudioFormat {
        val build = AudioFormat.Builder()


        if (mf.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
            val rate = mf.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            build.setSampleRate(rate)
        } else
            build.setSampleRate(44100)

        if (mf.containsKey(MediaFormat.KEY_CHANNEL_MASK)) {
            val mask = mf.getInteger(MediaFormat.KEY_CHANNEL_MASK)
            build.setChannelMask(mask)
        } else
            build.setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)

        if (mf.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
            val encode = mf.getInteger(MediaFormat.KEY_PCM_ENCODING)
            build.setEncoding(encode)
        } else
            build.setEncoding(AudioFormat.ENCODING_PCM_16BIT)


        return build.build()
    }

    fun makeAudioTrack(mf: MediaFormat): AudioTrack {
        val af = getAudioFormat(mf)
        val at = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                .setLegacyStreamType(AudioManager.STREAM_MUSIC)
                .build()


        val minBuff = AudioTrack.getMinBufferSize(af.sampleRate, af.channelMask, af.encoding)
        val build = AudioTrack.Builder()

        build.setAudioAttributes(at)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setAudioFormat(af)
                .setSessionId(AudioManager.AUDIO_SESSION_ID_GENERATE)
                .setBufferSizeInBytes(minBuff)

        return build.build()
    }
