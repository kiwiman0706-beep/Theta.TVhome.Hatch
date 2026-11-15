package app.thetahatch.tvhome.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.thetahatch.tvhome.R

/** ドット文字（DotGothic16、SIL Open Font License。licenses/）。Theta.KidsHome.Hatch と同じ。 */
val DotFont = FontFamily(Font(R.font.dotgothic16))

/** キッズホームと同じ、パステルの方眼紙とフチの太いウィンドウの配色（KidsHome の Themes.kt から）。 */
data class KidTheme(
    val id: String, val name: String, val mascot: String,
    val bgTop: Color, val bgBottom: Color, val grid: Color,
    val outline: Color, val text: Color, val accent: Color,
    val titleBars: List<Color>, val bubbles: List<Color>,
    val lines: List<String>,
)

private val commonLines = listOf("なにを みる？", "きょうも げんき？", "いっしょに みよう！", "えらいね！", "だいすき！")

val KidThemes = listOf(
    KidTheme("usagi", "うさぎ", "🐰", Color(0xFFFFD6E4), Color(0xFFFFF4F8), Color(0x66FFFFFF), Color(0xFF7A3E55), Color(0xFF7A3E55), Color(0xFFFF7FA8),
        listOf(Color(0xFFFFB3CF), Color(0xFFC9F0A8), Color(0xFFB8D8FF), Color(0xFFFFE29A)), listOf(Color(0xFFFFE0EA), Color(0xFFFFF0C9), Color(0xFFE3F4FF), Color(0xFFEDE4FF)), commonLines + "ぴょん ぴょん！"),
    KidTheme("neko", "ねこ", "🐱", Color(0xFFE4DAFF), Color(0xFFF8F4FF), Color(0x66FFFFFF), Color(0xFF4B3A7A), Color(0xFF4B3A7A), Color(0xFF9B7BFF),
        listOf(Color(0xFFCBB8FF), Color(0xFFFFB3CF), Color(0xFFB8E8F0), Color(0xFFFFE29A)), listOf(Color(0xFFEDE4FF), Color(0xFFFFE0EA), Color(0xFFDFF7EE), Color(0xFFFFF0C9)), commonLines + "にゃーん"),
    KidTheme("kuma", "くま", "🧸", Color(0xFFFFE6C7), Color(0xFFFFF8EE), Color(0x77FFFFFF), Color(0xFF6B4426), Color(0xFF6B4426), Color(0xFFD9925B),
        listOf(Color(0xFFF5C99B), Color(0xFFFFE29A), Color(0xFFC9E8A8), Color(0xFFFFB8B8)), listOf(Color(0xFFFFEBD2), Color(0xFFFFF5C2), Color(0xFFE6F3DA), Color(0xFFFFE0EA)), commonLines + "ぎゅーっ！"),
    KidTheme("dino", "きょうりゅう", "🦕", Color(0xFFD3F2CC), Color(0xFFF4FFF0), Color(0x77FFFFFF), Color(0xFF2F5E33), Color(0xFF2F5E33), Color(0xFF5DBB63),
        listOf(Color(0xFFA8E0A0), Color(0xFFFFE29A), Color(0xFFB8D8FF), Color(0xFFFFC4A8)), listOf(Color(0xFFDDF5D8), Color(0xFFFFF0C9), Color(0xFFD9F0FF), Color(0xFFFFE3D3)), commonLines + "がおー！"),
    KidTheme("unicorn", "ユニコーン", "🦄", Color(0xFFFFDDF2), Color(0xFFDDEEFF), Color(0x77FFFFFF), Color(0xFF5A3D7A), Color(0xFF5A3D7A), Color(0xFFC77DFF),
        listOf(Color(0xFFE2B8FF), Color(0xFFFFB8DE), Color(0xFFB8E0FF), Color(0xFFFFF0A0)), listOf(Color(0xFFFFE1F4), Color(0xFFE1F0FF), Color(0xFFFFF5CC), Color(0xFFE5FFE9)), commonLines + "キラキラ〜"),
    KidTheme("umi", "うみ", "🐬", Color(0xFFC9EDFF), Color(0xFFEFFBFF), Color(0x77FFFFFF), Color(0xFF1F4E6B), Color(0xFF1F4E6B), Color(0xFF3BA7E0),
        listOf(Color(0xFFA8D8F5), Color(0xFFB8F0E0), Color(0xFFFFE29A), Color(0xFFFFC4D8)), listOf(Color(0xFFD4F1FF), Color(0xFFFFF0C9), Color(0xFFDDF7F0), Color(0xFFFFE0EA)), commonLines + "きゅいきゅい！"),
    KidTheme("hoshi", "おほしさま", "🌙", Color(0xFF2B2F6B), Color(0xFF5A4A9A), Color(0x22FFFFFF), Color(0xFF23265A), Color(0xFFFFFFFF), Color(0xFFFFD95A),
        listOf(Color(0xFFB8A8FF), Color(0xFFFFD95A), Color(0xFFFFB8D9), Color(0xFFA8E0F5)), listOf(Color(0xFFEDE8FF), Color(0xFFFFF4C4), Color(0xFFFFE4F1), Color(0xFFE0F4FF)), commonLines + "おほしさま きれいだね"),
)

