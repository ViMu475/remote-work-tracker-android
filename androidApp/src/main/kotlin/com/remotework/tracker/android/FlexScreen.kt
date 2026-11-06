package com.remotework.tracker.android

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.remotework.tracker.Advice
import com.remotework.tracker.AdviceCode
import com.remotework.tracker.AdviceItem
import com.remotework.tracker.DayFlag
import com.remotework.tracker.DayRow
import com.remotework.tracker.FlexEntry
import com.remotework.tracker.FlexStatus
import com.remotework.tracker.Flextime
import com.remotework.tracker.MonthAnalysis
import com.remotework.tracker.MonthRef
import com.remotework.tracker.Priority
import com.remotework.tracker.TrackerState
import java.time.format.TextStyle as JavaTextStyle
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Flextime view: trend chart with the yellow (above 40 h) and red (above 60 h) zones, proposals to reduce
 * overtime and the detailed analysis of one month. The numbers come from the shared module ([Flextime]),
 * the same rules as the other platforms.
 */

// ---- text helpers ----

/** At most two decimals without trailing zeros: "40", "0,5", "2,5". */
internal fun numText(v: Double, lang: String): String {
    val text = fmtL(v, 2, lang)
    val sep = if (lang == "de") ',' else '.'
    return if (text.contains(sep)) text.trimEnd('0').trimEnd(sep) else text
}

/** "+41,50h", "-13,50h", "0,00h" (the plus sign only for positive values). */
internal fun signedHours(v: Double, lang: String): String = (if (v > 0.004) "+" else "") + fmtL(v, 2, lang) + "h"

internal fun monthShort(month: Int, lang: String): String =
    java.time.Month.of(month).getDisplayName(JavaTextStyle.SHORT, localeOf(lang)).trimEnd('.')

internal fun monthYearText(ref: MonthRef, lang: String): String =
    java.time.Month.of(ref.month).getDisplayName(JavaTextStyle.FULL, localeOf(lang)) + " " + ref.year

internal fun flexStatusText(status: FlexStatus?, lang: String): String = when (status) {
    FlexStatus.OK -> t("flex_status_ok", lang)
    FlexStatus.YELLOW_POS -> t("flex_status_yellow_pos", lang)
    FlexStatus.RED_POS -> t("flex_status_red_pos", lang)
    FlexStatus.YELLOW_NEG -> t("flex_status_yellow_neg", lang)
    FlexStatus.RED_NEG -> t("flex_status_red_neg", lang)
    null -> ""
}

/** The proposal as a sentence. The numbers are formatted in the app language. */
internal fun adviceText(item: AdviceItem, lang: String): String {
    fun v(key: String) = numText(item.values[key] ?: 0.0, lang)
    return when (item.code) {
        AdviceCode.CAP_CUT -> t("advice_cap_cut", lang).format(v("limit"), v("over"))
        AdviceCode.REDUCE -> t("advice_reduce", lang).format(v("balance"), v("over"), v("limit"), v("target"), v("need"))
        AdviceCode.NEAR_LIMIT -> t("advice_near_limit", lang).format(v("balance"), v("toLimit"), v("limit"), v("target"), v("need"))
        AdviceCode.FLEX_DAYS -> t("advice_flex_days", lang).format(v("days"), v("soll"), v("fullDays"), v("rest"))
        AdviceCode.SHORTER_DAYS -> t("advice_shorter_days", lang).format(v("perDay"), v("workdays"), fmtDate(item.until ?: "", lang))
        AdviceCode.APPROVAL -> t("advice_approval", lang)
        AdviceCode.TREND_UP -> t("advice_trend_up", lang).format(v("trend"), v("limit"), v("months"))
        AdviceCode.TREND_UP_OVER -> t("advice_trend_up_over", lang).format(v("trend"))
        AdviceCode.LONG_DAYS -> t("advice_long_days", lang).format(v("limit"), v("n"))
        AdviceCode.DAILY_SURPLUS -> t("advice_daily_surplus", lang).format(v("avgOver"), v("soll"), v("perMonth"))
        AdviceCode.WEEKEND_WORK -> t("advice_weekend_work", lang).format(v("n"))
        AdviceCode.OUTSIDE_WINDOW -> t("advice_outside_window", lang).format(v("n"))
        AdviceCode.BUILD_UP -> t("advice_build_up", lang).format(v("balance"), v("need"))
        AdviceCode.CHECK_MISSING -> t("advice_check_missing", lang).format(v("balance"))
        AdviceCode.ON_TRACK -> t("advice_on_track", lang).format(v("balance"))
    }
}

