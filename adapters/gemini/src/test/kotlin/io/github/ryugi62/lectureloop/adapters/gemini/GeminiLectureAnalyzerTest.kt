package io.github.ryugi62.lectureloop.adapters.gemini

import io.github.ryugi62.lectureloop.application.AnalyzerException
import io.github.ryugi62.lectureloop.domain.AudioRef
import io.github.ryugi62.lectureloop.domain.Timestamp
import io.github.ryugi62.lectureloop.domain.Violation
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.Base64
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GeminiLectureAnalyzerTest {
    private val server = MockWebServer().apply { start() }
    private val audioBytes = ByteArray(2048) { (it % 251).toByte() }
    private val audio = AudioRef("mem:lecture", "audio/mp4", durationSeconds = 148)
    private val source = object : AudioBytes {
        override fun size(ref: AudioRef): Long = audioBytes.size.toLong()
        override fun open(ref: AudioRef): InputStream = ByteArrayInputStream(audioBytes)
    }

    @AfterTest fun stop() = server.shutdown()

    private fun analyzer(inlineLimit: Long = 14L * 1024 * 1024) = GeminiLectureAnalyzer(
        apiKey = "test-key",
        model = "gemini-test",
        audio = source,
        http = OkHttpClient(),
        baseUrl = server.url("/"),
        inlineLimitBytes = inlineLimit,
        pollDelayMillis = 0,
    )

    /** The real answer Gemini gave for the 2:28 demo lecture on 2026-09-24 (see docs/VERIFICATION.md). */
    private val cardJson = javaClass.getResource("/gemini-card.json")!!.readText()

    private fun generateResponse(text: String) = MockResponse().setResponseCode(200).setBody(
        buildJsonObject {
            putJsonArray("candidates") {
                add(buildJsonObject {
                    putJsonObject("content") { putJsonArray("parts") { add(buildJsonObject { put("text", text) }) } }
                    put("finishReason", "STOP")
                })
            }
        }.toString(),
    )

    private fun requestJson(body: String) = Json.parseToJsonElement(body).jsonObject

    @Test fun inlineRequestHasTheAgreedShapeAndTheAnswerMapsToACard() = runTest {
        server.enqueue(generateResponse(cardJson))
        val card = analyzer().analyze(audio)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/v1beta/models/gemini-test:generateContent", request.path)
        assertEquals("test-key", request.getHeader("x-goog-api-key"))
        val body = requestJson(request.body.readUtf8())
        val parts = body["contents"]!!.jsonArray[0].jsonObject["parts"]!!.jsonArray
        val inline = parts[0].jsonObject["inline_data"]!!.jsonObject
        assertEquals("audio/mp4", inline["mime_type"]!!.jsonPrimitive.content)
        assertEquals(Base64.getEncoder().encodeToString(audioBytes), inline["data"]!!.jsonPrimitive.content)
        val schema = body["generationConfig"]!!.jsonObject["responseSchema"]!!.jsonObject
        val quiz = schema["properties"]!!.jsonObject["quiz"]!!.jsonObject
        assertEquals(5, quiz["minItems"]!!.jsonPrimitive.int)
        assertEquals(5, quiz["maxItems"]!!.jsonPrimitive.int)

        assertEquals("Lecture 5: Sampling and Aliasing", card.title)
        assertEquals(5, card.quiz.size)
        assertEquals(Timestamp.parse("00:38"), card.examPoints[0].at)
        assertEquals("next Monday before class", card.todos[1].due)
        assertEquals(null, card.todos[0].due)
    }

    @Test fun largeAudioGoesThroughTheResumableFilesApi() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setHeader("X-Goog-Upload-URL", server.url("/upload-session/1").toString()))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"file":{"name":"files/abc","uri":"https://files.example/abc","mimeType":"audio/mp4","state":"PROCESSING"}}"""))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"name":"files/abc","uri":"https://files.example/abc","mimeType":"audio/mp4","state":"ACTIVE"}"""))
        server.enqueue(generateResponse(cardJson))
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        analyzer(inlineLimit = 1024).analyze(audio)

        val start = server.takeRequest()
        assertEquals("/upload/v1beta/files", start.path)
        assertEquals("resumable", start.getHeader("X-Goog-Upload-Protocol"))
        assertEquals("start", start.getHeader("X-Goog-Upload-Command"))
        assertEquals(audioBytes.size.toString(), start.getHeader("X-Goog-Upload-Header-Content-Length"))
        assertEquals("audio/mp4", start.getHeader("X-Goog-Upload-Header-Content-Type"))

        val upload = server.takeRequest()
        assertEquals("/upload-session/1", upload.path)
        assertEquals("upload, finalize", upload.getHeader("X-Goog-Upload-Command"))
        assertEquals("0", upload.getHeader("X-Goog-Upload-Offset"))
        assertTrue(upload.body.readByteArray().contentEquals(audioBytes))

        val poll = server.takeRequest()
        assertEquals("GET", poll.method)
        assertEquals("/v1beta/files/abc", poll.path)

        val generate = requestJson(server.takeRequest().body.readUtf8())
        val fileData = generate["contents"]!!.jsonArray[0].jsonObject["parts"]!!.jsonArray[0].jsonObject["file_data"]!!.jsonObject
        assertEquals("https://files.example/abc", fileData["file_uri"]!!.jsonPrimitive.content)

        val delete = server.takeRequest()
        assertEquals("DELETE", delete.method)
        assertEquals("/v1beta/files/abc", delete.path)
    }

    @Test fun rateLimitAndBadAnswersMapToAnalyzerExceptions() = runTest {
        server.enqueue(MockResponse().setResponseCode(429).setBody("""{"error":{"code":429}}"""))
        assertFailsWith<AnalyzerException.RateLimited> { analyzer().analyze(audio) }

        server.enqueue(generateResponse("this is not json"))
        assertFailsWith<AnalyzerException.Malformed> { analyzer().analyze(audio) }

        val badTimestamp = cardJson.replaceFirst("\"00:38\"", "\"about a minute in\"")
        server.enqueue(generateResponse(badTimestamp))
        assertFailsWith<AnalyzerException.Malformed> { analyzer().analyze(audio) }

        server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))
        val e = assertFailsWith<AnalyzerException.Service> { analyzer().analyze(audio) }
        assertEquals(500, e.code)
    }

    @Test fun retryPromptCarriesEveryViolation() = runTest {
        server.enqueue(generateResponse(cardJson))
        val feedback = listOf(Violation.QuizSize(4), Violation.TimestampBeyondAudio("to-do 1", Timestamp(200), 148))
        analyzer().analyze(audio, feedback)
        val parts = requestJson(server.takeRequest().body.readUtf8())["contents"]!!.jsonArray[0].jsonObject["parts"]!!.jsonArray
        val prompt = parts.joinToString("\n") { (it as JsonObject)["text"]?.jsonPrimitive?.content ?: "" }
        feedback.forEach { assertTrue(prompt.contains(it.message), "prompt should contain: ${it.message}") }
    }
}
