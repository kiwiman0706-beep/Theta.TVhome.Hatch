package app.thetahatch.tvhome.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 四隅のかぎ括弧（HUD）。 */
fun Modifier.corners(color: Color, len: Dp = 10.dp, w: Dp = 2.dp) = drawBehind {
    val l = len.toPx(); val s = w.toPx()
    val pts = listOf(Offset(0f, 0f) to (1 to 1), Offset(size.width, 0f) to (-1 to 1), Offset(0f, size.height) to (1 to -1), Offset(size.width, size.height) to (-1 to -1))
    pts.forEach { (o, d) ->
        drawLine(color, o, Offset(o.x + l * d.first, o.y), s)
        drawLine(color, o, Offset(o.x, o.y + l * d.second), s)
    }
}

/** 斜めの縞（////）。 */
@Composable
fun Stripes(modifier: Modifier, color: Color) {
    Canvas(modifier) {
        val step = 6.dp.toPx()
        var x = -size.height
        while (x < size.width) { drawLine(color, Offset(x, size.height), Offset(x + size.height, 0f), 1.5.dp.toPx()); x += step }
    }
}

/**
 * 計器盤の区画（Home.Hatch の SectionFrame を TV 用に大きく、軽くしたもの）。
 * スキンの作り（style）で、hud は「[ CODE ] 見出し ////」と四隅のかぎ括弧、pop・soft・braun・window は角丸の台。
 */
@Composable
fun HudFrame(code: String, title: String, modifier: Modifier = Modifier, trailing: String = "", body: @Composable ColumnScope.() -> Unit) {
    val p = LocalPalette.current
    val rounded = p.style in listOf("pop", "soft", "braun", "window")
    val shape = RoundedCornerShape(if (rounded) maxOf(p.radius, 10.dp) else 0.dp)
    Column(
        modifier.fillMaxWidth()
            .let { if (rounded) it.clip(shape).background(p.panel) else it.background(p.panel.copy(alpha = 0.55f)).border(1.dp, p.line).corners(p.accent) }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (rounded) Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(p.accent))
            else T("[ $code ]", size = 13.sp, color = p.accent, font = Mono)
            T(title, size = 16.sp, color = p.muted)
            if (!rounded) Stripes(Modifier.weight(1f).height(6.dp), p.line) else Box(Modifier.weight(1f))
            if (trailing.isNotEmpty()) T(trailing, size = 14.sp, color = p.accent, font = Mono)
        }
        body()
    }
}

/** 小さな目盛り（0〜1）。 */
@Composable
fun Meter(frac: Float, modifier: Modifier = Modifier, color: Color = LocalPalette.current.accent) {
    val p = LocalPalette.current
    Box(modifier.height(6.dp).background(p.line.copy(alpha = 0.5f))) {
        Box(Modifier.fillMaxWidth(frac.coerceIn(0f, 1f)).height(6.dp).background(color))
    }
}

@Composable
fun Dot(on: Boolean, ok: Color = Color(0xFF3DDC84), off: Color = LocalPalette.current.muted) =
    Box(Modifier.padding(end = 6.dp).size(10.dp).clip(RoundedCornerShape(5.dp)).background(if (on) ok else off))
