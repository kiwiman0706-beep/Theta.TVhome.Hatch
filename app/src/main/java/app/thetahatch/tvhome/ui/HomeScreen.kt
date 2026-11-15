package app.thetahatch.tvhome.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.thetahatch.tvhome.TvHomeApp
import app.thetahatch.tvhome.data.AppItem
import app.thetahatch.tvhome.data.AppList
import app.thetahatch.tvhome.data.Event
import app.thetahatch.tvhome.data.Forecast
import app.thetahatch.tvhome.data.Weather
import app.thetahatch.tvhome.hub.Catalog
import app.thetahatch.tvhome.hub.UpdateHub
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val TILE_W = 200.dp
private val TILE_H = 128.dp

/** 長押し・メニューで出す、タイルの操作。 */
data class TileMenu(val title: String, val actions: List<Pair<String, () -> Unit>>)

@Composable
fun HomeScreen(app: TvHomeApp, firstFocus: FocusRequester, go: (String) -> Unit, menu: (TileMenu) -> Unit, guarded: (() -> Unit) -> Unit) {
    val s = app.store
    val state = app.state
    val tick by state.tick.collectAsState()
    val apps by state.apps.collectAsState()
    val entries by state.entries.collectAsState()
    val forecast by state.forecast.collectAsState()
    val events by state.events.collectAsState()
    val calErr by state.calendarError.collectAsState()
    val hub by UpdateHub.status.collectAsState()
    val p = LocalPalette.current
    val list = rememberLazyListState()

    val hidden = remember(tick, apps) { s.hidden }
    val favorites = remember(tick, apps) { s.favorites.mapNotNull { f -> apps.firstOrNull { it.pkg == f } } }
    val kids = remember(tick) { s.kidsMode }
    val updates = Catalog.android.count { UpdateHub.hasNewer(it, hub[it.id] ?: return@count false) }

    LaunchedEffect(Unit) { delay(150); runCatching { firstFocus.requestFocus() } }

    LazyColumn(state = list, contentPadding = PaddingValues(horizontal = 48.dp, vertical = 27.dp)) {
        item { Header(forecast, events, calErr) }

        item { SectionTitle("テレビ") }
        item {
            val shown = entries.filter { it.key !in hidden && (it.intent != null || !kids) }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(vertical = 10.dp, horizontal = 6.dp)) {
                items(shown, key = { it.key }) { e ->
                    Tile(
                        e.label, TILE_W, TILE_H, icon = e.icon, dim = e.intent == null,
                        sub = if (e.intent == null) "未設定" else null,
                        focusRequester = if (e == shown.first()) firstFocus else null,
                        onClick = { state.launch(e.intent) },
                        onMenu = { guarded { menu(TileMenu(e.label, listOf("かくす" to { s.hidden = s.hidden + e.key; app.settingsChanged() }))) } },
                    )
                }
            }
        }

        if (favorites.isNotEmpty() || !kids) {
            item { SectionTitle("すきなアプリ") }
            item {
                if (favorites.isEmpty()) T("アプリを選んで 長押し（またはメニューボタン）で ★ を付けると、ここに並びます", size = 20.sp, color = p.muted, lines = 2)
                else AppRow(app, favorites, big = true, menu = menu, guarded = guarded)
            }
        }

        if (!kids) {
            item { SectionTitle("アプリ") }
            item { AppRow(app, apps.filter { it.pkg !in hidden && it.pkg !in s.favorites }, big = false, menu = menu, guarded = guarded) }
        }

        item { SectionTitle("Hatch") }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(vertical = 10.dp, horizontal = 6.dp)) {
                if (kids) item { Tile("ぜんぶのアプリ", TILE_W, TILE_H, icon = "▦", onClick = { guarded { go("apps") } }) }
                item { Tile("こうしん", TILE_W, TILE_H, icon = "⟳", badge = updates.takeIf { it > 0 }?.toString(), onClick = { guarded { go("updates") } }) }
                item { Tile("せってい", TILE_W, TILE_H, icon = "⚙", onClick = { guarded { go("settings") } }) }
            }
        }
        item { Spacer(Modifier.height(40.dp)) }
    }
}

