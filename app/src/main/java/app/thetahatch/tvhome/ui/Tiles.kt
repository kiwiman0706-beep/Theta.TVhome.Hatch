package app.thetahatch.tvhome.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * リモコンで選ぶ枠。選んでいる間は accent の太い枠と少し拡大（THETA_TV_SPEC §2）。
 * 決定＝onClick、長押し・メニューボタン＝onMenu。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Focusable(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onMenu: (() -> Unit)? = null,
    focusRequester: FocusRequester? = null,
    scaleTo: Float = 1.08f,
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    val p = LocalPalette.current
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) scaleTo else 1f, label = "focus")
    val shape = RoundedCornerShape(maxOf(p.radius, 6.dp))
    Box(
        modifier
            .scale(scale)
            .let { if (focusRequester != null) it.focusRequester(focusRequester) else it }
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { e ->
                if (onMenu != null && e.type == KeyEventType.KeyUp && e.key == Key.Menu) { onMenu(); true } else false
            }
            .clip(shape)
            .background(if (focused) p.raised else p.panel)
            .border(if (focused) 4.dp else 1.dp, if (focused) p.accent else p.line, shape)
            .combinedClickable(onClick = onClick, onLongClick = onMenu),
    ) { content(focused) }
}

/** 絵と名前の大きなタイル（子どもでも分かるように）。 */
@Composable
fun Tile(
    label: String,
    width: Dp,
    height: Dp,
    onClick: () -> Unit,
    onMenu: (() -> Unit)? = null,
    focusRequester: FocusRequester? = null,
    image: ImageBitmap? = null,
    banner: Boolean = false,
    icon: String? = null,
    badge: String? = null,
    dim: Boolean = false,
    sub: String? = null,
) {
    val p = LocalPalette.current
    Focusable(Modifier.size(width, height), onClick, onMenu, focusRequester) { focused ->
        if (banner && image != null) {
            Image(image, label, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            if (focused) Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().background(p.bg.copy(alpha = 0.75f)).padding(8.dp, 4.dp)) {
                T(label, size = 20.sp)
            }
        } else {
            Column(Modifier.fillMaxSize().padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                when {
                    image != null -> Image(image, label, Modifier.size(height * 0.45f), contentScale = ContentScale.Fit)
                    icon != null -> T(icon, size = (height.value * 0.3f).sp)
                }
                Spacer(Modifier.height(6.dp))
                T(label, size = 22.sp, color = if (dim) p.muted else p.fg, weight = if (focused) FontWeight.SemiBold else FontWeight.Normal)
                sub?.let { T(it, size = 16.sp, color = p.muted) }
            }
        }
        badge?.let {
            Box(Modifier.align(Alignment.TopEnd).padding(6.dp).background(p.accent, RoundedCornerShape(12.dp)).padding(8.dp, 2.dp)) {
                T(it, size = 18.sp, color = p.onAccent, weight = FontWeight.SemiBold)
            }
        }
    }
}

/** 設定などの 1 行。 */
@Composable
fun RowButton(title: String, value: String? = null, onClick: () -> Unit, focusRequester: FocusRequester? = null, note: String? = null) {
    val p = LocalPalette.current
    Focusable(Modifier.fillMaxWidth().padding(vertical = 4.dp), onClick, focusRequester = focusRequester, scaleTo = 1.02f) {
        Row(Modifier.fillMaxWidth().padding(20.dp, 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                T(title, size = 24.sp)
                note?.let { T(it, size = 18.sp, color = p.muted, lines = 3) }
            }
            value?.let { T(it, size = 22.sp, color = p.accent, font = Mono) }
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    val p = LocalPalette.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 18.dp, bottom = 10.dp)) {
        Box(Modifier.width(6.dp).height(26.dp).background(p.accent))
        Spacer(Modifier.width(12.dp))
        T(text, size = 26.sp, color = p.muted, font = headFont(), weight = FontWeight.Light)
    }
}
