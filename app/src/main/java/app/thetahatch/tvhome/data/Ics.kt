package app.thetahatch.tvhome.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

data class Event(val title: String, val start: ZonedDateTime, val end: ZonedDateTime?, val allDay: Boolean, val location: String = "")

/**
 * iCalendar（.ics）を読む。Google カレンダーの「iCal 形式の非公開 URL」をそのまま使う想定。
 * 繰り返し（RRULE）は FREQ=DAILY/WEEKLY/MONTHLY/YEARLY・INTERVAL・COUNT・UNTIL・BYDAY（週）と EXDATE に対応。
 * 表示に要る「これからの数日」だけを作るので、軽い。
 */
object Ics {
    private val DT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
    private val D = DateTimeFormatter.ofPattern("yyyyMMdd")

    /** 折り返し行（先頭が空白・タブ）をつなぐ。 */
    fun unfold(text: String): List<String> {
        val out = ArrayList<String>()
        for (raw in text.split("\r\n", "\n")) {
            if ((raw.startsWith(" ") || raw.startsWith("\t")) && out.isNotEmpty()) out[out.size - 1] = out.last() + raw.substring(1)
            else out += raw
        }
        return out
    }

    private data class Prop(val name: String, val params: Map<String, String>, val value: String)

    private fun prop(line: String): Prop? {
        val colon = line.indexOf(':').takeIf { it > 0 } ?: return null
        val head = line.substring(0, colon).split(';')
        val params = head.drop(1).mapNotNull { p -> p.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0].uppercase() to it[1].trim('"') } }.toMap()
        return Prop(head[0].uppercase(), params, line.substring(colon + 1))
    }

    private fun text(v: String) = v.replace("\\n", " ").replace("\\N", " ").replace("\\,", ",").replace("\\;", ";").replace("\\\\", "\\")

    /** 日時を読む。Z 付きは UTC、TZID があればそれ、無ければ端末の時刻。 */
    fun time(p: String, params: Map<String, String>, zone: ZoneId): Pair<ZonedDateTime, Boolean>? = runCatching {
        if (params["VALUE"] == "DATE" || p.length == 8) {
            LocalDate.parse(p.take(8), D).atStartOfDay(zone) to true
        } else if (p.endsWith("Z")) {
            LocalDateTime.parse(p.dropLast(1), DT).atZone(ZoneOffset.UTC).withZoneSameInstant(zone) to false
        } else {
            val tz = params["TZID"]?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: zone
            LocalDateTime.parse(p.take(15), DT).atZone(tz).withZoneSameInstant(zone) to false
        }
    }.getOrNull()

    /** from から days 日の間にかかる予定を、始まりの早い順に。 */
    fun events(text: String, from: ZonedDateTime, days: Long, zone: ZoneId = from.zone): List<Event> {
        val to = from.plusDays(days)
        val out = ArrayList<Event>()
        var cur: MutableList<Prop>? = null
        for (line in unfold(text)) {
            when {
                line == "BEGIN:VEVENT" -> cur = ArrayList()
                line == "END:VEVENT" -> { cur?.let { out += expand(it, from, to, zone) }; cur = null }
                cur != null -> prop(line)?.let { cur.add(it) }
            }
        }
        // 同じ UID の「この回だけ変更」（RECURRENCE-ID）は今は区別しない。同じ題・同じ開始は 1 つに
        return out.distinctBy { it.title to it.start }.sortedBy { it.start }
    }

    private fun expand(props: List<Prop>, from: ZonedDateTime, to: ZonedDateTime, zone: ZoneId): List<Event> {
        val get = { n: String -> props.firstOrNull { it.name == n } }
        if (get("STATUS")?.value == "CANCELLED") return emptyList()
        val s = get("DTSTART") ?: return emptyList()
        val (start, allDay) = time(s.value, s.params, zone) ?: return emptyList()
        val end = get("DTEND")?.let { time(it.value, it.params, zone)?.first }
        val dur = end?.let { java.time.Duration.between(start, it) } ?: if (allDay) java.time.Duration.ofDays(1) else java.time.Duration.ZERO
        val title = text(get("SUMMARY")?.value ?: "（題なし）")
        val loc = text(get("LOCATION")?.value ?: "")
        val ex = props.filter { it.name == "EXDATE" }.flatMap { p -> p.value.split(',').mapNotNull { time(it, p.params, zone)?.first } }.toSet()
        val rule = get("RRULE")?.value?.split(';')?.mapNotNull { it.split('=', limit = 2).takeIf { kv -> kv.size == 2 }?.let { kv -> kv[0].uppercase() to kv[1] } }?.toMap()

        fun hit(st: ZonedDateTime): Event? {
            val en = st.plus(dur)
            val inRange = st < to && (if (dur.isZero) st >= from else en > from)
            return if (inRange && st !in ex) Event(title, st, if (dur.isZero) null else en, allDay, loc) else null
        }
        if (rule == null) return listOfNotNull(hit(start))

        val freq = rule["FREQ"] ?: return listOfNotNull(hit(start))
        val interval = rule["INTERVAL"]?.toLongOrNull()?.coerceAtLeast(1) ?: 1
        val count = rule["COUNT"]?.toIntOrNull()
        val until = rule["UNTIL"]?.let { time(it, emptyMap(), zone)?.first }
        val byDay = rule["BYDAY"]?.split(',')?.mapNotNull { day(it.takeLast(2)) }?.toSet().orEmpty()
        val res = ArrayList<Event>()
        var n = 0
        var i = 0L
        // 上限：何年も前から続く毎日の予定でも重くならないよう 3000 回で打ち切る
        while (i < 3000) {
            val base = when (freq) {
                "DAILY" -> start.plusDays(i * interval)
                "WEEKLY" -> start.plusWeeks(i * interval)
                "MONTHLY" -> start.plusMonths(i * interval)
                "YEARLY" -> start.plusYears(i * interval)
                else -> return listOfNotNull(hit(start))
            }
            val cands = if (freq == "WEEKLY" && byDay.isNotEmpty()) {
                val monday = base.with(DayOfWeek.MONDAY)
                byDay.map { monday.with(it) }.filter { it >= start }.sorted()
            } else listOf(base)
            for (c in cands) {
                if (until != null && c > until) return res
                n++
                if (count != null && n > count) return res
                hit(c)?.let { res += it }
            }
            if (base >= to) break
            i++
        }
        return res
    }

    private fun day(s: String): DayOfWeek? = when (s.uppercase()) {
        "MO" -> DayOfWeek.MONDAY; "TU" -> DayOfWeek.TUESDAY; "WE" -> DayOfWeek.WEDNESDAY; "TH" -> DayOfWeek.THURSDAY
        "FR" -> DayOfWeek.FRIDAY; "SA" -> DayOfWeek.SATURDAY; "SU" -> DayOfWeek.SUNDAY; else -> null
    }
}
