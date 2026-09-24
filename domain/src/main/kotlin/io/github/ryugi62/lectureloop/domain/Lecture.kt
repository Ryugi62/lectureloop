package io.github.ryugi62.lectureloop.domain

import java.time.Instant

@JvmInline
value class LectureId(val value: String)

/** Where the recording lives (opaque to the domain) and how long it is. */
data class AudioRef(val location: String, val mimeType: String, val durationSeconds: Int)

/** Aggregate root: one class session the student captured. */
data class Lecture(
    val id: LectureId,
    val course: String,
    val recordedAt: Instant,
    val audio: AudioRef,
    val card: ReviewCard,
    val review: ReviewState,
)
