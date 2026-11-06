package com.remotework.tracker

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.round

/**
 * Flextime (Gleitzeit) per month: the data behind the trend chart, the month analysis, the proposals to
 * reduce overtime and the flextime lines of the final report. Same rules as flask-tracker
 * (`services/flex_service.py`, `services/flex_analysis.py`).
 *
 * Every statement carries three numbers (hours): the balance at the start of the month ([FlexEntry.vorperiode]),
 * the change during the month ([FlexEntry.aktuell]) and the balance at the end of the month ([FlexEntry.gesamt]).
 * They are kept in [TrackerState.gleitzeitVerlauf], one entry per month.
 *
 * Thresholds of the employee portal (balance at the end of the month): above 40 hours yellow, above 60 hours red
 * (the hours above 60 are cut off at the end of the month), the same below -40 / -60 hours.
 */
@Serializable
data class FlexEntry(
    val jahr: Int,
    val monat: Int,
    val vorperiode: Double? = null,
    val aktuell: Double? = null,
    val gesamt: Double? = null
)

enum class FlexStatus { OK, YELLOW_POS, RED_POS, YELLOW_NEG, RED_NEG }

enum class DayFlag { NO_ENTRY, LONG_DAY, EARLY, LATE, NON_WORKING_DAY }

enum class Priority { HIGH, MEDIUM, INFO }

enum class AdviceCode {
    CAP_CUT, REDUCE, NEAR_LIMIT, FLEX_DAYS, SHORTER_DAYS, APPROVAL, TREND_UP, TREND_UP_OVER,
    LONG_DAYS, DAILY_SURPLUS, WEEKEND_WORK, OUTSIDE_WINDOW, BUILD_UP, CHECK_MISSING, ON_TRACK
}

/** Year and month (1-12) of a point of the series. */
data class MonthRef(val year: Int, val month: Int)

data class FlexSummary(
    val latest: Double?, val latestMonth: MonthRef?,
    val min: Double?, val minMonth: MonthRef?,
    val max: Double?, val maxMonth: MonthRef?,
    val status: FlexStatus?, val monthsYellow: Int, val monthsRed: Int
)

data class DayRow(
    val datum: String, val weekday: Int, val entries: Int, val start: String?, val end: String?,
    val net: Double, val remote: Double, val soll: Double, val delta: Double, val balance: Double,
    val flags: List<DayFlag>
)

data class WeekRow(val year: Int, val week: Int, val days: Int, val net: Double, val soll: Double, val delta: Double)

data class LongestDay(val datum: String, val net: Double)

data class MonthTotals(
    val daysWorked: Int, val net: Double, val remote: Double, val soll: Double, val deltaCalc: Double,
    val avgNet: Double?, val avgStart: String?, val avgEnd: String?, val longest: LongestDay?,
    val longDays: Int, val earlyDays: Int, val lateDays: Int, val nonWorkingDays: Int,
    val missingDays: List<String>, val missingHours: Double
)

data class MonthAnalysis(
    val year: Int, val month: Int, val complete: Boolean, val hasEntries: Boolean,
    val days: List<DayRow>, val weeks: List<WeekRow>, val totals: MonthTotals,
    val flex: FlexEntry?, val flexStatus: FlexStatus?,
    /** Change of the statement minus "net minus target" of the daily entries; null when it cannot be compared. */
    val difference: Double?
)

/** One proposal. The numbers it needs are in [values] (keys depend on the code), [until] is an ISO date. */
data class AdviceItem(val code: AdviceCode, val priority: Priority, val values: Map<String, Double> = emptyMap(), val until: String? = null)

data class Advice(val asOf: MonthRef?, val balance: Double?, val status: FlexStatus?, val trend: Double?, val items: List<AdviceItem>)

object Flextime {
    const val YELLOW_LIMIT = 40.0
    const val RED_LIMIT = 60.0
    const val TARGET_BUFFER = 30.0          // comfortable level below the yellow limit, the goal of the proposals
    const val LONG_DAY_HOURS = 10.0
    private const val MAX_ABS_HOURS = 10000.0
    private const val EARLIEST_START = 6 * 60   // work before 06:00 does not count
    private const val LATEST_END = 20 * 60      // work after 20:00 does not count
    private const val HIGH_FROM = 50.0          // from here on the proposals are urgent
    private const val MIN_HORIZON_WORKDAYS = 5  // fewer working days left in the month: plan until the end of the next month
    private const val TREND_MONTHS = 3
    private const val MIN_TREND_PER_MONTH = 0.5
    private const val MAX_TREND_WARNING_MONTHS = 6.0
    private const val DAILY_SURPLUS_FROM = 0.3

