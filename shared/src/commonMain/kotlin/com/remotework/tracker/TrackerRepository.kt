package com.remotework.tracker

import kotlinx.datetime.Clock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

expect class LocalStorage {
    fun save(key: String, value: String)
    fun load(key: String): String?
    fun clear(key: String)
}

class TrackerRepository(private val storage: LocalStorage) {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val stateKey = "tracker_state"

    fun loadState(): TrackerState {
        val stored = storage.load(stateKey)
        return if (stored != null) {
            try {
                json.decodeFromString<TrackerState>(stored)
            } catch (e: Exception) {
                createDefaultState()
            }
        } else {
            createDefaultState()
        }
    }

    fun saveState(state: TrackerState) {
        storage.save(stateKey, json.encodeToString(state))
    }

    fun clearAll() {
        storage.clear(stateKey)
    }

    private fun createDefaultState(): TrackerState {
        val currentDate = TrackerCalculations.getCurrentDate()
        val currentMonth = MonthNames.getMonthName(currentDate.monthNumber)
        val currentYear = currentDate.year
        val sollStunden = 8.0
        val quote = 40.0

        val sollGesamt = TrackerCalculations.calculateSollStundenMonth(currentYear, currentDate.monthNumber, sollStunden)
        val maxRemote = sollGesamt * (quote / 100.0)

        return TrackerState(
            aktuellerMonat = currentMonth,
            aktuellesJahr = currentYear,
            currentMonthData = MonthData(
                monat = currentMonth,
                jahr = currentYear,
                individuelleQuote = quote,
                sollStundenGesamt = sollGesamt,
                maxRemoteStunden = maxRemote,
                istRemoteStundenLaufend = 0.0,
                istArbeitszeitLaufend = 0.0
            ),
            currentQuartal = TrackerCalculations.getQuartalForMonth(currentMonth, currentYear),
            individuelleQuoteProzent = quote,
            sollStundenProTag = sollStunden
        )
    }

    /** Returns an error message for invalid times, pause or remote hours, otherwise null. */
    private fun validateEntryInput(
        startTime: String, endTime: String, pauseMinutes: Double, remoteHours: Double?
    ): String? {
        if (!TrackerCalculations.isValidTime(startTime) || !TrackerCalculations.isValidTime(endTime) ||
            startTime == endTime
        ) return "Ungültige Zeitangabe!"
        if (!pauseMinutes.isFinite() || pauseMinutes < 0) return "Ungültige Pause!"
        if (remoteHours != null && (!remoteHours.isFinite() || remoteHours < 0)) return "Ungültige Remote-Stunden!"
        return null
    }

    fun addWorkEntry(
        state: TrackerState,
        datum: String,
        startTime: String,
        endTime: String,
        pauseMinutes: Double,
        remoteHours: Double?
    ): OperationResult = addEntry(state, datum, startTime, endTime, pauseMinutes, remoteHours, null)

    /**
     * Same as [addWorkEntry] for entries read from a statement: marked as imported and without a
     * break warning (a statement lists sessions, not breaks, so the pause is 0).
     */
    fun importWorkEntry(
        state: TrackerState,
        datum: String,
        startTime: String,
        endTime: String,
        pauseMinutes: Double,
        remoteHours: Double?
    ): OperationResult = addEntry(state, datum, startTime, endTime, pauseMinutes, remoteHours, SOURCE_PDF_IMPORT)

