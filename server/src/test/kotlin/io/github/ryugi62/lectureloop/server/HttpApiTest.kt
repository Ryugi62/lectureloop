package io.github.ryugi62.lectureloop.server

import io.github.ryugi62.lectureloop.adapters.gemini.CardJson
import io.github.ryugi62.lectureloop.application.BuildCard
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.nio.file.Files
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HttpApiTest {
    private val service = CardService(
        buildCard = BuildCard(CountingAnalyzer { serverCard() }),
        entitlements = object : EntitlementChecker { override suspend fun isPro(appUserId: String) = false },
        usage = FileUsageStore(Files.createTempFile("usage", ".json").toFile().apply { delete() }),
        clock = { Instant.parse("2026-09-23T05:00:00Z") },
        freePerWeek = 1,
    )
    private val api = HttpApi(service, port = 0, tmpDir = Files.createTempDirectory("ll").toFile()).also { it.start() }
    private val http = OkHttpClient()

    @AfterTest fun stop() = api.stop()

    private fun post(user: String?, seconds: String? = "148"): okhttp3.Response {
        val b = Request.Builder().url("http://127.0.0.1:${api.port}/v1/cards").post(ByteArray(64).toRequestBody("audio/mp4".toMediaType()))
        user?.let { b.header("X-App-User-Id", it) }
        seconds?.let { b.header("X-Audio-Seconds", it) }
        b.header("X-Time-Zone", "Asia/Seoul")
        return http.newCall(b.build()).execute()
    }

    @Test fun cardThenPaymentRequired() {
        post("u1").use { r ->
            assertEquals(200, r.code)
            assertEquals(5, CardJson.decode(r.body.string()).quiz.size)
        }
        post("u1").use { r ->
            assertEquals(402, r.code)
            val body = r.body.string()
            assertTrue(body.contains("\"used\":1") && body.contains("\"limit\":1") && body.contains("2026-09-27T15:00:00Z"), body)
        }
    }

    @Test fun missingHeadersAreBadRequests() {
        post(null).use { assertEquals(400, it.code) }
        post("u1", seconds = null).use { assertEquals(400, it.code) }
    }

    @Test fun health() {
        http.newCall(Request.Builder().url("http://127.0.0.1:${api.port}/healthz").build()).execute().use {
            assertEquals(200, it.code)
        }
    }
}
