package app.thetahatch.tvhome.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.thetahatch.tvhome.TvHomeApp
import app.thetahatch.tvhome.data.Event
import app.thetahatch.tvhome.data.Forecast
import app.thetahatch.tvhome.data.NetInfo
import app.thetahatch.tvhome.data.NetStatus
import app.thetahatch.tvhome.data.Weather
import app.thetahatch.tvhome.hub.Catalog
import app.thetahatch.tvhome.hub.UpdateHub
import app.thetahatch.tvhome.media.NowPlaying
import app.thetahatch.tvhome.nas.Kind
import app.thetahatch.tvhome.media.Playing
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

// 1920×1080（960×540dp）のテレビを基準にした置き場所（mock/home.html と同じ）
private val SIDE = 48.dp
private val BAR_TOP = 84.dp        // 横の帯（まとまり）の上
private val CAT_W = 95.dp          // まとまりの間
private val SEL_X = 150.dp         // 選んでいるまとまりの真ん中
private val ITEMS_TOP = 169.dp     // 中身のはじまり
private val ROW = 39.dp
private val ROW_ON = 62.dp
private val STRIP_H = 107.dp       // 下の 2 週間の帯
private val GRID_TOP = 180.dp      // 4 週に広げたときの上
private const val MIN_WEEKS = -4
private const val MAX_WEEKS = 12

private val DOW = listOf("月", "火", "水", "木", "金", "土", "日")

