package io.github.ryugi62.lectureloop.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class TimestampTest {
    @Test fun parsesMinutesAndSeconds() = assertEquals(128, Timestamp.parse("02:08")?.seconds)

    @Test fun parsesHours() = assertEquals(3728, Timestamp.parse("1:02:08")?.seconds)

    @Test fun toleratesSurroundingSpaceAndBrackets() = assertEquals(128, Timestamp.parse(" [02:08] ")?.seconds)

    @Test fun rejectsBareNumbersNegativesAndText() {
        assertNull(Timestamp.parse("7"))
        assertNull(Timestamp.parse("-1:00"))
        assertNull(Timestamp.parse("ab"))
        assertNull(Timestamp.parse("01:75"))
    }

    @Test fun labelRoundTrips() {
        assertEquals("02:08", Timestamp(128).label)
        assertEquals("1:02:08", Timestamp(3728).label)
        assertEquals(Timestamp(3728), Timestamp.parse(Timestamp(3728).label))
    }

    @Test fun cannotBeNegative() {
        assertFailsWith<IllegalArgumentException> { Timestamp(-1) }
    }
}
