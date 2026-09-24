package io.github.ryugi62.lectureloop.adapters.storage

import io.github.ryugi62.lectureloop.application.LectureRepository
import io.github.ryugi62.lectureloop.domain.AudioRef
import io.github.ryugi62.lectureloop.domain.Concept
import io.github.ryugi62.lectureloop.domain.ExamPoint
import io.github.ryugi62.lectureloop.domain.Lecture
import io.github.ryugi62.lectureloop.domain.LectureId
import io.github.ryugi62.lectureloop.domain.QuizItem
import io.github.ryugi62.lectureloop.domain.ReviewAttempt
import io.github.ryugi62.lectureloop.domain.ReviewCard
import io.github.ryugi62.lectureloop.domain.ReviewState
import io.github.ryugi62.lectureloop.domain.ReviewStep
import io.github.ryugi62.lectureloop.domain.Timestamp
import io.github.ryugi62.lectureloop.domain.Todo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant

/** One JSON file per lecture — the lecture is the unit of truth, like the desktop prototype's digest.json. */
class JsonLectureRepository(private val dir: File) : LectureRepository {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val lock = Mutex()
    private val _version = MutableStateFlow(0)
    /** Bumps on every save so screens can refresh. */
    val version: StateFlow<Int> = _version

    init { dir.mkdirs() }

    override suspend fun save(lecture: Lecture) = withContext(Dispatchers.IO) {
        lock.withLock {
            val tmp = File(dir, "${lecture.id.value}.json.tmp")
            tmp.writeText(json.encodeToString(LectureDto.serializer(), LectureDto.from(lecture)))
            tmp.renameTo(File(dir, "${lecture.id.value}.json"))
        }
        _version.value++
        Unit
    }

    override suspend fun get(id: LectureId): Lecture? = withContext(Dispatchers.IO) {
        File(dir, "${id.value}.json").takeIf { it.exists() }?.let { read(it) }
    }

    override suspend fun all(): List<Lecture> = withContext(Dispatchers.IO) {
        dir.listFiles { f -> f.extension == "json" }.orEmpty().mapNotNull { runCatching { read(it) }.getOrNull() }
            .sortedByDescending { it.recordedAt }
    }

    private fun read(file: File): Lecture = json.decodeFromString(LectureDto.serializer(), file.readText()).toDomain()
}

@Serializable
internal data class LectureDto(
    val id: String, val course: String, val recordedAt: String,
    val audioLocation: String, val audioMime: String, val audioSeconds: Int,
    val card: CardDto, val review: ReviewDto,
) {
    fun toDomain() = Lecture(
        LectureId(id), course, Instant.parse(recordedAt), AudioRef(audioLocation, audioMime, audioSeconds),
        card.toDomain(), review.toDomain(),
    )

    companion object {
        fun from(l: Lecture) = LectureDto(
            l.id.value, l.course, l.recordedAt.toString(), l.audio.location, l.audio.mimeType, l.audio.durationSeconds,
            CardDto.from(l.card), ReviewDto.from(l.review),
        )
    }
}

@Serializable
internal data class CardDto(
    val title: String, val summary: String,
    val concepts: List<Item>, val examPoints: List<Item>, val quiz: List<Quiz>, val todos: List<Item>,
) {
    @Serializable data class Item(val a: String, val b: String? = null, val at: Int)
    @Serializable data class Quiz(val question: String, val choices: List<String>, val answerIndex: Int, val explanation: String, val at: Int)

    fun toDomain() = ReviewCard(
        title, summary,
        concepts.map { Concept(it.a, it.b.orEmpty(), Timestamp(it.at)) },
        examPoints.map { ExamPoint(it.a, it.b.orEmpty(), Timestamp(it.at)) },
        quiz.map { QuizItem(it.question, it.choices, it.answerIndex, it.explanation, Timestamp(it.at)) },
        todos.map { Todo(it.a, it.b, Timestamp(it.at)) },
    )

    companion object {
        fun from(c: ReviewCard) = CardDto(
            c.title, c.summary,
            c.concepts.map { Item(it.name, it.explanation, it.at.seconds) },
            c.examPoints.map { Item(it.point, it.cue, it.at.seconds) },
            c.quiz.map { Quiz(it.question, it.choices, it.answerIndex, it.explanation, it.at.seconds) },
            c.todos.map { Item(it.task, it.due, it.at.seconds) },
        )
    }
}

@Serializable
internal data class ReviewDto(val startedAt: String, val step: String?, val dueAt: String?, val attempts: List<Attempt>) {
    @Serializable data class Attempt(val at: String, val step: String, val score: Int)

    fun toDomain() = ReviewState(
        Instant.parse(startedAt), step?.let(ReviewStep::valueOf), dueAt?.let(Instant::parse),
        attempts.map { ReviewAttempt(Instant.parse(it.at), ReviewStep.valueOf(it.step), it.score) },
    )

    companion object {
        fun from(r: ReviewState) = ReviewDto(
            r.startedAt.toString(), r.step?.name, r.dueAt?.toString(),
            r.attempts.map { Attempt(it.at.toString(), it.step.name, it.score) },
        )
    }
}
