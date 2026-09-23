package com.hermes.promptpad

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

class HomeLogicTest {
    @Test fun textScaleSelectionPreservesEveryOptionAndDefaultsOnlyUnknownValues() {
        listOf(90, 100, 115, 130).forEachIndexed { index, scale ->
            assertEquals(index, textScaleIndex(scale))
        }
        assertEquals(1, textScaleIndex(99))
    }

    @Test fun accentHexIsNormalisedAndMustBeReadableOnBlack() {
        assertEquals("#FC7703", normalizeAccentHex(" #fc7703 "))
        assertEquals(null, normalizeAccentHex("#FC770"))
        assertEquals(null, normalizeAccentHex("#FC770300"))
        assertEquals("#FC7703", accentHex(colorForAccentHex("#FC7703")))
        assertEquals(false, accentIsReadableOnBlack(colorForAccentHex("#111111")))
        assertEquals(true, accentIsReadableOnBlack(colorForAccentHex("#FC7703")))
        assertEquals("#FC7703", accentHex(colorForAccentHex("broken")))
    }

    @Test fun todayFiltersTimedAndUtcAllDayEventsByLocalDate() {
        val zone = ZoneId.of("Europe/Berlin")
        val today = LocalDate.parse("2026-09-10")
        val now = today.atTime(0, 30).atZone(zone).toInstant().toEpochMilli()
        val utc = today.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
        assertEquals(false, eventIsToday(utc - 86400000, utc, true, now, zone))
        assertEquals(true, eventIsToday(utc, utc + 86400000, true, now, zone))
        assertEquals(false, eventIsToday(utc + 86400000, utc + 172800000, true, now, zone))
        val midnight = nextLocalMidnight(now, zone)
        assertEquals(true, eventIsToday(now - 1000, now + 1000, false, now, zone))
        assertEquals(false, eventIsToday(midnight, midnight + 1000, false, now, zone))
        assertEquals(false, eventIsToday(now - 1000, now, false, now, zone))
    }
    @Test fun todayEndsAtNextLocalMidnightIncludingBothDstChanges() {
        val zone = ZoneId.of("Europe/Berlin")
        for ((date, hours) in listOf("2026-03-29" to 23L, "2026-10-25" to 25L, "2026-09-10" to 24L)) {
            val start = LocalDate.parse(date).atStartOfDay(zone)
            val midnight = start.plusDays(1).toInstant().toEpochMilli()
            val now = start.toInstant().toEpochMilli()
            assertEquals(TimeUnit.HOURS.toMillis(hours), nextLocalMidnight(now, zone) - now)
            assertEquals(midnight, nextLocalMidnight(start.plusHours(12).toInstant().toEpochMilli(), zone))
            assertEquals(midnight, nextLocalMidnight(midnight - 1, zone))
        }
    }
}
