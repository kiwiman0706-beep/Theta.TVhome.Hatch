package app.thetahatch.tvhome.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.draw.clipToBounds
import app.thetahatch.tvhome.data.Event
import app.thetahatch.tvhome.data.Forecast
import app.thetahatch.tvhome.data.NetInfo
import app.thetahatch.tvhome.data.NetStatus
import app.thetahatch.tvhome.data.Weather
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.thetahatch.tvhome.TvHomeApp
import app.thetahatch.tvhome.hub.Catalog
import app.thetahatch.tvhome.hub.UpdateHub
import kotlinx.coroutines.delay
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

private val CAT_X = 110.dp      // 選んでいるまとまりの横の位置（左の区画の中）
private val CAT_STEP = 118.dp   // まとまりの間
private val BAR_Y = 200.dp      // 横の帯の高さ
private val ITEM_STEP = 64.dp   // 項目の間
private val LEFT_W = 530.dp     // クロスバーの区画の幅（右は計器盤）

/**
 * クロスバー＋計器盤：左にクロスバー（横にまとまり、縦に中身）、右に時計・てんき・よてい、下にネットなどの状態。
 * ←→ でまとまり、↑↓ で中身、決定で開く、長押し（決定を押し続ける）・メニューで操作。
 * 色はシリーズのスキン（枠は Home.Hatch の計器盤と同じ作り）。背景は時刻で色が変わり、ゆっくり波が動く。
 */
