package com.equationl.screenrecord.utils

import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.projection.MediaProjection
import android.view.Surface

class ScreenRecorder(
    private var width: Int,
    private var height: Int,
    private val frameRate: Int,
    private val dpi: Int,
    private val mediaProjection: MediaProjection?,
    private val savePath: String
) {
    private var encoder: MediaCodec? = null
    private var surface: Surface? = null
    private var muxer: MediaMuxer? = null
    private var muxerStarted = false
    private var videoTrackIndex = -1
    private val bufferInfo = MediaCodec.BufferInfo()
    private var virtualDisplay: VirtualDisplay? = null

    private var isStop = false

    /**
     * 停止录制
     * */
    fun stop() {
        isStop = true
    }

    /**
     * 开始录制
     * */
    fun start() {
        try {
            prepareEncoder()

            muxer = MediaMuxer(savePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            virtualDisplay = mediaProjection!!.createVirtualDisplay(
                "$TAG-display",
                width,
                height,
                dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
                surface,
                null,
                null
            )

            recordVirtualDisplay()
        } finally {
            release()
        }
    }


    private fun recordVirtualDisplay() {
        while (!isStop) {
            val index = encoder!!.dequeueOutputBuffer(bufferInfo, TIMEOUT_US.toLong())
            if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                resetOutputFormat()
            } else if (index == MediaCodec.INFO_TRY_AGAIN_LATER) {
                //Log.d(TAG, "retrieving buffers time out!");
                //delay(10)
            } else if (index >= 0) {
                check(muxerStarted) { "MediaMuxer dose not call addTrack(format) " }
                encodeToVideoTrack(index)
                encoder!!.releaseOutputBuffer(index, false)
            }
        }
    }

    private fun encodeToVideoTrack(index: Int) {
        var encodedData = encoder!!.getOutputBuffer(index)
        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
            bufferInfo.size = 0
        }
        if (bufferInfo.size == 0) {
            encodedData = null
        }
        if (encodedData != null) {
            encodedData.position(bufferInfo.offset)
            encodedData.limit(bufferInfo.offset + bufferInfo.size)
            muxer!!.writeSampleData(videoTrackIndex, encodedData, bufferInfo)
        }
    }

    private fun resetOutputFormat() {
        check(!muxerStarted) { "output format already changed!" }
        val newFormat = encoder!!.outputFormat
        videoTrackIndex = muxer!!.addTrack(newFormat)
        muxer!!.start()
        muxerStarted = true
    }

    private fun prepareEncoder() {
        val format = MediaFormat.createVideoFormat(MIME_TYPE, width, height)
        format.setInteger(
            MediaFormat.KEY_COLOR_FORMAT,
            MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
        )
        format.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
        format.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL)
        encoder = MediaCodec.createEncoderByType(MIME_TYPE)
        encoder!!.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        surface = encoder!!.createInputSurface()
        encoder!!.start()
    }

    private fun release() {
        if (encoder != null) {
            encoder!!.stop()
            encoder!!.release()
            encoder = null
        }
        if (virtualDisplay != null) {
            virtualDisplay!!.release()
        }
        mediaProjection?.stop()
        if (muxer != null) {
            muxer?.stop()
            muxer?.release()
            muxer = null
        }
    }

    companion object {
        private const val TAG = "el, In ScreenRecorder"

        private const val MIME_TYPE = "video/avc" // H.264 Advanced Video Coding
        private const val IFRAME_INTERVAL = 10 // 10 seconds between I-frames
        private const val BIT_RATE = 6000000
        private const val TIMEOUT_US = 10000
    }
}