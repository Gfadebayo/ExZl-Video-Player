package com.exzell.exzlvideoplayer.player

import android.media.*
import android.media.session.PlaybackState
import android.util.Log
import android.view.Surface
import com.exzell.exzlvideoplayer.BuildConfig.*
import com.exzell.exzlvideoplayer.MediaFile
import com.exzell.exzlvideoplayer.player.utils.getExtractorTracks
import com.exzell.exzlvideoplayer.player.utils.initCodec
import com.exzell.exzlvideoplayer.player.utils.makeAudioTrack
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class Player(val mMedia: MediaFile) {

    private lateinit var mAudioExecutor: ExecutorService
    private lateinit var mVideoExecutor: ExecutorService
    private var mMiscExecutor = Executors.newSingleThreadScheduledExecutor()

    private var mMediaSync: MediaSync? = MediaSync()
    private var mAudioTrack: AudioTrack? = null

    private var mVideoExtractor: MediaExtractor? = null
    private var mAudioExtractor: MediaExtractor? = null

    private var mAudioCodec: MediaCodec? = null
    private var mVideoCodec: MediaCodec? = null

    var state: PlayerState = PlayerState.PENIDNG
    var hasVideo = false
    var hasAudio = false

    lateinit var surface: Surface

    var loop = false
    var eos = false
    var exiting = false

    var onStreamComplete: OnStreamCompletedListener? = null


    fun setupPlayer(){
        val array = getExtractorTracks(mMedia.path)!!

        if(array[1] != -1){
            hasAudio = true
            mAudioExecutor = Executors.newSingleThreadExecutor()

            mAudioExtractor = MediaExtractor().apply {
                setDataSource(mMedia.path)
                selectTrack(array[1])
            }

            mAudioCodec = initCodec(mAudioExtractor!!.getTrackFormat(array[1]), callback())
            mAudioTrack = makeAudioTrack(mAudioExtractor!!.getTrackFormat(array[1]))

//            mAudioTrack.setBufferSizeInFrames(44100)
            mMediaSync!!.setAudioTrack(mAudioTrack)
        }

        if(array[0] != -1){
            mMediaSync!!.setSurface(surface)

            hasVideo = true
            mVideoExecutor = Executors.newSingleThreadExecutor()

            mVideoExtractor = MediaExtractor().apply {
                setDataSource(mMedia.path)
                selectTrack(array[0])
            }
            mVideoCodec = initCodec(mVideoExtractor!!.getTrackFormat(mVideoExtractor!!.sampleTrackIndex), callback(), mMediaSync!!.createInputSurface())

            mMediaSync!!.syncParams = changeSyncParams(mVideoExtractor!!.getTrackFormat(mVideoExtractor!!.sampleTrackIndex))
        }

        codecWatchers()
    }

    fun reconfigureCodec(){
        if(hasVideo){
            mVideoCodec!!.configure(mVideoExtractor!!.getTrackFormat(mVideoExtractor!!.sampleTrackIndex), surface, null, 0)
        }
        if(hasAudio){
            mAudioCodec!!.configure(mAudioExtractor!!.getTrackFormat(mAudioExtractor!!.sampleTrackIndex), null, null, 0)
        }
    }

    private fun codecWatchers(){
        mMediaSync!!.setCallback(object : MediaSync.Callback() {
            override fun onAudioBufferConsumed(sync: MediaSync, audioBuffer: ByteBuffer, bufferId: Int) {

                if(!hasVideo) {
                    if (eos && loop) restart()
                    else if (eos && !loop) onStreamComplete!!.streamComplete()

                    mMedia.position = sync.timestamp?.anchorMediaTimeUs!!
                }

                if(state.equals(PlayerState.PLAYING)) mAudioCodec!!.releaseOutputBuffer(bufferId, false)
            }

        }, null)

        if (hasVideo) {
            mVideoCodec!!.setOnFrameRenderedListener({ c, timeUs, timeNs ->
                if (eos && loop) restart()
                else if (eos && !loop) onStreamComplete!!.streamComplete()

                mMedia.position = timeUs
            }, null)
        }
    }

    private fun changeSyncParams(format: MediaFormat?) = SyncParams().apply {
        tolerance = 0.01f

        audioAdjustMode = SyncParams.AUDIO_ADJUST_MODE_DEFAULT
        syncSource = SyncParams.SYNC_SOURCE_DEFAULT

        if(hasVideo){
            val frames = format?.getInteger(MediaFormat.KEY_FRAME_RATE) ?: 30
            setFrameRate(frames.toFloat())
        }
    }

    fun stop(){
        state = PlayerState.STOPPED

//        mMedia.position = mMediaSync!!.timestamp?.anchorMediaTimeUs!!

        mMediaSync!!.playbackParams = PlaybackParams().setSpeed(0f)

        if(hasAudio) mAudioExecutor.shutdownNow()
        if(hasVideo) mVideoExecutor.shutdownNow()

//        flush()
    }

    fun start(){

//        if(state.equals(PlayerState.FLUSHING)) reconfigureCodec()
            if(hasAudio){
                if(mAudioExecutor.isShutdown) mAudioExecutor = Executors.newSingleThreadExecutor()
                mAudioExtractor!!.seekTo(mMedia.position, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                mAudioCodec!!.start()
            }

            if(hasVideo){
                if(mVideoExecutor.isShutdown) mVideoExecutor = Executors.newSingleThreadExecutor()
                mVideoExtractor!!.seekTo(mMedia.position, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                mVideoCodec!!.start()
            }

            mMediaSync!!.playbackParams = PlaybackParams().setSpeed(1.0f)

            state = PlayerState.PLAYING
    }

    fun resume() {
        if(hasAudio) mAudioExecutor = Executors.newSingleThreadScheduledExecutor()
        if(hasVideo) mVideoExecutor = Executors.newSingleThreadScheduledExecutor()

        mMediaSync!!.playbackParams = PlaybackParams().setSpeed(1.0f)
        state = PlayerState.PLAYING
    }

    fun restart(){
        flush()
        mMedia.position = 0
        start()
    }

    private fun flush(){
        mMiscExecutor.submit{

            if(hasAudio) {
                mAudioCodec!!.flush()
            }

            if(hasVideo) {
                mVideoCodec!!.flush()
            }

            mMediaSync!!.flush()
        }
        state = PlayerState.FLUSHING
    }

    fun seek(time: Long){
        if(hasAudio) mAudioExtractor!!.seekTo(time, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
        if(hasVideo) mVideoExtractor!!.seekTo(time, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
    }

    fun close(){
        Thread{
            if(hasAudio) {
                mAudioExecutor.shutdown()
                mAudioTrack?.release()
                mAudioCodec?.release()
                mAudioExtractor?.release()


                mAudioTrack = null
                mAudioCodec = null
                mAudioExtractor = null
            }

            if(hasVideo) {
                mVideoExecutor.shutdown()
                mVideoCodec?.release()
                mVideoExtractor?.release()

                mVideoCodec = null
                mVideoExtractor = null
            }

            mMediaSync?.release()
            mMediaSync = null
        }.start()

        exiting = true
    }

    fun getCurrentTime() = mMediaSync!!.timestamp?.anchorMediaTimeUs?: -1

    private fun callback() = object : MediaCodec.Callback() {

        override fun onOutputBufferAvailable(codec: MediaCodec, index: Int, info: MediaCodec.BufferInfo) {
            if(exiting || !state.equals(PlayerState.PLAYING)) return

            val mime = codec.inputFormat.getString(MediaFormat.KEY_MIME)!!
            val exe = if(mime.startsWith("video/")) mVideoExecutor else mAudioExecutor

            outputBuffer(exe, codec, index, info.presentationTimeUs, mime)
        }

        override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
            if(DEBUG) Log.w(TAG, "Output format changed to: " + format.toString())
            val mime = codec.inputFormat.getString(MediaFormat.KEY_MIME)!!
        }

        override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
            if(DEBUG) Log.i(TAG, "Error: " + e.message)
        }

        override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
            if(exiting || !state.equals(PlayerState.PLAYING)) return

            val mime = codec.inputFormat.getString(MediaFormat.KEY_MIME)!!
            val exe = if(mime.startsWith("video/")) mVideoExecutor else mAudioExecutor
            val extract = if(mime.startsWith("video/")) mVideoExtractor else mAudioExtractor

            inputBuffers(exe, codec, index, extract, mime)
        }

    }

    private fun inputBuffers(executor: ExecutorService, codec: MediaCodec, i: Int, extract: MediaExtractor?, mimeType: String){
        if(executor.isShutdown) return

        executor.submit{
            with(codec){
                val buffer = this.getInputBuffer(i)!!

                val size = extract!!.readSampleData(buffer, 0)

                if(size < 0) {
                    signalEndOfInputStream()
                    eos = true
                    return@submit
                }

                queueInputBuffer(i, 0, size, extract.sampleTime, 0)
                extract.advance()
            }
        }
    }

    private fun outputBuffer(executor: ExecutorService, codec: MediaCodec, i: Int, presentTime: Long, mimeType: String){
        if(eos || executor.isShutdown) return
        executor.submit{
            if(mimeType.startsWith("audio/"))
                mMediaSync!!.queueAudio(codec.getOutputBuffer(i)!!, i, presentTime)
            else
                codec.releaseOutputBuffer(i, presentTime * 1000)
        }
    }

    companion object{ const val TAG = "Player" }


    @FunctionalInterface
    interface OnStreamCompletedListener {
        fun streamComplete()
    }
}