private fun priorityText(p: Priority, lang: String) = when (p) {
    Priority.HIGH -> t("advice_prio_high", lang)
    Priority.MEDIUM -> t("advice_prio_medium", lang)
    Priority.INFO -> t("advice_prio_info", lang)
}

private fun flagText(flag: DayFlag, lang: String) = when (flag) {
    DayFlag.NO_ENTRY -> t("fm_flag_no_entry", lang)
    DayFlag.LONG_DAY -> t("fm_flag_long_day", lang)
    DayFlag.EARLY -> t("fm_flag_early", lang)
    DayFlag.LATE -> t("fm_flag_late", lang)
    DayFlag.NON_WORKING_DAY -> t("fm_flag_non_working_day", lang)
}

/** "06.10." in German, "10-06" in English: the day without the year (the month is named next to it). */
private fun shortDate(iso: String, lang: String): String =
    fmtDate(iso, lang).let { if (lang == "de") it.take(6) else it.takeLast(5) }

// ---- colours ----

/** Container and content colour of a traffic light (same roles as the ratings of the report). */
@Composable
internal fun flexStatusColors(status: FlexStatus?): Pair<Color, Color> {
    val s = MaterialTheme.status
    val scheme = MaterialTheme.colorScheme
    return when (status) {
        FlexStatus.OK -> s.successContainer to s.onSuccessContainer
        FlexStatus.YELLOW_POS, FlexStatus.YELLOW_NEG -> s.warningContainer to s.onWarningContainer
        FlexStatus.RED_POS, FlexStatus.RED_NEG -> scheme.errorContainer to scheme.onErrorContainer
        null -> scheme.surfaceVariant to scheme.onSurface
    }
}

/** A small pill with the traffic light; nothing for "ok" and for no data (like the web app). */
@Composable
fun FlexStatusBadge(status: FlexStatus?, lang: String, modifier: Modifier = Modifier) {
    if (status == null || status == FlexStatus.OK) return
    val (bg, fg) = flexStatusColors(status)
    Text(
        "⚠ " + flexStatusText(status, lang),
        style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = fg,
        modifier = modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 10.dp, vertical = 3.dp)
    )
}

// ---- chart ----

@Composable
private fun LegendItem(color: Color, alpha: Float, text: String, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(color.copy(alpha = alpha)))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Trend of the flextime: bars = change during the month, line = balance at the end of the month, yellow and red
 * zones above 40 / 60 hours (and below -40 / -60). Months without a statement are bridged by a dashed line.
 */
