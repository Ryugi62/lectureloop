package io.github.ryugi62.lectureloop.server

import io.github.ryugi62.lectureloop.domain.AudioRef
import kotlinx.coroutines.test.runTest
import org.junit.Assume.assumeTrue
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Needs ffmpeg: FFMPEG=/path/to/ffmpeg (skipped otherwise). */
class FfmpegAudioSplitterTest {
    private val ffmpeg = System.getenv("FFMPEG")?.takeIf { File(it).canExecute() }

    @Test fun cutsA25MinuteRecordingIntoThreeWindows() = runTest {
        assumeTrue("set FFMPEG to run", ffmpeg != null)
        val dir = Files.createTempDirectory("split").toFile()
        val src = File(dir, "tone.m4a")
        val p = ProcessBuilder(ffmpeg, "-loglevel", "error", "-f", "lavfi", "-i", "sine=frequency=440:duration=1500", "-ac", "1", "-c:a", "aac", "-b:a", "32k", src.path).start()
        p.waitFor()
        val splitter = FfmpegAudioSplitter(ffmpeg!!, File(dir, "parts"))
        val parts = splitter.split(AudioRef(src.path, "audio/mp4", 1500), listOf(0 to 600, 600 to 1200, 1200 to 1500))!!
        assertEquals(listOf(600, 600, 300), parts.map { it.durationSeconds })
        assertTrue(parts.all { File(it.location).length() > 10_000 })
        splitter.release(parts)
        assertTrue(parts.none { File(it.location).exists() })
    }
}