    private fun addEntry(
        state: TrackerState,
        datum: String,
        startTime: String,
        endTime: String,
        pauseMinutes: Double,
        remoteHours: Double?,
        source: String?
    ): OperationResult {
        // Validate date
        val validation = TrackerCalculations.isValidWorkingDay(datum)
        if (!validation.isValid) {
            return OperationResult(error = validation.errorMessage)
        }

        validateEntryInput(startTime, endTime, pauseMinutes, remoteHours)?.let {
            return OperationResult(error = it)
        }

        // Check overlap
        val existingEntries = state.dailyRecords[datum] ?: emptyList()
        val overlapError = TrackerCalculations.checkTimeOverlap(datum, startTime, endTime, existingEntries)
        if (overlapError != null) {
            return OperationResult(error = overlapError)
        }

        // Calculate hours
        val timeResult = TrackerCalculations.calculateNetHours(startTime, endTime, pauseMinutes)
        if (timeResult.netHours < 0) {
            return OperationResult(error = "Ungültige Zeitangabe!")
        }

        val actualRemoteHours = remoteHours ?: timeResult.netHours
        if (actualRemoteHours > timeResult.netHours) {
            return OperationResult(error = "Remote-Stunden ($actualRemoteHours h) > Netto (${timeResult.netHours} h)")
        }

        // Create entry
        val entryId = "$datum|$startTime|$endTime|${Clock.System.now().toEpochMilliseconds()}"
        val newEntry = WorkEntry(
            id = entryId,
            datum = datum,
            startTime = startTime,
            endTime = endTime,
            pauseMinutes = pauseMinutes,
            remoteStundenHeute = actualRemoteHours,
            totalStundenHeute = timeResult.netHours,
            pauseWarning = if (source == SOURCE_PDF_IMPORT) null else timeResult.pauseWarning,
            source = source
        )

        // Update state
        val updatedRecords = state.dailyRecords.toMutableMap()
        val dayEntries = (updatedRecords[datum] ?: emptyList()).toMutableList()
        dayEntries.add(newEntry)
        updatedRecords[datum] = dayEntries

        val newState = recalculateMonthlyTotals(state.copy(dailyRecords = updatedRecords))
        return OperationResult(state = newState)
    }

    fun deleteEntry(state: TrackerState, entryId: String): TrackerState {
        val updatedRecords = state.dailyRecords.toMutableMap()

        for ((datum, entries) in updatedRecords) {
            val filteredEntries = entries.filter { it.id != entryId }
            if (filteredEntries.size != entries.size) {
                if (filteredEntries.isEmpty()) {
                    updatedRecords.remove(datum)
                } else {
                    updatedRecords[datum] = filteredEntries
                }
                break
            }
        }

        return recalculateMonthlyTotals(state.copy(dailyRecords = updatedRecords))
    }

    fun recalculateMonthlyTotals(state: TrackerState): TrackerState {
        val monthNum = MonthNames.getMonthNumber(state.aktuellerMonat)

        var totalRemote = 0.0
        var totalWork = 0.0

        for ((datum, entries) in state.dailyRecords) {
            try {
                val parts = datum.split("-")
                if (parts.size == 3) {
                    val entryYear = parts[0].toIntOrNull() ?: continue
                    val entryMonth = parts[1].toIntOrNull() ?: continue

                    if (entryYear == state.aktuellesJahr && entryMonth == monthNum) {
                        for (entry in entries) {
                            totalRemote += entry.remoteStundenHeute
                            totalWork += entry.totalStundenHeute
                        }
                    }
                }
            } catch (e: Exception) {
                continue
            }
        }

        val sollGesamt = TrackerCalculations.calculateSollStundenMonth(
            state.aktuellesJahr, monthNum, state.sollStundenProTag
        )
        val maxRemote = sollGesamt * (state.individuelleQuoteProzent / 100.0)
        val tatsaechlicheQuote = if (sollGesamt > 0) (totalRemote / sollGesamt * 100) else 0.0

        val updatedMonthData = state.currentMonthData.copy(
            sollStundenGesamt = sollGesamt,
            maxRemoteStunden = maxRemote,
            istRemoteStundenLaufend = totalRemote,
            istArbeitszeitLaufend = totalWork,
            tatsaechlicheRemoteQuote = tatsaechlicheQuote,
            individuelleQuote = state.individuelleQuoteProzent
        )

        return state.copy(currentMonthData = updatedMonthData)
    }

