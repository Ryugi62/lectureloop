package io.github.ryugi62.lectureloop.adapters.audio

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import io.github.ryugi62.lectureloop.application.AudioSplitter
import io.github.ryugi62.lectureloop.domain.AudioRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

/**
 * Cuts an AAC/M4A recording into windows on the phone without re-encoding (MediaExtractor → MediaMuxer).
 * Our own recordings and most phone recorders produce M4A; other formats return null and use one call.
 */
class Mp4AudioSplitter(private val dir: File) : AudioSplitter {
    override suspend fun split(audio: AudioRef, windows: List<Pair<Int, Int>>): List<AudioRef>? = withContext(Dispatchers.IO) {
        if (audio.mimeType != "audio/mp4") return@withContext null
        dir.mkdirs()
        val out = mutableListOf<AudioRef>()
        try {
            for ((i, w) in windows.withIndex()) out += cut(audio, i, w.first, w.second)
            out
        } catch (e: Exception) {
            release(out)
            null
        }
    }

    private fun cut(audio: AudioRef, index: Int, startS: Int, endS: Int): AudioRef {
        val extractor = MediaExtractor()
        val file = File(dir, "w$index-${System.nanoTime()}.m4a")
        try {
            extractor.setDataSource(audio.location.removePrefix("file:"))
            val track = (0 until extractor.trackCount).first {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            }
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val startUs = startS * 1_000_000L
            val endUs = endS * 1_000_000L
            val muxer = MediaMuxer(file.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            try {
                val outTrack = muxer.addTrack(format)
                muxer.start()
                val capacity = if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) else 256 * 1024
                val buffer = ByteBuffer.allocate(maxOf(capacity, 64 * 1024))
                val info = MediaCodec.BufferInfo()
                extractor.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                var written = 0
                while (true) {
                    val size = extractor.readSampleData(buffer, 0)
                    if (size < 0) break
                    val pts = extractor.sampleTime
                    if (pts >= endUs) break
                    if (pts >= startUs) {
                        val flags = if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                        info.set(0, size, pts - startUs, flags)
                        muxer.writeSampleData(outTrack, buffer, info)
                        written++
                    }
                    extractor.advance()
                }
                check(written > 0) { "empty window $index" }
                muxer.stop()
            } finally {
                muxer.release()
            }
        } finally {
            extractor.release()
        }
        return AudioRef(file.absolutePath, "audio/mp4", endS - startS)
    }

    override fun release(parts: List<AudioRef>) {
        parts.forEach { File(it.location).delete() }
    }
}
