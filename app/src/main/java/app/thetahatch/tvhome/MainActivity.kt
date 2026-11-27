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
import android.view.KeyEvent
import app.thetahatch.tvhome.hub.UpdateHub
import app.thetahatch.tvhome.rescue.BackCounter
import app.thetahatch.tvhome.rescue.Rescue
import app.thetahatch.tvhome.rescue.RescueActivity
import app.thetahatch.tvhome.skin.paletteFor
import app.thetahatch.tvhome.ui.AllAppsScreen
import app.thetahatch.tvhome.ui.AskScreen
import app.thetahatch.tvhome.ui.CrossBarLayout
import app.thetahatch.tvhome.ui.KidsLayout
import app.thetahatch.tvhome.ui.HomeScreen
import app.thetahatch.tvhome.ui.LocalPalette
import app.thetahatch.tvhome.ui.MenuOverlay
import app.thetahatch.tvhome.ui.NasScreen
import app.thetahatch.tvhome.ui.PhoneInputOverlay
import app.thetahatch.tvhome.ui.PinPad
import app.thetahatch.tvhome.ui.SettingsScreen
import app.thetahatch.tvhome.ui.TileMenu
import app.thetahatch.tvhome.ui.UpdatesScreen

class MainActivity : ComponentActivity() {
    private val screen = mutableStateOf("home")
    private val homeFocus = FocusRequester()
    private var rescued = false
    private val healthy = android.os.Handler(android.os.Looper.getMainLooper())
    private val markHealthy = Runnable { Rescue.onHomeHealthy(this) }
    private val backs = BackCounter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as TvHomeApp
        // ① 落ちる・固まるのが続いていたら、ホームの代わりにレスキュー画面（別プロセス）
        val safe = intent?.getBooleanExtra(Rescue.EXTRA_SAFE, false) == true
        if (Rescue.onHomeStart(this) && !safe) {
            rescued = true
            openRescue()
            finish()
            return
        }
        if (safe) app.safeMode = true
        watchBack()
        intent?.getStringExtra(EXTRA_SCREEN)?.let { screen.value = it }
        setContent {
            val tick by app.state.tick.collectAsState()
            val dark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) != Configuration.UI_MODE_NIGHT_NO
            val palette = remember(tick) { paletteFor(app.store.skin, dark) }
            var menu by remember { mutableStateOf<TileMenu?>(null) }
            var pinAsk by remember { mutableStateOf<Pair<String, (String) -> Unit>?>(null) }
            var unlockedAt by remember { mutableStateOf(0L) }
            val current by screen
            // Google の許可の画面（せってい →「Google カレンダーにつなぐ」）
            val consentPi by app.state.consent.collectAsState()
            val consentLauncher = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult()) { r ->
                app.state.onConsent(r.data)
            }
            androidx.compose.runtime.LaunchedEffect(consentPi) {
                consentPi?.let { pi ->
                    runCatching { consentLauncher.launch(androidx.activity.result.IntentSenderRequest.Builder(pi.intentSender).build()) }
                        .onFailure { app.state.consent.value = null; app.toast("許可の画面を出せません") }
                }
            }

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
                        "ask" -> { val (m, v) = app.state.askOpen.collectAsState().value; AskScreen(app, m, v) }
                        "nas" -> { val v = app.state.nasOpen.collectAsState().value; NasScreen(app, v) }
                        else -> when (app.store.layout.also { tick }) {
                            "bar" -> CrossBarLayout(app, homeFocus, go, { menu = it }, guarded)
                            "simple" -> HomeScreen(app, homeFocus, go, { menu = it }, guarded)
                            else -> KidsLayout(app, homeFocus, go, { menu = it }, guarded)
                        }
                    }
                    menu?.let { m -> MenuOverlay(m) { menu = null } }
                    PhoneInputOverlay()
                    pinAsk?.let { (title, done) -> PinPad(title, { pin -> pinAsk = null; done(pin) }, { pinAsk = null }) }
                    if (current == "rescue-pin") {
                        PinPad("レスキュー：おうちの人の番号", { pin -> screen.value = "home"; if (app.store.checkPin(pin)) openRescue() else app.toast("番号がちがいます") }, { screen.value = "home" })
                    }
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

    /**
     * ② リモコンの「戻る」を 4 秒のうちに 7 回：どの画面からでもレスキュー。
     * 画面の部品（Compose）より先に受けるため、ウィンドウの入口で数える。
     */
    private fun watchBack() {
        val original = window.callback
        window.callback = object : android.view.Window.Callback by original {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.action == KeyEvent.ACTION_UP && event.keyCode == KeyEvent.KEYCODE_BACK && backs.hit()) {
                    val app = application as TvHomeApp
                    // 子どもモードで番号があるときは、番号を聞いてから（落ちたときの自動のレスキューは聞かない）
                    if (app.store.kidsMode && app.store.hasPin()) screen.value = "rescue-pin" else openRescue()
                    return true
                }
                return original.dispatchKeyEvent(event)
            }
        }
    }

    fun openRescue() {
        runCatching { startActivity(Intent(this, RescueActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { Rescue.openStockHome(this) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // ホームボタン：どの画面からでもホームの先頭へ
        screen.value = intent.getStringExtra(EXTRA_SCREEN) ?: "home"
        if (screen.value == "home") runCatching { homeFocus.requestFocus() }
    }

    override fun onResume() {
        super.onResume()
        if (rescued) return
        (application as TvHomeApp).state.onShown()
        // ③ 8 秒ふつうに動いたら「無事」
        healthy.postDelayed(markHealthy, 8000)
    }

    override fun onPause() {
        super.onPause()
        UpdateHub.foreground = false
    }

    override fun onStop() {
        if (!rescued) { healthy.removeCallbacks(markHealthy); Rescue.onHomeHealthy(this) }
        super.onStop()
    }

    companion object {
        const val EXTRA_SCREEN = "screen"
    }
}
