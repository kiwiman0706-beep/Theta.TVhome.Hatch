package app.thetahatch.tvhome.ui

import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.thetahatch.tvhome.TvHomeApp
import app.thetahatch.tvhome.data.NetStatus
import app.thetahatch.tvhome.music.MusicLib
import app.thetahatch.tvhome.nas.Kind
import app.thetahatch.tvhome.nas.Names
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import app.thetahatch.tvhome.viewer.Pictures
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.graphics.asImageBitmap
import android.content.Intent
import app.thetahatch.tvhome.video.VideoActivity
import app.thetahatch.tvhome.viewer.BookActivity
import app.thetahatch.tvhome.viewer.PhotoActivity
import app.thetahatch.tvhome.nas.NasEntry
import app.thetahatch.tvhome.nas.Unreachable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * NAS のライブラリ（えいが・おんがく・しゃしん・ほん）の一覧の画面。
 * - たどる：決定でフォルダの中へ、戻るで 1 つ上。えいが・おんがく・しゃしんは絵の並び（ポスター・ジャケット・写真）
 * - さいきん ついか：索引の新しい順（えいがは作品、ほかはフォルダ）
 * - さがす：名前で（もじ・こえ）。索引から
 * 開くと：おんがくは裏で再生、えいがは mpv（続きから）、ほんはページ送り（読んだページ）、しゃしんはスライドショー
 */
