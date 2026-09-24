package io.github.ryugi62.lectureloop.adapters.gemini

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GeminiCardComposerTest {
    private val server = MockWebServer().apply { start() }
    @AfterTest fun stop() = server.shutdown()

    @Test fun sendsEveryPartsTitleAndReadsTheAnswer() = runTest {
        server.enqueue(MockResponse().setBody("""{"candidates":[{"content":{"parts":[{"text":"{\"title\":\"Sampling\",\"summary\":\"All of it.\"}"}]}}]}"""))
        val card = CardJson.decode(javaClass.getResource("/gemini-card.json")!!.readText())
        val composer = GeminiCardComposer("k", "m", OkHttpClient(), server.url("/"))
        val (title, summary) = composer.titleAndSummary(listOf(card, card.copy(title = "Second part")))
        assertEquals("Sampling", title)
        assertEquals("All of it.", summary)
        val sent = server.takeRequest().body.readUtf8()
        assertTrue(sent.contains("Part 1: Lecture 5: Sampling and Aliasing") && sent.contains("Part 2: Second part"), sent)
    }
}
