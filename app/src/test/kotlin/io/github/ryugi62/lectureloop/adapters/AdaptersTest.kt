package io.github.ryugi62.lectureloop.adapters

import com.revenuecat.purchases.Package
import com.revenuecat.purchases.PackageType
import com.revenuecat.purchases.PresentedOfferingContext
import com.revenuecat.purchases.models.Period
import com.revenuecat.purchases.models.Price
import com.revenuecat.purchases.models.TestStoreProduct
import io.github.ryugi62.lectureloop.adapters.billing.planOf
import io.github.ryugi62.lectureloop.adapters.storage.JsonLectureRepository
import io.github.ryugi62.lectureloop.domain.AudioRef
import io.github.ryugi62.lectureloop.domain.Concept
import io.github.ryugi62.lectureloop.domain.ExamPoint
import io.github.ryugi62.lectureloop.domain.Lecture
import io.github.ryugi62.lectureloop.domain.LectureId
import io.github.ryugi62.lectureloop.domain.PlanKind
import io.github.ryugi62.lectureloop.domain.QuizItem
import io.github.ryugi62.lectureloop.domain.ReviewCard
import io.github.ryugi62.lectureloop.domain.ReviewScheduler
import io.github.ryugi62.lectureloop.domain.Timestamp
import io.github.ryugi62.lectureloop.domain.Todo
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class AdaptersTest {
    private fun lecture(id: String, at: Instant): Lecture {
        val card = ReviewCard(
            "Sampling", "Summary",
            listOf(Concept("A", "a", Timestamp(1)), Concept("B", "b", Timestamp(2)), Concept("C", "c", Timestamp(3))),
            listOf(ExamPoint("P", "cue", Timestamp(38))),
            List(5) { QuizItem("Q$it", listOf("a", "b", "c", "d"), 2, "e", Timestamp(40)) },
            listOf(Todo("HW", null, Timestamp(120)), Todo("Read", "next class", Timestamp(125))),
        )
        val review = ReviewScheduler.record(ReviewScheduler.start(at), 5, at.plusSeconds(86_400))
        return Lecture(LectureId(id), "DSP", at, AudioRef("/data/x.m4a", "audio/mp4", 148), card, review)
    }

    @Test fun lectureSurvivesAJsonRoundTrip() = runTest {
        val dir = Files.createTempDirectory("lectures").toFile()
        val repo = JsonLectureRepository(dir)
        val a = lecture("a", Instant.parse("2026-09-21T01:00:00Z"))
        val b = lecture("b", Instant.parse("2026-09-23T01:00:00Z"))
        repo.save(a)
        repo.save(b)
        assertEquals(a, JsonLectureRepository(dir).get(LectureId("a")))
        assertEquals(listOf(b, a), JsonLectureRepository(dir).all())
        assertEquals(emptyList(), dir.listFiles()!!.filter { it.name.endsWith(".tmp") })
    }

    @Test fun revenueCatPackagesBecomePlansWithStorePrices() {
        val context = PresentedOfferingContext("default")
        val semester = Package(
            "\$rc_six_month", PackageType.SIX_MONTH,
            TestStoreProduct("semester_pass", "Semester Pass", "Semester Pass", "6 months", Price("$19.99", 19_990_000, "USD"), Period(6, Period.Unit.MONTH, "P6M")),
            context,
        )
        val monthly = Package(
            "\$rc_monthly", PackageType.MONTHLY,
            TestStoreProduct("monthly", "Monthly", "Monthly", "1 month", Price("$4.99", 4_990_000, "USD"), Period(1, Period.Unit.MONTH, "P1M")),
            context,
        )
        val s = planOf(semester)
        assertEquals(PlanKind.SEMESTER, s.kind)
        assertEquals(6, s.periodMonths)
        assertEquals("$19.99", s.priceText)
        assertEquals(19_990_000, s.priceMicros)
        assertEquals(PlanKind.MONTHLY, planOf(monthly).kind)
    }
}
