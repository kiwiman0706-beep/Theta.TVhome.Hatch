package app.thetahatch.tvhome.ui

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import app.thetahatch.tvhome.R
import app.thetahatch.tvhome.skin.DarkPalette
import app.thetahatch.tvhome.skin.Palette

val LocalPalette = staticCompositionLocalOf<Palette> { DarkPalette }

val Oswald = FontFamily(
    Font(R.font.oswald_light, FontWeight.Light),
    Font(R.font.oswald_regular, FontWeight.Normal),
    Font(R.font.oswald_semibold, FontWeight.SemiBold),
)
val Mono = FontFamily(Font(R.font.share_tech_mono))

/** 見出し・時計の書体（スキンの font に合わせる）。日本語は端末の書体に落ちる。 */
@Composable
fun headFont(): FontFamily = when (LocalPalette.current.font) {
    "mono" -> Mono
    "sans" -> FontFamily.SansSerif
    else -> Oswald
}

/** 本文（テレビなので 24sp 以上が目安）。 */
@Composable
fun T(
    text: String,
    modifier: Modifier = Modifier,
    size: TextUnit = 24.sp,
    color: Color = LocalPalette.current.fg,
    weight: FontWeight = FontWeight.Normal,
    font: FontFamily? = null,
    lines: Int = 1,
) {
    BasicText(
        text, modifier,
        style = TextStyle(color = color, fontSize = size, fontWeight = weight, fontFamily = font),
        maxLines = lines, overflow = TextOverflow.Ellipsis,
    )
}
