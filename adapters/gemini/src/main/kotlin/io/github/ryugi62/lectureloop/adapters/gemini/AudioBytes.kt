package io.github.ryugi62.lectureloop.adapters.gemini

import io.github.ryugi62.lectureloop.domain.AudioRef
import java.io.File
import java.io.InputStream

/** Resolves an [AudioRef.location] to bytes. */
interface AudioBytes {
    fun size(ref: AudioRef): Long
    fun open(ref: AudioRef): InputStream
}

/** `location` is an absolute path (optionally prefixed with `file:`). Works on the JVM and on Android. */
class FileAudioBytes : AudioBytes {
    private fun file(ref: AudioRef) = File(ref.location.removePrefix("file:"))
    override fun size(ref: AudioRef): Long = file(ref).length()
    override fun open(ref: AudioRef): InputStream = file(ref).inputStream()
}