@Composable
fun CrossBarLayout(app: TvHomeApp, firstFocus: FocusRequester, go: (String) -> Unit, menu: (TileMenu) -> Unit, guarded: (() -> Unit) -> Unit) {
    val state = app.state
    val tick by state.tick.collectAsState()
    val apps by state.apps.collectAsState()
    val entries by state.entries.collectAsState()
    val forecast by state.forecast.collectAsState()
    val events by state.events.collectAsState()
    val hub by UpdateHub.status.collectAsState()
    val net by NetStatus.state.collectAsState()
    val p = LocalPalette.current
    val updates = Catalog.android.count { UpdateHub.hasNewer(it, hub[it.id] ?: return@count false) }
    val base = remember(tick, apps, entries, updates) { buildCategories(app, apps, entries, updates, go, menu, guarded) }
    val today = remember(forecast, events) {
        val items = buildList {
            upcoming(events).take(10).forEach { e -> add(Item("ev" + e.start + e.title, e.title, "🗓", sub = eventWhen(e) + (if (e.location.isNotBlank()) "  " + e.location else ""))) }
            weatherLine(forecast)?.let { (icon, text) -> add(Item("weather", text, icon, sub = "てんき")) }
            if (isEmpty()) add(Item("none", "よていは ないよ", "🗓", dim = true))
        }
        Category("today", "きょう", "🗓", items)
    }
    val cats = listOf(today) + base
    val colors = listOf(p.accent2, p.accent, p.widget, p.launch, p.accent3)
    var cat by remember { mutableIntStateOf(1) }
    val pos = remember { mutableStateMapOf<String, Int>() }
    if (cat >= cats.size) cat = cats.size - 1
    val current = cats[cat]
    val idx = (pos[current.key] ?: 0).coerceIn(0, (current.items.size - 1).coerceAtLeast(0))
    val catAnim by animateFloatAsState(cat.toFloat(), label = "cat")
    val itemAnim by animateFloatAsState(idx.toFloat(), label = "item")
    var longPressed by remember { mutableStateOf(false) }
    var now by remember { mutableStateOf(ZonedDateTime.now()) }
    LaunchedEffect(Unit) { while (true) { now = ZonedDateTime.now(); delay(1000L * (60 - now.second).coerceAtLeast(1)) } }
    LaunchedEffect(Unit) { delay(150); runCatching { firstFocus.requestFocus() } }

    fun move(dc: Int, di: Int) {
        if (dc != 0) cat = (cat + dc).coerceIn(0, cats.size - 1)
        if (di != 0) pos[current.key] = (idx + di).coerceIn(0, (current.items.size - 1).coerceAtLeast(0))
    }

    Box(
        Modifier.fillMaxSize()
            .focusRequester(firstFocus)
            .onKeyEvent { e ->
                val center = e.key == Key.Enter || e.key == Key.DirectionCenter || e.key == Key.NumPadEnter
                when {
                    e.type == KeyEventType.KeyDown && center -> {
                        if (e.nativeKeyEvent.repeatCount == 8 && !longPressed) { longPressed = true; current.items.getOrNull(idx)?.onMenu?.invoke() }
                        true
                    }
                    e.type == KeyEventType.KeyUp && center -> {
                        if (!longPressed) current.items.getOrNull(idx)?.onClick?.invoke()
                        longPressed = false; true
                    }
                    e.type == KeyEventType.KeyUp && e.key == Key.Menu -> { current.items.getOrNull(idx)?.onMenu?.invoke(); true }
                    e.type != KeyEventType.KeyDown -> false
                    e.key == Key.DirectionLeft -> { move(-1, 0); true }
                    e.key == Key.DirectionRight -> { move(1, 0); true }
                    e.key == Key.DirectionUp -> { move(0, -1); true }
                    e.key == Key.DirectionDown -> { move(0, 1); true }
                    else -> false
                }
            }
            .focusable(),
    ) {
        WaveBackground()
        Column(Modifier.fillMaxSize().padding(start = 40.dp, end = 40.dp, top = 24.dp, bottom = 18.dp)) {
            Row(Modifier.weight(1f)) {
                // ---- 左：クロスバー
                Box(Modifier.width(LEFT_W).fillMaxHeight().clipToBounds()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).background(p.accent))
                        Spacer(Modifier.width(8.dp))
                        T("THETA // TVHOME", size = 14.sp, color = p.muted, font = Mono)
                        Spacer(Modifier.width(12.dp))
                        T(greeting(now.hour), size = 18.sp, color = p.fg)
                    }
                    cats.forEachIndexed { i, c ->
                        val d = i - catAnim
                        val sel = i == cat
                        val col = colors[i % colors.size]
                        Column(
                            Modifier.offset(x = CAT_X + CAT_STEP * d - 52.dp, y = BAR_Y - 64.dp).width(104.dp)
                                .alpha(if (sel) 1f else 0.6f)
                                .clickable(remember { MutableInteractionSource() }, null) { cat = i },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                Modifier.size(if (sel) 72.dp else 56.dp).clip(RoundedCornerShape(if (sel) 20.dp else 16.dp))
                                    .background(col.copy(alpha = if (sel) 0.95f else 0.35f)),
                                contentAlignment = Alignment.Center,
                            ) { T(c.icon, size = if (sel) 40.sp else 30.sp) }
                            Spacer(Modifier.height(4.dp))
                            T(c.label, size = if (sel) 22.sp else 18.sp, weight = if (sel) FontWeight.SemiBold else FontWeight.Normal, color = if (sel) p.fg else p.muted)
                        }
                    }
                    current.items.forEachIndexed { i, it ->
                        val d = i - itemAnim
                        if (abs(d) > 6) return@forEachIndexed
                        val sel = i == idx
                        // 選んでいる物は帯のすぐ下。後ろはその下、前は帯の上へ
                        val ySel = BAR_Y + 66.dp
                        val yAbove = BAR_Y - 140.dp
                        val y = when {
                            d >= 1f -> ySel + 104.dp + ITEM_STEP * (d - 1f)
                            d >= 0f -> ySel + 104.dp * d
                            d >= -1f -> ySel + (ySel - yAbove) * d
                            else -> yAbove + ITEM_STEP * (d + 1f)
                        }
                        ItemRow(it, sel, d, i, current.items.size, colors[cat % colors.size], Modifier.offset(x = CAT_X - 52.dp, y = y)) {
                            if (sel) it.onClick() else pos[current.key] = i
                        }
                    }
                    if (current.items.isEmpty()) {
                        T(if (current.key == "fav") "アプリで 長押し（メニュー）→ ★" else "なし", Modifier.offset(x = CAT_X - 52.dp, y = BAR_Y + 90.dp), size = 22.sp, color = p.muted)
                    }
                }
                Spacer(Modifier.width(20.dp))
                // ---- 右：計器盤
                Dashboard(Modifier.weight(1f).fillMaxHeight(), now, forecast, events)
            }
            Spacer(Modifier.height(10.dp))
            StatusStrip(net, updates, app)
        }
    }
}

private fun greeting(h: Int) = when (h) { in 4..10 -> "おはよう！"; in 11..17 -> "こんにちは！"; else -> "こんばんは！" }

