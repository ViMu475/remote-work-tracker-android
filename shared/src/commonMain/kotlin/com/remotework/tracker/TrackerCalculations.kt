package com.remotework.tracker

import kotlinx.datetime.*
import kotlin.math.roundToInt

private fun roundTo2(value: Double): String {
    val rounded = (value * 100).roundToInt() / 100.0
    val intPart = rounded.toInt()
    val fracPart = ((rounded - intPart) * 100).roundToInt()
    return "$intPart.${fracPart.toString().padStart(2, '0')}"
}

object TrackerCalculations {

    private val strictTime = Regex("""^([01]\d|2[0-3]):[0-5]\d$""")

    /** Strict "HH:mm" (00:00 - 23:59). timeToMinutes() itself stays lenient. */
    fun isValidTime(value: String): Boolean = strictTime.matches(value)

    fun timeToMinutes(timeStr: String): Int {
        val parts = timeStr.split(":")
        return if (parts.size == 2) {
            parts[0].toIntOrNull()?.times(60)?.plus(parts[1].toIntOrNull() ?: 0) ?: 0
        } else 0
    }

    /**
     * Minutes the time system deducts from the booked time of ONE day. Derived from real statements:
     * none up to 6 h; above 6 h 30 min, but never below 6 h net; above 9 h 45 min, but never below 9 h net.
     * Gaps between the day's bookings already count as break, with one exception: a single booking
     * longer than 6 h ([longestBlockMinutes]) is worked without a break, so the day still loses at least
     * 30 min (or the whole required break if that is less) however long the gap is.
     */
    fun statementBreakMinutes(workMinutes: Int, gapMinutes: Int = 0, longestBlockMinutes: Int = 0): Int {
        val required = when {
            workMinutes <= 360 -> 0
            workMinutes <= 390 -> workMinutes - 360
            workMinutes <= 570 -> 30
            else -> minOf(45, workMinutes - 540)
        }
        val floor = if (longestBlockMinutes > 360) minOf(required, 30) else 0
        return maxOf(floor, required - maxOf(0, gapMinutes))
    }

    /**
     * Break minutes per session for the sessions (start, end as "HH:mm") of ONE day, in the order given.
     * The deduction of the day goes to the longest session (spilling over to the next longest
     * one when a session is too short to carry it).
     */
    fun assignStatementBreaks(sessions: List<Pair<String, String>>): List<Double> {
        if (sessions.isEmpty()) return emptyList()
        val spans = sessions.map { (start, end) ->
            val s = timeToMinutes(start)
            val e = timeToMinutes(end).let { if (it < s) it + 24 * 60 else it }
            s to e
        }
        val ordered = spans.sortedBy { it.first }
        val work = ordered.sumOf { it.second - it.first }
        var gaps = 0
        for (i in 0 until ordered.size - 1) gaps += maxOf(0, ordered[i + 1].first - ordered[i].second)
        val lengths = spans.map { it.second - it.first }
        val deduction = statementBreakMinutes(work, gaps, lengths.max()).toDouble()
        // Longest session first (first one on ties); a session never carries more than its own length,
        // the rest of the day's deduction moves on to the next longest one.
        val result = MutableList(spans.size) { 0.0 }
        var remaining = deduction
        for (i in spans.indices.sortedWith(compareBy({ -lengths[it] }, { it }))) {
            if (remaining <= 0.0) break
            val part = minOf(remaining, lengths[i].toDouble())
            result[i] = part
            remaining -= part
        }
        return result
    }

    fun calculateNetHours(startTime: String, endTime: String, pauseMinutes: Double): TimeResult {
        val startMin = timeToMinutes(startTime)
        var endMin = timeToMinutes(endTime)

        if (endMin < startMin) {
            endMin += 24 * 60
        }

        val grossMin = endMin - startMin
        val grossHours = grossMin / 60.0
        val pauseHours = pauseMinutes / 60.0
        val netHours = grossHours - pauseHours

        val statutoryPause = when {
            grossHours >= 9 -> 0.75
            grossHours >= 6 -> 0.5
            else -> 0.0
        }

        val pauseWarning = if (statutoryPause > pauseHours) {
            "WARNUNG: Pause (${roundTo2(pauseHours)}h) < gesetzl. Minimum (${roundTo2(statutoryPause)}h)!"
        } else null

        return TimeResult(grossHours, netHours, pauseWarning)
    }

