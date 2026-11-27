package app.thetahatch.tvhome.ui

import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.thetahatch.tvhome.TvHomeApp
import app.thetahatch.tvhome.data.Gemini
import app.thetahatch.tvhome.data.Place
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import app.thetahatch.tvhome.data.GCal
import java.time.ZoneId
import java.time.ZonedDateTime
import java.net.URLEncoder
import java.util.Locale

/**
 * きく：Gemini に聞く（search＝Google 検索で調べる、maps＝Google マップで近くを調べる）、
 * image＝Google 画像検索をブラウザで開く。こえ（音声入力）か もじ（BT キーボード）で。続けて聞ける。答えは読み上げもできる。
 * 「あしたの 3じに はいしゃの よてい いれて」「ぎゅうにゅうを かう を やることに」→ 確認の画面 →「はい」で Google カレンダー・ToDo に入れる（キッズでは しない）。
 */
@Composable
fun AskScreen(app: TvHomeApp, mode: String, voiceFirst: Boolean) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val s = app.store
    var turns by remember(mode) { mutableStateOf<List<Gemini.Turn>>(emptyList()) }
    var sources by remember(mode) { mutableStateOf<List<Gemini.Source>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var asking by remember(mode) { mutableStateOf(!voiceFirst) }
    var pending by remember(mode) { mutableStateOf<Gemini.Action?>(null) }
    val consentFor = remember { mutableStateOf<Pair<Gemini.Action, android.app.PendingIntent>?>(null) }
    val yes = remember { FocusRequester() }
    val first = remember { FocusRequester() }
    val zone = remember { ZoneId.systemDefault() }

    fun doAdd(a: Gemini.Action, consented: Boolean = false): Unit = run {
        busy = true; error = null
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { app.state.gcal.add(a, zone) } }
                .onSuccess {
                    pending = null
                    turns = turns + Gemini.Turn(false, (if (a.task) "やることに いれました。\n" else "カレンダーに いれました。\n") + a.describe())
                    if (!a.task) app.state.refreshCalendar(force = true)
                }
                .onFailure { e ->
                    if (e is GCal.NeedConsent && !consented) consentFor.value = a to e.intent
                    else error = e.message ?: "いれられませんでした"
                }
            busy = false
            delay(100); runCatching { first.requestFocus() }
        }
    }
    val title = when (mode) { "maps" -> "ちずで さがす"; "image" -> "がぞうを さがす"; else -> "きく（Google で しらべる）" }

    val voiceIntent = remember { Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM).putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ja-JP") }
    val canVoice = remember { ctx.packageManager.resolveActivity(voiceIntent, 0) != null }
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(Unit) { onDispose { tts?.shutdown() } }

    fun send(q: String) {
        val question = q.trim()
        if (question.isEmpty()) return
        if (mode == "image") {
            val url = "https://www.google.com/search?tbm=isch&q=" + URLEncoder.encode(question, "UTF-8")
            if (!app.state.launch(Intent(Intent.ACTION_VIEW, Uri.parse(url)))) error = "ブラウザが ありません（テレビに ブラウザを 入れると 画像検索が 見られます）"
            return
        }
        val now = turns + Gemini.Turn(true, question)
        turns = now; busy = true; error = null; pending = null
        if (mode == "search" && Gemini.wantsAction(question)) {
            if (s.kidsMode) { busy = false; error = "よていや やることを いれるのは、おとなの がめんで してね"; return }
            scope.launch {
                runCatching { withContext(Dispatchers.IO) { Gemini.plan(s.secret("gemini.key"), s.get("gemini.model")?.ifBlank { null } ?: "gemini-2.5-flash", now.takeLast(10), ZonedDateTime.now(zone)) } }
                    .onSuccess { pl ->
                        if (pl.action != null) pending = pl.action
                        else turns = now + Gemini.Turn(false, pl.text.ifBlank { "ごめんね、よく わからなかった。もう いちど いってね" })
                    }
                    .onFailure { e -> error = e.message }
                busy = false
                delay(100); runCatching { (if (pending != null) yes else first).requestFocus() }
            }
            return
        }
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    Gemini.ask(s.secret("gemini.key"), s.get("gemini.model")?.ifBlank { null } ?: "gemini-2.5-flash", now.takeLast(10), mode, Place.decode(s.weatherPlace), s.kidsMode)
                }
            }.onSuccess { a -> turns = now + Gemini.Turn(false, a.text); sources = a.sources }
                .onFailure { e -> error = e.message; turns = now }
            busy = false
            delay(100); runCatching { first.requestFocus() }
        }
    }

    val voice = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { r ->
        r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { send(it) }
    }
    val consentLauncher = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult()) { r ->
        val a = consentFor.value?.first
        consentFor.value = null
        if (a != null && app.state.gcal.acceptResult(r.data)) doAdd(a, consented = true) else error = "Google の きょかが ありませんでした"
    }
    LaunchedEffect(consentFor.value) {
        consentFor.value?.let { (_, pi) ->
            runCatching { consentLauncher.launch(androidx.activity.result.IntentSenderRequest.Builder(pi.intentSender).build()) }
                .onFailure { consentFor.value = null; error = "きょかの がめんを だせません" }
        }
    }
    val voiceTick by app.state.voiceTick.collectAsState()
    LaunchedEffect(mode, voiceTick) {
        if (voiceFirst) { if (canVoice) runCatching { voice.launch(voiceIntent) } else asking = true }
        delay(150); runCatching { first.requestFocus() }
    }

    if (asking) {
        TextPrompt(title, "", false, { q -> asking = false; send(q) }, { asking = false })
        return
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 96.dp, vertical = 27.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
                T(when (mode) { "maps" -> "🗺"; "image" -> "🖼"; else -> "💬" }, size = 30.sp)
                Spacer(Modifier.width(10.dp))
                T(title, size = 30.sp, font = headFont(), weight = FontWeight.Light)
            }
        }
        items(turns) { t ->
            Column(Modifier.padding(vertical = 6.dp)) {
                T(if (t.user) "きいたこと" else "こたえ", size = 13.sp, color = if (t.user) p.muted else p.accent)
                T(t.text, size = if (t.user) 20.sp else 24.sp, weight = if (t.user) FontWeight.Normal else FontWeight.Medium, lines = 40)
            }
        }
        pending?.let { a ->
            item {
                Column(Modifier.padding(vertical = 8.dp)) {
                    T("これで いい？", size = 13.sp, color = p.accent)
                    T(a.describe(), size = 26.sp, weight = FontWeight.Medium, lines = 6)
                    Spacer(Modifier.padding(4.dp))
                    RowButton(if (a.task) "✅ はい、やることに いれる" else "✅ はい、カレンダーに いれる", null, focusRequester = yes, onClick = { if (!busy) doAdd(a) })
                    RowButton("✖ やめる", null, onClick = { pending = null; turns = turns + Gemini.Turn(false, "やめました") })
                }
            }
        }
        if (busy) item { T(if (pending != null) "いれています…" else "しらべています…", size = 20.sp, color = p.accent) }
        error?.let { e -> item { T(e, size = 18.sp, color = p.accent, lines = 4) } }
        item {
            val again = if (turns.isEmpty()) "" else "つづけて "
            Column(Modifier.padding(top = 8.dp)) {
                if (canVoice) RowButton("🎤 ${again}こえで きく", null, focusRequester = first, onClick = { runCatching { voice.launch(voiceIntent) } })
                RowButton("⌨ ${again}もじで きく", null, focusRequester = if (canVoice) null else first, onClick = { asking = true })
                val last = turns.lastOrNull { !it.user }
                if (last != null) RowButton("🔊 よみあげる", null, onClick = {
                    val t = tts
                    if (t == null) tts = TextToSpeech(ctx) { st -> if (st == TextToSpeech.SUCCESS) { tts?.language = Locale.JAPAN; tts?.speak(last.text, TextToSpeech.QUEUE_FLUSH, null, "ans") } }
                    else t.speak(last.text, TextToSpeech.QUEUE_FLUSH, null, "ans")
                })
                if (turns.isNotEmpty()) RowButton("🧹 あたらしく きく", null, onClick = { turns = emptyList(); sources = emptyList(); error = null })
            }
        }
        if (sources.isNotEmpty()) {
            item { SectionTitle(if (sources.any { it.place }) "ばしょ" else "しらべた ところ") }
            items(sources) { src ->
                RowButton((if (src.place) "📍 " else "🔗 ") + src.title, null, onClick = {
                    if (!app.state.launch(Intent(Intent.ACTION_VIEW, Uri.parse(src.uri)))) app.toast("ひらける アプリが ありません")
                }, note = if (src.place) "Google マップで ひらく" else "ブラウザで ひらく")
            }
        }
    }
}