fun kidThemeById(id: String) = KidThemes.firstOrNull { it.id == id } ?: KidThemes.first()

private val WindowShape = RoundedCornerShape(18.dp)
private val dotColors = listOf(Color(0xFFFFE29A), Color(0xFFB8F0A8), Color(0xFFFFB3CF))

/** シールのような、ずらした影。 */
fun Modifier.stickerShadow(color: Color, offset: Dp = 5.dp, radius: Dp = 18.dp) = drawBehind {
    val o = offset.toPx()
    drawRoundRect(color, topLeft = Offset(o, o), size = size, cornerRadius = CornerRadius(radius.toPx()))
}

@Composable
fun KText(text: String, theme: KidTheme, size: Int = 18, color: Color = theme.outline, modifier: Modifier = Modifier, lines: Int = 1) =
    T(text, modifier, size.sp, color, font = DotFont, lines = lines)

/**
 * レトロなパソコンのウィンドウ（タイトルバーに小さな丸 3 つ）。onClick があれば押せる。
 * リモコンで選んでいる間は accent の太い枠と少し拡大。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RetroWindow(
    theme: KidTheme,
    modifier: Modifier = Modifier,
    title: String? = null,
    titleColor: Color = theme.titleBars.first(),
    onClick: (() -> Unit)? = null,
    onMenu: (() -> Unit)? = null,
    onFocus: (() -> Unit)? = null,
    focusRequester: FocusRequester? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (pressed) 0.94f else if (focused) 1.06f else 1f, label = "press")
    Column(
        modifier
            .scale(scale)
            .stickerShadow(theme.outline.copy(alpha = 0.85f), if (pressed) 2.dp else 5.dp)
            .clip(WindowShape)
            .background(Color(0xFFFFFDFE))
            .border(if (focused) 6.dp else 3.dp, if (focused) theme.accent else theme.outline, WindowShape)
            .let { m -> if (focusRequester != null) m.focusRequester(focusRequester) else m }
            .let { m -> if (onFocus != null) m.onFocusChanged { if (it.isFocused) onFocus() } else m }
            .onPreviewKeyEvent { e -> if (onMenu != null && e.type == KeyEventType.KeyUp && e.key == Key.Menu) { onMenu(); true } else false }
            .let { m -> if (onClick != null) m.combinedClickable(interactionSource = interaction, indication = null, onClick = onClick, onLongClick = onMenu) else m },
    ) {
        if (title != null) {
            Row(
                Modifier.fillMaxWidth().background(titleColor)
                    .drawBehind { val w = 3.dp.toPx(); drawLine(theme.outline, Offset(0f, size.height - w / 2), Offset(size.width, size.height - w / 2), w) }
                    .padding(start = 12.dp, end = 10.dp, top = 5.dp, bottom = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                KText(title, theme, 15, modifier = Modifier.weight(1f))
                dotColors.forEach { c ->
                    Box(Modifier.padding(start = 5.dp).size(10.dp).clip(CircleShape).background(c).border(2.dp, theme.outline, CircleShape))
                }
            }
        }
        content()
    }
}

/** 方眼紙のグラデーションの背景。 */
@Composable
fun RetroBackground(theme: KidTheme, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize().background(Brush.verticalGradient(listOf(theme.bgTop, theme.bgBottom)))) {
        val step = 36.dp.toPx()
        val w = 1.5.dp.toPx()
        var x = step
        while (x < size.width) { drawLine(theme.grid, Offset(x, 0f), Offset(x, size.height), w); x += step }
        var y = step
        while (y < size.height) { drawLine(theme.grid, Offset(0f, y), Offset(size.width, y), w); y += step }
    }
}

@Composable
fun KChip(theme: KidTheme, text: String) {
    val shape = RoundedCornerShape(50)
    Box(Modifier.padding(start = 10.dp).stickerShadow(theme.outline, 3.dp, 40.dp).clip(shape).background(Color.White).border(3.dp, theme.outline, shape).padding(horizontal = 16.dp, vertical = 5.dp)) {
        KText(text, theme, 22)
    }
}

/** 丸い台に絵（アプリの絵か絵文字）。 */
@Composable
fun Bubble(theme: KidTheme, index: Int, size: Dp, content: @Composable () -> Unit) {
    Box(
        Modifier.size(size).clip(CircleShape).background(theme.bubbles[index % theme.bubbles.size]).border(2.5.dp, theme.outline.copy(alpha = 0.35f), CircleShape),
        contentAlignment = Alignment.Center,
    ) { content() }
}