@Composable
fun NasScreen(app: TvHomeApp, view: app.thetahatch.tvhome.HomeState.NasView) {
    val st = app.state
    val kind = view.kind
    val p = LocalPalette.current
    val scope = rememberCoroutineScope()
    val root = st.root(kind)
    var stack by remember(view) { mutableStateOf(if (view.mode == "browse") listOf(view.path.ifBlank { root }) else emptyList()) }
    var mode by remember(view) { mutableStateOf(view.mode) }
    var query by remember(view) { mutableStateOf(view.query) }
    var entries by remember { mutableStateOf<List<NasEntry>?>(null) }
    var error by remember { mutableStateOf<Throwable?>(null) }
    var tick by remember { mutableStateOf(0) }
    var asking by remember { mutableStateOf(false) }
    val indexing by st.indexing.collectAsState()
    val first = remember { FocusRequester() }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val path = stack.lastOrNull()

    val viewer = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { r ->
        val d = r.data ?: return@rememberLauncherForActivityResult
        val fp = d.getStringExtra("path") ?: return@rememberLauncherForActivityResult
        if (d.hasExtra(VideoActivity.EXTRA_POS)) app.store.setMark(fp, d.getDoubleExtra(VideoActivity.EXTRA_POS, 0.0))
        if (d.hasExtra(BookActivity.EXTRA_PAGE)) app.store.setMark(fp, d.getIntExtra(BookActivity.EXTRA_PAGE, 0).toDouble())
        tick++
    }
    // こえで さがす（テレビに音声入力があれば）
    val voiceIntent = remember { Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM).putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "ja-JP") }
    val canVoice = remember { ctx.packageManager.resolveActivity(voiceIntent, 0) != null }
    val voice = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { r ->
        r.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { query = it; mode = "search"; stack = emptyList() }
    }

    LaunchedEffect(path, tick) {
        if (path == null) return@LaunchedEffect
        entries = null; error = null
        runCatching { withContext(Dispatchers.IO) { st.dsm.list(path) } }
            .onSuccess { entries = it; delay(100); runCatching { first.requestFocus() } }
            .onFailure { error = it; st.nasProblem(it) }
    }
    // さいきん・さがす：索引を読む（古ければ裏で作り直す）
    var indexVersion by remember { mutableStateOf(0) }
    LaunchedEffect(mode, kind) { if (mode != "browse") st.ensureIndex(kind) { indexVersion++ } }
    LaunchedEffect(mode, query, indexVersion) { if (mode != "browse") { delay(150); runCatching { first.requestFocus() } } }
    LaunchedEffect(mode) {
        if (mode == "search" && query.isBlank()) asking = true
        if (mode == "voice") { mode = "search"; if (canVoice) runCatching { voice.launch(voiceIntent) } else { app.toast("このテレビには こえの入力が ありません"); asking = true } }
    }

    androidx.activity.compose.BackHandler(enabled = stack.size > 1 || (stack.isNotEmpty() && mode != "browse")) {
        stack = stack.dropLast(1)
    }

    if (asking) {
        TextPrompt("${kind.label}を さがす（なまえ）", query, false, { q -> query = q.trim(); asking = false; stack = emptyList(); mode = "search" }, { asking = false })
        return
    }

    val all = entries.orEmpty()
    val list = if (path != null) kind.sort(all) else {
        val ix = st.indexes.getValue(kind)
        val found = if (mode == "recent") ix.recent() else ix.search(query)
        @Suppress("UNUSED_EXPRESSION") indexVersion
        found.map { NasEntry(it.path, it.name, it.dir, 0, it.mtime) }
    }
    val items = list.filter { !it.dir }
    val title = when {
        path == null && mode == "recent" -> "さいきん ついか"
        path == null -> "さがす：$query"
        path == root -> kind.label
        else -> label(kind, path.trimEnd('/').substringAfterLast('/'), true)
    }

    fun open(e: NasEntry) {
        val dir = e.path.substringBeforeLast('/')
        val siblings = if (path != null) items else listOf(e)
        app.store.setLast(kind.key, dir)
        scope.launch {
            runCatching {
                when (kind) {
                    Kind.MUSIC -> st.music.playFolder(dir, label(kind, dir.substringAfterLast('/'), true), siblings, siblings.indexOf(e).coerceAtLeast(0), if (path != null) MusicLib.cover(all) else null)
                    Kind.VIDEO -> {
                        val url = withContext(Dispatchers.IO) { st.dsm.downloadUrl(e.path) }
                        viewer.launch(Intent(ctx, VideoActivity::class.java).putExtra(VideoActivity.EXTRA_URL, url).putExtra(VideoActivity.EXTRA_TITLE, label(kind, e.name, false))
                            .putExtra(VideoActivity.EXTRA_PATH, e.path).putExtra(VideoActivity.EXTRA_START, app.store.mark(e.path)))
                    }
                    Kind.BOOK -> {
                        val url = withContext(Dispatchers.IO) { st.dsm.downloadUrl(e.path) }
                        viewer.launch(Intent(ctx, BookActivity::class.java).putExtra(BookActivity.EXTRA_URL, url).putExtra(BookActivity.EXTRA_NAME, e.name)
                            .putExtra(BookActivity.EXTRA_PATH, e.path).putExtra(BookActivity.EXTRA_PAGE, app.store.mark(e.path).toInt()))
                    }
                    Kind.PHOTO -> {
                        val urls = withContext(Dispatchers.IO) { siblings.map { st.dsm.downloadUrl(it.path) } }
                        ctx.startActivity(Intent(ctx, PhotoActivity::class.java).putStringArrayListExtra(PhotoActivity.EXTRA_URLS, ArrayList(urls))
                            .putStringArrayListExtra(PhotoActivity.EXTRA_NAMES, ArrayList(siblings.map { it.name })).putExtra(PhotoActivity.EXTRA_START, siblings.indexOf(e).coerceAtLeast(0)))
                    }
                }
            }.onFailure { app.toast("ひらけません：${it.message}"); st.nasProblem(it) }
        }
    }
    fun enter(e: NasEntry) { if (e.dir) stack = stack + e.path else open(e) }

    fun menuActions(): List<Pair<String, () -> Unit>> = buildList {
        add("🔍 さがす" to { asking = true })
        if (canVoice) add("🎤 こえで さがす" to { runCatching { voice.launch(voiceIntent) } })
        add("🆕 さいきん ついか" to { stack = emptyList(); mode = "recent" })
        if (mode != "browse" || stack.size > 1) add("📁 ぜんぶ（いちばん上）" to { mode = "browse"; stack = listOf(root) })
        if (mode != "browse") add("⟳ しらべなおす" to { st.ensureIndex(kind, force = true) { indexVersion++ } })
        if (kind == Kind.MUSIC && items.isNotEmpty() && path != null) add("▶ ぜんぶ さいせい" to { open(items.first()) })
        if (kind == Kind.MUSIC) add("⏯ いちじていし／さいかい" to { scope.launch { runCatching { st.music.toggle() } } })
        if (kind == Kind.PHOTO && items.isNotEmpty() && path != null) add("▶ スライドショー" to { open(items.first()) })
    }

    var menuOpen by remember { mutableStateOf(false) }
    if (menuOpen) { MenuOverlay(TileMenu(kind.label, menuActions())) { menuOpen = false }; return }
    val tiles = kind != Kind.BOOK
    val (tw, th) = when (kind) { Kind.VIDEO -> 132.dp to 196.dp; Kind.MUSIC -> 150.dp to 186.dp; else -> 176.dp to 150.dp }
    androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
        if (tiles) androidx.compose.foundation.lazy.grid.GridCells.Adaptive(tw) else androidx.compose.foundation.lazy.grid.GridCells.Fixed(1),
        Modifier.fillMaxSize().onPreviewKeyEvent { ev ->
            if (ev.key == androidx.compose.ui.input.key.Key.Menu && ev.type == androidx.compose.ui.input.key.KeyEventType.KeyUp) { menuOpen = true; true } else false
        },
        contentPadding = PaddingValues(horizontal = 64.dp, vertical = 24.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(if (tiles) 16.dp else 0.dp),
    ) {
        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
                T(kind.icon, size = 30.sp)
                Spacer(Modifier.width(10.dp))
                T(title, Modifier.weight(1f, fill = false), size = 30.sp, font = headFont(), weight = FontWeight.Light)
                Spacer(Modifier.width(16.dp))
                path?.let { T(it, size = 12.sp, color = p.muted, font = Mono) }
                indexing[kind]?.let { n -> Spacer(Modifier.width(12.dp)); T("しらべています… $n こ", size = 14.sp, color = p.accent) }
            }
        }
        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
            // 操作は見るだけの案内（上の段にフォーカスは移さない。ほかの操作はメニューボタン）
            T("メニュー：" + menuActions().joinToString("・") { it.first }, Modifier.padding(bottom = 8.dp), size = 12.sp, color = p.muted)
        }
        error?.let { e -> item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) { NasTrouble(app, e) { tick++ } } }
        if (path != null && entries == null && error == null) item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) { T("よみこみ中…", size = 18.sp, color = p.muted) }
        if (path == null && list.isEmpty()) item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
            T(if (indexing[kind] != null) "しらべおわるまで まってね" else if (mode == "search") "みつかりません" else "まだ ありません", size = 18.sp, color = p.muted)
        }
        itemsIndexed(list, key = { _, it -> it.path + tick }) { i, e ->
            val fr = if (i == 0) first else null
            val mark = if (e.dir) 0.0 else app.store.mark(e.path)
            val sub = when {
                kind == Kind.VIDEO && mark > 0 -> "つづきから %d:%02d".format(mark.toInt() / 60, mark.toInt() % 60)
                kind == Kind.BOOK && mark > 0 -> "${mark.toInt() + 1} ページから"
                path == null -> e.path.substringBeforeLast('/').substringAfterLast('/').let { kind.shelfName(it) }
                kind == Kind.VIDEO && e.dir -> Names.work(e.name).second
                else -> null
            }
            if (tiles) NasTile(app, kind, e, label(kind, e.name, e.dir), sub, tw, th, fr) { enter(e) }
            else RowButton((if (e.dir) "📁  " else "📖  ") + label(kind, e.name, e.dir), sub ?: if (e.dir) null else e.name.substringAfterLast('.').uppercase(), focusRequester = fr, onClick = { enter(e) })
        }
    }
}

