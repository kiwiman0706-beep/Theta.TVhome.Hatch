package app.thetahatch.tvhome.ui

import app.thetahatch.tvhome.TvHomeApp
import app.thetahatch.tvhome.data.AppItem
import app.thetahatch.tvhome.music.MusicLib
import app.thetahatch.tvhome.nas.NasEntry
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
    /** false：ぜんぶのアプリは出さない（クロスバーは右上の「ぜんぶのアプリ」から） */
    withAll: Boolean = true,
    /** おんがくの棚（NAS）。空でも NAS の設定があれば「おんがく」を出す */
    music: List<NasEntry> = emptyList(),
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
        if (kids || !withAll) add(Item("all", "ぜんぶのアプリ", "▦", sub = "ホームに だす アプリを えらぶ", onClick = { guarded { go("apps") } }))
        add(Item("updates", "こうしん", "⟳", badge = updates.takeIf { it > 0 }?.toString(), sub = if (updates > 0) "あたらしい版が $updates こ" else null, onClick = { guarded { go("updates") } }))
        add(Item("settings", "せってい", "⚙", onClick = { guarded { go("settings") } }))
    }
    val musicItems = buildList {
        if (!app.state.dsm.configured) add(Item("nas", "NAS を せっていする", "⚙", sub = "NAS の音楽・動画・本を見るには", onClick = { guarded { go("settings") } }))
        else {
            val last = s.musicLast
            if (last.isNotBlank()) add(Item("music-last", "つづきから", "⏯", sub = MusicLib.shelfName(last.trimEnd('/').substringAfterLast('/')), onClick = { app.state.musicPath.value = last; go("music") }))
            music.forEach { e -> add(Item("m:" + e.path, MusicLib.shelfName(e.name), "💿", onClick = { app.state.musicPath.value = e.path; go("music") })) }
            add(Item("music-all", "ぜんぶの おんがく", "🎵", sub = app.state.musicRoot, onClick = { app.state.musicPath.value = ""; go("music") }))
        }
    }
    return buildList {
        add(Category("tv", "テレビ", "📺", tv))
        add(Category("music", "おんがく", "🎵", musicItems))
        if (fav.isNotEmpty() || !kids) add(Category("fav", "すき", "⭐", fav))
        if (!kids && withAll) add(Category("apps", "アプリ", "▦", rest))
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