    fun isValidWorkingDay(dateString: String): ValidationResult {
        try {
            val date = LocalDate.parse(dateString)
            val dayOfWeek = date.dayOfWeek

            if (BavarianHolidays.isHoliday(dateString)) {
                return ValidationResult(false, "Arbeitszeitprotokollierung an einem Feiertag ist nicht zulässig.")
            }

            if (dayOfWeek == DayOfWeek.SUNDAY) {
                return ValidationResult(false, "Arbeitszeitprotokollierung an Sonntagen ist nicht zulässig.")
            }

            if (dayOfWeek == DayOfWeek.SATURDAY) {
                return ValidationResult(false, "Arbeitszeitprotokollierung an Samstagen ist nur bei genehmigter Mehrarbeit erlaubt.")
            }

            return ValidationResult(true, null)
        } catch (e: Exception) {
            return ValidationResult(false, "Ungültiges Datumsformat.")
        }
    }

    fun checkTimeOverlap(
        datum: String,
        startTime: String,
        endTime: String,
        existingEntries: List<WorkEntry>,
        excludeId: String? = null
    ): String? {
        val newStart = timeToMinutes(startTime)
        var newEnd = timeToMinutes(endTime)
        if (newEnd < newStart) newEnd += 24 * 60

        for (entry in existingEntries) {
            if (excludeId != null && entry.id == excludeId) continue

            val existingStart = timeToMinutes(entry.startTime)
            var existingEnd = timeToMinutes(entry.endTime)
            if (existingEnd < existingStart) existingEnd += 24 * 60

            val overlaps = (existingStart <= newStart && newStart < existingEnd) ||
                    (existingStart < newEnd && newEnd <= existingEnd) ||
                    (newStart <= existingStart && newEnd >= existingEnd) ||
                    (existingStart <= newStart && existingEnd >= newEnd)

            if (overlaps) {
                return "Zeitüberlappung erkannt! ${entry.startTime}-${entry.endTime} überschneidet sich."
            }
        }

        return null
    }

    /**
     * Company-specific special days that are not public holidays but are credited in the time statement:
     * 24 Dec is off (0), 31 Dec is a half day (0.5). Every other day counts fully (1).
     */
    fun specialDayFactor(month: Int, day: Int): Double = when {
        month == 12 && day == 24 -> 0.0
        month == 12 && day == 31 -> 0.5
        else -> 1.0
    }

    fun calculateSollStundenMonth(year: Int, month: Int, sollStundenProTag: Double): Double {
        val firstDay = LocalDate(year, month, 1)
        val lastDay = LocalDate(year, month, 1).plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY)

        var totalHours = 0.0
        var currentDay = firstDay

        while (currentDay <= lastDay) {
            val dateString = currentDay.toString()
            val dayOfWeek = currentDay.dayOfWeek

            if (dayOfWeek != DayOfWeek.SATURDAY &&
                dayOfWeek != DayOfWeek.SUNDAY &&
                !BavarianHolidays.isHoliday(dateString)
            ) {
                totalHours += sollStundenProTag * specialDayFactor(currentDay.monthNumber, currentDay.dayOfMonth)
            }

            currentDay = currentDay.plus(1, DateTimeUnit.DAY)
        }

        return totalHours
    }

    fun getQuartalForMonth(monthName: String, year: Int): String {
        val monthNum = MonthNames.getMonthNumber(monthName)
        val q = ((monthNum - 1) / 3) + 1
        return "Q$q/$year"
    }

    fun getCurrentDate(): LocalDate {
        return Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    }
}

data class TimeResult(
    val grossHours: Double,
    val netHours: Double,
    val pauseWarning: String?
)

data class ValidationResult(
    val isValid: Boolean,
    val errorMessage: String?
)