    fun closeMonth(state: TrackerState): Pair<TrackerState, String?> {
        val monthNum = MonthNames.getMonthNumber(state.aktuellerMonat)

        val nextMonth: String
        val nextYear: Int
        if (monthNum == 12) {
            nextMonth = "Januar"
            nextYear = state.aktuellesJahr + 1
        } else {
            nextMonth = MonthNames.getMonthName(monthNum + 1)
            nextYear = state.aktuellesJahr
        }

        val completedMonth = state.currentMonthData.copy(
            quartal = TrackerCalculations.getQuartalForMonth(state.aktuellerMonat, state.aktuellesJahr)
        )

        val updatedHistory = state.monatlicheHistorie + completedMonth

        var intermediateState = state.copy(monatlicheHistorie = updatedHistory)

        // Check quarterly adjustment
        val (adjustedState, quarterMessage) = checkQuarterlyAdjustment(intermediateState, monthNum, state.aktuellesJahr)
        intermediateState = adjustedState

        val nextMonthNum = MonthNames.getMonthNumber(nextMonth)
        val sollGesamt = TrackerCalculations.calculateSollStundenMonth(nextYear, nextMonthNum, intermediateState.sollStundenProTag)
        val maxRemote = sollGesamt * (intermediateState.individuelleQuoteProzent / 100.0)

        val newMonthData = MonthData(
            monat = nextMonth,
            jahr = nextYear,
            individuelleQuote = intermediateState.individuelleQuoteProzent,
            sollStundenGesamt = sollGesamt,
            maxRemoteStunden = maxRemote,
            istRemoteStundenLaufend = 0.0,
            istArbeitszeitLaufend = 0.0
        )

        val finalState = intermediateState.copy(
            aktuellerMonat = nextMonth,
            aktuellesJahr = nextYear,
            currentMonthData = newMonthData,
            currentQuartal = TrackerCalculations.getQuartalForMonth(nextMonth, nextYear),
            monatlicheHistorie = intermediateState.monatlicheHistorie
        )

        return Pair(finalState, quarterMessage)
    }

    fun clearAllHistory(state: TrackerState): TrackerState {
        val currentDate = TrackerCalculations.getCurrentDate()
        val currentMonth = MonthNames.getMonthName(currentDate.monthNumber)
        val currentYear = currentDate.year

        val sollGesamt = TrackerCalculations.calculateSollStundenMonth(currentYear, currentDate.monthNumber, 8.0)
        val maxRemote = sollGesamt * 0.4

        return TrackerState(
            aktuellerMonat = currentMonth,
            aktuellesJahr = currentYear,
            currentMonthData = MonthData(
                monat = currentMonth,
                jahr = currentYear,
                individuelleQuote = 40.0,
                sollStundenGesamt = sollGesamt,
                maxRemoteStunden = maxRemote,
                istRemoteStundenLaufend = 0.0,
                istArbeitszeitLaufend = 0.0
            ),
            currentQuartal = TrackerCalculations.getQuartalForMonth(currentMonth, currentYear),
            dailyRecords = emptyMap(),
            monatlicheHistorie = emptyList(),
            individuelleQuoteProzent = 40.0,
            sollStundenProTag = 8.0
        )
    }

    /**
     * Get all unique months from daily records
     * Returns list of Pair(year, monthNumber) sorted chronologically
     */
    fun getMonthsFromEntries(state: TrackerState): List<Pair<Int, Int>> {
        val months = mutableSetOf<Pair<Int, Int>>()

        for (datum in state.dailyRecords.keys) {
            try {
                val parts = datum.split("-")
                if (parts.size == 3) {
                    val year = parts[0].toIntOrNull() ?: continue
                    val month = parts[1].toIntOrNull() ?: continue
                    months.add(Pair(year, month))
                }
            } catch (e: Exception) {
                continue
            }
        }

        return months.sortedWith(compareBy({ it.first }, { it.second }))
    }