@Composable
private fun ItemRow(it: Item, sel: Boolean, d: Float, i: Int, n: Int, col: Color, modifier: Modifier, onClick: () -> Unit) {
    val p = LocalPalette.current
    val box = if (sel) 92.dp else 52.dp
    Row(
        modifier
            .width(LEFT_W - CAT_X + 40.dp)
            .alpha(when { sel -> 1f; d < 0 -> 0.32f; else -> 0.8f })
            .let { m -> if (sel) m.background(Brush.horizontalGradient(listOf(p.raised, p.raised.copy(alpha = 0f)))).drawBehind { drawRect(p.accent, size = androidx.compose.ui.geometry.Size(4.dp.toPx(), size.height)) } else m }
            .clickable(remember { MutableInteractionSource() }, null, onClick = onClick)
            .padding(start = if (sel) 12.dp else 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(box).clip(RoundedCornerShape(if (sel) 22.dp else 14.dp))
                .background(if (sel) col.copy(alpha = 0.25f) else p.panel.copy(alpha = 0.7f))
                .border(if (sel) 3.dp else 0.dp, if (sel) p.accent else Color.Transparent, RoundedCornerShape(if (sel) 22.dp else 14.dp)),
            contentAlignment = Alignment.Center,
        ) {
            val a = it.app
            if (a != null) {
                val px = with(LocalDensity.current) { (box * 0.72f).roundToPx() }
                val art = appArt(a.copy(hasBanner = false), px)
                if (art != null) Image(art, null, Modifier.size(box * 0.72f), contentScale = ContentScale.Fit)
                else T(a.label.take(1), size = if (sel) 40.sp else 24.sp, font = headFont())
            } else T(it.icon, size = if (sel) 46.sp else 26.sp)
            it.badge?.let { b -> Box(Modifier.align(Alignment.TopEnd).clip(RoundedCornerShape(10.dp)).background(p.accent).padding(6.dp, 0.dp)) { T(b, size = 16.sp, color = p.onAccent) } }
        }
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f)) {
            T(it.label, size = if (sel) 32.sp else 22.sp, color = if (it.dim) p.muted else p.fg, weight = if (sel) FontWeight.SemiBold else FontWeight.Normal)
            if (sel) Row(verticalAlignment = Alignment.CenterVertically) {
                T("%02d/%02d".format(i + 1, n), size = 14.sp, color = p.muted, font = Mono)
                Spacer(Modifier.width(10.dp))
                T(it.sub ?: "けってい で ひらく", size = 18.sp, color = p.accent)
            }
        }
    }
}

/** 右の計器盤：時計・てんき・よてい。 */
@Composable
private fun Dashboard(modifier: Modifier, now: ZonedDateTime, forecast: Forecast?, events: List<Event>) {
    val p = LocalPalette.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HudFrame("SYS.TIME", "じこく", trailing = now.format(DateTimeFormatter.ofPattern("EEE", Locale.US)).uppercase()) {
            Row(verticalAlignment = Alignment.Bottom) {
                T(now.format(DateTimeFormatter.ofPattern("H:mm")), size = 44.sp, font = headFont(), weight = FontWeight.Light)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.padding(bottom = 6.dp)) {
                    T(now.format(DateTimeFormatter.ofPattern("M月d日（E）", Locale.JAPAN)), size = 20.sp)
                }
            }
            // 1 日のうち、どこまで来たか
            Meter((now.hour * 60 + now.minute) / 1440f, Modifier.fillMaxWidth())
        }
        HudFrame("WX", "てんき", trailing = forecast?.let { "%.0f°".format(it.temp) } ?: "--") {
            if (forecast == null) T("せってい で 場所を えらぶと 出ます", size = 16.sp, color = p.muted, lines = 2)
            else {
                val (icon, word) = Weather.describe(forecast.code, forecast.isDay)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    T(icon, size = 30.sp)
                    Spacer(Modifier.width(10.dp))
                    T("%.0f°".format(forecast.temp), size = 32.sp, font = headFont(), weight = FontWeight.Light)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        T(word, size = 16.sp)
                        forecast.days.firstOrNull()?.let { d -> T("%.0f° / %.0f°".format(d.max, d.min), size = 14.sp, color = p.muted, font = Mono) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    forecast.days.take(3).forEachIndexed { i, d ->
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                T(listOf("きょう", "あした", "あさって")[i], size = 14.sp, color = p.muted)
                                Spacer(Modifier.width(4.dp))
                                T(Weather.describe(d.code).first, size = 16.sp)
                                Spacer(Modifier.weight(1f))
                                T("%d%%".format(d.rain), size = 12.sp, color = p.muted, font = Mono)
                            }
                            Meter(d.rain / 100f, Modifier.fillMaxWidth().padding(top = 2.dp), color = Color(0xFF4FA3E0))
                        }
                    }
                }
            }
        }
        val up = upcoming(events).take(4)
        HudFrame("CAL", "よてい", Modifier.weight(1f), trailing = "%02d".format(upcoming(events).size)) {
            if (up.isEmpty()) T("よていは ないよ", size = 18.sp, color = p.muted)
            up.forEach { e ->
                val nowOn = e.start <= now && (e.end ?: e.start) >= now
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(4.dp).height(22.dp).background(if (nowOn) p.accent else if (e.start.toLocalDate() == now.toLocalDate()) p.widget else p.line))
                    Spacer(Modifier.width(10.dp))
                    T(if (nowOn) "いま" else eventWhen(e), Modifier.width(110.dp), size = 14.sp, color = if (nowOn) p.accent else p.muted)
                    T(e.title, size = 17.sp)
                }
            }
        }
    }
}