    private fun r2(v: Double) = round(v * 100.0) / 100.0
    private fun r1(v: Double) = round(v * 10.0) / 10.0

    /** Traffic light of a month-end balance; null without a usable number. */
    fun status(balance: Double?): FlexStatus? {
        if (balance == null || balance.isNaN()) return null
        return when {
            balance > RED_LIMIT -> FlexStatus.RED_POS
            balance > YELLOW_LIMIT -> FlexStatus.YELLOW_POS
            balance < -RED_LIMIT -> FlexStatus.RED_NEG
            balance < -YELLOW_LIMIT -> FlexStatus.YELLOW_NEG
            else -> FlexStatus.OK
        }
    }

    private fun hours(v: Double?): Double? = v?.takeIf { it.isFinite() && abs(it) < MAX_ABS_HOURS }?.let(::r2)

    /** A valid entry or null (month outside 1-12, year outside 1900-2200, no usable number). */
    fun cleanEntry(e: FlexEntry): FlexEntry? {
        if (e.monat !in 1..12 || e.jahr !in 1900..2200) return null
        val clean = e.copy(vorperiode = hours(e.vorperiode), aktuell = hours(e.aktuell), gesamt = hours(e.gesamt))
        return clean.takeIf { it.vorperiode != null || it.aktuell != null || it.gesamt != null }
    }

    /** Valid entries only, one per month (the last wins), sorted by year and month. */
    fun normalize(entries: List<FlexEntry>): List<FlexEntry> {
        val unique = LinkedHashMap<Pair<Int, Int>, FlexEntry>()
        for (e in entries) cleanEntry(e)?.let { unique[it.jahr to it.monat] = it }
        return unique.values.sortedWith(compareBy({ it.jahr }, { it.monat }))
    }

    /** Stores the numbers of one statement; the state stays unchanged without a month or a usable number. */
    fun record(state: TrackerState, balance: FlexBalance?, year: Int? = balance?.year, month: Int? = balance?.month): TrackerState {
        if (balance == null || year == null || month == null) return state
        val entry = cleanEntry(FlexEntry(year, month, balance.previous, balance.current, balance.total)) ?: return state
        val rest = normalize(state.gleitzeitVerlauf).filter { it.jahr != year || it.monat != month }
        return state.copy(gleitzeitVerlauf = normalize(rest + entry))
    }

    /** The series for charts and reports. Older data that only has the latest balance is included as one point. */
    fun series(state: TrackerState): List<FlexEntry> {
        val entries = normalize(state.gleitzeitVerlauf)
        val saldo = state.gleitzeitSaldo ?: return entries
        val y = saldo.year
        val m = saldo.month
        if (y == null || m == null) return entries
        val entry = cleanEntry(FlexEntry(y, m, saldo.previous, saldo.current, saldo.total)) ?: return entries
        return if (entries.any { it.jahr == y && it.monat == m }) entries else normalize(entries + entry)
    }

    fun summarize(entries: List<FlexEntry>): FlexSummary {
        val totals = entries.filter { it.gesamt != null }
        if (totals.isEmpty()) return FlexSummary(null, null, null, null, null, null, null, 0, 0)
        fun ref(e: FlexEntry) = MonthRef(e.jahr, e.monat)
        val low = totals.minByOrNull { it.gesamt!! }!!
        val high = totals.maxByOrNull { it.gesamt!! }!!
        val states = totals.map { status(it.gesamt) }
        return FlexSummary(
            latest = totals.last().gesamt, latestMonth = ref(totals.last()),
            min = low.gesamt, minMonth = ref(low), max = high.gesamt, maxMonth = ref(high),
            status = states.last(),
            monthsYellow = states.count { it == FlexStatus.YELLOW_POS || it == FlexStatus.YELLOW_NEG },
            monthsRed = states.count { it == FlexStatus.RED_POS || it == FlexStatus.RED_NEG }
        )
    }

