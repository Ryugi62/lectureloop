package io.github.ryugi62.lectureloop.application

import io.github.ryugi62.lectureloop.domain.Access
import io.github.ryugi62.lectureloop.domain.AccessPolicy
import kotlinx.coroutines.CancellationException

/** UC-5: is the student Pro, and if not, how many free lectures are left this week? */
class AccessStatus(
    private val lectures: LectureRepository,
    private val billing: BillingGateway,
    private val clock: Clock,
) {
    suspend operator fun invoke(): Access {
        val now = clock.now()
        val isPro = runCatchingNonCancel { billing.status().isPro } ?: false
        val remote = if (isPro) null else runCatchingNonCancel { billing.offer().freePerWeek }
        val weekStart = AccessPolicy.weekStart(now, clock.zone)
        val used = lectures.all().count { !it.recordedAt.isBefore(weekStart) }
        return AccessPolicy.decide(isPro, used, AccessPolicy.sanitizeAllowance(remote), now, clock.zone)
    }
}

internal inline fun <T> runCatchingNonCancel(block: () -> T): T? = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    null
}