/** 下の帯：ネット・Tailscale・更新・版。 */
@Composable
private fun StatusStrip(net: NetInfo, updates: Int, app: TvHomeApp) {
    val p = LocalPalette.current
    val ver = remember { runCatching { app.packageManager.getPackageInfo(app.packageName, 0).versionName }.getOrNull() ?: "" }
    Row(
        Modifier.fillMaxWidth().drawBehind { drawLine(p.line, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) }.padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        Stat("NET", when (net.kind) { "wifi" -> "Wi-Fi"; "ethernet" -> "LAN"; "none" -> "つながっていない"; else -> net.kind }, net.kind != "none") {
            if (net.kind == "wifi" && net.bars >= 0) Bars(net.bars)
        }
        Stat("WAN", if (net.online) "インターネット OK" else "インターネット ×", net.online)
        Stat("VPN", if (net.vpn) "Tailscale オン" else "オフ", net.vpn)
        if (net.downMbps > 0) Stat("LINK", "${net.downMbps} Mbps", true)
        Stat("UPD", if (updates > 0) "あたらしい版 $updates" else "さいしん", updates == 0)
        Spacer(Modifier.weight(1f))
        T("v$ver", size = 14.sp, color = p.muted, font = Mono)
    }
}

@Composable
private fun Stat(code: String, text: String, ok: Boolean, extra: @Composable () -> Unit = {}) {
    val p = LocalPalette.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        T(code, size = 13.sp, color = p.accent, font = Mono)
        Spacer(Modifier.width(8.dp))
        Dot(ok)
        T(text, size = 16.sp, color = if (ok) p.fg else p.muted)
        Spacer(Modifier.width(6.dp))
        extra()
    }
}

@Composable
private fun Bars(n: Int) {
    val p = LocalPalette.current
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        (1..4).forEach { k -> Box(Modifier.width(4.dp).height((4 + k * 3).dp).background(if (k <= n) p.fg else p.line)) }
    }
}

/** 時刻で色が変わる背景と、ゆっくり動く 2 本の波（描くだけなので軽い）。 */
@Composable
private fun WaveBackground() {
    val p = LocalPalette.current
    val hour = remember { ZonedDateTime.now().hour }
    val tint = when (hour) { in 5..9 -> Color(0xFF3A7BD5); in 10..15 -> p.accent; in 16..18 -> Color(0xFFE0703A); else -> Color(0xFF3A2C7A) }
    val top = lerp(p.bg, tint, 0.18f)
    val t by rememberInfiniteTransition(label = "wave").animateFloat(0f, 1f, infiniteRepeatable(tween(14000, easing = LinearEasing)), label = "t")
    Box(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(top, p.bg)))
            .drawBehind {
                fun wave(phase: Float, amp: Float, yc: Float, color: Color, width: Float) {
                    val path = Path()
                    val n = 48
                    for (k in 0..n) {
                        val x = size.width * k / n
                        val y = yc + amp * sin((k.toFloat() / n * 2f * PI.toFloat()) * 1.3f + phase)
                        if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(path, color, style = Stroke(width))
                }
                val ph = t * 2f * PI.toFloat()
                wave(ph, size.height * 0.05f, size.height * 0.84f, p.accent.copy(alpha = 0.25f), 3.dp.toPx())
                wave(-ph * 0.7f + 1.2f, size.height * 0.07f, size.height * 0.88f, p.fg.copy(alpha = 0.1f), 1.5.dp.toPx())
            },
    )
}
