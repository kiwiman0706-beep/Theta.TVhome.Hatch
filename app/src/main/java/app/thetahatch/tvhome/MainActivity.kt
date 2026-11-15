package app.thetahatch.tvhome

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import app.thetahatch.tvhome.hub.UpdateHub
import app.thetahatch.tvhome.skin.paletteFor
import app.thetahatch.tvhome.ui.AllAppsScreen
import app.thetahatch.tvhome.ui.CrossBarLayout
import app.thetahatch.tvhome.ui.KidsLayout
import app.thetahatch.tvhome.ui.HomeScreen
import app.thetahatch.tvhome.ui.LocalPalette
import app.thetahatch.tvhome.ui.MenuOverlay
import app.thetahatch.tvhome.ui.PinPad
import app.thetahatch.tvhome.ui.SettingsScreen
import app.thetahatch.tvhome.ui.TileMenu
import app.thetahatch.tvhome.ui.UpdatesScreen

class MainActivity : ComponentActivity() {
    private val screen = mutableStateOf("home")
    private val homeFocus = FocusRequester()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as TvHomeApp
        intent?.getStringExtra(EXTRA_SCREEN)?.let { screen.value = it }
        setContent {
            val tick by app.state.tick.collectAsState()
            val dark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) != Configuration.UI_MODE_NIGHT_NO
            val palette = remember(tick) { paletteFor(app.store.skin, dark) }
            var menu by remember { mutableStateOf<TileMenu?>(null) }
            var pinAsk by remember { mutableStateOf<Pair<String, (String) -> Unit>?>(null) }
            var unlockedAt by remember { mutableStateOf(0L) }
            val current by screen

            /** おうちの人の番号で守る（番号が無い・5 分以内に入れたなら、そのまま）。 */
            val guarded: (() -> Unit) -> Unit = { action ->
                if (!app.store.hasPin() || System.currentTimeMillis() - unlockedAt < 5 * 60_000L) action()
                else pinAsk = "おうちの人の番号" to { pin ->
                    if (app.store.checkPin(pin)) { unlockedAt = System.currentTimeMillis(); action() } else app.toast("番号がちがいます")
                }
            }
            val go: (String) -> Unit = { screen.value = it }

            CompositionLocalProvider(LocalPalette provides palette) {
                Box(Modifier.fillMaxSize().background(palette.bg)) {
                    when (current) {
                        "settings" -> SettingsScreen(app, go) { title, done -> pinAsk = title to done }
                        "updates" -> UpdatesScreen(app)
                        "apps" -> AllAppsScreen(app, { menu = it }, guarded)
                        else -> when (app.store.layout.also { tick }) {
                            "bar" -> CrossBarLayout(app, homeFocus, go, { menu = it }, guarded)
                            "simple" -> HomeScreen(app, homeFocus, go, { menu = it }, guarded)
                            else -> KidsLayout(app, homeFocus, go, { menu = it }, guarded)
                        }
                    }
                    menu?.let { m -> MenuOverlay(m) { menu = null } }
                    pinAsk?.let { (title, done) -> PinPad(title, { pin -> pinAsk = null; done(pin) }, { pinAsk = null }) }
                }
                // ホームでは「戻る」で何もしない（ホームアプリなので）。ほかの画面ではホームへ
                BackHandler(enabled = true) {
                    when {
                        pinAsk != null -> pinAsk = null
                        menu != null -> menu = null
                        current != "home" -> { UpdateHub.foreground = false; screen.value = "home" }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // ホームボタン：どの画面からでもホームの先頭へ
        screen.value = intent.getStringExtra(EXTRA_SCREEN) ?: "home"
        if (screen.value == "home") runCatching { homeFocus.requestFocus() }
    }

    override fun onResume() {
        super.onResume()
        (application as TvHomeApp).state.onShown()
    }

    override fun onPause() {
        super.onPause()
        UpdateHub.foreground = false
    }

    companion object {
        const val EXTRA_SCREEN = "screen"
    }
}