    /**
     * Calculate MonthData for a specific month from entries
     */
    fun calculateMonthData(state: TrackerState, year: Int, monthNum: Int): MonthData {
        var totalRemote = 0.0
        var totalWork = 0.0

        for ((datum, entries) in state.dailyRecords) {
            try {
                val parts = datum.split("-")
                if (parts.size == 3) {
                    val entryYear = parts[0].toIntOrNull() ?: continue
                    val entryMonth = parts[1].toIntOrNull() ?: continue

                    if (entryYear == year && entryMonth == monthNum) {
                        for (entry in entries) {
                            totalRemote += entry.remoteStundenHeute
                            totalWork += entry.totalStundenHeute
                        }
                    }
                }
            } catch (e: Exception) {
                continue
            }
        }

        val monthName = MonthNames.getMonthName(monthNum)
        val sollGesamt = TrackerCalculations.calculateSollStundenMonth(year, monthNum, state.sollStundenProTag)
        val maxRemote = sollGesamt * (state.individuelleQuoteProzent / 100.0)
        val tatsaechlicheQuote = if (sollGesamt > 0) (totalRemote / sollGesamt * 100) else 0.0

        return MonthData(
            monat = monthName,
            jahr = year,
            individuelleQuote = state.individuelleQuoteProzent,
            sollStundenGesamt = sollGesamt,
            maxRemoteStunden = maxRemote,
            istRemoteStundenLaufend = totalRemote,
            istArbeitszeitLaufend = totalWork,
            tatsaechlicheRemoteQuote = tatsaechlicheQuote,
            quartal = TrackerCalculations.getQuartalForMonth(monthName, year)
        )
    }

    /**
     * Close a specific month and move its entries to history
     */
    fun closeSpecificMonth(state: TrackerState, year: Int, monthNum: Int): Pair<TrackerState, String?> {
        val monthName = MonthNames.getMonthName(monthNum)

        // Calculate month data for this specific month
        val monthData = calculateMonthData(state, year, monthNum)

        // Check if this month is already in history
        val alreadyInHistory = state.monatlicheHistorie.any {
            it.monat == monthName && it.jahr == year
        }

        if (alreadyInHistory) {
            // Just remove entries, don't add to history again
            val updatedRecords = state.dailyRecords.toMutableMap()
            val keysToRemove = mutableListOf<String>()

            for (datum in updatedRecords.keys) {
                val parts = datum.split("-")
                if (parts.size == 3) {
                    val entryYear = parts[0].toIntOrNull() ?: continue
                    val entryMonth = parts[1].toIntOrNull() ?: continue
                    if (entryYear == year && entryMonth == monthNum) {
                        keysToRemove.add(datum)
                    }
                }
            }

            keysToRemove.forEach { updatedRecords.remove(it) }
            return Pair(state.copy(dailyRecords = updatedRecords), null)
        }

        // Add to history
        val updatedHistory = state.monatlicheHistorie + monthData

        // Remove entries for this month from dailyRecords
        val updatedRecords = state.dailyRecords.toMutableMap()
        val keysToRemove = mutableListOf<String>()

        for (datum in updatedRecords.keys) {
            val parts = datum.split("-")
            if (parts.size == 3) {
                val entryYear = parts[0].toIntOrNull() ?: continue
                val entryMonth = parts[1].toIntOrNull() ?: continue
                if (entryYear == year && entryMonth == monthNum) {
                    keysToRemove.add(datum)
                }
            }
        }

        keysToRemove.forEach { updatedRecords.remove(it) }

        // Sort history by date
        val sortedHistory = updatedHistory.sortedWith(compareBy({ it.jahr }, { MonthNames.getMonthNumber(it.monat) }))

        // Update current month view to remaining entries or system date
        val remainingMonths = getMonthsFromEntries(state.copy(dailyRecords = updatedRecords))

        val (newMonth, newYear) = if (remainingMonths.isNotEmpty()) {
            val latest = remainingMonths.last()
            Pair(MonthNames.getMonthName(latest.second), latest.first)
        } else {
            val currentDate = TrackerCalculations.getCurrentDate()
            Pair(MonthNames.getMonthName(currentDate.monthNumber), currentDate.year)
        }

        val newMonthNum = MonthNames.getMonthNumber(newMonth)
        val sollGesamt = TrackerCalculations.calculateSollStundenMonth(newYear, newMonthNum, state.sollStundenProTag)
        val maxRemote = sollGesamt * (state.individuelleQuoteProzent / 100.0)

        // Recalculate for new current month
        var totalRemote = 0.0
        var totalWork = 0.0
        for ((datum, entries) in updatedRecords) {
            val parts = datum.split("-")
            if (parts.size == 3) {
                val entryYear = parts[0].toIntOrNull() ?: continue
                val entryMonth = parts[1].toIntOrNull() ?: continue
                if (entryYear == newYear && entryMonth == newMonthNum) {
                    for (entry in entries) {
                        totalRemote += entry.remoteStundenHeute
                        totalWork += entry.totalStundenHeute
                    }
                }
            }
        }

        val newMonthData = MonthData(
            monat = newMonth,
            jahr = newYear,
            individuelleQuote = state.individuelleQuoteProzent,
            sollStundenGesamt = sollGesamt,
            maxRemoteStunden = maxRemote,
            istRemoteStundenLaufend = totalRemote,
            istArbeitszeitLaufend = totalWork
        )

        var resultState = state.copy(
            aktuellerMonat = newMonth,
            aktuellesJahr = newYear,
            currentMonthData = newMonthData,
            currentQuartal = TrackerCalculations.getQuartalForMonth(newMonth, newYear),
            dailyRecords = updatedRecords,
            monatlicheHistorie = sortedHistory
        )

        // Check quarterly adjustment
        val (adjustedState, quarterMessage) = checkQuarterlyAdjustment(resultState, monthNum, year)
        return Pair(adjustedState, quarterMessage)
    }

