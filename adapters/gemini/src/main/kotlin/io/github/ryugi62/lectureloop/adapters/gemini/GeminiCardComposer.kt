package io.github.ryugi62.lectureloop.adapters.gemini

import io.github.ryugi62.lectureloop.application.AnalyzerException
import io.github.ryugi62.lectureloop.application.CardComposer
import io.github.ryugi62.lectureloop.domain.ReviewCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** Text-only call: one title and summary for a lecture that was analysed in windows. No timestamps are produced here. */
class GeminiCardComposer(
    private val apiKey: String,
    private val model: String = GeminiLectureAnalyzer.DEFAULT_MODEL,
    private val http: OkHttpClient = GeminiLectureAnalyzer.defaultClient(),
    private val baseUrl: HttpUrl = "https://generativelanguage.googleapis.com/".toHttpUrl(),
) : CardComposer {
    override suspend fun titleAndSummary(parts: List<ReviewCard>): Pair<String, String> = withContext(Dispatchers.IO) {
        val listing = parts.mapIndexed { i, p -> "Part ${i + 1}: ${p.title} — ${p.summary}" }.joinToString("\n")
        val body = buildJsonObject {
            putJsonArray("contents") {
                add(buildJsonObject {
                    put("role", "user")
                    putJsonArray("parts") {
                        add(buildJsonObject {
                            put("text", "These are consecutive parts of one university lecture, in order.\n$listing\n\n" +
                                "Write one title (at most 8 words) and a summary of at most 2 sentences for the whole lecture. Use only what is listed.")
                        })
                    }
                })
            }
            putJsonObject("generationConfig") {
                put("responseMimeType", "application/json")
                putJsonObject("responseSchema") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {
                        putJsonObject("title") { put("type", "STRING") }
                        putJsonObject("summary") { put("type", "STRING") }
                    }
                    putJsonArray("required") { add(kotlinx.serialization.json.JsonPrimitive("title")); add(kotlinx.serialization.json.JsonPrimitive("summary")) }
                }
                put("temperature", 0.2)
            }
        }
        val request = Request.Builder()
            .url(baseUrl.resolve("v1beta/models/$model:generateContent")!!)
            .header("x-goog-api-key", apiKey)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(request).execute().use { r ->
            val text = r.body.string()
            if (!r.isSuccessful) throw AnalyzerException.Service(r.code, text.take(200))
            val answer = Json.parseToJsonElement(text).jsonObject["candidates"]!!.jsonArray[0].jsonObject["content"]!!
                .jsonObject["parts"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content
            val o = Json.parseToJsonElement(answer).jsonObject
            o["title"]!!.jsonPrimitive.content.trim() to o["summary"]!!.jsonPrimitive.content.trim()
        }
    }
}
