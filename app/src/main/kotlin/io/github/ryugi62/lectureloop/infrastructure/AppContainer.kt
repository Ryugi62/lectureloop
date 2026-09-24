package io.github.ryugi62.lectureloop.infrastructure

import android.content.Context
import io.github.ryugi62.lectureloop.BuildConfig
import io.github.ryugi62.lectureloop.adapters.audio.AudioImporter
import io.github.ryugi62.lectureloop.adapters.audio.LecturePlayer
import io.github.ryugi62.lectureloop.adapters.billing.RevenueCatBilling
import io.github.ryugi62.lectureloop.adapters.billing.UnconfiguredBilling
import io.github.ryugi62.lectureloop.adapters.gemini.FileAudioBytes
import io.github.ryugi62.lectureloop.adapters.gemini.GeminiLectureAnalyzer
import io.github.ryugi62.lectureloop.adapters.gemini.ProxyLectureAnalyzer
import io.github.ryugi62.lectureloop.adapters.gemini.GeminiCardComposer
import io.github.ryugi62.lectureloop.adapters.audio.Mp4AudioSplitter
import io.github.ryugi62.lectureloop.application.BuildCard
import com.revenuecat.purchases.Purchases
import okhttp3.HttpUrl.Companion.toHttpUrl
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
    val prefs = context.getSharedPreferences("lectureloop", Context.MODE_PRIVATE)
    val clock: Clock = SystemClock()
    val lectures = JsonLectureRepository(File(context.filesDir, "lectures"))
    val revenueCat: RevenueCatBilling? = if (billingConfigured) RevenueCatBilling() else null
    val billing: BillingGateway = revenueCat ?: UnconfiguredBilling()
    private val installId: String by lazy {
        prefs.getString("install_id", null) ?: UUID.randomUUID().toString().also { prefs.edit().putString("install_id", it).apply() }
    }

    /** Server mode when a server URL is configured (no AI key on the device); otherwise direct mode for development. */
    val analyzer: LectureAnalyzer = when {
        BuildConfig.LECTURELOOP_SERVER_URL.isNotBlank() -> ProxyLectureAnalyzer(
            baseUrl = BuildConfig.LECTURELOOP_SERVER_URL.toHttpUrl(),
            audio = FileAudioBytes(),
            appUserId = { if (billingConfigured) Purchases.sharedInstance.appUserID else installId },
        )
        BuildConfig.GEMINI_API_KEY.isNotBlank() ->
            GeminiLectureAnalyzer(apiKey = BuildConfig.GEMINI_API_KEY, audio = FileAudioBytes(), model = BuildConfig.GEMINI_MODEL)
        else -> MissingKeyAnalyzer
    }
    val importer = AudioImporter(context)
    val player = LecturePlayer()


    /** Direct mode cuts long lectures into windows on the phone; in server mode the server does it. */
    private val buildCard: BuildCard =
        if (analyzer is GeminiLectureAnalyzer) {
            BuildCard(analyzer, Mp4AudioSplitter(File(context.cacheDir, "windows")), GeminiCardComposer(BuildConfig.GEMINI_API_KEY, BuildConfig.GEMINI_MODEL))
        } else {
            BuildCard(analyzer)
        }
    val processLecture = ProcessLecture(buildCard, lectures, billing, clock, UuidIds())
    val accessStatus = AccessStatus(lectures, billing, clock)
    val todayQueue = TodayQueue(lectures, clock)
    val submitQuiz = SubmitQuiz(lectures, clock)
    val loadPaywall = LoadPaywall(billing)
    val purchase = Purchase(billing)
    val restore = Restore(billing)
}

private object MissingKeyAnalyzer : LectureAnalyzer {
    override suspend fun analyze(audio: AudioRef, feedback: List<Violation>) =
        throw AnalyzerException.Service(401, "Add LECTURELOOP_SERVER_URL (or GEMINI_API_KEY for development) to local.properties and rebuild")
}
