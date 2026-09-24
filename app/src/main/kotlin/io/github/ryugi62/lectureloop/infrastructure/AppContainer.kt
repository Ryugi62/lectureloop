package io.github.ryugi62.lectureloop.infrastructure

import android.content.Context
import io.github.ryugi62.lectureloop.BuildConfig
import io.github.ryugi62.lectureloop.adapters.audio.AudioImporter
import io.github.ryugi62.lectureloop.adapters.audio.LecturePlayer
import io.github.ryugi62.lectureloop.adapters.billing.RevenueCatBilling
import io.github.ryugi62.lectureloop.adapters.billing.UnconfiguredBilling
import io.github.ryugi62.lectureloop.adapters.gemini.FileAudioBytes
import io.github.ryugi62.lectureloop.adapters.gemini.GeminiLectureAnalyzer
import io.github.ryugi62.lectureloop.adapters.storage.JsonLectureRepository
import io.github.ryugi62.lectureloop.application.AccessStatus
import io.github.ryugi62.lectureloop.application.AnalyzerException
import io.github.ryugi62.lectureloop.application.BillingGateway
import io.github.ryugi62.lectureloop.application.Clock
import io.github.ryugi62.lectureloop.application.IdSource
import io.github.ryugi62.lectureloop.application.LectureAnalyzer
import io.github.ryugi62.lectureloop.application.LoadPaywall
import io.github.ryugi62.lectureloop.application.ProcessLecture
import io.github.ryugi62.lectureloop.application.Purchase
import io.github.ryugi62.lectureloop.application.Restore
import io.github.ryugi62.lectureloop.application.SubmitQuiz
import io.github.ryugi62.lectureloop.application.TodayQueue
import io.github.ryugi62.lectureloop.domain.AudioRef
import io.github.ryugi62.lectureloop.domain.LectureId
import io.github.ryugi62.lectureloop.domain.Violation
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

class SystemClock : Clock {
    override fun now(): Instant = Instant.now()
    override val zone: ZoneId get() = ZoneId.systemDefault()
}

class UuidIds : IdSource {
    override fun next() = LectureId(UUID.randomUUID().toString())
}

/** Composition root: the only place that knows which adapter backs which port. */
class AppContainer(context: Context, val billingConfigured: Boolean) {
    val clock: Clock = SystemClock()
    val lectures = JsonLectureRepository(File(context.filesDir, "lectures"))
    val revenueCat: RevenueCatBilling? = if (billingConfigured) RevenueCatBilling() else null
    val billing: BillingGateway = revenueCat ?: UnconfiguredBilling()
    val analyzer: LectureAnalyzer =
        if (BuildConfig.GEMINI_API_KEY.isBlank()) MissingKeyAnalyzer
        else GeminiLectureAnalyzer(apiKey = BuildConfig.GEMINI_API_KEY, audio = FileAudioBytes(), model = BuildConfig.GEMINI_MODEL)
    val importer = AudioImporter(context)
    val player = LecturePlayer()
    val prefs = context.getSharedPreferences("lectureloop", Context.MODE_PRIVATE)

    val processLecture = ProcessLecture(analyzer, lectures, billing, clock, UuidIds())
    val accessStatus = AccessStatus(lectures, billing, clock)
    val todayQueue = TodayQueue(lectures, clock)
    val submitQuiz = SubmitQuiz(lectures, clock)
    val loadPaywall = LoadPaywall(billing)
    val purchase = Purchase(billing)
    val restore = Restore(billing)
}

private object MissingKeyAnalyzer : LectureAnalyzer {
    override suspend fun analyze(audio: AudioRef, feedback: List<Violation>) =
        throw AnalyzerException.Service(401, "Add GEMINI_API_KEY to local.properties and rebuild")
}
