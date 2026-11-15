package app.thetahatch.tvhome.ui

import app.thetahatch.tvhome.rescue.Rescue
import app.thetahatch.tvhome.rescue.RescueActivity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.thetahatch.tvhome.TvHomeApp
import app.thetahatch.tvhome.data.Place
import app.thetahatch.tvhome.data.Weather
import app.thetahatch.tvhome.hub.Catalog
import app.thetahatch.tvhome.hub.UpdateHub
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

val LAYOUTS = listOf("bar", "kids", "simple")
val SKINS = listOf("dark", "system", "light", "pop", "alert", "scifi", "term", "braun", "soft", "window", "lcd")

@Composable
fun Page(title: String, first: FocusRequester, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    LaunchedEffect(Unit) { delay(150); runCatching { first.requestFocus() } }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 96.dp, vertical = 27.dp)) {
        item { T(title, size = 44.sp, font = headFont(), weight = FontWeight.Light, modifier = Modifier.padding(bottom = 12.dp)) }
        content()
        item { Spacer(Modifier.height(40.dp)) }
    }
}

@Composable
fun SettingsScreen(app: TvHomeApp, go: (String) -> Unit, askPin: (String, (String) -> Unit) -> Unit) {
    val s = app.store
    val first = remember { FocusRequester() }
    val tick by app.state.tick.collectAsState()
    val entries by app.state.entries.collectAsState()
    var placeQuery by remember { mutableStateOf("") }
    var places by remember { mutableStateOf<List<Place>>(emptyList()) }
    var placeMsg by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val p = LocalPalette.current
    val changed = { app.settingsChanged() }
    // tick を読む（ADB などで変わったら描き直す）
    @Suppress("UNUSED_EXPRESSION") tick

    Page("せってい", first) {
        item { SectionTitle("見た目") }
        item {
            val names = mapOf("kids" to "キッズ", "bar" to "クロスバー", "simple" to "たな")
            RowButton("ホームの形", names[s.layout] ?: s.layout, focusRequester = first, onClick = {
                s.layout = LAYOUTS[(LAYOUTS.indexOf(s.layout) + 1) % LAYOUTS.size]; changed()
            }, note = "クロスバー（右に時計・てんき・よてい）・キッズ（キッズホーム風）・たな（横の棚）")
        }
        item {
            RowButton("キッズの いろ", kidThemeById(s.kidTheme).let { it.mascot + " " + it.name }, onClick = {
                val i = KidThemes.indexOfFirst { it.id == s.kidTheme }
                s.kidTheme = KidThemes[(i + 1) % KidThemes.size].id; changed()
            }, note = "ホームの形が「キッズ」のとき")
        }
        item {
            RowButton("スキン", s.skin, onClick = {
                s.skin = SKINS[(SKINS.indexOf(s.skin) + 1) % SKINS.size]; changed()
            }, note = "クロスバー・たな・設定の画面の色（シリーズ共通のスキン）")
        }

        item { SectionTitle("子ども") }
        item { RowButton("子どもモード", if (s.kidsMode) "オン" else "オフ", onClick = { s.kidsMode = !s.kidsMode; changed() }, note = "オン：すきなアプリとテレビだけを出し、ほかはおうちの人の番号で") }
        item {
            RowButton("おうちの人の番号", if (s.hasPin()) "設定済み" else "なし", onClick = {
                askPin("新しい番号（4 けた。空で消す）") { pin -> s.setPin(pin); changed(); app.toast(if (pin.isEmpty()) "番号を消しました" else "番号を決めました") }
            }, note = "設定・更新・アプリの並べ替えを守ります")
        }

        item { SectionTitle("天気") }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(
                    placeQuery, { placeQuery = it }, singleLine = true,
                    textStyle = TextStyle(color = p.fg, fontSize = 24.sp), cursorBrush = SolidColor(p.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        scope.launch {
                            placeMsg = "さがしています…"
                            places = runCatching { withContext(Dispatchers.IO) { Weather.search(placeQuery) } }.getOrElse { placeMsg = "さがせません：${it.message}"; emptyList() }
                            if (places.isNotEmpty()) placeMsg = null else if (placeMsg == "さがしています…") placeMsg = "見つかりません"
                        }
                    }),
                    modifier = Modifier.width(560.dp).border(2.dp, p.line).padding(16.dp),
                    decorationBox = { inner -> if (placeQuery.isEmpty()) T("地名（例：横浜）→ 決定で検索", size = 22.sp, color = p.muted); inner() },
                )
                Spacer(Modifier.width(20.dp))
                T("いま：" + (Place.decode(s.weatherPlace)?.name?.ifEmpty { "設定済み" } ?: "なし"), size = 22.sp, color = p.muted)
            }
        }
        placeMsg?.let { m -> item { T(m, size = 20.sp, color = p.muted) } }
        items(places) { pl -> RowButton(pl.name, "%.2f, %.2f".format(pl.lat, pl.lon), onClick = { s.weatherPlace = pl.encode(); places = emptyList(); changed() }) }

        item { SectionTitle("カレンダー") }
        item {
            RowButton("予定（iCal の非公開 URL）", if (s.hasSecret("calendar.ics")) "設定済み" else "未設定", onClick = { app.state.refreshCalendar(force = true) },
                note = "URL は秘密なので ADB で入れます（README「カレンダー」）。押すと読み直します")
        }

        item { SectionTitle("テレビの入口") }
        items(entries, key = { it.key }) { e ->
            val src = when (e.source) { "set" -> "設定済み"; "auto" -> "自動"; else -> "未設定" }
            RowButton(e.label + if (e.key in s.hidden) "（かくし中）" else "", src, onClick = {
                if (e.key in s.hidden) { s.hidden = s.hidden - e.key; changed() } else app.state.launch(e.intent)
            }, note = e.key + if (e.key in s.hidden) " ・押すと出します" else " ・押すと試せます")
        }

        val hiddenApps = app.state.apps.value.filter { it.pkg in s.hidden }
        if (hiddenApps.isNotEmpty()) {
            item { SectionTitle("かくしたアプリ") }
            items(hiddenApps, key = { "h" + it.pkg }) { a -> RowButton(a.label, "出す", onClick = { s.hidden = s.hidden - a.pkg; changed() }) }
        }

        item { SectionTitle("更新") }
        item { RowButton("自動で更新する", if (s.autoUpdate) "オン" else "オフ", onClick = { s.autoUpdate = !s.autoUpdate; changed() }, note = "12 時間ごとに確かめ、このホームと Hatch のアプリを新しい版にします") }
        item { RowButton("更新の画面", null, onClick = { go("updates") }) }

        item { SectionTitle("このアプリ") }
        item {
            val home = UpdateHub.defaultHome(app)
            RowButton("ホームアプリ", if (home == app.packageName) "このアプリ" else (home ?: "未選択"), onClick = {
                val ok = app.state.launch(Intent(Settings.ACTION_HOME_SETTINGS))
                if (!ok) app.toast("ADB で切り替えます（README「ホームにする」）")
            }, note = "リモコンのホームボタンでこの画面を出すには、既定のホームに選びます")
        }
        item { SectionTitle("こまったとき") }
        item { RowButton("いつもの（純正の）ホームを開く", null, onClick = { if (!Rescue.openStockHome(app)) app.toast("見つかりません") }, note = "このホームがおかしいときの逃げ道。既定のホームは変えません") }
        item { RowButton("レスキュー画面", null, onClick = { app.startActivity(android.content.Intent(app, RescueActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }, note = "リモコンの「戻る」を すばやく 7 回でも開けます。テレビのアプリ一覧の「TVhome のレスキュー」からも") }
        item { RowButton("版", app.packageManager.getPackageInfo(app.packageName, 0).versionName, onClick = {}) }
        item { RowButton("テレビの設定を開く", null, onClick = { app.state.launch(Intent(Settings.ACTION_SETTINGS)) }) }
    }
}

