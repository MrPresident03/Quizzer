package com.example.notifications

import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class NextReminderDelayTest {
  private val auckland = ZoneId.of("Pacific/Auckland")
  private val newYork = ZoneId.of("America/New_York")

  private fun at(zone: ZoneId, y: Int, m: Int, d: Int, h: Int, min: Int = 0) =
    ZonedDateTime.of(y, m, d, h, min, 0, 0, zone)

  @Test
  fun beforeSixPm_targetsToday() {
    assertEquals(Duration.ofHours(8), nextReminderDelay(at(auckland, 2026, 6, 10, 10)))
  }

  @Test
  fun afterSixPm_targetsTomorrow() {
    assertEquals(Duration.ofHours(23), nextReminderDelay(at(auckland, 2026, 6, 10, 19)))
  }

  @Test
  fun exactlySixPm_targetsTomorrow() {
    assertEquals(Duration.ofHours(24), nextReminderDelay(at(newYork, 2026, 6, 10, 18)))
  }

  @Test
  fun customHour() {
    assertEquals(Duration.ofHours(2), nextReminderDelay(at(newYork, 2026, 6, 10, 7), hour = 9))
  }

  @Test
  fun auckland_dstStart_nightIsOneHourShort() {
    // Clocks jump 02:00 -> 03:00 on 2026-09-27.
    assertEquals(Duration.ofHours(22), nextReminderDelay(at(auckland, 2026, 9, 26, 19)))
    assertEquals(Duration.ofHours(16), nextReminderDelay(at(auckland, 2026, 9, 27, 1)))
  }

  @Test
  fun auckland_dstEnd_nightIsOneHourLong() {
    // Clocks fall back 03:00 -> 02:00 on 2026-04-05.
    assertEquals(Duration.ofHours(24), nextReminderDelay(at(auckland, 2026, 4, 4, 19)))
  }

  @Test
  fun newYork_dstStart_nightIsOneHourShort() {
    // Clocks jump 02:00 -> 03:00 on 2026-03-08.
    assertEquals(Duration.ofHours(22), nextReminderDelay(at(newYork, 2026, 3, 7, 19)))
    assertEquals(Duration.ofHours(8), nextReminderDelay(at(newYork, 2026, 3, 8, 10)))
  }

  @Test
  fun newYork_dstEnd_nightIsOneHourLong() {
    // Clocks fall back 02:00 -> 01:00 on 2026-11-01.
    assertEquals(Duration.ofHours(24), nextReminderDelay(at(newYork, 2026, 10, 31, 19)))
  }
}