/**
 * クロスバー（はじめの形）：
 * - 上端に横の帯（まとまり：カレンダー・テレビ・アプリ・せってい。左右はループ）、選んだまとまりの中身はその下に縦
 * - 下にさりげなく 2 週間の帯（月曜はじまり・てんき・よてい）。カレンダーを選ぶと 4 週の表に広がり、↓ で日にちを選べる
 * - 左上：いま再生中のもの（無ければ名前とあいさつ）。右上：時計・てんき・ぜんぶのアプリ・ネット
 * - 操作しないと 5 秒で薄く、25 秒でメニューが消える（いちばん上で「戻る」でもすぐ消える）。なにかキーで戻る（そのキーは出すだけ）
 * 操作：←→ まとまり、↑↓ えらぶ、決定 ひらく、長押し・メニュー 操作。中身のいちばん上で ↑ → 右上の「ぜんぶのアプリ」
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
    val playing by NowPlaying.state.collectAsState()
    val sv = state.shelves.getValue(Kind.VIDEO).collectAsState().value
    val sm = state.shelves.getValue(Kind.MUSIC).collectAsState().value
    val sp = state.shelves.getValue(Kind.PHOTO).collectAsState().value
    val sb = state.shelves.getValue(Kind.BOOK).collectAsState().value
    val p = LocalPalette.current
    val updates = Catalog.android.count { UpdateHub.hasNewer(it, hub[it.id] ?: return@count false) }
    val base = remember(tick, apps, entries, updates, sv, sm, sp, sb) {
        buildCategories(app, apps, entries, updates, go, menu, guarded, withAll = false,
            nas = mapOf(Kind.VIDEO to sv, Kind.MUSIC to sm, Kind.PHOTO to sp, Kind.BOOK to sb))
    }
    val cats = remember(base) { listOf(Category("cal", "カレンダー", "📅", emptyList())) + base.map { if (it.key == "fav") it.copy(label = "アプリ", icon = "⭐") else it } }
    val colors = listOf(p.accent2, p.accent, p.accent3, p.widget, p.launch)

    var cat by remember { mutableIntStateOf(1) }
    val pos = remember { mutableStateMapOf<String, Int>() }
    var inGrid by remember { mutableStateOf(false) }
    var top by remember { mutableStateOf(false) } // 右上の「ぜんぶのアプリ」を選んでいる
    var longPressed by remember { mutableStateOf(false) }
    var lastKey by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var idle by remember { mutableIntStateOf(0) }
    var now by remember { mutableStateOf(ZonedDateTime.now()) }
    if (cat >= cats.size) cat = cats.size - 1
    val current = cats[cat]
    val idx = (pos[current.key] ?: 0).coerceIn(0, (current.items.size - 1).coerceAtLeast(0))

    // 4 週の表：今週の月曜から 28 日
    val today = now.toLocalDate()
    val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
    val todayIdx = (today.dayOfWeek.value - 1)
    var sel by remember { mutableIntStateOf(todayIdx) }
    // 表を何週ずらしているか（↓ で 1 週ずつ先へ、チャンネル＋－ で 4 週。4 週前〜12 週先）
    var weeks by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) { while (true) { now = ZonedDateTime.now(); delay(1000L * (60 - now.second).coerceAtLeast(1)) } }
    LaunchedEffect(Unit) { delay(150); runCatching { firstFocus.requestFocus() } }
    // 操作しないと薄く→消える
    LaunchedEffect(lastKey) {
        idle = 0
        delay(5000); idle = 1
        delay(20000); idle = 2
    }
    val menuAlpha by animateFloatAsState(when (idle) { 0 -> 1f; 1 -> 0.22f; else -> 0f }, tween(900), label = "idle")
    val catAnim by animateFloatAsState(cat.toFloat(), label = "cat")
    val itemAnim by animateFloatAsState(idx.toFloat(), label = "item")
    val open by animateFloatAsState(if (current.key == "cal") 1f else 0f, tween(450), label = "cal")

    fun move(dc: Int) {
        val n = cats.size
        cat = ((cat + dc) % n + n) % n
        inGrid = false; top = false; weeks = 0; sel = todayIdx
    }

    Box(
        Modifier.fillMaxSize()
            .focusRequester(firstFocus)
            .onKeyEvent { e ->
                val center = e.key == Key.Enter || e.key == Key.DirectionCenter || e.key == Key.NumPadEnter
                val back = e.key == Key.Back || e.key == Key.Escape
                if (e.type == KeyEventType.KeyDown && idle == 2) return@onKeyEvent true // 消えているとき：押した分は出すだけ
                if (e.type == KeyEventType.KeyUp && idle == 2) { lastKey = System.currentTimeMillis(); return@onKeyEvent true }
                lastKey = System.currentTimeMillis()
                when {
                    // 表の中
                    inGrid && current.key == "cal" -> {
                        if (e.type != KeyEventType.KeyDown) return@onKeyEvent back
                        when (e.key) {
                            Key.DirectionLeft -> if (sel > 0) sel-- else if (weeks > MIN_WEEKS) { weeks--; sel = 6 }
                            Key.DirectionRight -> if (sel < 27) sel++ else if (weeks < MAX_WEEKS) { weeks++; sel = 21 }
                            Key.DirectionUp -> when { sel >= 7 -> sel -= 7; weeks > 0 -> weeks--; else -> inGrid = false }
                            Key.DirectionDown -> if (sel < 21) sel += 7 else if (weeks < MAX_WEEKS) weeks++
                            Key.ChannelUp, Key.PageDown -> weeks = (weeks + 4).coerceAtMost(MAX_WEEKS)
                            Key.ChannelDown, Key.PageUp -> weeks = (weeks - 4).coerceAtLeast(MIN_WEEKS)
                            Key.Back, Key.Escape -> { inGrid = false; weeks = 0; sel = todayIdx }
                            else -> return@onKeyEvent false
                        }
                        true
                    }
                    // 右上の「ぜんぶのアプリ」
                    top -> {
                        if (center && e.type == KeyEventType.KeyUp) { guarded { go("apps") }; true }
                        else if (e.type == KeyEventType.KeyDown && (e.key == Key.DirectionDown || back)) { top = false; true }
                        else e.type == KeyEventType.KeyDown && e.key != Key.Back
                    }
                    e.type == KeyEventType.KeyDown && center -> {
                        if (e.nativeKeyEvent.repeatCount == 8 && !longPressed) { longPressed = true; current.items.getOrNull(idx)?.onMenu?.invoke() }
                        true
                    }
                    e.type == KeyEventType.KeyUp && center -> {
                        if (current.key == "cal") inGrid = true
                        else if (!longPressed) current.items.getOrNull(idx)?.onClick?.invoke()
                        longPressed = false; true
                    }
                    e.type == KeyEventType.KeyUp && e.key == Key.Menu -> { current.items.getOrNull(idx)?.onMenu?.invoke(); true }
                    // いちばん上で「戻る」：メニューを消す（ホームなので、それ以上どこへも行かない）
                    e.type == KeyEventType.KeyUp && back -> { idle = 2; true }
                    e.type != KeyEventType.KeyDown -> false
                    e.key == Key.DirectionLeft -> { move(-1); true }
                    e.key == Key.DirectionRight -> { move(1); true }
                    e.key == Key.DirectionUp -> { if (current.key == "cal" || idx == 0) top = true else pos[current.key] = idx - 1; true }
                    e.key == Key.DirectionDown -> { if (current.key == "cal") inGrid = true else pos[current.key] = (idx + 1).coerceAtMost((current.items.size - 1).coerceAtLeast(0)); true }
                    else -> false
                }
            }
            .focusable(),
    ) {
        Backdrop(app.safeMode)

        // ---- 左上：再生中（無ければ名前とあいさつ）
        Box(Modifier.offset(x = SIDE, y = 17.dp).alpha(if (idle == 2) 1f else maxOf(menuAlpha, 0.6f))) {
            playing?.let { NowPlayingCard(it) } ?: Row(verticalAlignment = Alignment.CenterVertically) {
                T("THETA // TVHOME", size = 10.sp, color = p.muted, font = Mono)
                Spacer(Modifier.width(9.dp))
                T(greeting(now.hour), size = 14.sp, weight = FontWeight.Medium)
            }
        }

        // ---- 右上：時計・てんき・ぜんぶのアプリ・ネット
        TopRight(now, forecast, net, top, Modifier.align(Alignment.TopEnd).padding(top = 20.dp, end = SIDE), menuAlpha, idle == 2) { guarded { go("apps") } }

        // ---- 横の帯：まとまり（ループ）
        Box(Modifier.fillMaxWidth().alpha(menuAlpha)) {
            val n = cats.size
            cats.forEachIndexed { i, c ->
                var d = ((i - cat) % n + n) % n
                if (d > n / 2) d -= n
                // 移る途中も近い向きで
                var da = i - catAnim
                while (da > n / 2f) da -= n
                while (da < -n / 2f) da += n
                val sel0 = i == cat
                val col = colors[i % colors.size]
                Column(
                    Modifier.offset(x = SEL_X - CAT_W / 2 + CAT_W * da, y = BAR_TOP).width(CAT_W)
                        .alpha(if (sel0) 1f else 0.5f)
                        .clickable(remember { MutableInteractionSource() }, null) { cat = i; inGrid = false; top = false; lastKey = System.currentTimeMillis() },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val s = if (sel0) 52.dp else 42.dp
                    Box(
                        Modifier.size(s).clip(RoundedCornerShape(if (sel0) 13.dp else 12.dp)).background(if (sel0) col else col.copy(alpha = 0.22f)),
                        contentAlignment = Alignment.Center,
                    ) { T(c.icon, size = if (sel0) 27.sp else 21.sp) }
                    Spacer(Modifier.height(4.dp))
                    T(c.label, size = if (sel0) 14.sp else 12.sp, weight = if (sel0) FontWeight.Bold else FontWeight.Normal, color = if (sel0) p.fg else p.muted)
                }
            }
            Box(
                Modifier.offset(y = BAR_TOP + 74.dp).padding(horizontal = SIDE).fillMaxWidth().height(1.dp)
                    .background(Brush.horizontalGradient(listOf(p.accent, p.line, Color.Transparent))).alpha(0.6f),
            )
        }

        // ---- 中身（選んだまとまりの真下）
        if (current.key != "cal") {
            Box(Modifier.offset(x = SEL_X - 35.dp, y = ITEMS_TOP).alpha(menuAlpha)) {
                current.items.forEachIndexed { i, it ->
                    val d = i - itemAnim
                    if (d < -1.5f || d > 6f) return@forEachIndexed
                    // 選んでいる物を 2 段目に（前の物は 1 つだけ薄く）
                    val base0 = if (idx == 0) 0f else 1f
                    val y = ROW * (base0 + d) + (if (d > 0f) ROW_ON - ROW else 0.dp) * minOf(1f, d)
                    ItemRow(it, i == idx, i, current.items.size, colors[cat % colors.size], d < 0, Modifier.offset(y = y)) {
                        lastKey = System.currentTimeMillis()
                        if (i == idx) it.onClick() else pos[current.key] = i
                    }
                }
                if (current.items.isEmpty()) {
                    T(if (current.key == "fav") "右上の「ぜんぶのアプリ」で えらんで「ホームに だす」" else "なし", size = 13.sp, color = p.muted)
                }
            }
        }

        // ---- カレンダー：ふだんは下に 2 週間の帯、カレンダーを選ぶと 4 週の表
        CalendarBand(monday, weeks, today, events, forecast, open, if (inGrid && current.key == "cal") sel else -1, menuAlpha) { i ->
            cat = 0; inGrid = true; sel = i; lastKey = System.currentTimeMillis()
        }
        if (current.key == "cal") {
            Box(Modifier.offset(x = SIDE, y = 160.dp).alpha(menuAlpha * open)) {
                if (inGrid) {
                    val d = monday.plusWeeks(weeks.toLong()).plusDays(sel.toLong())
                    val evs = eventsOn(d, events)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        T("${d.monthValue}月${d.dayOfMonth}日（${DOW[d.dayOfWeek.value - 1]}）", size = 12.sp, color = p.accent, weight = FontWeight.Bold)
                        Spacer(Modifier.width(9.dp))
                        T(if (evs.isEmpty()) "よていは ないよ" else evs.joinToString("　・　") { (if (it.allDay) "いちにち" else it.start.format(DateTimeFormatter.ofPattern("H:mm"))) + "　" + it.title }, size = 11.sp)
                    }
                } else T("↓ で日にちへ（下の段で ↓ ／ チャンネル＋－ で先の週・月へ）　戻る で帯にもどる", size = 10.sp, color = p.muted)
            }
        }

        // ---- 操作のヒント
        T("← →  まとまり　↑ ↓  えらぶ　決定  ひらく　メニュー  ★・ならべかえ", Modifier.align(Alignment.BottomStart).padding(start = SIDE, bottom = 6.dp).alpha(menuAlpha * (1f - open)), size = 8.sp, color = p.faint, font = Mono)
        if (idle == 2) T("なにか ボタンを おすと メニューが でるよ", Modifier.align(Alignment.BottomStart).padding(start = SIDE, bottom = 12.dp), size = 9.sp, color = p.muted)
    }
}

private fun greeting(h: Int) = when (h) { in 4..10 -> "おはよう！"; in 11..17 -> "こんにちは！"; else -> "こんばんは！" }

/** 背景：時刻で色が少し変わり、ゆっくり光が動く（描くだけ。セーフモードでは止める）。 */
@Composable
private fun Backdrop(safe: Boolean) {
    val p = LocalPalette.current
    val hour = remember { ZonedDateTime.now().hour }
    val tint = when (hour) { in 5..9 -> Color(0xFF3A7BD5); in 10..15 -> p.accent; in 16..18 -> Color(0xFFE0703A); else -> Color(0xFF3A2C7A) }
    val t = if (safe) 0.5f else rememberInfiniteTransition(label = "drift").animateFloat(0f, 1f, infiniteRepeatable(tween(30000, easing = LinearEasing), androidx.compose.animation.core.RepeatMode.Reverse), label = "t").value
    Box(
        Modifier.fillMaxSize()
            .background(Brush.radialGradient(listOf(lerp(p.bg, tint, 0.16f), p.bg), center = Offset(260f, 0f), radius = 2400f))
            .drawBehind {
                val c = Offset(size.width * (0.72f - 0.08f * t), size.height * (0.4f + 0.06f * t))
                drawCircle(Brush.radialGradient(listOf(p.accent.copy(alpha = 0.10f), Color.Transparent), center = c, radius = size.width * 0.4f), radius = size.width * 0.4f, center = c)
            },
    )
}

