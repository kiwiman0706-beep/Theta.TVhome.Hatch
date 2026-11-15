package app.thetahatch.tvhome.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.thetahatch.tvhome.data.Event
import app.thetahatch.tvhome.data.Forecast
import app.thetahatch.tvhome.data.Weather
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** 日曜はじまりの 6 週。 */
fun monthGrid(today: LocalDate): List<LocalDate> {
    val first = today.withDayOfMonth(1)
    val start = first.minusDays((first.dayOfWeek.value % 7).toLong())
    return (0 until 42).map { start.plusDays(it.toLong()) }
}

/** その日にかかる予定（何日も続く予定は毎日に出す）。 */
fun eventsOn(day: LocalDate, events: List<Event>): List<Event> = events.filter { e ->
    val s = e.start.toLocalDate()
    // 終日の予定の終わりは「次の日の 0 時」なので 1 日戻す
    val end = e.end?.let { if (e.allDay) it.toLocalDate().minusDays(1) else it.toLocalDate() } ?: s
    !day.isBefore(s) && !day.isAfter(maxOf(s, end))
}

/**
 * 予定とてんき入りの月のカレンダー。マスに日付・てんき（7 日先まで）・予定の名前（2 件、残りは +n）。
 * きょうは accent の枠、日曜は赤、土曜は青、ほかの月は薄く。
 */
@Composable
fun MonthCalendar(today: LocalDate, events: List<Event>, forecast: Forecast?, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val days = remember(today) { monthGrid(today) }
    val zone = remember { ZoneId.systemDefault() }
    val wx = remember(forecast) {
        forecast?.days?.associateBy { Instant.ofEpochMilli(it.date).atZone(zone).toLocalDate() }.orEmpty()
    }
    val sun = Color(0xFFE5534B)
    val sat = Color(0xFF4FA3E0)
    // 6 週目が全部ほかの月なら 5 週で出す
    val weeks = if (days[35].month != today.month) 5 else 6
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            listOf("日", "月", "火", "水", "木", "金", "土").forEachIndexed { i, w ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    T(w, size = 12.sp, color = when (i) { 0 -> sun; 6 -> sat; else -> p.muted })
                }
            }
        }
        Spacer(Modifier.height(2.dp))
        Column(Modifier.fillMaxSize()) {
            for (w in 0 until weeks) {
                Row(Modifier.fillMaxWidth().weight(1f)) {
                    for (d in 0 until 7) {
                        val day = days[w * 7 + d]
                        val inMonth = day.month == today.month
                        val isToday = day == today
                        val list = eventsOn(day, events)
                        Column(
                            Modifier.weight(1f).fillMaxHeight().padding(1.dp)
                                .background(if (isToday) p.accent.copy(alpha = 0.16f) else p.panel.copy(alpha = if (inMonth) 0.5f else 0.2f))
                                .border(if (isToday) 2.dp else 0.5.dp, if (isToday) p.accent else p.line.copy(alpha = 0.5f))
                                .padding(horizontal = 3.dp, vertical = 1.dp)
                                .alpha(if (inMonth) 1f else 0.4f),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                T(day.dayOfMonth.toString(), size = 12.sp, font = Mono, weight = if (isToday) FontWeight.SemiBold else FontWeight.Normal,
                                    color = when { isToday -> p.accent; d == 0 -> sun; d == 6 -> sat; else -> p.fg })
                                Spacer(Modifier.weight(1f))
                                wx[day]?.let { dw -> T(Weather.describe(dw.code).first + "%.0f".format(dw.max), size = 10.sp, color = p.muted) }
                            }
                            list.take(2).forEach { e ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.width(2.dp).height(10.dp).background(if (e.allDay) p.widget else p.accent))
                                    Spacer(Modifier.width(2.dp))
                                    T(e.title, size = 10.sp)
                                }
                            }
                            if (list.size > 2) T("+${list.size - 2}", size = 9.sp, color = p.muted)
                        }
                    }
                }
            }
        }
    }
}
