package com.debaxyz.selfattendance.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToLong

/** s: p=present, l=late, v=leave, w=weekly off, f=festival | sh: d/n | ot: overtime hours */
@IgnoreExtraProperties
data class Rec(
    @DocumentId val date: String = "",
    val s: String = "p",
    val sh: String? = null,
    val ot: Double? = null,
    val inT: Long? = null,
    val outT: Long? = null,
    val userId: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class Cfg(
    val std: Int = 8,
    val currency: String = "₹",
    val dayRate: Double = 500.0,
    val nightRate: Double = 500.0,
    val otHourRate: Double = 150.0,
    val festivalRate: Double = 500.0,
    val userId: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class MonthStats(
    val ym: YearMonth,
    val name: String,
    val totalDays: Int,
    val present: Int,
    val late: Int,
    val leave: Int,
    val festivalDays: Int,
    val hours: Long,
    val dayShifts: Int,
    val nightShifts: Int,
    val weeklyOff: Int,
    val overtime: Double,
    val baseMoney: Long,
    val otMoney: Long,
    val festivalMoney: Long,
    val totalMoney: Long
)

data class Totals(
    val present: Int,
    val dayShifts: Int,
    val nightShifts: Int,
    val overtime: Double,
    val hours: Long,
    val leave: Int,
    val weeklyOff: Int,
    val festivalDays: Int,
    val baseMoney: Long,
    val otMoney: Long,
    val festivalMoney: Long,
    val totalMoney: Long
)

val CURRENCIES = listOf("\u20b9", "$", "\u20ac", "\u00a3", "AED", "SAR", "\u00a5")
val OT_VALUES = listOf(0.0, 1.0, 2.0, 3.0, 4.0, 5.0)
val DOW = listOf("S", "M", "T", "W", "T", "F", "S")

fun num(d: Double): String = if (d % 1.0 == 0.0) d.toLong().toString() else d.toString()
fun f1(d: Double): String = String.format(Locale.US, "%.1f", d)
fun round1(d: Double): Double = Math.round(d * 10.0) / 10.0
fun money(n: Long): String = NumberFormat.getInstance().format(n)
fun pad2(n: Int) = n.toString().padStart(2, '0')
fun fmtDate(d: LocalDate, pattern: String): String =
    d.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))

fun fmtTime(ms: Long): String =
    Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()))

fun workedHours(r: Rec?, std: Int): Double {
    if (r == null) return 0.0
    if (r.inT != null && r.outT != null) return (r.outT - r.inT) / 3.6e6
    if (r.s == "p" || r.s == "l") return std + (r.ot ?: 0.0)
    return 0.0
}

fun monthStats(ym: YearMonth, data: Map<String, Rec>, cfg: Cfg): MonthStats {
    var p = 0; var lt = 0; var v = 0; var h = 0.0; var d = 0; var n = 0; var o = 0; var f = 0
    var ot = 0.0
    val days = ym.lengthOfMonth()
    for (day in 1..days) {
        val e = data[ym.atDay(day).toString()] ?: continue
        val work = e.s != "v" && e.s != "w" && e.s != "f"
        if (e.s == "w") o++
        if (e.s == "f") f++
        if (work) ot += e.ot ?: max(0.0, workedHours(e, cfg.std) - cfg.std)
        when (e.s) { "p" -> p++; "l" -> lt++; "v" -> v++ }
        h += workedHours(e, cfg.std)
        if (work) { if (e.sh == "n") n++ else d++ }
    }
    val present = p + lt
    val base = d * cfg.dayRate + n * cfg.nightRate
    val otM = ot * cfg.otHourRate
    val fM = f * cfg.festivalRate
    return MonthStats(
        ym, ym.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())), days,
        present, lt, v, f, h.roundToLong(), d, n, o, round1(ot),
        base.roundToLong(), otM.roundToLong(), fM.roundToLong(), (base + otM + fM).roundToLong()
    )
}

fun totals(list: List<MonthStats>) = Totals(
    list.sumOf { it.present }, list.sumOf { it.dayShifts }, list.sumOf { it.nightShifts },
    list.sumOf { it.overtime }, list.sumOf { it.hours }, list.sumOf { it.leave },
    list.sumOf { it.weeklyOff }, list.sumOf { it.festivalDays },
    list.sumOf { it.baseMoney }, list.sumOf { it.otMoney },
    list.sumOf { it.festivalMoney }, list.sumOf { it.totalMoney }
)
