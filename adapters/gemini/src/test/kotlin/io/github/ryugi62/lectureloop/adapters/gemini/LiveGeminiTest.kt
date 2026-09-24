package io.github.ryugi62.lectureloop.adapters.gemini

import io.github.ryugi62.lectureloop.domain.AudioRef
import io.github.ryugi62.lectureloop.domain.ReviewCardRules
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Physical check against the real Gemini API. Skipped unless LIVE_GEMINI=1.
 * LIVE_GEMINI=1 GEMINI_API_KEY=… LIVE_AUDIO=/path/demo-lecture.m4a ./gradlew :adapters:gemini:test --tests '*LiveGeminiTest*'
 */
class LiveGeminiTest {
    private val enabled = System.getenv("LIVE_GEMINI") == "1"

    private fun run(label: String, inlineLimit: Long) {
        org.junit.Assume.assumeTrue("set LIVE_GEMINI=1 to call the real API", enabled)
        val file = File(System.getenv("LIVE_AUDIO"))
        val seconds = System.getenv("LIVE_AUDIO_SECONDS")?.takeIf { it.isNotBlank() }?.toInt() ?: 148
        val model = System.getenv("LIVE_MODEL")?.takeIf { it.isNotBlank() } ?: GeminiLectureAnalyzer.DEFAULT_MODEL
        val analyzer = GeminiLectureAnalyzer(apiKey = System.getenv("GEMINI_API_KEY"), audio = FileAudioBytes(), model = model, inlineLimitBytes = inlineLimit)
        val started = System.nanoTime()
        val card = runBlocking { analyzer.analyze(AudioRef(file.absolutePath, "audio/mp4", seconds)) }
        val ms = (System.nanoTime() - started) / 1_000_000
        val report = buildString {
            appendLine("path=$label model=$model ms=$ms")
            card.examPoints.forEach { appendLine("exam ${it.at.label} ${it.point}") }
            card.concepts.forEach { appendLine("concept ${it.at.label} ${it.name}") }
            card.quiz.forEach { appendLine("quiz ${it.at.label} ${it.question}") }
            card.todos.forEach { appendLine("todo ${it.at.label} ${it.task} | due=${it.due}") }
        }
        File("build/live-gemini-$label-$model.txt").writeText(report)
        println(report)
        assertEquals(emptyList(), ReviewCardRules.check(card, seconds))
    }

    @Test fun inlinePath() = run("inline", inlineLimit = 14L * 1024 * 1024)

    @Test fun filesApiPath() = run("files-api", inlineLimit = 0)
}