@OptIn(ExperimentalTextApi::class)
@Composable
fun FlexChart(series: List<FlexEntry>, lang: String, modifier: Modifier = Modifier, showSummary: Boolean = true) {
    val points = series.filter { it.gesamt != null || it.aktuell != null }
    if (points.isEmpty()) {
        Text(t("flex_none", lang), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
        return
    }
    val summary = remember(series) { Flextime.summarize(series) }
    val scheme = MaterialTheme.colorScheme
    val st = MaterialTheme.status
    val measurer = rememberTextMeasurer()
    val muted = MaterialTheme.typography.labelSmall.copy(color = scheme.onSurfaceVariant)
    val strong = MaterialTheme.typography.labelSmall.copy(color = scheme.onSurface, fontWeight = FontWeight.Bold)
    val description = t("flex_chart_desc", lang).format(
        summary.latest?.let { signedHours(it, lang) } ?: "-", summary.min?.let { signedHours(it, lang) } ?: "-",
        summary.max?.let { signedHours(it, lang) } ?: "-"
    )
    val hasGap = points.filter { it.gesamt != null }.zipWithNext().any { (a, b) -> (b.jahr * 12 + b.monat) - (a.jahr * 12 + a.monat) > 1 }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(Modifier.fillMaxWidth().height(250.dp).semantics { contentDescription = description }) {
            fun dp(v: Float) = v.dp.toPx()
            val left = dp(36f)
            val right = dp(44f)
            val top = dp(14f)
            val bottom = dp(40f)
            val pw = size.width - left - right
            val ph = size.height - top - bottom
            val idx = { e: FlexEntry -> e.jahr * 12 + e.monat - 1 }
            val first = idx(points.first())
            val n = idx(points.last()) - first + 1

            val values = ArrayList<Double>().apply { add(0.0) }
            points.forEach { e -> e.gesamt?.let { values.add(it) }; e.aktuell?.let { values.add(it) } }
            val yellow = Flextime.YELLOW_LIMIT
            val red = Flextime.RED_LIMIT
            var lo = values.min()
            var hi = values.max()
            val reachHigh = hi
            val reachLow = lo
            lo = min(lo, -yellow); hi = max(hi, yellow)                                // the yellow zone is always visible
            if (reachHigh > yellow - 5) hi = max(hi, red + 8)                          // room for the red zone where it matters
            if (reachLow < -(yellow - 5)) lo = min(lo, -(red + 8))
            val raw = (hi - lo).takeIf { it > 0 }?.div(5) ?: 1.0
            val mag = 10.0.pow(floor(log10(raw)))
            val step = listOf(1.0, 2.0, 2.5, 3.0, 5.0, 10.0).map { it * mag }.first { it >= raw }       // 30 puts the grid on the 60 h line
            lo = floor(lo / step + 1e-9) * step
            hi = ceil(hi / step - 1e-9) * step
            if (hi == lo) hi = lo + step
            fun y(v: Double) = (top + ph * (hi - v) / (hi - lo)).toFloat()
            val slot = pw / n
            fun x(e: FlexEntry) = (left + slot * (idx(e) - first + 0.5)).toFloat()

            fun zone(from: Double, to: Double, color: Color) {
                val a = max(min(from, to), lo)
                val b = min(max(from, to), hi)
                if (b > a) drawRect(color.copy(alpha = 0.16f), Offset(left, y(b)), Size(pw, y(a) - y(b)))
            }
            zone(yellow, red, st.warning); zone(red, hi, scheme.error)
            zone(-red, -yellow, st.warning); zone(lo, -red, scheme.error)

            // grid and axis labels
            var v = lo
            while (v <= hi + step / 1000) {
                val isZero = abs(v) < step / 1000
                drawLine(scheme.outline.copy(alpha = if (isZero) 0.9f else 0.25f), Offset(left, y(v)), Offset(left + pw, y(v)), dp(if (isZero) 1.5f else 1f))
                val label = (if (step % 1.0 == 0.0) "%.0f" else "%.1f").format(localeOf(lang), v)
                val m = measurer.measure(label, muted)
                drawText(measurer, label, Offset(left - dp(5f) - m.size.width, y(v) - m.size.height / 2f), muted)
                v += step
            }
            // limit lines of the zones
            for ((limit, color) in listOf(yellow to st.warning, -yellow to st.warning, red to scheme.error, -red to scheme.error)) {
                if (limit < lo || limit > hi) continue
                drawLine(color, Offset(left, y(limit)), Offset(left + pw, y(limit)), dp(1.5f), pathEffect = PathEffect.dashPathEffect(floatArrayOf(dp(5f), dp(4f))))
                val label = (if (limit > 0) "+" else "−") + numText(abs(limit), lang) + " h"
                val labelStyle = muted.copy(color = color, fontWeight = FontWeight.Bold)
                val m = measurer.measure(label, labelStyle)
                drawText(measurer, label, Offset(left + pw + dp(4f), y(limit) - m.size.height / 2f), labelStyle)
            }
            // bars: change during the month
            val barWidth = min(dp(18f), slot * 0.55f)
            for (e in points) {
                val change = e.aktuell ?: continue
                drawRect(
                    (if (change >= 0) st.success else scheme.error).copy(alpha = 0.6f),
                    Offset(x(e) - barWidth / 2, y(max(0.0, change))), Size(barWidth, max(1f, abs(y(change) - y(0.0))))
                )
            }
            // line: balance at the end of the month, dashed over months without a statement
            val totals = points.filter { it.gesamt != null }
            for ((a, b) in totals.zipWithNext()) {
                val gap = idx(b) - idx(a) > 1
                drawLine(
                    scheme.primary, Offset(x(a), y(a.gesamt!!)), Offset(x(b), y(b.gesamt!!)), dp(2.5f),
                    pathEffect = if (gap) PathEffect.dashPathEffect(floatArrayOf(dp(6f), dp(5f))) else null
                )
            }
            for ((i, e) in totals.withIndex()) {
                val total = e.gesamt!!
                val ring = when (Flextime.status(total)) {
                    FlexStatus.RED_POS, FlexStatus.RED_NEG -> scheme.error
                    FlexStatus.YELLOW_POS, FlexStatus.YELLOW_NEG -> st.warning
                    else -> scheme.surface
                }
                drawCircle(ring, dp(6.5f), Offset(x(e), y(total)))
                drawCircle(scheme.primary, dp(4f), Offset(x(e), y(total)))
                val extreme = total == totals.maxOf { it.gesamt!! } || total == totals.minOf { it.gesamt!! }
                if (totals.size <= 9 || i == totals.lastIndex || extreme) {                       // many months: only latest, lowest, highest
                    val label = (if (total > 0.004) "+" else "") + fmtL(total, 2, lang)
                    val m = measurer.measure(label, strong)
                    val tx = (x(e) - m.size.width / 2f).coerceIn(0f, size.width - m.size.width)
                    val ty = if (total >= 0) y(total) - dp(12f) - m.size.height else y(total) + dp(10f)
                    drawRoundRect(
                        scheme.surface.copy(alpha = 0.78f), Offset(tx - dp(2f), ty), Size(m.size.width + dp(4f), m.size.height.toFloat()),
                        CornerRadius(dp(3f))
                    )
                    drawText(measurer, label, Offset(tx, ty), strong)
                }
            }
            // month labels (with the year under the first month and every January)
            val stride = ceil(n / max(2, (pw / dp(40f)).toInt()).toDouble()).toInt().coerceAtLeast(1)
            var k = 0
            while (k < n) {
                val month = (first + k) % 12 + 1
                val year = (first + k) / 12
                val cx = left + slot * (k + 0.5f)
                val label = monthShort(month, lang)
                val m = measurer.measure(label, muted)
                drawText(measurer, label, Offset(cx - m.size.width / 2f, top + ph + dp(6f)), muted)
                if (month == 1 || k == 0) {
                    val ym = measurer.measure(year.toString(), strong)
                    drawText(measurer, year.toString(), Offset(cx - ym.size.width / 2f, top + ph + dp(6f) + m.size.height), strong)
                }
                k += stride
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LegendItem(scheme.primary, 1f, t("flex_legend_total", lang), Modifier.weight(1f))
            LegendItem(st.success, 0.6f, t("flex_legend_change", lang), Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LegendItem(st.warning, 0.4f, t("flex_band_yellow", lang), Modifier.weight(1f))
            LegendItem(scheme.error, 0.4f, t("flex_band_red", lang), Modifier.weight(1f))
        }
        if (hasGap) Text(t("flex_gap_note", lang), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        if (showSummary) FlexSummaryLines(summary.latest, summary.latestMonth, summary.min, summary.minMonth, summary.max, summary.maxMonth, summary.status, lang)
    }
}

@Composable
private fun FlexSummaryLines(
    latest: Double?, latestMonth: MonthRef?, min: Double?, minMonth: MonthRef?, max: Double?, maxMonth: MonthRef?,
    status: FlexStatus?, lang: String
) {
    if (latest == null || latestMonth == null) return
    Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.semantics(mergeDescendants = true) {}) {
        Text(
            "${t("flex_latest", lang)}: ${signedHours(latest, lang)} (${monthYearText(latestMonth, lang)})",
            style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold
        )
        if (min != null && minMonth != null && max != null && maxMonth != null) {
            Text(
                "${t("flex_min", lang)}: ${signedHours(min, lang)} (${monthYearText(minMonth, lang)})",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "${t("flex_max", lang)}: ${signedHours(max, lang)} (${monthYearText(maxMonth, lang)})",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (status != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                Text("${t("flex_status_label", lang)}:", style = MaterialTheme.typography.bodySmall)
                if (status == FlexStatus.OK) {
                    val (bg, fg) = flexStatusColors(status)
                    Text(
                        flexStatusText(status, lang), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = fg,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 10.dp, vertical = 3.dp)
                    )
                } else FlexStatusBadge(status, lang)
            }
        }
    }
}

// ---- proposals ----

@Composable
fun FlexAdviceSection(advice: Advice, lang: String) {
    val balance = advice.balance
    val asOf = advice.asOf
    if (advice.items.isEmpty() || asOf == null || balance == null) {
        Text(t("flex_none", lang), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            t("flex_advice_as_of", lang).format(signedHours(balance, lang), monthYearText(asOf, lang), numText(Flextime.TARGET_BUFFER, lang)),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        for (item in advice.items) {
            val (bg, fg) = when (item.priority) {
                Priority.HIGH -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
                Priority.MEDIUM -> MaterialTheme.status.warningContainer to MaterialTheme.status.onWarningContainer
                Priority.INFO -> MaterialTheme.status.successContainer to MaterialTheme.status.onSuccessContainer
            }
            Card(colors = appCardColors(bg, fg), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp).semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(priorityText(item.priority, lang), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Text(adviceText(item, lang), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

// ---- month analysis ----

@Composable
private fun MonthChip(label: String, isSelected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Text(
        label,
        style = MaterialTheme.typography.labelLarge, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
        color = if (isSelected) scheme.onPrimary else scheme.onSurface,
        modifier = Modifier
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(50))
            .background(if (isSelected) scheme.primary else scheme.surfaceVariant)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { selected = isSelected }
            .padding(horizontal = 14.dp, vertical = 9.dp)
    )
}

@Composable
private fun TableLine(cells: List<String>, weights: List<Float>, bold: Boolean = false, toneIndex: Int = -1, tone: Color = Color.Unspecified) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        cells.forEachIndexed { i, text ->
            Text(
                text, modifier = Modifier.weight(weights[i]), textAlign = if (i == 0) TextAlign.Start else TextAlign.End,
                style = MaterialTheme.typography.bodySmall, fontWeight = if (bold || i == toneIndex) FontWeight.Bold else null,
                color = if (i == toneIndex) tone else Color.Unspecified, maxLines = 1
            )
        }
    }
}

@Composable
private fun deltaColor(v: Double): Color = when {
    v > 0.004 -> MaterialTheme.status.success
    v < -0.004 -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun DayLine(row: DayRow, lang: String) {
    val date = java.time.LocalDate.parse(row.datum)
    val dayLabel = date.dayOfWeek.getDisplayName(JavaTextStyle.SHORT, localeOf(lang)).trimEnd('.') + " " + shortDate(row.datum, lang)
    val missing = DayFlag.NO_ENTRY in row.flags
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp).semantics(mergeDescendants = true) {}) {
        TableLine(
            listOf(
                dayLabel, if (row.start != null) "${row.start}–${row.end}" else "", if (row.entries > 0) fmtL(row.net, 2, lang) else "",
                if (row.soll > 0 || row.net > 0) signedHours(row.delta, lang) else "", signedHours(row.balance, lang)
            ),
            listOf(1.25f, 1.35f, 0.8f, 1f, 1f), toneIndex = 3, tone = deltaColor(row.delta)
        )
        val flags = row.flags.joinToString(", ") { flagText(it, lang) }
        if (flags.isNotEmpty()) {
            Text(
                flags, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold,
                color = if (missing) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.status.warning
            )
        }
    }
}

@Composable
private fun FlexMonthDetails(a: MonthAnalysis, lang: String) {
    if (!a.hasEntries && a.flex == null) {
        Text(t("fm_no_data", lang), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val totals = a.totals
    val dash = "–"
    val tiles = listOf(
        t("fm_balance_start", lang) to (a.flex?.vorperiode?.let { signedHours(it, lang) } ?: dash),
        t("fm_balance_end", lang) to (a.flex?.gesamt?.let { signedHours(it, lang) } ?: dash),
        t("fm_change_sap", lang) to (a.flex?.aktuell?.let { signedHours(it, lang) } ?: dash),
        t("fm_change_calc", lang) to signedHours(totals.deltaCalc, lang),
        t("fm_difference", lang) to (a.difference?.let { signedHours(it, lang) } ?: dash),
        t("fm_avg_day", lang) to (totals.avgNet?.let { fmtL(it, 2, lang) + "h" } ?: dash),
        t("fm_avg_times", lang) to (if (totals.avgStart != null) "${totals.avgStart} – ${totals.avgEnd}" else dash),
        t("fm_longest", lang) to (totals.longest?.let { "${fmtL(it.net, 2, lang)}h (${shortDate(it.datum, lang)})" } ?: dash),
        t("fm_long_days", lang) to totals.longDays.toString(),
        t("fm_outside", lang) to (totals.earlyDays + totals.lateDays).toString(),
        t("fm_non_working", lang) to totals.nonWorkingDays.toString(),
        t("fm_missing", lang) to "${totals.missingDays.size} (${fmtL(totals.missingHours, 2, lang)}h)"
    )
    val large = LocalDensity.current.fontScale > 1.15f
    // without daily entries (e.g. after "close month") only the figures of the statement mean anything
    val shown = if (a.hasEntries) tiles else tiles.take(3)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (a.hasEntries && !a.complete) Text(t("fm_incomplete", lang), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (a.flexStatus != null && a.flexStatus != FlexStatus.OK) FlexStatusBadge(a.flexStatus, lang)
        if (large) {
            shown.forEach { (label, value) -> StatCard(label, value, Modifier.fillMaxWidth(), horizontal = true) }
        } else {
            shown.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { (label, value) -> StatCard(label, value, Modifier.weight(1f).fillMaxHeight()) }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        if (!a.hasEntries) {
            Text(t("fm_no_data", lang), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            FlexMonthTables(a, lang)
        }
    }
}

/** Hint, week table and day table of a month that still has its daily entries. */
@Composable
private fun FlexMonthTables(a: MonthAnalysis, lang: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(t("fm_diff_hint", lang), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Text(t("fm_weeks", lang), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp).semantics { heading() })
        Card(colors = appCardColors(), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                val w = listOf(1f, 0.8f, 1.2f, 1.2f, 1.2f)
                TableLine(listOf(t("fm_week_short", lang), t("fm_days", lang), t("fm_col_net", lang), t("fm_col_soll", lang), t("fm_col_delta", lang)), w, bold = true)
                a.weeks.forEach {
                    TableLine(
                        listOf("${t("fm_week_short", lang)} ${it.week}", it.days.toString(), fmtL(it.net, 2, lang) + "h", fmtL(it.soll, 2, lang) + "h", signedHours(it.delta, lang)),
                        w, toneIndex = 4, tone = deltaColor(it.delta)
                    )
                }
            }
        }

        Text(t("fm_days", lang), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp).semantics { heading() })
        Card(colors = appCardColors(), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                TableLine(listOf(t("fm_days", lang), "", t("fm_col_net", lang), t("fm_col_delta", lang), t("fm_col_balance", lang)), listOf(1.25f, 1.35f, 0.8f, 1f, 1f), bold = true)
                a.days.forEach { DayLine(it, lang) }
            }
        }
    }
}

@Composable
fun FlexMonthSection(state: TrackerState, series: List<FlexEntry>, lang: String) {
    val months = series.filter { it.gesamt != null || it.aktuell != null }.reversed()
    if (months.isEmpty()) return
    var selected by rememberSaveable { mutableStateOf("") }
    val current = months.firstOrNull { "${it.jahr}-${it.monat}" == selected } ?: months.first()
    val analysis = remember(state, current) { Flextime.analyzeMonth(state, current.jahr, current.monat) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (e in months) {
                val key = "${e.jahr}-${e.monat}"
                MonthChip("${monthShort(e.monat, lang)} ${e.jahr % 100}", key == "${current.jahr}-${current.monat}") { selected = key }
            }
        }
        Text(
            monthYearText(MonthRef(current.jahr, current.monat), lang), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() }
        )
        FlexMonthDetails(analysis, lang)
    }
}

// ---- dialog ----

@Composable
private fun SectionHeading(text: String) {
    Text(
        text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp).semantics { heading() }
    )
}

/** Full-screen flextime view: trend chart, proposals and month analysis. */
@Composable
fun FlexDialog(state: TrackerState, lang: String, onDismiss: () -> Unit) {
    val series = remember(state) { Flextime.series(state) }
    val advice = remember(state) {
        val latest = series.lastOrNull { it.gesamt != null }
        Flextime.advice(state, entries = series, analysis = latest?.let { Flextime.analyzeMonth(state, it.jahr, it.monat) })
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        t("flex_title", lang), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f).semantics { heading() }
                    )
                    TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text(t("close", lang)) }
                }
                Box(Modifier.fillMaxSize().navigationBarsPadding(), contentAlignment = Alignment.TopCenter) {
                    Column(
                        Modifier.widthIn(max = 640.dp).fillMaxSize().testTag("flex_scroll").verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp).padding(bottom = 24.dp)
                    ) {
                        FlexChart(series, lang)
                        SectionHeading(t("flex_advice_title", lang))
                        FlexAdviceSection(advice, lang)
                        SectionHeading(t("flex_month_title", lang))
                        FlexMonthSection(state, series, lang)
                    }
                }
            }
        }
    }
}