@Composable
private fun NowPlayingCard(pl: Playing) {
    val p = LocalPalette.current
    Row(
        Modifier.clip(RoundedCornerShape(9.dp)).background(p.bg.copy(alpha = 0.7f)).border(0.5.dp, p.line, RoundedCornerShape(9.dp)).padding(5.dp, 5.dp, 11.dp, 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(6.dp)).background(p.raised), contentAlignment = Alignment.Center) {
            pl.art?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) } ?: T("♪", size = 20.sp)
        }
        Spacer(Modifier.width(9.dp))
        Column(Modifier.width(180.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                T(pl.title, Modifier.weight(1f, fill = false), size = 13.sp, weight = FontWeight.Bold)
                if (pl.playing) { Spacer(Modifier.width(6.dp)); T("▮▮▮", size = 7.sp, color = p.accent) }
            }
            if (pl.sub.isNotBlank()) T(pl.sub, size = 9.sp, color = p.muted)
            if (pl.duration > 0) {
                val pos = (pl.position + if (pl.playing) System.currentTimeMillis() - pl.at else 0).coerceAtMost(pl.duration)
                Meter(pos.toFloat() / pl.duration, Modifier.fillMaxWidth().padding(top = 5.dp))
            }
        }
        Spacer(Modifier.width(9.dp))
        T(pl.appLabel, Modifier.width(56.dp), size = 8.sp, color = p.muted, font = Mono)
    }
}