@Composable
fun UpdatesScreen(app: TvHomeApp) {
    val first = remember { FocusRequester() }
    val status by UpdateHub.status.collectAsState()
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    val p = LocalPalette.current
    LaunchedEffect(Unit) { UpdateHub.foreground = true; busy = true; runCatching { UpdateHub.check(app) }; busy = false }
    val v = UpdateHub.versionOf(app)
    val list = Catalog.android.filter { it.installedPackage(v) != null }
    val canInstall = app.packageManager.canRequestPackageInstalls()

    Page("こうしん", first) {
        item {
            RowButton(if (busy) "確かめています…" else "新しい版を確かめる", null, focusRequester = first, onClick = {
                if (!busy) scope.launch { busy = true; runCatching { UpdateHub.check(app) }; busy = false }
            })
        }
        if (!canInstall) item {
            RowButton("インストールの許可", "未許可", onClick = {
                val ok = app.state.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${app.packageName}")))
                if (!ok) app.toast("ADB で許可します（README）")
            }, note = "テレビでは画面から許可できないことがあります。ADB：appops set app.thetahatch.tvhome REQUEST_INSTALL_PACKAGES allow")
        }
        item {
            RowButton("すべて更新", null, onClick = {
                scope.launch { busy = true; runCatching { UpdateHub.runAll(app, install = true, mobile = true, fromWorker = false) }; busy = false }
            })
        }
        items(list, key = { it.id }) { a ->
            val st = status[a.id]
            val newer = st != null && UpdateHub.hasNewer(a, st)
            val value = when {
                st?.installing == true -> "入れています…"
                st?.progress != null -> "取得 ${(st.progress * 100).toInt()}%"
                newer -> "${st!!.installed} → ${st.latest!!.version}"
                else -> st?.installed ?: v(a.installedPackage(v)!!) ?: ""
            }
            RowButton(a.name, value, note = st?.error ?: st?.result ?: if (newer) "押すと更新します" else "最新です", onClick = {
                if (newer) scope.launch { UpdateHub.update(app, a) }
            })
        }
        item { T("ほかの Hatch アプリも、このホームが入れたものは自動で更新します（Theta.Hatch.Series の catalog）。", size = 18.sp, color = p.muted, lines = 2) }
    }
}

