package io.github.ryugi62.lectureloop.adapters.gemini

import io.github.ryugi62.lectureloop.domain.ReviewCard
import kotlinx.serialization.json.Json

/** The review-card wire format shared by the model answer, the server response and the app. */
object CardJson {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(card: ReviewCard): String = json.encodeToString(CardDto.serializer(), CardDto.from(card))

    /** @throws io.github.ryugi62.lectureloop.application.AnalyzerException.Malformed */
    fun decode(text: String): ReviewCard = try {
        json.decodeFromString(CardDto.serializer(), text).toDomain()
    } catch (e: kotlinx.serialization.SerializationException) {
        throw io.github.ryugi62.lectureloop.application.AnalyzerException.Malformed(e.message ?: "invalid JSON")
    } catch (e: IllegalArgumentException) {
        throw io.github.ryugi62.lectureloop.application.AnalyzerException.Malformed(e.message ?: "invalid JSON")
    }
}
