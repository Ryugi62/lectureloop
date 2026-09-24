package io.github.ryugi62.lectureloop.server

import io.github.ryugi62.lectureloop.adapters.gemini.FileAudioBytes
import io.github.ryugi62.lectureloop.adapters.gemini.GeminiCardComposer
import io.github.ryugi62.lectureloop.adapters.gemini.GeminiLectureAnalyzer
import io.github.ryugi62.lectureloop.application.BuildCard
import io.github.ryugi62.lectureloop.domain.AccessPolicy
import java.io.File
import java.time.Instant

/**
 * Composition root. Environment:
 *   GEMINI_API_KEY (required) · GEMINI_MODEL · REVENUECAT_SECRET_KEY (without it everyone is free)
 *   FREE_LECTURES_PER_WEEK (default 2) · PORT (default 8787) · DATA_DIR (default ./data) · FFMPEG (else ffmpeg on PATH)
 */
fun main() {
    val env = System.getenv()
    val geminiKey = env["GEMINI_API_KEY"]?.takeIf { it.isNotBlank() } ?: error("GEMINI_API_KEY is required")
    val dataDir = File(env["DATA_DIR"] ?: "data")
    val entitlements = env["REVENUECAT_SECRET_KEY"]?.takeIf { it.isNotBlank() }?.let { RevenueCatRestEntitlements(it) }
        ?: NoEntitlements.also { System.err.println("REVENUECAT_SECRET_KEY not set: every user is on the free allowance") }
    val analyzer = GeminiLectureAnalyzer(
        apiKey = geminiKey,
        audio = FileAudioBytes(),
        model = env["GEMINI_MODEL"]?.takeIf { it.isNotBlank() } ?: GeminiLectureAnalyzer.DEFAULT_MODEL,
    )
    val model = env["GEMINI_MODEL"]?.takeIf { it.isNotBlank() } ?: GeminiLectureAnalyzer.DEFAULT_MODEL
    // Long lectures are analysed in 10-minute windows (exact timestamps); needs ffmpeg for cutting.
    val ffmpeg = env["FFMPEG"]?.takeIf { File(it).canExecute() }
        ?: System.getenv("PATH").orEmpty().split(File.pathSeparator).map { File(it, "ffmpeg") }.firstOrNull { it.canExecute() }?.path
    val splitter = ffmpeg?.let { FfmpegAudioSplitter(it, File(dataDir, "windows")) }
        ?: null.also { System.err.println("ffmpeg not found: long recordings are analysed in one call (timestamps less exact)") }
    val service = CardService(
        buildCard = BuildCard(analyzer, splitter, GeminiCardComposer(geminiKey, model)),
        entitlements = entitlements,
        usage = FileUsageStore(File(dataDir, "usage.json")),
        clock = Instant::now,
        freePerWeek = AccessPolicy.sanitizeAllowance(env["FREE_LECTURES_PER_WEEK"]?.toIntOrNull()),
    )
    val api = HttpApi(service, port = env["PORT"]?.toIntOrNull() ?: 8787, tmpDir = File(dataDir, "tmp"))
    api.start()
    println("LectureLoop server on :${api.port}")
}
