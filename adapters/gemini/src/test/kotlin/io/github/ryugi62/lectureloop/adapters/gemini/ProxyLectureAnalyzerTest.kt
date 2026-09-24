package io.github.ryugi62.lectureloop.adapters.gemini

import io.github.ryugi62.lectureloop.application.AnalyzerException
import io.github.ryugi62.lectureloop.domain.Access
import io.github.ryugi62.lectureloop.domain.AudioRef
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.time.Instant
import java.time.ZoneId
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ProxyLectureAnalyzerTest {
    private val server = MockWebServer().apply { start() }
    private val bytes = ByteArray(1000) { it.toByte() }
    private val audio = AudioRef("mem:x", "audio/mp4", 148)
    private val source = object : AudioBytes {
        override fun size(ref: AudioRef) = bytes.size.toLong()
        override fun open(ref: AudioRef): InputStream = ByteArrayInputStream(bytes)
    }
    private val analyzer = ProxyLectureAnalyzer(server.url("/"), source, { "\$RCAnonymousID:abc" }, { ZoneId.of("Asia/Seoul") }, OkHttpClient())
    private val cardJson = javaClass.getResource("/gemini-card.json")!!.readText()

    @AfterTest fun stop() = server.shutdown()

    @Test fun sendsTheRecordingWithTheRevenueCatUserAndGetsACard() = runTest {
        server.enqueue(MockResponse().setBody(CardJson.encode(CardJson.decode(cardJson))))
        val card = analyzer.analyze(audio)
        val r = server.takeRequest()
        assertEquals("/v1/cards", r.path)
        assertEquals("\$RCAnonymousID:abc", r.getHeader("X-App-User-Id"))
        assertEquals("148", r.getHeader("X-Audio-Seconds"))
        assertEquals("Asia/Seoul", r.getHeader("X-Time-Zone"))
        assertEquals("audio/mp4", r.getHeader("Content-Type"))
        assertTrue(r.body.readByteArray().contentEquals(bytes))
        assertEquals(5, card.quiz.size)
    }

    @Test fun paymentRequiredBecomesTheServersLimit() = runTest {
        server.enqueue(MockResponse().setResponseCode(402).setBody("""{"error":"weekly_limit","used":2,"limit":2,"resetsAt":"2026-09-27T15:00:00Z"}"""))
        val e = assertFailsWith<AnalyzerException.LimitReached> { analyzer.analyze(audio) }
        assertEquals(Access.Paywalled(2, 2, Instant.parse("2026-09-27T15:00:00Z")), e.access)
    }

    @Test fun cardJsonRoundTrips() {
        val card = CardJson.decode(cardJson)
        assertEquals(card, CardJson.decode(CardJson.encode(card)))
    }
}
