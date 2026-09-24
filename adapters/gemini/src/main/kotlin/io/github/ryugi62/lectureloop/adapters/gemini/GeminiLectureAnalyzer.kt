package io.github.ryugi62.lectureloop.adapters.gemini

import io.github.ryugi62.lectureloop.application.AnalyzerException
import io.github.ryugi62.lectureloop.application.LectureAnalyzer
import io.github.ryugi62.lectureloop.domain.AudioRef
import io.github.ryugi62.lectureloop.domain.ReviewCard
import io.github.ryugi62.lectureloop.domain.Violation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.BufferedSink
import okio.source
import java.io.IOException
import java.io.InputStream
import java.util.Base64
import java.util.concurrent.TimeUnit

/**
 * [LectureAnalyzer] backed by Gemini `generateContent` with a JSON response schema.
 * One multimodal call: the model listens to the recording and answers in the review-card shape,
 * with MM:SS evidence for every item. Audio above [inlineLimitBytes] goes through the resumable Files API.
 */
class GeminiLectureAnalyzer(
    private val apiKey: String,
    private val audio: AudioBytes,
    private val model: String = DEFAULT_MODEL,
    private val http: OkHttpClient = defaultClient(),
    private val baseUrl: HttpUrl = "https://generativelanguage.googleapis.com/".toHttpUrl(),
    private val inlineLimitBytes: Long = 14L * 1024 * 1024,
    private val pollDelayMillis: Long = 1_000,
) : LectureAnalyzer {

    override suspend fun analyze(audio: AudioRef, feedback: List<Violation>): ReviewCard = withContext(Dispatchers.IO) {
        var uploaded: String? = null
        try {
            val media = if (this@GeminiLectureAnalyzer.audio.size(audio) <= inlineLimitBytes) {
                val data = this@GeminiLectureAnalyzer.audio.open(audio).use { Base64.getEncoder().encodeToString(it.readBytes()) }
                buildJsonObject { putJsonObject("inline_data") { put("mime_type", audio.mimeType); put("data", data) } }
            } else {
                val file = uploadFile(audio, this@GeminiLectureAnalyzer.audio.size(audio))
                uploaded = file.first
                buildJsonObject { putJsonObject("file_data") { put("mime_type", audio.mimeType); put("file_uri", file.second) } }
            }
            val body = requestBody(media, feedback)
            val request = Request.Builder()
                .url(baseUrl.resolve("v1beta/models/$model:generateContent")!!)
                .header("x-goog-api-key", apiKey)
                .post(body.toString().toRequestBody(JSON))
                .build()
            http.newCall(request).execute().use { response -> parseCard(expectOk(response)) }
        } catch (e: IOException) {
            throw AnalyzerException.Network(e.message ?: e.javaClass.simpleName)
        } finally {
            // The Files API would keep the recording for 48 hours; we do not need it after this call.
            uploaded?.let { name -> runCatching { http.newCall(Request.Builder().url(baseUrl.resolve("v1beta/$name")!!).header("x-goog-api-key", apiKey).delete().build()).execute().close() } }
        }
    }

    private fun requestBody(media: JsonObject, feedback: List<Violation>): JsonObject = buildJsonObject {
        putJsonArray("contents") {
            add(buildJsonObject {
                put("role", "user")
                putJsonArray("parts") {
                    add(media)
                    add(buildJsonObject { put("text", PROMPT) })
                    if (feedback.isNotEmpty()) {
                        val fix = feedback.joinToString("\n") { "- ${it.message}" }
                        add(buildJsonObject { put("text", "Your previous answer was rejected. Fix these problems:\n$fix") })
                    }
                }
            })
        }
        putJsonObject("generationConfig") {
            put("responseMimeType", "application/json")
            put("responseSchema", RESPONSE_SCHEMA)
            put("temperature", 0.2)
        }
    }

    /** Resumable upload: start → upload+finalize → wait until ACTIVE. Returns (file name, file URI). */
    private suspend fun uploadFile(ref: AudioRef, size: Long): Pair<String, String> {
        val start = Request.Builder()
            .url(baseUrl.resolve("upload/v1beta/files")!!)
            .header("x-goog-api-key", apiKey)
            .header("X-Goog-Upload-Protocol", "resumable")
            .header("X-Goog-Upload-Command", "start")
            .header("X-Goog-Upload-Header-Content-Length", size.toString())
            .header("X-Goog-Upload-Header-Content-Type", ref.mimeType)
            .post("""{"file":{"display_name":"lecture"}}""".toRequestBody(JSON))
            .build()
        val uploadUrl = http.newCall(start).execute().use { response ->
            expectOk(response)
            response.header("X-Goog-Upload-URL") ?: throw AnalyzerException.Malformed("upload start without X-Goog-Upload-URL")
        }
        val upload = Request.Builder()
            .url(uploadUrl)
            .header("X-Goog-Upload-Offset", "0")
            .header("X-Goog-Upload-Command", "upload, finalize")
            .post(StreamBody(ref.mimeType.toMediaType(), size) { audio.open(ref) })
            .build()
        var file = http.newCall(upload).execute().use { response ->
            json.parseToJsonElement(expectOk(response)).jsonObject["file"]?.jsonObject
                ?: throw AnalyzerException.Malformed("upload finished without a file")
        }
        repeat(MAX_POLLS) {
            when (file["state"]?.jsonPrimitive?.content) {
                "ACTIVE", null -> return file["name"]!!.jsonPrimitive.content to file["uri"]!!.jsonPrimitive.content
                "FAILED" -> throw AnalyzerException.Service(422, "audio could not be processed")
            }
            delay(pollDelayMillis)
            val name = file["name"]!!.jsonPrimitive.content
            val poll = Request.Builder().url(baseUrl.resolve("v1beta/$name")!!).header("x-goog-api-key", apiKey).get().build()
            file = http.newCall(poll).execute().use { json.parseToJsonElement(expectOk(it)).jsonObject }
        }
        throw AnalyzerException.Service(504, "audio still processing after $MAX_POLLS checks")
    }

    private fun expectOk(response: Response): String {
        val text = response.body.string()
        return when {
            response.code == 429 -> throw AnalyzerException.RateLimited()
            !response.isSuccessful -> throw AnalyzerException.Service(response.code, text.take(300))
            else -> text
        }
    }

    private fun parseCard(body: String): ReviewCard = try {
        val candidate = json.parseToJsonElement(body).jsonObject["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
            ?: throw AnalyzerException.Malformed("no candidates")
        val text = candidate["content"]?.jsonObject?.get("parts")?.jsonArray
            ?.joinToString("") { it.jsonObject["text"]?.jsonPrimitive?.content.orEmpty() }
            ?.takeIf { it.isNotBlank() }
            ?: throw AnalyzerException.Malformed("empty answer (finishReason=${candidate["finishReason"]})")
        json.decodeFromString(CardDto.serializer(), text).toDomain()
    } catch (e: SerializationException) {
        throw AnalyzerException.Malformed(e.message ?: "invalid JSON")
    } catch (e: IllegalArgumentException) {
        throw AnalyzerException.Malformed(e.message ?: "invalid JSON")
    }

    private class StreamBody(private val type: MediaType, private val length: Long, private val open: () -> InputStream) : RequestBody() {
        override fun contentType() = type
        override fun contentLength() = length
        override fun writeTo(sink: BufferedSink) {
            open().source().use { sink.writeAll(it) }
        }
    }

    companion object {
        /** Free-tier friendly and fast: 2:28 of audio → card in ~6.5 s (docs/VERIFICATION.md). */
        const val DEFAULT_MODEL = "gemini-3.5-flash-lite"
        private const val MAX_POLLS = 60
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private val json = Json { ignoreUnknownKeys = true }

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .writeTimeout(180, TimeUnit.SECONDS)
            .build()
    }
}
