package io.github.ryugi62.lectureloop.adapters.audio

import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.provider.OpenableColumns
import io.github.ryugi62.lectureloop.domain.AudioRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Copies a shared or picked recording into app storage and measures it. */
class AudioImporter(private val context: Context) {
    suspend fun import(uri: Uri): AudioRef = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri)?.takeIf { it.startsWith("audio/") } ?: "audio/mp4"
        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: "recording"
        val ext = name.substringAfterLast('.', "m4a").take(5)
        val out = File(File(context.filesDir, "audio").apply { mkdirs() }, "imp-${System.currentTimeMillis()}.$ext")
        resolver.openInputStream(uri)!!.use { input -> out.outputStream().use { input.copyTo(it) } }
        describe(out, mime)
    }

    fun describe(file: File, mime: String = "audio/mp4"): AudioRef {
        val seconds = MediaMetadataRetriever().run {
            try {
                setDataSource(file.absolutePath)
                ((extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L) + 999) / 1000
            } finally {
                release()
            }
        }
        return AudioRef(location = file.absolutePath, mimeType = normalizeMime(mime, file), durationSeconds = seconds.toInt())
    }

    private fun normalizeMime(mime: String, file: File) = when (file.extension.lowercase()) {
        "m4a", "mp4", "aac" -> "audio/mp4"
        "mp3" -> "audio/mpeg"
        "wav" -> "audio/wav"
        "ogg", "opus" -> "audio/ogg"
        "flac" -> "audio/flac"
        else -> mime
    }
}

/** Plays the lecture from the second an exam point or quiz answer was said. */
class LecturePlayer {
    private var player: MediaPlayer? = null
    private var path: String? = null

    fun playFrom(audio: AudioRef, seconds: Int) {
        if (path != audio.location) release()
        val p = player ?: MediaPlayer().also {
            it.setDataSource(audio.location)
            it.prepare()
            player = it
            path = audio.location
        }
        p.seekTo(seconds * 1000)
        p.start()
    }

    fun pause() { player?.takeIf { it.isPlaying }?.pause() }

    val isPlaying: Boolean get() = player?.isPlaying == true

    fun positionSeconds(): Int = (player?.currentPosition ?: 0) / 1000

    fun release() {
        player?.release()
        player = null
        path = null
    }
}
