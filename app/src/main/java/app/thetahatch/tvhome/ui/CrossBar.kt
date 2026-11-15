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

private val CAT_X = 210.dp      // 選んでいるまとまりの横の位置
private val CAT_STEP = 150.dp   // まとまりの間
private val BAR_Y = 190.dp      // 横の帯の高さ
private val ITEM_STEP = 70.dp   // 項目の間

/**
 * クロスバー：横にまとまり（テレビ・すき・アプリ・せってい）、縦にその中身。
 * ←→ でまとまり、↑↓ で中身、決定で開く、長押し・メニューで操作。
 * 色はシリーズのスキン。背景は時刻で色が少し変わり、ゆっくり動く波（描くだけなので軽い）。
 */
@Composable
fun CrossBarLayout(app: TvHomeApp, firstFocus: FocusRequester, go: (String) -> Unit, menu: (TileMenu) -> Unit, guarded: (() -> Unit) -> Unit) {
    val s = app.store
    val state = app.state
    val tick by state.tick.collectAsState()
    val apps by state.apps.collectAsState()
    val entries by state.entries.collectAsState()
    val forecast by state.forecast.collectAsState()
    val events by state.events.collectAsState()
    val hub by UpdateHub.status.collectAsState()
    val p = LocalPalette.current
    val updates = Catalog.android.count { UpdateHub.hasNewer(it, hub[it.id] ?: return@count false) }
    val base = remember(tick, apps, entries, updates) { buildCategories(app, apps, entries, updates, go, menu, guarded) }
    // 先頭に「きょう」（天気・よてい。見るだけ）
    val today = remember(forecast, events) {
        val items = buildList {
            weatherLine(forecast)?.let { (icon, text) -> add(Item("weather", text, icon, sub = "てんき")) }
            upcoming(events).take(8).forEach { e -> add(Item("ev" + e.start + e.title, e.title, "🗓", sub = eventWhen(e))) }
            if (isEmpty()) add(Item("none", "よていは ありません", "🗓", dim = true))
        }
        Category("today", "きょう", "☀", items)
    }
    val cats = listOf(today) + base
    var cat by remember { mutableIntStateOf(1) }
    val pos = remember { mutableStateMapOf<String, Int>() }
    if (cat >= cats.size) cat = cats.size - 1
    val current = cats[cat]
    val idx = (pos[current.key] ?: 0).coerceIn(0, (current.items.size - 1).coerceAtLeast(0))
    val catAnim by animateFloatAsState(cat.toFloat(), label = "cat")
    val itemAnim by animateFloatAsState(idx.toFloat(), label = "item")
    var longPressed by remember { mutableStateOf(false) }

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
                        // 押し続けたら操作のメニュー
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
                    else -> e.key.nativeKeyCode == 0
                }
            }
            .focusable(),
    ) {
        WaveBackground()
        Clock(forecast)

        // 横の帯：まとまり
        cats.forEachIndexed { i, c ->
            val d = i - catAnim
            val sel = i == cat
            Column(
                Modifier.offset(x = CAT_X + CAT_STEP * d - 50.dp, y = BAR_Y - 46.dp).width(100.dp)
                    .alpha(if (sel) 1f else 0.55f)
                    .clickable(remember { MutableInteractionSource() }, null) { cat = i },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                T(c.icon, size = if (sel) 52.sp else 40.sp)
                T(c.label, size = if (sel) 24.sp else 20.sp, font = headFont(), weight = if (sel) FontWeight.SemiBold else FontWeight.Normal, color = if (sel) p.fg else p.muted)
            }
        }

        // 縦：中身（選んでいる物は帯のすぐ下、その前の物は帯の上へ）
        current.items.forEachIndexed { i, it ->
            val d = i - itemAnim
            if (abs(d) > 7) return@forEachIndexed
            val sel = i == idx
            // 選んでいる物は帯のすぐ下。後ろの物はその下に並び、前の物は帯の上へ逃げる
            val ySel = BAR_Y + 78.dp
            val yAbove = BAR_Y - 120.dp
            val y = when {
                d >= 1f -> ySel + 100.dp + ITEM_STEP * (d - 1f)
                d >= 0f -> ySel + 100.dp * d
                d >= -1f -> ySel + (ySel - yAbove) * d
                else -> yAbove + ITEM_STEP * (d + 1f)
            }
            Row(
                Modifier.offset(x = CAT_X - 44.dp, y = y)
                    .alpha(when { sel -> 1f; d < 0 -> 0.35f; else -> 0.75f })
                    .clickable(remember { MutableInteractionSource() }, null) { if (sel) it.onClick() else pos[current.key] = i },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val box = if (sel) 88.dp else 56.dp
                Box(
                    Modifier.size(box).clip(RoundedCornerShape(if (sel) 18.dp else 12.dp))
                        .background(if (sel) p.raised else p.panel.copy(alpha = 0.6f))
                        .border(if (sel) 3.dp else 0.dp, if (sel) p.accent else Color.Transparent, RoundedCornerShape(if (sel) 18.dp else 12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    val a = it.app
                    if (a != null) {
                        val px = with(LocalDensity.current) { (box * 0.75f).roundToPx() }
                        val art = appArt(a.copy(hasBanner = false), px)
                        if (art != null) Image(art, null, Modifier.size(box * 0.75f), contentScale = ContentScale.Fit)
                        else T(a.label.take(1), size = if (sel) 40.sp else 26.sp, font = headFont())
                    } else T(it.icon, size = if (sel) 44.sp else 28.sp)
                    it.badge?.let { b -> Box(Modifier.align(Alignment.TopEnd).clip(RoundedCornerShape(10.dp)).background(p.accent).padding(6.dp, 0.dp)) { T(b, size = 16.sp, color = p.onAccent) } }
                }
                Spacer(Modifier.width(20.dp))
                Column {
                    T(it.label, size = if (sel) 30.sp else 24.sp, color = if (it.dim) p.muted else p.fg, weight = if (sel) FontWeight.SemiBold else FontWeight.Normal)
                    if (sel) it.sub?.let { sub -> T(sub, size = 20.sp, color = p.accent) }
                }
            }
        }
        if (current.items.isEmpty()) {
            T(if (current.key == "fav") "アプリで 長押し（メニュー）→ ★" else "なし", Modifier.offset(x = CAT_X - 44.dp, y = BAR_Y + 80.dp), size = 22.sp, color = p.muted)
        }
    }
}

/** 時刻で色が変わる背景と、ゆっくり動く 2 本の波。 */
@Composable
private fun WaveBackground() {
    val p = LocalPalette.current
    val hour = remember { ZonedDateTime.now().hour }
    // 朝・昼・夕・夜で背景にまぜる色
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
                wave(ph, size.height * 0.05f, size.height * 0.86f, p.accent.copy(alpha = 0.28f), 3.dp.toPx())
                wave(-ph * 0.7f + 1.2f, size.height * 0.07f, size.height * 0.9f, p.fg.copy(alpha = 0.12f), 1.5.dp.toPx())
                drawLine(p.line.copy(alpha = 0.5f), Offset(0f, BAR_Y.toPx() + 60.dp.toPx()), Offset(size.width, BAR_Y.toPx() + 60.dp.toPx()), 1.dp.toPx())
            },
    )
}

@Composable
private fun Clock(forecast: app.thetahatch.tvhome.data.Forecast?) {
    val p = LocalPalette.current
    var now by remember { mutableStateOf(ZonedDateTime.now()) }
    LaunchedEffect(Unit) { while (true) { now = ZonedDateTime.now(); delay(1000L * (60 - now.second).coerceAtLeast(1)) } }
    Box(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 27.dp), contentAlignment = Alignment.TopEnd) {
        Column(horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                weatherLine(forecast)?.let { (icon, _) -> T("$icon %.0f°".format(forecast!!.temp), size = 26.sp, color = p.muted); Spacer(Modifier.width(18.dp)) }
                T(now.format(DateTimeFormatter.ofPattern("M/d（E）", Locale.JAPAN)), size = 22.sp, color = p.muted)
                Spacer(Modifier.width(14.dp))
                T(now.format(DateTimeFormatter.ofPattern("H:mm")), size = 44.sp, font = headFont(), weight = FontWeight.Light)
            }
        }
    }
}
