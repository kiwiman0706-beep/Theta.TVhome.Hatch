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
import app.thetahatch.tvhome.nas.NasEntry
import app.thetahatch.tvhome.nas.Unreachable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * おんがく：NAS の音楽のフォルダをたどって鳴らす。フォルダを決定で中へ、曲を決定でそこから再生、戻るで 1 つ上。
 * 再生は裏（別プロセス）で続くので、ホームに戻っても鳴り続け、左上に曲名が出る。リモコンの再生・一時停止ボタンもそのまま効く。
 */
@Composable
fun MusicScreen(app: TvHomeApp, startPath: String, onExit: () -> Unit) {
    val st = app.state
    val p = LocalPalette.current
    val scope = rememberCoroutineScope()
    var stack by remember { mutableStateOf(listOf(startPath.ifBlank { st.musicRoot })) }
    var entries by remember { mutableStateOf<List<NasEntry>?>(null) }
    var error by remember { mutableStateOf<Throwable?>(null) }
    val first = remember { FocusRequester() }
    val path = stack.last()

    LaunchedEffect(path) {
        entries = null; error = null
        runCatching { withContext(Dispatchers.IO) { st.dsm.list(path) } }
            .onSuccess { entries = it; delay(100); runCatching { first.requestFocus() } }
            .onFailure { error = it; st.nasProblem(it) }
    }
    androidx.activity.compose.BackHandler(enabled = stack.size > 1) { stack = stack.dropLast(1) }

    val all = entries.orEmpty()
    val list = MusicLib.sort(all)
    val songs = list.filter { !it.dir }
    val cover = MusicLib.cover(all)
    val name = if (path == st.musicRoot) "おんがく" else MusicLib.shelfName(path.trimEnd('/').substringAfterLast('/'))
    fun play(start: Int) {
        app.store.musicLast = path
        scope.launch { runCatching { st.music.playFolder(path, name, songs, start, cover) }.onFailure { app.toast("再生できません：${it.message}") } }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 96.dp, vertical = 27.dp)) {
        item {
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(bottom = 12.dp)) {
                T("🎵", size = 34.sp)
                Spacer(Modifier.width(12.dp))
                T(name, size = 34.sp, font = headFont(), weight = FontWeight.Light)
                Spacer(Modifier.width(16.dp))
                T(path, size = 14.sp, color = p.muted, font = Mono)
            }
        }
        error?.let { e -> item { NasTrouble(app, e) { stack = stack.toList() } } }
        if (entries == null && error == null) item { T("よみこみ中…", size = 20.sp, color = p.muted) }
        if (songs.isNotEmpty()) {
            item { RowButton("▶ ぜんぶ さいせい（${songs.size} きょく）", null, focusRequester = first, onClick = { play(0) }) }
            item { RowButton("⏯ いちじていし／さいかい", null, onClick = { scope.launch { runCatching { st.music.toggle() } } }) }
        }
        itemsIndexed(list, key = { _, it -> it.path }) { i, e ->
            val fr = if (songs.isEmpty() && i == 0) first else null
            if (e.dir) RowButton("📁  " + MusicLib.shelfName(e.name), null, focusRequester = fr, onClick = { stack = stack + e.path })
            else RowButton("♪  " + MusicLib.title(e.name), e.name.substringAfterLast('.').uppercase(), focusRequester = fr, onClick = { play(songs.indexOf(e)) })
        }
        if (entries != null && list.isEmpty()) item { T("曲も フォルダも ありません", size = 18.sp, color = p.muted) }
    }
}

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