    // ---- month analysis ----

    private fun lastDayOf(year: Int, month: Int): LocalDate =
        LocalDate(year, month, 1).plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY)

    /** Target hours of a calendar day: 0 on weekends and holidays, the special days of December are shortened. */
    fun targetHours(day: LocalDate, perDay: Double): Double {
        if (day.dayOfWeek == DayOfWeek.SATURDAY || day.dayOfWeek == DayOfWeek.SUNDAY || BavarianHolidays.isHoliday(day.toString())) return 0.0
        return perDay * TrackerCalculations.specialDayFactor(day.monthNumber, day.dayOfMonth)
    }

    /** Working days (with target hours) from [start] to [end], both included. */
    fun workdays(start: LocalDate, end: LocalDate, perDay: Double = 8.0): List<LocalDate> {
        val days = ArrayList<LocalDate>()
        var day = start
        while (day <= end) {
            if (targetHours(day, perDay) > 0) days.add(day)
            day = day.plus(1, DateTimeUnit.DAY)
        }
        return days
    }

    private fun isoWeek(day: LocalDate): Pair<Int, Int> {
        val thursday = day.plus(4 - day.dayOfWeek.isoDayNumber, DateTimeUnit.DAY)
        return thursday.year to (thursday.dayOfYear - 1) / 7 + 1
    }

    private val clock = Regex("""^(\d{1,2}):(\d{2})$""")

    private fun minutesOf(text: String?): Int? {
        val m = clock.matchEntire(text?.trim() ?: return null) ?: return null
        return m.groupValues[1].toInt() * 60 + m.groupValues[2].toInt()
    }

    private fun clockOf(minutes: Double): String {
        val total = minutes.toInt()
        return "${(total / 60).toString().padStart(2, '0')}:${(total % 60).toString().padStart(2, '0')}"
    }

    private fun num(v: Double) = if (v.isFinite()) v else 0.0

    /** The detailed analysis of one month from the daily entries (see the file comment). */
    fun analyzeMonth(state: TrackerState, year: Int, month: Int, today: LocalDate = TrackerCalculations.getCurrentDate()): MonthAnalysis {
        val perDay = state.sollStundenProTag.takeIf { it.isFinite() && it > 0 } ?: 8.0
        val first = LocalDate(year, month, 1)
        val last = lastDayOf(year, month)
        val through: LocalDate? = if (today >= last) last else if (today >= first) today else null  // target hours count until here

        val entriesByDay = HashMap<LocalDate, List<WorkEntry>>()
        val prefix = "$year-${month.toString().padStart(2, '0')}-"
        for ((key, list) in state.dailyRecords) {
            if (!key.startsWith(prefix) || list.isEmpty()) continue
            val day = try { LocalDate.parse(key) } catch (e: Exception) { continue }
            entriesByDay[day] = list
        }
        val days = (entriesByDay.keys + (if (through != null) workdays(first, through, perDay) else emptyList())).toSortedSet()

        val rows = ArrayList<DayRow>()
        var balance = 0.0
        for (day in days) {
            val mine = entriesByDay[day] ?: emptyList()
            val net = mine.sumOf { num(it.totalStundenHeute) }
            val remote = mine.sumOf { num(it.remoteStundenHeute) }
            val starts = mine.mapNotNull { minutesOf(it.startTime) }
            val ends = mine.mapNotNull { minutesOf(it.endTime) }
            val soll = if (through != null && day <= through) targetHours(day, perDay) else 0.0
            balance += net - soll
            val flags = ArrayList<DayFlag>()
            if (mine.isEmpty()) flags.add(DayFlag.NO_ENTRY)
            if (net > LONG_DAY_HOURS) flags.add(DayFlag.LONG_DAY)
            if (starts.isNotEmpty() && starts.min() < EARLIEST_START) flags.add(DayFlag.EARLY)
            if (ends.isNotEmpty() && ends.max() > LATEST_END) flags.add(DayFlag.LATE)
            if (mine.isNotEmpty() && targetHours(day, perDay) == 0.0) flags.add(DayFlag.NON_WORKING_DAY)
            rows.add(
                DayRow(
                    datum = day.toString(), weekday = day.dayOfWeek.isoDayNumber, entries = mine.size,
                    start = starts.minOrNull()?.let { clockOf(it.toDouble()) }, end = ends.maxOrNull()?.let { clockOf(it.toDouble()) },
                    net = r2(net), remote = r2(remote), soll = r2(soll), delta = r2(net - soll), balance = r2(balance), flags = flags
                )
            )
        }

        val weeks = LinkedHashMap<Pair<Int, Int>, DoubleArray>()       // net, soll, days worked
        for (row in rows) {
            val week = weeks.getOrPut(isoWeek(LocalDate.parse(row.datum))) { DoubleArray(3) }
            week[0] += row.net
            week[1] += row.soll
            if (row.net > 0) week[2] += 1.0
        }
        val weekRows = weeks.entries.sortedWith(compareBy({ it.key.first }, { it.key.second })).map { (key, v) ->
            WeekRow(key.first, key.second, v[2].toInt(), r2(v[0]), r2(v[1]), r2(v[0] - v[1]))
        }

        val worked = rows.filter { it.net > 0 }
        val netTotal = rows.sumOf { it.net }
        val sollTotal = rows.sumOf { it.soll }
        val startMinutes = worked.mapNotNull { minutesOf(it.start) }
        val endMinutes = worked.mapNotNull { minutesOf(it.end) }
        val longest = worked.maxByOrNull { it.net }
        val deltaCalc = r2(netTotal - sollTotal)
        val missing = rows.filter { DayFlag.NO_ENTRY in it.flags }
        val flex = series(state).firstOrNull { it.jahr == year && it.monat == month }
        val monthOver = through == last

        return MonthAnalysis(
            year = year, month = month, complete = monthOver, hasEntries = entriesByDay.isNotEmpty(),
            days = rows, weeks = weekRows,
            totals = MonthTotals(
                daysWorked = worked.size, net = r2(netTotal), remote = r2(rows.sumOf { it.remote }), soll = r2(sollTotal), deltaCalc = deltaCalc,
                avgNet = if (worked.isNotEmpty()) r2(netTotal / worked.size) else null,
                avgStart = if (startMinutes.isNotEmpty()) clockOf(startMinutes.average()) else null,
                avgEnd = if (endMinutes.isNotEmpty()) clockOf(endMinutes.average()) else null,
                longest = longest?.let { LongestDay(it.datum, it.net) },
                longDays = rows.count { DayFlag.LONG_DAY in it.flags }, earlyDays = rows.count { DayFlag.EARLY in it.flags },
                lateDays = rows.count { DayFlag.LATE in it.flags }, nonWorkingDays = rows.count { DayFlag.NON_WORKING_DAY in it.flags },
                missingDays = missing.map { it.datum }, missingHours = r2(missing.sumOf { it.soll })
            ),
            flex = flex, flexStatus = status(flex?.gesamt),
            difference = if (flex?.aktuell != null && monthOver && entriesByDay.isNotEmpty()) r2(flex.aktuell - deltaCalc) else null
        )
    }

    // ---- proposals ----

    private fun priorityOrder(p: Priority) = when (p) { Priority.HIGH -> 0; Priority.MEDIUM -> 1; Priority.INFO -> 2 }

    /**
     * Proposals from the latest month-end balance: what to do to get below the buffer (flextime days or shorter
     * days), when to talk to the manager, what the trend means and which habits of the last month cause the overtime.
     * Nothing is changed by this. [analysis] is [analyzeMonth] of the latest month (optional).
     */
    fun advice(state: TrackerState, today: LocalDate = TrackerCalculations.getCurrentDate(),
               entries: List<FlexEntry> = series(state), analysis: MonthAnalysis? = null): Advice {
        val usable = entries.filter { it.gesamt != null }
        if (usable.isEmpty()) return Advice(null, null, null, null, emptyList())
        val latest = usable.last()
        val balance = latest.gesamt!!
        val now = status(balance)
        val perDay = state.sollStundenProTag.takeIf { it.isFinite() && it > 0 } ?: 8.0
        val changes = usable.takeLast(TREND_MONTHS).mapNotNull { it.aktuell }
        val trend = if (changes.isEmpty()) null else r2(changes.sum() / changes.size)

        val endOfMonth = lastDayOf(today.year, today.monthNumber)
        var plan = workdays(today, endOfMonth, perDay)
        if (plan.size < MIN_HORIZON_WORKDAYS) {
            val following = endOfMonth.plus(1, DateTimeUnit.DAY)
            plan = workdays(today, lastDayOf(following.year, following.monthNumber), perDay)
        }

        val items = ArrayList<AdviceItem>()
        fun add(code: AdviceCode, priority: Priority, until: String? = null, vararg values: Pair<String, Double>) {
            items.add(AdviceItem(code, priority, values.toMap(), until))
        }

        if (now == FlexStatus.RED_POS) add(AdviceCode.CAP_CUT, Priority.HIGH, null, "over" to r2(balance - RED_LIMIT), "limit" to RED_LIMIT)
        if (balance > TARGET_BUFFER) {
            val need = r2(balance - TARGET_BUFFER)
            val priority = if (balance >= HIGH_FROM || now == FlexStatus.RED_POS) Priority.HIGH else Priority.MEDIUM
            if (now == FlexStatus.YELLOW_POS || now == FlexStatus.RED_POS) {
                add(AdviceCode.REDUCE, priority, null, "balance" to balance, "over" to r2(balance - YELLOW_LIMIT),
                    "limit" to YELLOW_LIMIT, "target" to TARGET_BUFFER, "need" to need)
            } else {
                add(AdviceCode.NEAR_LIMIT, priority, null, "balance" to balance, "toLimit" to r2(YELLOW_LIMIT - balance),
                    "limit" to YELLOW_LIMIT, "target" to TARGET_BUFFER, "need" to need)
            }
            val full = floor(need / perDay)
            add(AdviceCode.FLEX_DAYS, priority, null, "days" to r1(need / perDay), "fullDays" to full,
                "rest" to r2(need - full * perDay), "soll" to perDay)
            if (plan.isNotEmpty()) add(AdviceCode.SHORTER_DAYS, priority, plan.last().toString(),
                "perDay" to r2(need / plan.size), "workdays" to plan.size.toDouble())
            if (balance >= HIGH_FROM || now == FlexStatus.RED_POS) add(AdviceCode.APPROVAL, Priority.MEDIUM)
            if (trend != null && trend > MIN_TREND_PER_MONTH) add(AdviceCode.TREND_UP_OVER, Priority.MEDIUM, null, "trend" to trend)
        } else if (trend != null && trend > MIN_TREND_PER_MONTH) {
            val months = (YELLOW_LIMIT - balance) / trend
            if (months <= MAX_TREND_WARNING_MONTHS) add(AdviceCode.TREND_UP, Priority.MEDIUM, null,
                "trend" to trend, "months" to r1(months), "limit" to YELLOW_LIMIT)
        }

        if (balance > 10 && analysis != null) {
            val t = analysis.totals
            if (t.longDays > 0) add(AdviceCode.LONG_DAYS, Priority.MEDIUM, null, "n" to t.longDays.toDouble(), "limit" to LONG_DAY_HOURS)
            val surplus = (t.avgNet ?: perDay) - perDay
            if (surplus > DAILY_SURPLUS_FROM) add(AdviceCode.DAILY_SURPLUS, Priority.MEDIUM, null,
                "avgOver" to r2(surplus), "perMonth" to r1(surplus * t.daysWorked), "soll" to perDay)
            if (t.nonWorkingDays > 0) add(AdviceCode.WEEKEND_WORK, Priority.MEDIUM, null, "n" to t.nonWorkingDays.toDouble())
            if (t.earlyDays + t.lateDays > 0) add(AdviceCode.OUTSIDE_WINDOW, Priority.INFO, null, "n" to (t.earlyDays + t.lateDays).toDouble())
        }

        if (now == FlexStatus.YELLOW_NEG || now == FlexStatus.RED_NEG) {
            add(AdviceCode.CHECK_MISSING, Priority.HIGH, null, "balance" to balance)
        } else if (balance < -10) {
            add(AdviceCode.BUILD_UP, Priority.INFO, null, "balance" to balance, "need" to r2(-balance))
        }
        if (items.isEmpty()) add(AdviceCode.ON_TRACK, Priority.INFO, null, "balance" to balance)

        return Advice(
            asOf = MonthRef(latest.jahr, latest.monat), balance = balance, status = now, trend = trend,
            items = items.sortedBy { priorityOrder(it.priority) }
        )
    }
}