/** 見せる名前：えいがは「題名 (年)」→ 題名、「Season 01」→「シーズン 1」、話は「3 話 サブタイトル」。 */
private fun label(kind: Kind, name: String, dir: Boolean): String = when {
    kind == Kind.VIDEO && dir -> Names.season(name) ?: Names.work(kind.shelfName(name)).first
    kind == Kind.VIDEO -> Names.episode(name)
    dir -> kind.shelfName(name)
    else -> kind.title(name)
}

/** 絵の並びの 1 つ：えいがの作品はポスター、おんがくのアルバムはジャケット、しゃしんは縮小版。無ければ絵文字。 */
@Composable
private fun NasTile(app: TvHomeApp, kind: Kind, e: NasEntry, title: String, sub: String?, w: androidx.compose.ui.unit.Dp, h: androidx.compose.ui.unit.Dp, fr: FocusRequester?, onClick: () -> Unit) {
    val p = LocalPalette.current
    val thumbPath = when {
        kind == Kind.VIDEO && e.dir && Names.season(e.name) == null -> "${e.path}/poster.jpg"
        kind == Kind.MUSIC && e.dir -> "${e.path}/cover.jpg"
        kind == Kind.PHOTO && !e.dir -> e.path
        else -> null
    }
    val img by androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, thumbPath) {
        value = thumbPath?.let { tp -> withContext(Dispatchers.IO) { Thumbs.get(app, tp) } }
    }
    Focusable(Modifier.width(w).height(h), onClick, focusRequester = fr) { focused ->
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().weight(1f).background(p.raised), contentAlignment = Alignment.Center) {
                img?.let { androidx.compose.foundation.Image(it, null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop) }
                    ?: T(if (e.dir) (if (Names.season(e.name) != null) "📂" else kind.icon) else ITEM_ICON.getValue(kind), size = 30.sp)
            }
            Column(Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
                T(title, size = 12.sp, weight = if (focused) FontWeight.SemiBold else FontWeight.Normal, lines = 2)
                sub?.let { T(it, size = 10.sp, color = p.accent) }
            }
        }
    }
}

