package io.github.ryugi62.lectureloop.domain

/** Every timestamp moved by [seconds] — a window's card placed back on the whole lecture's timeline. */
fun ReviewCard.shiftedBy(seconds: Int): ReviewCard {
    fun Timestamp.plus() = Timestamp(this.seconds + seconds)
    return copy(
        concepts = concepts.map { it.copy(at = it.at.plus()) },
        examPoints = examPoints.map { it.copy(at = it.at.plus()) },
        quiz = quiz.map { it.copy(at = it.at.plus()) },
        todos = todos.map { it.copy(at = it.at.plus()) },
    )
}

/**
 * Long lectures are analysed in windows because one call over a 74-minute recording placed some timestamps
 * minutes away from the sentence (docs/VERIFICATION.md), while short windows are exact.
 * Items are picked round-robin across windows — so the quiz covers the beginning, middle and end —
 * then shown in lecture order. Timestamps are never invented here: every one comes from a window's answer.
 */
object CardMerger {
    const val WINDOW_SECONDS = 600
    private const val MIN_TAIL_SECONDS = 120
    private const val MAX_EXAM_POINTS = 8

    /** [start, end) windows over the recording; a tail shorter than 2 minutes joins the previous window. */
    fun windows(durationSeconds: Int, windowSeconds: Int = WINDOW_SECONDS): List<Pair<Int, Int>> {
        val out = mutableListOf<Pair<Int, Int>>()
        var start = 0
        while (start < durationSeconds) {
            val end = minOf(start + windowSeconds, durationSeconds)
            if (end - start < MIN_TAIL_SECONDS && out.isNotEmpty()) {
                out[out.lastIndex] = out.last().first to end
            } else {
                out += start to end
            }
            start = end
        }
        return out
    }

    fun merge(parts: List<ReviewCard>, title: String, summary: String): ReviewCard = ReviewCard(
        title = title,
        summary = summary,
        concepts = roundRobin(parts.map { it.concepts }, ReviewCardRules.CONCEPTS_MAX) { it.name }.sortedBy { it.at },
        examPoints = roundRobin(parts.map { it.examPoints }, MAX_EXAM_POINTS) { it.point }.sortedBy { it.at },
        quiz = roundRobin(parts.map { it.quiz }, ReviewCardRules.QUIZ_SIZE) { it.question }.sortedBy { it.at },
        todos = parts.flatMap { it.todos }.sortedBy { it.at }.distinctBy { normalize(it.task) },
    )

    /**
     * Window visiting order: when there are more windows than slots, start with evenly spaced windows
     * (first, …, last) so a 75-minute lecture's quiz is not only about its first 50 minutes.
     */
    fun spreadOrder(windows: Int, slots: Int): List<Int> {
        if (windows <= slots || slots < 2) return (0 until windows).toList()
        val first = (0 until slots).map { j -> Math.round(j * (windows - 1).toDouble() / (slots - 1)).toInt() }.distinct()
        return first + (0 until windows).filter { it !in first }
    }

    private fun <T> roundRobin(lists: List<List<T>>, limit: Int, key: (T) -> String): List<T> {
        val picked = mutableListOf<T>()
        val seen = mutableSetOf<String>()
        val order = spreadOrder(lists.size, limit).map { lists[it] }
        var i = 0
        while (picked.size < limit && lists.any { i < it.size }) {
            for (list in order) {
                val item = list.getOrNull(i) ?: continue
                if (picked.size < limit && seen.add(normalize(key(item)))) picked += item
            }
            i++
        }
        return picked
    }

    private fun normalize(text: String) = text.lowercase().replace(Regex("[^a-z0-9가-힣]+"), " ").trim()
}
