package io.github.ryugi62.lectureloop.adapters.gemini

import io.github.ryugi62.lectureloop.application.AnalyzerException
import io.github.ryugi62.lectureloop.domain.Concept
import io.github.ryugi62.lectureloop.domain.ExamPoint
import io.github.ryugi62.lectureloop.domain.QuizItem
import io.github.ryugi62.lectureloop.domain.ReviewCard
import io.github.ryugi62.lectureloop.domain.Timestamp
import io.github.ryugi62.lectureloop.domain.Todo
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** Shape the model must answer in. Field names match [ReviewCard]. */
@Serializable
internal data class CardDto(
    val title: String,
    val summary: String,
    val concepts: List<ConceptDto>,
    val examPoints: List<ExamPointDto>,
    val quiz: List<QuizDto>,
    val todos: List<TodoDto> = emptyList(),
) {
    companion object
}

@Serializable internal data class ConceptDto(val name: String, val explanation: String, val at: String)
@Serializable internal data class ExamPointDto(val point: String, val cue: String, val at: String)
@Serializable internal data class QuizDto(val question: String, val choices: List<String>, val answerIndex: Int, val explanation: String, val at: String)
@Serializable internal data class TodoDto(val task: String, val due: String? = null, val at: String)

internal fun CardDto.Companion.from(card: ReviewCard) = CardDto(
    title = card.title,
    summary = card.summary,
    concepts = card.concepts.map { ConceptDto(it.name, it.explanation, it.at.label) },
    examPoints = card.examPoints.map { ExamPointDto(it.point, it.cue, it.at.label) },
    quiz = card.quiz.map { QuizDto(it.question, it.choices, it.answerIndex, it.explanation, it.at.label) },
    todos = card.todos.map { TodoDto(it.task, it.due, it.at.label) },
)

internal fun CardDto.toDomain(): ReviewCard {
    fun at(value: String, where: String) =
        Timestamp.parse(value) ?: throw AnalyzerException.Malformed("$where has timestamp '$value', expected MM:SS")
    return ReviewCard(
        title = title.trim(),
        summary = summary.trim(),
        concepts = concepts.mapIndexed { i, c -> Concept(c.name.trim(), c.explanation.trim(), at(c.at, "concept ${i + 1}")) },
        examPoints = examPoints.mapIndexed { i, p -> ExamPoint(p.point.trim(), p.cue.trim(), at(p.at, "exam point ${i + 1}")) },
        quiz = quiz.mapIndexed { i, q -> QuizItem(q.question.trim(), q.choices.map { it.trim() }, q.answerIndex, q.explanation.trim(), at(q.at, "quiz ${i + 1}")) },
        todos = todos.mapIndexed { i, t -> Todo(t.task.trim(), t.due?.trim()?.takeIf { it.isNotEmpty() && it != "null" }, at(t.at, "to-do ${i + 1}")) },
    )
}

/** OpenAPI-subset schema for `generationConfig.responseSchema`. Counts mirror ReviewCardRules. */
internal val RESPONSE_SCHEMA: JsonObject = buildJsonObject {
    fun obj(block: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit) = buildJsonObject(block)
    val timestamp = obj {
        put("type", "STRING")
        put("description", "MM:SS position in the audio where the supporting sentence starts")
    }
    val string = obj { put("type", "STRING") }
    fun array(items: JsonObject, min: Int? = null, max: Int? = null) = obj {
        put("type", "ARRAY")
        put("items", items)
        min?.let { put("minItems", it) }
        max?.let { put("maxItems", it) }
    }
    fun record(vararg fields: Pair<String, JsonObject>, required: List<String> = fields.map { it.first }) = obj {
        put("type", "OBJECT")
        putJsonObject("properties") { fields.forEach { (k, v) -> put(k, v) } }
        putJsonArray("required") { required.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } }
    }

    put("type", "OBJECT")
    putJsonObject("properties") {
        put("title", string)
        put("summary", string)
        put("concepts", array(record("name" to string, "explanation" to string, "at" to timestamp), min = 3, max = 5))
        put(
            "examPoints",
            array(
                record(
                    "point" to string,
                    "cue" to obj { put("type", "STRING"); put("description", "The lecturer's own words that signal this will be tested") },
                    "at" to timestamp,
                ),
                max = 8,
            ),
        )
        put(
            "quiz",
            array(
                record(
                    "question" to string,
                    "choices" to array(string, min = 4, max = 4),
                    "answerIndex" to obj { put("type", "INTEGER") },
                    "explanation" to string,
                    "at" to timestamp,
                ),
                min = 5, max = 5,
            ),
        )
        put(
            "todos",
            array(
                record(
                    "task" to string,
                    "due" to obj { put("type", "STRING"); put("nullable", true) },
                    "at" to timestamp,
                    required = listOf("task", "at"),
                ),
            ),
        )
    }
    putJsonArray("required") {
        listOf("title", "summary", "concepts", "examPoints", "quiz", "todos").forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
    }
}

internal const val PROMPT = """You are LectureLoop. The audio is a recording of one university lecture, captured by a student.
Build a review card the student can study in 3 minutes right after class.

Rules:
- Use only what the lecturer actually says in the audio. Do not add outside facts.
- Cover the whole recording, not just its opening: lectures run 60 to 90 minutes, so draw concepts and questions from the beginning, the middle and the end.
- "at" is the MM:SS position in the audio where the supporting sentence starts. Every item needs one.
- examPoints: things the lecturer signals will be tested or must be memorized ("this will be on the exam", "remember this", "practice this"). Put the lecturer's own words in "cue". If the lecturer gives no such signal, return an empty list. Skip small talk and logistics that are unrelated to the subject.
- concepts: 3 to 5 core ideas, each explained in one or two plain sentences a classmate would understand.
- quiz: exactly 5 multiple-choice questions with 4 choices each; answerIndex is 0-based; mix recall and calculation questions when the lecture has numbers.
- todos: readings and homework the lecturer assigns. Give each task only the deadline spoken for that task, copied as spoken (for example "next class" or "next Monday before class"); null if that task has no deadline.
- Write in the language of the lecture."""
