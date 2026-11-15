package app.thetahatch.tvhome.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.thetahatch.tvhome.TvHomeApp
import app.thetahatch.tvhome.hub.Catalog
import app.thetahatch.tvhome.hub.UpdateHub
import kotlinx.coroutines.delay
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * キッズホーム風（Theta.KidsHome.Hatch の見た目）：パステルの方眼紙に、フチの太いレトロなウィンドウ。
 * 左に「きょう」（日づけ・天気・よてい）、まんなかに大きなタイル、右にタブ（選ぶだけで切り替わる）。
 */
@Composable
fun KidsLayout(app: TvHomeApp, firstFocus: FocusRequester, go: (String) -> Unit, menu: (TileMenu) -> Unit, guarded: (() -> Unit) -> Unit) {
    val s = app.store
    val state = app.state
    val tick by state.tick.collectAsState()
    val apps by state.apps.collectAsState()
    val entries by state.entries.collectAsState()
    val forecast by state.forecast.collectAsState()
    val events by state.events.collectAsState()
    val hub by UpdateHub.status.collectAsState()
    val theme = remember(tick) { kidThemeById(s.kidTheme) }
    val updates = Catalog.android.count { UpdateHub.hasNewer(it, hub[it.id] ?: return@count false) }
    val cats = remember(tick, apps, entries, updates) { buildCategories(app, apps, entries, updates, go, menu, guarded) }
    var tab by rememberSaveable { mutableStateOf("") }
    val current = cats.firstOrNull { it.key == tab } ?: cats.firstOrNull { it.key == "fav" && it.items.isNotEmpty() } ?: cats.first()
    var now by remember { mutableStateOf(ZonedDateTime.now()) }
    LaunchedEffect(Unit) { while (true) { now = ZonedDateTime.now(); delay(1000L * (60 - now.second).coerceAtLeast(1)) } }
    LaunchedEffect(Unit) { delay(150); runCatching { firstFocus.requestFocus() } }
    val line = remember(now.hour) { theme.lines.random() }

    Box(Modifier.fillMaxSize()) {
        RetroBackground(theme)
        Column(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 22.dp)) {
            // 上のバー
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                T(theme.mascot, size = 34.sp)
                Spacer(Modifier.width(10.dp))
                KText("おうちの テレビ", theme, 30, theme.text)
                listOf(Color(0xFFFFE29A), Color(0xFFB8F0A8), Color(0xFFFFB3CF)).forEach { c ->
                    Box(Modifier.padding(start = 8.dp).size(16.dp).clip(CircleShape).background(c).border(2.5.dp, theme.outline, CircleShape))
                }
                Spacer(Modifier.weight(1f))
                weatherLine(forecast)?.let { (icon, _) -> KChip(theme, "$icon %.0f°".format(forecast!!.temp)) }
                KChip(theme, now.format(DateTimeFormatter.ofPattern("H:mm")))
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxSize()) {
                // 左：きょう
                Column(Modifier.width(230.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    RetroWindow(theme, Modifier.fillMaxWidth(), title = "きょう", titleColor = theme.titleBars[0]) {
                        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            KText(now.format(DateTimeFormatter.ofPattern("M がつ d にち", Locale.JAPAN)), theme, 22)
                            KText(now.format(DateTimeFormatter.ofPattern("EEEE", Locale.JAPAN)).replace("曜日", "ようび"), theme, 18, theme.outline.copy(alpha = 0.7f))
                            Spacer(Modifier.height(6.dp))
                            Box(Modifier.clip(RoundedCornerShape(14.dp)).background(theme.bubbles[0]).border(2.5.dp, theme.outline, RoundedCornerShape(14.dp)).padding(10.dp, 4.dp)) {
                                KText(line, theme, 16)
                            }
                            weatherLine(forecast)?.let { (icon, text) ->
                                Spacer(Modifier.height(6.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    T(icon, size = 30.sp)
                                    Spacer(Modifier.width(6.dp))
                                    KText(text, theme, 16)
                                }
                            }
                        }
                    }
                    RetroWindow(theme, Modifier.fillMaxWidth(), title = "よてい", titleColor = theme.titleBars[3]) {
                        Column(Modifier.padding(12.dp)) {
                            val up = upcoming(events).take(4)
                            if (up.isEmpty()) KText("よていは ないよ", theme, 16, theme.outline.copy(alpha = 0.6f))
                            up.forEach { e ->
                                KText(eventWhen(e), theme, 14, theme.accent)
                                KText(e.title, theme, 18, modifier = Modifier.padding(bottom = 4.dp))
                            }
                        }
                    }
                }
                Spacer(Modifier.width(20.dp))
                // まんなか：タイル
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    if (current.items.isEmpty()) {
                        KText(if (current.key == "fav") "アプリを ながおし（メニュー）で ★ を つけると ここに ならぶよ" else "なにも ないよ", theme, 18, theme.text, lines = 2)
                    }
                    LazyVerticalGrid(
                        GridCells.Fixed(3), horizontalArrangement = Arrangement.spacedBy(18.dp), verticalArrangement = Arrangement.spacedBy(18.dp),
                        contentPadding = PaddingValues(8.dp, 8.dp, 12.dp, 24.dp),
                    ) {
                        itemsIndexed(current.items, key = { _, it -> current.key + it.key }) { i, it ->
                            KidTile(theme, it, i, current.label, if (i == 0) firstFocus else null)
                        }
                    }
                }
                // 右：タブ
                Column(Modifier.fillMaxHeight().padding(start = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    cats.forEachIndexed { i, c -> KidTab(theme, c, i, c.key == current.key) { tab = c.key } }
                }
            }
        }
    }
}

