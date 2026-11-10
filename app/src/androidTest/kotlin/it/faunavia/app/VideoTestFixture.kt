package it.faunavia.app

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaExtractor
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sin

/** Original synthetic six-second red/green/blue animation, generated locally; no external media. */
internal fun videoFixture(destination: File) {
    val width = 160
    val height = 120
    val encoder = MediaCodec.createEncoderByType("video/avc")
    val format = MediaFormat.createVideoFormat("video/avc", width, height).apply {
        setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
        setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
        setInteger(MediaFormat.KEY_FRAME_RATE, 10)
        setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
    }
    val muxer = MediaMuxer(destination.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    muxer.setLocation(45f, 9f)
    var started = false
    try {
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoder.start()
        var frame = 0
        var sentEnd = false
        var track = -1
        val info = MediaCodec.BufferInfo()
        val deadline = System.currentTimeMillis() + 30_000
        while (true) {
            check(System.currentTimeMillis() < deadline) { "Synthetic video encoder timed out" }
            if (!sentEnd) {
                val index = encoder.dequeueInputBuffer(10_000)
                if (index >= 0) {
                    if (frame == 60) {
                        encoder.queueInputBuffer(index, 0, 0, 6_000_000, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        sentEnd = true
                    } else {
                        val color = when (frame / 20) { 0 -> intArrayOf(81, 90, 240); 1 -> intArrayOf(145, 54, 34); else -> intArrayOf(41, 240, 110) }
                        requireNotNull(encoder.getInputImage(index)).let { image ->
                            image.planes.forEachIndexed { planeIndex, plane ->
                                val w = if (planeIndex == 0) width else width / 2
                                val h = if (planeIndex == 0) height else height / 2
                                for (y in 0 until h) for (x in 0 until w) plane.buffer.put(y * plane.rowStride + x * plane.pixelStride, color[planeIndex].toByte())
                            }
                        }
                        encoder.queueInputBuffer(index, 0, width * height * 3 / 2, frame * 100_000L, 0)
                        frame++
                    }
                }
            }
            when (val index = encoder.dequeueOutputBuffer(info, 10_000)) {
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> { track = muxer.addTrack(encoder.outputFormat); muxer.start(); started = true }
                else -> if (index >= 0) {
                    if (info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                        muxer.writeSampleData(track, requireNotNull(encoder.getOutputBuffer(index)), info)
                    }
                    val end = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    encoder.releaseOutputBuffer(index, false)
                    if (end) break
                }
            }
        }
    } finally {
        encoder.stop(); encoder.release()
        if (started) muxer.stop()
        muxer.release()
    }
    addSyntheticAudio(destination)
}

private fun addSyntheticAudio(video: File) {
    val audio = File(video.parentFile, "${video.name}.audio.m4a")
    val combined = File(video.parentFile, "${video.name}.combined.mp4")
    val encoder = MediaCodec.createEncoderByType("audio/mp4a-latm")
    val format = MediaFormat.createAudioFormat("audio/mp4a-latm", 44100, 1).apply {
        setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
        setInteger(MediaFormat.KEY_BIT_RATE, 64_000)
    }
    val muxer = MediaMuxer(audio.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    var started = false
    try {
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE); encoder.start()
        var samples = 0
        var sentEnd = false
        var track = -1
        val info = MediaCodec.BufferInfo()
        val deadline = System.currentTimeMillis() + 30_000
        while (true) {
            check(System.currentTimeMillis() < deadline)
            if (!sentEnd) {
                val index = encoder.dequeueInputBuffer(10_000)
                if (index >= 0) {
                    val buffer = requireNotNull(encoder.getInputBuffer(index)).order(ByteOrder.nativeOrder())
                    val count = minOf(1024, 6 * 44100 - samples)
                    for (sample in 0 until count) buffer.putShort((sin((samples + sample) * 2.0 * Math.PI * 440 / 44100) * 8000).toInt().toShort())
                    encoder.queueInputBuffer(index, 0, count * 2, samples * 1_000_000L / 44100,
                        if (count == 0) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0)
                    samples += count
                    sentEnd = count == 0
                }
            }
            when (val index = encoder.dequeueOutputBuffer(info, 10_000)) {
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> { track = muxer.addTrack(encoder.outputFormat); muxer.start(); started = true }
                else -> if (index >= 0) {
                    if (info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) muxer.writeSampleData(track, requireNotNull(encoder.getOutputBuffer(index)), info)
                    val end = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    encoder.releaseOutputBuffer(index, false)
                    if (end) break
                }
            }
        }
    } finally { encoder.stop(); encoder.release(); if (started) muxer.stop(); muxer.release() }
    val output = MediaMuxer(combined.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    val readers = listOf(video, audio).map { file -> MediaExtractor().apply { setDataSource(file.absolutePath); selectTrack(0) } }
    try {
        val tracks = readers.map { output.addTrack(it.getTrackFormat(0)) }
        output.setLocation(45f, 9f); output.start()
        readers.forEachIndexed { index, reader ->
            val buffer = ByteBuffer.allocate(1024 * 1024)
            val info = MediaCodec.BufferInfo()
            while (true) {
                buffer.clear()
                val size = reader.readSampleData(buffer, 0)
                if (size < 0) break
                info.set(0, size, reader.sampleTime, if (reader.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
                output.writeSampleData(tracks[index], buffer, info)
                reader.advance()
            }
        }
        output.stop()
    } finally { readers.forEach { it.release() }; output.release(); audio.delete() }
    check(video.delete() && combined.renameTo(video))
}