@Composable
fun AppRow(app: TvHomeApp, items: List<AppItem>, big: Boolean, menu: (TileMenu) -> Unit, guarded: (() -> Unit) -> Unit) {
    val w = if (big) 260.dp else 200.dp
    val h = if (big) 146.dp else 112.dp
    LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(vertical = 10.dp, horizontal = 6.dp)) {
        items(items, key = { it.pkg }) { a ->
            val px = with(LocalDensity.current) { w.roundToPx() }
            val art = appArt(a, px)
            Tile(
                a.label, w, h, image = art, banner = a.hasBanner,
                onClick = { app.state.launch(a.intent) },
                onMenu = { guarded { menu(appMenu(app, a)) } },
            )
        }
    }
}

@Composable
fun appArt(a: AppItem, px: Int): ImageBitmap? {
    val ctx = LocalContext.current
    val bmp by produceState<ImageBitmap?>(null, a.pkg, px) {
        value = withContext(Dispatchers.IO) { AppList.art(ctx, a, px)?.asImageBitmap() }
    }
    return bmp
}

fun appMenu(app: TvHomeApp, a: AppItem): TileMenu {
    val s = app.store
    val fav = s.favorites
    val actions = mutableListOf<Pair<String, () -> Unit>>()
    if (a.pkg in fav) {
        actions += "ホームから はずす" to { s.favorites = fav - a.pkg }
        val i = fav.indexOf(a.pkg)
        if (i > 0) actions += "まえへ（↑・←）" to { s.favorites = fav.toMutableList().apply { removeAt(i); add(i - 1, a.pkg) } }
        if (i < fav.size - 1) actions += "うしろへ（↓・→）" to { s.favorites = fav.toMutableList().apply { removeAt(i); add(i + 1, a.pkg) } }
    } else actions += "★ ホームに だす" to { s.favorites = fav + a.pkg }
    actions += "かくす" to { s.hidden = s.hidden + a.pkg }
    return TileMenu(a.label, actions.map { (t, f) -> t to { f(); app.settingsChanged() } })
}

@Composable
private fun Header(forecast: Forecast?, events: List<Event>, calErr: String?) {
    val p = LocalPalette.current
    var now by remember { mutableStateOf(ZonedDateTime.now()) }
    LaunchedEffect(Unit) { while (true) { now = ZonedDateTime.now(); delay(1000L * (60 - now.second).coerceAtLeast(1)) } }
    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.Top) {
        Column {
            T(now.format(DateTimeFormatter.ofPattern("H:mm")), size = 76.sp, font = headFont(), weight = FontWeight.Light)
            T(now.format(DateTimeFormatter.ofPattern("M月d日（E）", Locale.JAPAN)), size = 26.sp, color = p.muted)
        }
        Spacer(Modifier.width(32.dp))
        forecast?.let { f ->
            Column(Modifier.width(290.dp).padding(top = 14.dp)) {
                val (icon, word) = Weather.describe(f.code, f.isDay)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    T(icon, size = 52.sp)
                    Spacer(Modifier.width(12.dp))
                    T("%.0f°".format(f.temp), size = 52.sp, font = headFont(), weight = FontWeight.Light)
                    Spacer(Modifier.width(12.dp))
                    T(word, size = 22.sp, color = p.muted)
                }
                f.days.firstOrNull()?.let { d -> T("%.0f° / %.0f°  ☂%d%%".format(d.max, d.min, d.rain), size = 22.sp, color = p.muted) }
                f.days.getOrNull(1)?.let { d -> T("あした " + Weather.describe(d.code).first + " %.0f° ☂%d%%".format(d.max, d.rain), size = 20.sp, color = p.muted) }
            }
        }
        Spacer(Modifier.weight(1f).widthIn(min = 24.dp))
        Column(Modifier.width(330.dp).padding(top = 14.dp)) {
            val today = LocalDate.now()
            when {
                calErr != null -> T("カレンダーを読めません：$calErr", size = 18.sp, color = p.muted, lines = 2)
                events.isEmpty() -> {}
                else -> events.filter { (it.end ?: it.start) >= now }.take(4).forEach { e ->
                    val d = e.start.toLocalDate()
                    val day = when (d) { today -> "きょう"; today.plusDays(1) -> "あした"; else -> d.format(DateTimeFormatter.ofPattern("M/d")) }
                    val time = if (e.allDay) "" else e.start.format(DateTimeFormatter.ofPattern("H:mm"))
                    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(4.dp).height(24.dp).background(if (d == today) p.accent else p.line))
                        Spacer(Modifier.width(10.dp))
                        T("$day $time", size = 20.sp, color = p.muted, font = Mono, modifier = Modifier.width(140.dp))
                        T(e.title, size = 22.sp)
                    }
                }
            }
        }
    }
}
