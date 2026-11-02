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
                totalHours += sollStundenProTag
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