@Composable
private fun KidTile(theme: KidTheme, it: Item, i: Int, title: String, focus: FocusRequester?) {
    RetroWindow(
        theme, Modifier.aspectRatio(0.9f), title = it.sub ?: title, titleColor = if (it.dim) Color(0xFFDDDDDD) else theme.titleBars[i % theme.titleBars.size],
        onClick = it.onClick, onMenu = it.onMenu, focusRequester = focus,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Bubble(theme, i, 72.dp) {
                    val a = it.app
                    if (a != null) {
                        val px = with(LocalDensity.current) { 56.dp.roundToPx() }
                        val art = appArt(a.copy(hasBanner = false), px)
                        if (art != null) Image(art, null, Modifier.size(52.dp), contentScale = ContentScale.Fit)
                        else KText(a.label.take(1), theme, 34)
                    } else T(it.icon, size = 36.sp, color = theme.outline)
                }
                Spacer(Modifier.height(8.dp))
                KText(it.label, theme, 18, if (it.dim) theme.outline.copy(alpha = 0.5f) else theme.outline, lines = 2)
            }
            it.badge?.let { b ->
                Box(Modifier.align(Alignment.TopEnd).padding(6.dp).clip(CircleShape).background(Color(0xFFFF4D6D)).padding(8.dp, 2.dp)) { KText(b, theme, 16, Color.White) }
            }
        }
    }
}

@Composable
private fun KidTab(theme: KidTheme, c: Category, i: Int, selected: Boolean, onSelect: () -> Unit) {
    val shape = RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp)
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Box(
        Modifier
            .offset(x = if (selected) 0.dp else 10.dp)
            .stickerShadow(theme.outline, if (selected) 4.dp else 2.dp, 18.dp)
            .width(92.dp)
            .clip(shape)
            .background(if (selected) Color.White else theme.titleBars[i % theme.titleBars.size])
            .border(if (focused) 5.dp else 3.dp, if (focused) theme.accent else theme.outline, shape)
            // リモコンで選んだだけで切り替える（決定を押さなくてよい）
            .onFocusChanged { if (it.isFocused && !selected) onSelect() }
            .clickable(interactionSource = interaction, indication = null, onClick = onSelect)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            T(c.icon, size = 28.sp, color = theme.outline)
            KText(c.label, theme, 14)
        }
    }
}