/** 縮小版の絵（NAS の Thumb）を覚えておく（テレビの少ないメモリでも軽く）。 */
object Thumbs {
    private val cache = object : android.util.LruCache<String, androidx.compose.ui.graphics.ImageBitmap>(48) {}
    private val missing = java.util.Collections.synchronizedSet(HashSet<String>())
    fun get(app: TvHomeApp, path: String): androidx.compose.ui.graphics.ImageBitmap? {
        cache.get(path)?.let { return it }
        if (path in missing) return null
        val b = runCatching { Pictures.decode(Pictures.fetch(app.state.dsm.thumbUrl(path, "small"), 15_000), 320, 320) }.getOrNull()
        if (b == null) { missing += path; return null }
        return b.asImageBitmap().also { cache.put(path, it) }
    }
}

private val ITEM_ICON = mapOf(Kind.MUSIC to "♪", Kind.VIDEO to "▶", Kind.BOOK to "📖", Kind.PHOTO to "🖼")

/** NAS に届かないとき：Tailscale をつなぐ・入れる案内。2 段階認証ならせっていへ。 */
@Composable
fun NasTrouble(app: TvHomeApp, e: Throwable, retry: () -> Unit) {
    val p = LocalPalette.current
    val net by NetStatus.state.collectAsState()
    Column(Modifier.padding(vertical = 8.dp)) {
        T(e.message ?: "NAS につながりません", size = 20.sp, color = p.accent, lines = 2)
        if (e is Unreachable && !net.vpn) {
            val installed = runCatching { app.packageManager.getPackageInfo(TAILSCALE, 0) }.isSuccess
            if (installed) RowButton("Tailscale を ひらいて つなぐ", null, onClick = { app.state.launch(app.packageManager.getLeanbackLaunchIntentForPackage(TAILSCALE) ?: app.packageManager.getLaunchIntentForPackage(TAILSCALE)) },
                note = "家の外・別のネットワークのときは、Tailscale をつなぐと NAS に届きます")
            else RowButton("Tailscale を いれる（Google Play）", null, onClick = {
                if (!app.state.launch(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$TAILSCALE")))) app.state.launch(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$TAILSCALE")))
            }, note = "家の外から NAS を見るには Tailscale が要ります（Android TV 版あり）")
        }
        RowButton("もういちど", null, onClick = { app.state.dsm.reset(); retry() })
    }
}

const val TAILSCALE = "com.tailscale.ipn"