    /**
     * Get entries for a specific month
     */
    fun getEntriesForMonth(state: TrackerState, year: Int, monthNum: Int): List<WorkEntry> {
        val entries = mutableListOf<WorkEntry>()

        for ((datum, dayEntries) in state.dailyRecords) {
            val parts = datum.split("-")
            if (parts.size == 3) {
                val entryYear = parts[0].toIntOrNull() ?: continue
                val entryMonth = parts[1].toIntOrNull() ?: continue
                if (entryYear == year && entryMonth == monthNum) {
                    entries.addAll(dayEntries)
                }
            }
        }

        return entries.sortedBy { it.datum }
    }

    /**
     * Switch current view to a specific month (from entries)
     */
    fun switchToMonth(state: TrackerState, year: Int, monthNum: Int): TrackerState {
        val monthName = MonthNames.getMonthName(monthNum)
        val monthData = calculateMonthData(state, year, monthNum)

        return state.copy(
            aktuellerMonat = monthName,
            aktuellesJahr = year,
            currentMonthData = monthData,
            currentQuartal = TrackerCalculations.getQuartalForMonth(monthName, year)
        )
    }

    // Feature: CSV Export
    fun generateCsv(state: TrackerState, year: Int, monthNum: Int): String {
        val entries = getEntriesForMonth(state, year, monthNum)
        val sb = StringBuilder()
        sb.appendLine("Datum,Start,Ende,Pause (min),Remote (h),Gesamt (h)")
        for (entry in entries.sortedBy { it.datum }) {
            sb.appendLine("${entry.datum},${entry.startTime},${entry.endTime},${entry.pauseMinutes},${entry.remoteStundenHeute},${entry.totalStundenHeute}")
        }
        return sb.toString()
    }

    // Feature: Serialize state to JSON for backup
    fun serializeState(state: TrackerState): String {
        return json.encodeToString(state)
    }

