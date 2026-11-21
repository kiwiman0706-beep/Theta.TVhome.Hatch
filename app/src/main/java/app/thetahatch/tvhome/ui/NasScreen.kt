package app.thetahatch.tvhome.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
 * NAS のライブラリ（おんがく・えいが・ほん・しゃしん）をフォルダでたどる。決定でフォルダの中へ、戻るで 1 つ上。
 * - おんがく：そこから裏で再生（左上に曲名）
 * - えいが：mpv で再生（別プロセス）。見たところを覚えて、次は続きから
 * - ほん：PDF・漫画をページ送り（別プロセス）。読んだページを覚える
 * - しゃしん：フォルダの写真をスライドショー（別プロセス）
 */
@Composable
fun NasScreen(app: TvHomeApp, kind: Kind, startPath: String) {
    val st = app.state
    val p = LocalPalette.current
    val scope = rememberCoroutineScope()
    val root = st.root(kind)
    var stack by remember(kind, startPath) { mutableStateOf(listOf(startPath.ifBlank { root })) }
    var entries by remember { mutableStateOf<List<NasEntry>?>(null) }
    var error by remember { mutableStateOf<Throwable?>(null) }
    var tick by remember { mutableStateOf(0) }
    val first = remember { FocusRequester() }
    val path = stack.last()
    val ctx = androidx.compose.ui.platform.LocalContext.current

    // えいが・ほんから戻ったら、見たところを覚える
    val viewer = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { r ->
        val d = r.data ?: return@rememberLauncherForActivityResult
        val fp = d.getStringExtra("path") ?: return@rememberLauncherForActivityResult
        if (d.hasExtra(VideoActivity.EXTRA_POS)) app.store.setMark(fp, d.getDoubleExtra(VideoActivity.EXTRA_POS, 0.0))
        if (d.hasExtra(BookActivity.EXTRA_PAGE)) app.store.setMark(fp, d.getIntExtra(BookActivity.EXTRA_PAGE, 0).toDouble())
        tick++
    }

    LaunchedEffect(path) {
        entries = null; error = null
        runCatching { withContext(Dispatchers.IO) { st.dsm.list(path) } }
            .onSuccess { entries = it; delay(100); runCatching { first.requestFocus() } }
            .onFailure { error = it; st.nasProblem(it) }
    }
    androidx.activity.compose.BackHandler(enabled = stack.size > 1) { stack = stack.dropLast(1) }

    val all = entries.orEmpty()
    val list = kind.sort(all)
    val items = list.filter { !it.dir }
    val name = if (path == root) kind.label else kind.shelfName(path.trimEnd('/').substringAfterLast('/'))

    fun open(e: NasEntry) {
        app.store.setLast(kind.key, path)
        scope.launch {
            runCatching {
                when (kind) {
                    Kind.MUSIC -> st.music.playFolder(path, name, items, items.indexOf(e), MusicLib.cover(all))
                    Kind.VIDEO -> {
                        val url = withContext(Dispatchers.IO) { st.dsm.downloadUrl(e.path) }
                        viewer.launch(Intent(ctx, VideoActivity::class.java).putExtra(VideoActivity.EXTRA_URL, url).putExtra(VideoActivity.EXTRA_TITLE, kind.title(e.name))
                            .putExtra(VideoActivity.EXTRA_PATH, e.path).putExtra(VideoActivity.EXTRA_START, app.store.mark(e.path)))
                    }
                    Kind.BOOK -> {
                        val url = withContext(Dispatchers.IO) { st.dsm.downloadUrl(e.path) }
                        viewer.launch(Intent(ctx, BookActivity::class.java).putExtra(BookActivity.EXTRA_URL, url).putExtra(BookActivity.EXTRA_NAME, e.name)
                            .putExtra(BookActivity.EXTRA_PATH, e.path).putExtra(BookActivity.EXTRA_PAGE, app.store.mark(e.path).toInt()))
                    }
                    Kind.PHOTO -> {
                        val urls = withContext(Dispatchers.IO) { items.map { st.dsm.downloadUrl(it.path) } }
                        ctx.startActivity(Intent(ctx, PhotoActivity::class.java).putStringArrayListExtra(PhotoActivity.EXTRA_URLS, ArrayList(urls))
                            .putStringArrayListExtra(PhotoActivity.EXTRA_NAMES, ArrayList(items.map { it.name })).putExtra(PhotoActivity.EXTRA_START, items.indexOf(e).coerceAtLeast(0)))
                    }
                }
            }.onFailure { app.toast("ひらけません：${it.message}"); st.nasProblem(it) }
        }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 96.dp, vertical = 27.dp)) {
        item {
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(bottom = 12.dp)) {
                T(kind.icon, size = 34.sp)
                Spacer(Modifier.width(12.dp))
                T(name, size = 34.sp, font = headFont(), weight = FontWeight.Light)
                Spacer(Modifier.width(16.dp))
                T(path, size = 14.sp, color = p.muted, font = Mono)
            }
        }
        error?.let { e -> item { NasTrouble(app, e) { stack = stack.toList() } } }
        if (entries == null && error == null) item { T("よみこみ中…", size = 20.sp, color = p.muted) }
        if (items.isNotEmpty() && kind == Kind.MUSIC) {
            item { RowButton("▶ ぜんぶ さいせい（${items.size} きょく）", null, focusRequester = first, onClick = { open(items.first()) }) }
            item { RowButton("⏯ いちじていし／さいかい", null, onClick = { scope.launch { runCatching { st.music.toggle() } } }) }
        }
        if (items.isNotEmpty() && kind == Kind.PHOTO) item { RowButton("▶ スライドショー（${items.size} まい）", null, focusRequester = first, onClick = { open(items.first()) }) }
        itemsIndexed(list, key = { _, it -> it.path + tick }) { i, e ->
            val fr = if ((kind == Kind.VIDEO || kind == Kind.BOOK || items.isEmpty()) && i == 0) first else null
            if (e.dir) RowButton("📁  " + kind.shelfName(e.name), null, focusRequester = fr, onClick = { stack = stack + e.path })
            else {
                val mark = app.store.mark(e.path)
                val sub = when {
                    kind == Kind.VIDEO && mark > 0 -> "つづきから %d:%02d".format(mark.toInt() / 60, mark.toInt() % 60)
                    kind == Kind.BOOK && mark > 0 -> "${mark.toInt() + 1} ページから"
                    else -> e.name.substringAfterLast('.').uppercase()
                }
                RowButton(ITEM_ICON.getValue(kind) + "  " + kind.title(e.name), sub, focusRequester = fr, onClick = { open(e) })
            }
        }
        if (entries != null && list.isEmpty()) item { T("なにも ありません", size = 18.sp, color = p.muted) }
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
