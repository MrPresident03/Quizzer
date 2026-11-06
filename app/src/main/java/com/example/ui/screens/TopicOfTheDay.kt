package com.example.ui.screens

import java.time.LocalDate

/** Weekday rotation for the "Today" badge on Practice. Index 0 is Saturday, then Sunday, Monday... */
private val TOPICS_BY_WEEKDAY_SLOT = listOf(
    "History",
    "Science & Nature",
    "Geography",
    "Sports & Recreation",
    "Entertainment",
    "Technology & Computing",
    "General Knowledge"
)

/**
 * Today's featured topic. Same mapping as the old Home card (Calendar.DAY_OF_WEEK % 7):
 * Sat = History, Sun = Science & Nature, Mon = Geography, Tue = Sports, Wed = Entertainment,
 * Thu = Technology, Fri = General Knowledge.
 */
fun topicOfTheDay(date: LocalDate = LocalDate.now()): String {
    val calendarDayOfWeek = date.dayOfWeek.value % 7 + 1 // Sunday = 1 ... Saturday = 7
    return TOPICS_BY_WEEKDAY_SLOT[calendarDayOfWeek % TOPICS_BY_WEEKDAY_SLOT.size]
}
