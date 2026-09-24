package io.github.ryugi62.lectureloop.adapters.gemini

import io.github.ryugi62.lectureloop.application.AnalyzerException
import io.github.ryugi62.lectureloop.application.LectureAnalyzer
import io.github.ryugi62.lectureloop.domain.Access
import io.github.ryugi62.lectureloop.domain.AudioRef
import io.github.ryugi62.lectureloop.domain.ReviewCard
import io.github.ryugi62.lectureloop.domain.Violation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
import java.io.IOException
import java.time.Instant
import java.time.ZoneId

/**
 * Production path: the app sends the recording to the LectureLoop server, which checks the RevenueCat
 * `pro` entitlement and the weekly allowance *before* spending AI credits, then calls Gemini with its own key.
 * The app never holds an AI key. [appUserId] is RevenueCat's app user ID.
 */
class ProxyLectureAnalyzer(
    private val baseUrl: HttpUrl,
    private val audio: AudioBytes,
    private val appUserId: () -> String,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
    private val http: OkHttpClient = GeminiLectureAnalyzer.defaultClient(),
) : LectureAnalyzer {

    override suspend fun analyze(audio: AudioRef, feedback: List<Violation>): ReviewCard = withContext(Dispatchers.IO) {
        val size = this@ProxyLectureAnalyzer.audio.size(audio)
        val body = object : RequestBody() {
            override fun contentType() = audio.mimeType.toMediaType()
            override fun contentLength() = size
            override fun writeTo(sink: BufferedSink) {
                this@ProxyLectureAnalyzer.audio.open(audio).source().use { sink.writeAll(it) }
            }
        }
        val request = Request.Builder()
            .url(baseUrl.resolve("v1/cards")!!)
            .header(HEADER_USER, appUserId())
            .header(HEADER_SECONDS, audio.durationSeconds.toString())
            .header(HEADER_ZONE, zone().id)
            .post(body)
            .build()
        try {
            http.newCall(request).execute().use { response ->
                val text = response.body.string()
                when (response.code) {
                    200 -> CardJson.decode(text)
                    402 -> throw AnalyzerException.LimitReached(limitFrom(text))
                    429 -> throw AnalyzerException.RateLimited()
                    422 -> throw AnalyzerException.Malformed("server rejected the card twice")
                    else -> throw AnalyzerException.Service(response.code, text.take(300))
                }
            }
        } catch (e: IOException) {
            throw AnalyzerException.Network(e.message ?: e.javaClass.simpleName)
        }
    }

    private fun limitFrom(text: String): Access.Paywalled = try {
        val o = Json.parseToJsonElement(text).jsonObject
        Access.Paywalled(
            used = o["used"]!!.jsonPrimitive.int,
            limit = o["limit"]!!.jsonPrimitive.int,
            resetsAt = Instant.parse(o["resetsAt"]!!.jsonPrimitive.content),
        )
    } catch (e: Exception) {
        throw AnalyzerException.Malformed("402 without a limit body")
    }

    companion object {
        const val HEADER_USER = "X-App-User-Id"
        const val HEADER_SECONDS = "X-Audio-Seconds"
        const val HEADER_ZONE = "X-Time-Zone"
    }
}