@Composable
fun AllAppsScreen(app: TvHomeApp, menu: (TileMenu) -> Unit, guarded: (() -> Unit) -> Unit) {
    val apps by app.state.apps.collectAsState()
    val first = remember { FocusRequester() }
    LaunchedEffect(apps.isNotEmpty()) { delay(150); runCatching { first.requestFocus() } }
    Column(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 27.dp)) {
        T("ぜんぶのアプリ", size = 44.sp, font = headFont(), weight = FontWeight.Light, modifier = Modifier.padding(bottom = 12.dp))
        LazyVerticalGrid(GridCells.Adaptive(220.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(8.dp)) {
            items(apps, key = { it.pkg }) { a ->
                val px = with(LocalDensity.current) { 200.dp.roundToPx() }
                Tile(a.label, 200.dp, 112.dp, image = appArt(a, px), banner = a.hasBanner,
                    focusRequester = if (a == apps.first()) first else null,
                    onClick = { app.state.launch(a.intent) }, onMenu = { guarded { menu(appMenu(app, a)) } })
            }
        }
    }
}

/** 番号を入れる（リモコンの数字ボタンでも、画面の数字でも）。 */
@Composable
fun PinPad(title: String, onDone: (String) -> Unit, onCancel: () -> Unit) {
    val p = LocalPalette.current
    var pin by remember { mutableStateOf("") }
    val first = remember { FocusRequester() }
    var shake by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { delay(100); runCatching { first.requestFocus() } }
    Box(
        Modifier.fillMaxSize().background(p.bg.copy(alpha = 0.92f)).onKeyEvent { e ->
            if (e.type != KeyEventType.KeyUp) return@onKeyEvent false
            val d = when (e.key) {
                Key.Zero, Key.NumPad0 -> "0"; Key.One, Key.NumPad1 -> "1"; Key.Two, Key.NumPad2 -> "2"; Key.Three, Key.NumPad3 -> "3"
                Key.Four, Key.NumPad4 -> "4"; Key.Five, Key.NumPad5 -> "5"; Key.Six, Key.NumPad6 -> "6"; Key.Seven, Key.NumPad7 -> "7"
                Key.Eight, Key.NumPad8 -> "8"; Key.Nine, Key.NumPad9 -> "9"; else -> null
            } ?: return@onKeyEvent false
            pin = (pin + d).take(8); true
        },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            T(title, size = 30.sp)
            Spacer(Modifier.height(12.dp))
            T(if (pin.isEmpty()) "—" else "●".repeat(pin.length), size = 40.sp, color = p.accent, font = Mono)
            Spacer(Modifier.height(20.dp))
            val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "けす", "0", "OK")
            keys.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(6.dp)) {
                    row.forEach { k ->
                        Focusable(Modifier.size(110.dp, 76.dp), focusRequester = if (k == "1") first else null, onClick = {
                            when (k) { "けす" -> pin = pin.dropLast(1); "OK" -> onDone(pin); else -> pin = (pin + k).take(8) }
                        }) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { T(k, size = 30.sp, font = Mono) } }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Focusable(Modifier.size(354.dp, 64.dp), onClick = onCancel) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { T("やめる", size = 24.sp) } }
            @Suppress("UNUSED_EXPRESSION") shake
        }
    }
}

@Composable
fun MenuOverlay(m: TileMenu, onClose: () -> Unit) {
    val p = LocalPalette.current
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(100); runCatching { first.requestFocus() } }
    Box(Modifier.fillMaxSize().background(p.bg.copy(alpha = 0.85f)), contentAlignment = Alignment.Center) {
        Column(Modifier.width(560.dp).background(p.panel).border(2.dp, p.accent).padding(24.dp)) {
            T(m.title, size = 30.sp, modifier = Modifier.padding(bottom = 12.dp))
            m.actions.forEachIndexed { i, (label, f) ->
                RowButton(label, null, focusRequester = if (i == 0) first else null, onClick = { f(); onClose() })
            }
            RowButton("とじる", null, onClick = onClose)
        }
    }
}