@Composable
private fun TopRight(now: ZonedDateTime, forecast: Forecast?, net: NetInfo, allFocused: Boolean, modifier: Modifier, menuAlpha: Float, hidden: Boolean, openAll: () -> Unit) {
    val p = LocalPalette.current
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.Bottom) {
            T(now.format(DateTimeFormatter.ofPattern("H:mm")), size = 38.sp, font = headFont(), weight = FontWeight.Light)
            Spacer(Modifier.width(7.dp))
            T(now.format(DateTimeFormatter.ofPattern("M/d（E）", Locale.JAPAN)), Modifier.padding(bottom = 6.dp), size = 11.sp, color = p.muted)
        }
        forecast?.let { f ->
            Spacer(Modifier.width(15.dp))
            Box(Modifier.width(0.5.dp).height(34.dp).background(p.line))
            Spacer(Modifier.width(15.dp))
            val (icon, word) = Weather.describe(f.code, f.isDay)
            T(icon, size = 20.sp)
            Spacer(Modifier.width(6.dp))
            T("%.0f°".format(f.temp), size = 22.sp, font = headFont(), weight = FontWeight.Light)
            Spacer(Modifier.width(6.dp))
            Column {
                T(word, size = 9.sp, color = p.muted)
                f.days.firstOrNull()?.let { d -> T("%.0f° / %.0f°  ☂%d%%".format(d.max, d.min, d.rain), size = 9.sp, color = p.muted) }
            }
        }
        // ぜんぶのアプリ（天気の右）。中身のいちばん上で ↑ で選べる
        Spacer(Modifier.width(15.dp))
        Box(
            Modifier.alpha(if (hidden) 0f else menuAlpha).clip(RoundedCornerShape(16.dp))
                .background(if (allFocused) p.accent else p.panel)
                .border(if (allFocused) 0.dp else 0.5.dp, p.line, RoundedCornerShape(16.dp))
                .clickable(remember { MutableInteractionSource() }, null, onClick = openAll)
                .padding(horizontal = 11.dp, vertical = 6.dp),
        ) { T("▦ ぜんぶのアプリ", size = 11.sp, color = if (allFocused) p.onAccent else p.fg, weight = FontWeight.Medium) }
        Spacer(Modifier.width(15.dp))
        Column(Modifier.alpha(if (hidden) 0f else 1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            NetLine(net.kind != "none", when (net.kind) { "wifi" -> "Wi-Fi"; "ethernet" -> "LAN"; "none" -> "オフライン"; else -> net.kind }, if (net.kind == "wifi") net.bars else -1)
            NetLine(net.online, if (net.online) "ネット" else "ネット ×")
            NetLine(net.vpn, "Tailscale")
        }
    }
}

