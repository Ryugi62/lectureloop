package io.github.ryugi62.lectureloop.server

import io.github.ryugi62.lectureloop.application.AudioSplitter
import io.github.ryugi62.lectureloop.domain.AudioRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/** Cuts windows with `ffmpeg -c copy` (no re-encoding). Needs an ffmpeg binary; returns null if it fails. */
class FfmpegAudioSplitter(private val ffmpeg: String = "ffmpeg", private val tmpDir: File) : AudioSplitter {
    override suspend fun split(audio: AudioRef, windows: List<Pair<Int, Int>>): List<AudioRef>? = withContext(Dispatchers.IO) {
        tmpDir.mkdirs()
        val input = File(audio.location.removePrefix("file:"))
        val ext = when (audio.mimeType) {
            "audio/mpeg", "audio/mp3" -> "mp3"
            "audio/wav", "audio/x-wav" -> "wav"
            "audio/ogg" -> "ogg"
            else -> "m4a"
        }
        val out = mutableListOf<AudioRef>()
        for ((i, w) in windows.withIndex()) {
            val file = File(tmpDir, "${input.nameWithoutExtension}-w$i-${System.nanoTime()}.$ext")
            val p = ProcessBuilder(
                ffmpeg, "-hide_banner", "-loglevel", "error", "-y",
                "-ss", w.first.toString(), "-t", (w.second - w.first).toString(), "-i", input.absolutePath,
                "-vn", "-c", "copy", file.absolutePath,
            ).redirectErrorStream(true).start()
            val finished = p.waitFor(120, TimeUnit.SECONDS)
            if (!finished || p.exitValue() != 0 || !file.exists() || file.length() == 0L) {
                release(out); file.delete()
                return@withContext null
            }
            out += AudioRef(file.absolutePath, audio.mimeType, w.second - w.first)
        }
        out
    }

    override fun release(parts: List<AudioRef>) {
        parts.forEach { File(it.location).delete() }
    }
}
