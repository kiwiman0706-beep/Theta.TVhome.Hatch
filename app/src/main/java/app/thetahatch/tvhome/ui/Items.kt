package app.thetahatch.tvhome.ui

import app.thetahatch.tvhome.TvHomeApp
import app.thetahatch.tvhome.data.AppItem
import app.thetahatch.tvhome.data.Entry
import app.thetahatch.tvhome.data.Event
import app.thetahatch.tvhome.data.Forecast
import app.thetahatch.tvhome.data.Weather
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** どの形（キッズ・クロスバー）でも使う、並べる物 1 つ。 */
data class Item(
    val key: String,
    val label: String,
    val icon: String = "",
    val app: AppItem? = null,
    val sub: String? = null,
    val dim: Boolean = false,
    val badge: String? = null,
    val onClick: () -> Unit = {},
    val onMenu: (() -> Unit)? = null,
)

data class Category(val key: String, val label: String, val icon: String, val items: List<Item>)

/** ホームに出すまとまり（テレビ・すき・アプリ・Hatch）。 */
fun buildCategories(
    app: TvHomeApp,
    apps: List<AppItem>,
    entries: List<Entry>,
    updates: Int,
    go: (String) -> Unit,
    menu: (TileMenu) -> Unit,
    guarded: (() -> Unit) -> Unit,
): List<Category> {
    val s = app.store
    val hidden = s.hidden
    val kids = s.kidsMode
    val tv = entries.filter { it.key !in hidden && (it.intent != null || !kids) }.map { e ->
        Item(
            e.key, e.label, e.icon, sub = if (e.intent == null) "未設定" else null, dim = e.intent == null,
            onClick = { app.state.launch(e.intent) },
            onMenu = { guarded { menu(TileMenu(e.label, listOf("かくす" to { s.hidden = s.hidden + e.key; app.settingsChanged() }))) } },
        )
    }
    fun appItem(a: AppItem) = Item(a.pkg, a.label, app = a, onClick = { app.state.launch(a.intent) }, onMenu = { guarded { menu(appMenu(app, a)) } })
    val fav = s.favorites.mapNotNull { f -> apps.firstOrNull { it.pkg == f } }.map(::appItem)
    val rest = apps.filter { it.pkg !in hidden && it.pkg !in s.favorites }.map(::appItem)
    val hatch = buildList {
        if (kids) add(Item("all", "ぜんぶのアプリ", "▦", onClick = { guarded { go("apps") } }))
        add(Item("updates", "こうしん", "⟳", badge = updates.takeIf { it > 0 }?.toString(), sub = if (updates > 0) "あたらしい版が $updates こ" else null, onClick = { guarded { go("updates") } }))
        add(Item("settings", "せってい", "⚙", onClick = { guarded { go("settings") } }))
    }
    return buildList {
        add(Category("tv", "テレビ", "📺", tv))
        if (fav.isNotEmpty() || !kids) add(Category("fav", "すき", "⭐", fav))
        if (!kids) add(Category("apps", "アプリ", "▦", rest))
        add(Category("hatch", "せってい", "⚙", hatch))
    }
}

/** 予定の 1 行（「きょう 16:30」）。 */
fun eventWhen(e: Event): String {
    val today = LocalDate.now()
    val d = e.start.toLocalDate()
    val day = when (d) { today -> "きょう"; today.plusDays(1) -> "あした"; today.plusDays(2) -> "あさって"; else -> d.format(DateTimeFormatter.ofPattern("M/d")) }
    return if (e.allDay) day else day + " " + e.start.format(DateTimeFormatter.ofPattern("H:mm"))
}

fun upcoming(events: List<Event>, now: ZonedDateTime = ZonedDateTime.now()) = events.filter { (it.end ?: it.start) >= now }

fun weatherLine(f: Forecast?): Pair<String, String>? {
    f ?: return null
    val (icon, word) = Weather.describe(f.code, f.isDay)
    val d = f.days.firstOrNull()
    return icon to ("%.0f°  $word".format(f.temp) + (d?.let { "  ☂%d%%".format(it.rain) } ?: ""))
}