@Composable
private fun NetLine(ok: Boolean, text: String, bars: Int = -1) {
    val p = LocalPalette.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(5.dp).clip(CircleShape).background(if (ok) Color(0xFF3DDC84) else p.faint))
        Spacer(Modifier.width(4.dp))
        T(text, size = 9.sp, color = p.muted, font = Mono)
        if (bars >= 0) {
            Spacer(Modifier.width(4.dp))
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                (1..4).forEach { k -> Box(Modifier.width(2.dp).height((1 + k * 2).dp).background(if (k <= bars) p.fg else p.faint)) }
            }
        }
    }
}

@Composable
private fun ItemRow(it: Item, sel: Boolean, i: Int, n: Int, col: Color, before: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val p = LocalPalette.current
    val box = if (sel) 48.dp else 28.dp
    Row(
        modifier.width(420.dp).height(if (sel) ROW_ON else ROW)
            .alpha(when { sel -> 1f; before -> 0.28f; else -> 0.72f })
            .let { m -> if (sel) m.background(Brush.horizontalGradient(listOf(p.raised, Color.Transparent))).drawBehind { drawRect(p.accent, size = androidx.compose.ui.geometry.Size(2.5.dp.toPx(), size.height)) } else m }
            .clickable(remember { MutableInteractionSource() }, null, onClick = onClick)
            .padding(start = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(box).clip(RoundedCornerShape(if (sel) 13.dp else 8.dp)).background(if (sel) col.copy(alpha = 0.26f) else p.panel)
                .border(if (sel) 1.5.dp else 0.dp, if (sel) p.accent else Color.Transparent, RoundedCornerShape(if (sel) 13.dp else 8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            val a = it.app
            if (a != null) {
                val px = with(LocalDensity.current) { (box * 0.74f).roundToPx() }
                val art = appArt(a.copy(hasBanner = false), px)
                if (art != null) Image(art, null, Modifier.size(box * 0.74f), contentScale = ContentScale.Fit)
                else T(a.label.take(1), size = if (sel) 22.sp else 13.sp, font = headFont())
            } else T(it.icon, size = if (sel) 27.sp else 15.sp)
            it.badge?.let { b -> Box(Modifier.align(Alignment.TopEnd).clip(RoundedCornerShape(5.dp)).background(p.accent).padding(3.dp, 0.dp)) { T(b, size = 8.sp, color = p.onAccent) } }
        }
        Spacer(Modifier.width(11.dp))
        Column {
            T(it.label, size = if (sel) 19.sp else 13.sp, color = if (it.dim) p.muted else p.fg, weight = if (sel) FontWeight.Bold else FontWeight.Normal)
            if (sel) Row(verticalAlignment = Alignment.CenterVertically) {
                T("%02d/%02d".format(i + 1, n), size = 8.sp, color = p.muted, font = Mono)
                Spacer(Modifier.width(7.dp))
                T(it.sub ?: "けってい で ひらく", size = 10.sp, color = p.accent)
            }
        }
    }
}

/**
 * 2 週間の帯（open=0）と 4 週の表（open=1）。帯は先頭の 14 日、表は 28 日（7 列×4 段）。
 * open の途中は、帯から表へ上に広がる。
 */
@Composable
private fun CalendarBand(monday: LocalDate, weeks: Int, today: LocalDate, events: List<Event>, forecast: Forecast?, open: Float, sel: Int, menuAlpha: Float, onDay: (Int) -> Unit) {
    val p = LocalPalette.current
    val zone = remember { ZoneId.systemDefault() }
    val wx = remember(forecast) { forecast?.days?.associateBy { Instant.ofEpochMilli(it.date).atZone(zone).toLocalDate() }.orEmpty() }
    val sun = Color(0xFFFF6B6B)
    val sat = Color(0xFF5AA9FF)
    val full = open > 0.5f
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize().alpha(if (full) menuAlpha else maxOf(menuAlpha, 0f))) {
        val h = maxHeight
        val bandTop = h - 27.dp - STRIP_H
        val y = bandTop + (GRID_TOP - bandTop) * open
        if (!full) T("NEXT 14 DAYS", Modifier.offset(x = SIDE, y = bandTop - 15.dp).alpha(1f - open), size = 8.sp, color = p.faint, font = Mono)
        else {
            val a = monday.plusWeeks(weeks.toLong())
            val b = a.plusDays(27)
            val label = "${a.year}年 ${a.monthValue}月" + (if (b.monthValue != a.monthValue) " 〜 ${b.monthValue}月" else "") + if (weeks != 0) "（${if (weeks > 0) "+" else ""}$weeks しゅう）" else ""
            Box(Modifier.fillMaxWidth().offset(y = GRID_TOP - 20.dp).padding(horizontal = SIDE), contentAlignment = Alignment.CenterEnd) {
                T(label, size = 12.sp, color = p.muted, font = headFont())
            }
        }
        Column(
            Modifier.offset(y = y).padding(horizontal = SIDE).fillMaxWidth().height(h - 27.dp - y)
                .let { m -> if (full) m.clip(RoundedCornerShape(9.dp)).background(p.panel).border(0.5.dp, p.line, RoundedCornerShape(9.dp)) else m },
        ) {
            val rows = if (full) 4 else 1
            val cols = if (full) 7 else 14
            for (r in 0 until rows) {
                Row(Modifier.fillMaxWidth().weight(1f)) {
                    for (c in 0 until cols) {
                        val i = r * cols + c
                        val d = (if (full) monday.plusWeeks(weeks.toLong()) else monday).plusDays(i.toLong())
                        DayCell(d, today, eventsOn(d, events), wx[d], full, i == sel, sun, sat, Modifier.weight(1f).fillMaxHeight()
                            .clickable(remember { MutableInteractionSource() }, null) { onDay(i) })
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(d: LocalDate, today: LocalDate, evs: List<Event>, w: app.thetahatch.tvhome.data.Day?, full: Boolean, selected: Boolean, sun: Color, sat: Color, modifier: Modifier) {
    val p = LocalPalette.current
    val isToday = d == today
    val past = d.isBefore(today)
    val dow = d.dayOfWeek.value // 1=月 … 7=日
    Column(
        modifier
            .alpha(if (past && !full) 0.45f else 1f)
            .background(when { selected -> p.raised; isToday -> p.accent.copy(alpha = 0.09f); else -> Color.Transparent })
            .drawBehind {
                drawLine(p.line, Offset(0f, 0f), Offset(0f, size.height), 0.5.dp.toPx())
                if (full) drawLine(p.line, Offset(0f, 0f), Offset(size.width, 0f), 0.5.dp.toPx())
            }
            .let { m -> if (selected) m.border(2.dp, p.accent) else m }
            .padding(horizontal = 6.dp, vertical = 5.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            T(d.dayOfMonth.toString(), size = if (full) 15.sp else 13.sp, font = headFont(),
                color = when { isToday -> p.accent; dow == 7 -> sun; dow == 6 -> sat; full -> p.fg; else -> p.muted })
            Spacer(Modifier.width(4.dp))
            T((if (d.dayOfMonth == 1) "${d.monthValue}月 " else "") + DOW[dow - 1], size = 7.5.sp, color = if (isToday) p.accent else p.faint)
            Spacer(Modifier.weight(1f))
            w?.let { T(Weather.describe(it.code).first + "%.0f°".format(it.max), size = if (full) 8.5.sp else 7.5.sp, color = if (full) p.muted else p.faint) }
        }
        evs.take(if (full) 3 else 2).forEach { e ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(1.5.dp).height(9.dp).background(eventColor(e) ?: if (e.allDay) p.widget else p.accent))
                Spacer(Modifier.width(4.dp))
                if (!e.allDay) { T(e.start.format(DateTimeFormatter.ofPattern("H:mm")), size = 6.5.sp, color = p.faint, font = Mono); Spacer(Modifier.width(3.dp)) }
                T(e.title, size = if (full) 8.5.sp else 7.5.sp, color = if (full) p.fg else p.muted)
            }
        }
        if (evs.size > (if (full) 3 else 2)) T("+${evs.size - if (full) 3 else 2}", size = 7.sp, color = p.faint)
    }
}

/** Google カレンダーの色（"#RRGGBB"）。 */
private fun eventColor(e: Event): Color? = runCatching { if (e.color.length == 7) Color(android.graphics.Color.parseColor(e.color)) else null }.getOrNull()

@Suppress("unused")
private fun unusedDp(d: Dp) = abs(d.value)
