package com.remotework.tracker

import kotlin.math.round

/**
 * History helpers and the quarterly final report. Same rules as flask-tracker
 * (`services/final_report.py`) and the JavaScript apps:
 *  - a month is "over quota" when its remote hours exceed its remote limit (compared on 2 decimals);
 *  - the history is sorted by year and month;
 *  - the report groups months by quarter and rates each quarter by the share of the quarter's
 *    remote limit that was used: <= 90 % good, <= 100 % tight, above 100 % exceeded.
 */
object FinalReport {

    enum class Rating { GOOD, TIGHT, EXCEEDED }

    const val TIGHT_FROM = 0.9
    const val MIN_MONTHS_FOR_COMPLETE_QUARTER = 3

    data class MonthRow(
        val monat: String, val jahr: Int, val quote: Double, val remote: Double, val maxRemote: Double,
        val soll: Double, val gesamt: Double, val actualQuote: Double, val over: Boolean,
        /** Flextime at the end of the month / change in the month / traffic light; null without statement data. */
        val flexTotal: Double? = null, val flexChange: Double? = null, val flexStatus: FlexStatus? = null
    )

    data class QuarterReport(
        val quartal: String, val months: List<MonthRow>, val monthCount: Int, val complete: Boolean,
        val remote: Double, val maxRemote: Double, val soll: Double, val gesamt: Double,
        val actualQuote: Double, val monthsOver: Int, val usage: Double, val rating: Rating,
        /** Flextime at the end of the quarter (last month with data) and the change over its months. */
        val flexEnd: Double? = null, val flexChange: Double? = null, val flexStatus: FlexStatus? = null
    )

    /** The whole flextime series (for the trend chart) and its summary. */
    data class FlexReport(val series: List<FlexEntry>, val summary: FlexSummary)

    data class Overall(
        val months: Int, val monthsOver: Int, val quarters: Int, val quartersExceeded: Int,
        val remote: Double, val maxRemote: Double, val soll: Double, val actualQuote: Double,
        val rating: Rating?
    )

    data class Report(
        val quarters: List<QuarterReport>, val overall: Overall,
        val flex: FlexReport = FlexReport(emptyList(), Flextime.summarize(emptyList()))
    )

    private fun round2(v: Double) = round(v * 100.0) / 100.0

    fun isOverQuota(m: MonthData): Boolean = round2(m.istRemoteStundenLaufend) > round2(m.maxRemoteStunden)

    fun sortHistory(history: List<MonthData>, newestFirst: Boolean = false): List<MonthData> {
        val sorted = history.sortedWith(compareBy({ it.jahr }, { MonthNames.getMonthNumber(it.monat) }))
        return if (newestFirst) sorted.reversed() else sorted
    }

    fun rate(remote: Double, limit: Double): Rating = when {
        round2(remote) > round2(limit) -> Rating.EXCEEDED
        limit > 0 && remote > TIGHT_FROM * limit -> Rating.TIGHT
        else -> Rating.GOOD
    }

    private fun quote(remote: Double, soll: Double) = if (soll > 0) remote / soll * 100.0 else 0.0

    private fun row(m: MonthData, flex: FlexEntry? = null) = MonthRow(
        m.monat, m.jahr, m.individuelleQuote, m.istRemoteStundenLaufend, m.maxRemoteStunden,
        m.sollStundenGesamt, m.istArbeitszeitLaufend, quote(m.istRemoteStundenLaufend, m.sollStundenGesamt), isOverQuota(m),
        flexTotal = flex?.gesamt, flexChange = flex?.aktuell, flexStatus = Flextime.status(flex?.gesamt)
    )

    private fun quartalKey(label: String): Pair<Int, Int> {
        val parts = label.split("/")
        val q = parts.getOrNull(0)?.removePrefix("Q")?.toIntOrNull() ?: 0
        val y = parts.getOrNull(1)?.toIntOrNull() ?: 0
        return y to q
    }

    /** [flexSeries] ([Flextime.series]) adds the flextime per month and quarter and the whole series for the chart. */
    fun build(history: List<MonthData>, flexSeries: List<FlexEntry> = emptyList()): Report {
        val flexByMonth = flexSeries.associateBy { it.jahr to it.monat }
        val seen = HashSet<Pair<String, Int>>()
        val groups = LinkedHashMap<String, MutableList<MonthData>>()
        for (m in sortHistory(history)) {
            if (!seen.add(m.monat to m.jahr)) continue
            val label = m.quartal ?: TrackerCalculations.getQuartalForMonth(m.monat, m.jahr)
            groups.getOrPut(label) { mutableListOf() }.add(m)
        }

        val quarters = groups.keys.sortedWith(compareBy({ quartalKey(it).first }, { quartalKey(it).second })).map { label ->
            val rows = groups.getValue(label).map { row(it, flexByMonth[it.jahr to MonthNames.getMonthNumber(it.monat)]) }
            val flexEnd = rows.lastOrNull { it.flexTotal != null }?.flexTotal
            val flexChanges = rows.mapNotNull { it.flexChange }
            val remote = rows.sumOf { it.remote }
            val limit = rows.sumOf { it.maxRemote }
            val soll = rows.sumOf { it.soll }
            QuarterReport(
                quartal = label, months = rows, monthCount = rows.size,
                complete = rows.size >= MIN_MONTHS_FOR_COMPLETE_QUARTER,
                remote = remote, maxRemote = limit, soll = soll, gesamt = rows.sumOf { it.gesamt },
                actualQuote = quote(remote, soll), monthsOver = rows.count { it.over },
                usage = if (limit > 0) remote / limit else 0.0, rating = rate(remote, limit),
                flexEnd = flexEnd, flexChange = if (flexChanges.isEmpty()) null else round2(flexChanges.sum()),
                flexStatus = Flextime.status(flexEnd)
            )
        }

        val remote = quarters.sumOf { it.remote }
        val limit = quarters.sumOf { it.maxRemote }
        val soll = quarters.sumOf { it.soll }
        val overall = Overall(
            months = quarters.sumOf { it.monthCount }, monthsOver = quarters.sumOf { it.monthsOver },
            quarters = quarters.size, quartersExceeded = quarters.count { it.rating == Rating.EXCEEDED },
            remote = remote, maxRemote = limit, soll = soll, actualQuote = quote(remote, soll),
            rating = if (quarters.isEmpty()) null else rate(remote, limit)
        )
        return Report(quarters, overall, FlexReport(flexSeries, Flextime.summarize(flexSeries)))
    }
}