    // Feature: Deserialize state from JSON for restore
    fun deserializeState(jsonString: String): TrackerState? {
        return try {
            json.decodeFromString<TrackerState>(jsonString)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Restore from a user-chosen backup: parses, validates every field and recalculates the
     * derived month totals. Never returns a half-valid state.
     */
    fun restoreState(jsonString: String): OperationResult {
        val parsed = deserializeState(jsonString) ?: return OperationResult(error = "Ungültige Backup-Datei")
        validateState(parsed)?.let { return OperationResult(error = it) }
        val monthName = parsed.aktuellerMonat
        val normalized = parsed.copy(
            currentMonthData = parsed.currentMonthData.copy(monat = monthName, jahr = parsed.aktuellesJahr),
            currentQuartal = TrackerCalculations.getQuartalForMonth(monthName, parsed.aktuellesJahr)
        )
        return OperationResult(state = recalculateMonthlyTotals(normalized))
    }

    private fun validateState(s: TrackerState): String? {
        val years = 2000..2100
        if (s.aktuellerMonat !in MonthNames.german) return "Ungültiger Monat: ${s.aktuellerMonat}"
        if (s.aktuellesJahr !in years) return "Ungültiges Jahr: ${s.aktuellesJahr}"
        if (!s.individuelleQuoteProzent.isFinite() || s.individuelleQuoteProzent !in 40.0..60.0) return "Ungültige Quote"
        if (!s.sollStundenProTag.isFinite() || s.sollStundenProTag !in 1.0..10.0) return "Ungültige Soll-Stunden"
        if (s.language != "de" && s.language != "en") return "Ungültige Sprache"
        for ((key, entries) in s.dailyRecords) {
            val date = try { kotlinx.datetime.LocalDate.parse(key) } catch (e: Exception) { null }
                ?: return "Ungültiges Datum: $key"
            if (date.year !in years) return "Ungültiges Datum: $key"
            for (e in entries) {
                if (e.id.isBlank() || e.datum != key) return "Ungültiger Eintrag am $key"
                if (!TrackerCalculations.isValidTime(e.startTime) || !TrackerCalculations.isValidTime(e.endTime)) {
                    return "Ungültige Zeit am $key"
                }
                val numbers = listOf(e.pauseMinutes, e.remoteStundenHeute, e.totalStundenHeute)
                if (numbers.any { !it.isFinite() || it < 0 }) return "Ungültige Stunden am $key"
            }
        }
        for (m in s.monatlicheHistorie + s.currentMonthData) {
            if (m.monat !in MonthNames.german || m.jahr !in years) return "Ungültiger Historieneintrag"
            val numbers = listOf(
                m.individuelleQuote, m.sollStundenGesamt, m.maxRemoteStunden,
                m.istRemoteStundenLaufend, m.istArbeitszeitLaufend, m.tatsaechlicheRemoteQuote
            )
            if (numbers.any { !it.isFinite() || it < 0 }) return "Ungültiger Historieneintrag"
        }
        return null
    }

    // Feature 1: Edit/Update Entry
    fun updateEntry(
        state: TrackerState,
        entryId: String,
        datum: String,
        startTime: String,
        endTime: String,
        pauseMinutes: Double,
        remoteHours: Double?
    ): OperationResult {
        // Validate date
        val validation = TrackerCalculations.isValidWorkingDay(datum)
        if (!validation.isValid) {
            return OperationResult(error = validation.errorMessage)
        }

        validateEntryInput(startTime, endTime, pauseMinutes, remoteHours)?.let {
            return OperationResult(error = it)
        }

        // Check overlap excluding self
        val existingEntries = state.dailyRecords[datum] ?: emptyList()
        val overlapError = TrackerCalculations.checkTimeOverlap(datum, startTime, endTime, existingEntries, excludeId = entryId)
        if (overlapError != null) {
            return OperationResult(error = overlapError)
        }

        // Calculate hours
        val timeResult = TrackerCalculations.calculateNetHours(startTime, endTime, pauseMinutes)
        if (timeResult.netHours < 0) {
            return OperationResult(error = "Ungültige Zeitangabe!")
        }

        val actualRemoteHours = remoteHours ?: timeResult.netHours
        if (actualRemoteHours > timeResult.netHours) {
            return OperationResult(error = "Remote-Stunden ($actualRemoteHours h) > Netto (${timeResult.netHours} h)")
        }

        // Remove old entry
        val updatedRecords = state.dailyRecords.toMutableMap()
        for ((d, entries) in updatedRecords) {
            val filtered = entries.filter { it.id != entryId }
            if (filtered.size != entries.size) {
                if (filtered.isEmpty()) {
                    updatedRecords.remove(d)
                } else {
                    updatedRecords[d] = filtered
                }
                break
            }
        }

        // Create new entry with SAME entryId
        val newEntry = WorkEntry(
            id = entryId,
            datum = datum,
            startTime = startTime,
            endTime = endTime,
            pauseMinutes = pauseMinutes,
            remoteStundenHeute = actualRemoteHours,
            totalStundenHeute = timeResult.netHours,
            pauseWarning = timeResult.pauseWarning
        )

        val dayEntries = (updatedRecords[datum] ?: emptyList()).toMutableList()
        dayEntries.add(newEntry)
        updatedRecords[datum] = dayEntries

        val newState = recalculateMonthlyTotals(state.copy(dailyRecords = updatedRecords))
        return OperationResult(state = newState)
    }

    // Feature 2: Update Settings
    fun updateSettings(state: TrackerState, newQuote: Double, newSollStunden: Double): OperationResult {
        if (!newQuote.isFinite() || newQuote < 40.0 || newQuote > 60.0) return OperationResult(error = "Quote must be 40-60%")
        if (!newSollStunden.isFinite() || newSollStunden < 1.0 || newSollStunden > 10.0) return OperationResult(error = "Hours must be 1-10")
        val updated = state.copy(individuelleQuoteProzent = newQuote, sollStundenProTag = newSollStunden)
        return OperationResult(state = recalculateMonthlyTotals(updated))
    }

    // Feature 3: Quarterly auto-adjustment helper
    private fun checkQuarterlyAdjustment(state: TrackerState, monthNum: Int, year: Int): Pair<TrackerState, String?> {
        // Quarter-end months: 3 (März/Q1), 6 (Juni/Q2), 9 (September/Q3), 12 (Dezember/Q4)
        val quarterEndMonths = setOf(3, 6, 9, 12)
        if (monthNum !in quarterEndMonths) return Pair(state, null)

        val quarterStartMonth = monthNum - 2
        val quarterMonths = (quarterStartMonth..monthNum).map { it }

        // Collect history entries for this quarter
        val quarterHistory = state.monatlicheHistorie.filter { h ->
            h.jahr == year && MonthNames.getMonthNumber(h.monat) in quarterMonths
        }

        if (quarterHistory.size < 3) return Pair(state, null)

        val totalRemote = quarterHistory.sumOf { it.istRemoteStundenLaufend }
        val totalMaxAllowed = quarterHistory.sumOf { it.maxRemoteStunden }

        var newQuote = state.individuelleQuoteProzent
        val adjustMessage: String

        if (totalRemote > totalMaxAllowed) {
            newQuote = (newQuote - 10.0).coerceAtLeast(40.0)
            adjustMessage = "Quartal abgeschlossen. Remote über Quote! Neue Quote: ${newQuote.toInt()}%"
        } else {
            newQuote = (newQuote + 10.0).coerceAtMost(60.0)
            adjustMessage = "Quartal abgeschlossen. Neue Quote: ${newQuote.toInt()}%"
        }

        if (newQuote != state.individuelleQuoteProzent) {
            val updatedState = recalculateMonthlyTotals(state.copy(individuelleQuoteProzent = newQuote))
            return Pair(updatedState, adjustMessage)
        }

        return Pair(state, adjustMessage)
    }
}
