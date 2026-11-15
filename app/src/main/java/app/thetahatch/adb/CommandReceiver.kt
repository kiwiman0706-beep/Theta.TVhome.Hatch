package app.thetahatch.adb

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.thetahatch.tvhome.MainActivity
import app.thetahatch.tvhome.TvHomeApp
import app.thetahatch.tvhome.data.Entries
import app.thetahatch.tvhome.data.Place
import app.thetahatch.tvhome.data.Store
import app.thetahatch.tvhome.hub.Catalog
import app.thetahatch.tvhome.hub.UpdateHub
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File

/**
 * ADB の窓口：操作（THETA_ADB_SPEC §3.2）。権限 DUMP なので adb の shell からだけ。
 *   adb shell am broadcast -a app.thetahatch.adb.<命令> -n app.thetahatch.tvhome/app.thetahatch.adb.CommandReceiver [--es key 値 ...]
 * 結果コード 0＝成功、データは JSON。
 *
 * | 命令 | 引数 | すること |
 * | GET | key | 設定を 1 つ（秘密は有無だけ） |
 * | SET | key・value | 設定を変える（ui.skin・weather.place・kids.mode・apps.favorites（改行かカンマ区切り）・apps.hidden・entry.<入口>・label.<入口>・ほか） |
 * | SET_SECRET | key・file | 秘密を入れる。file は /sdcard/Android/data/app.thetahatch.tvhome/files/ の下（読んだら消す） |
 * | OPEN | screen（home・settings・updates・apps） | 画面を開く |
 * | LAUNCH | key（入口）か uri（Intent の URI） | 入口を試しに開く |
 * | PROBE | — | テレビの入口を調べる（dumpsys … probe と同じ） |
 * | CHECK_UPDATE | — | 新しい版を今すぐ確かめる（受け付けたら返す。結果は dumpsys） |
 * | UPDATE | packageName（省略で全部） | 更新を入れる |
 */
class CommandReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as TvHomeApp
        val s = app.store
        val cmd = intent.action?.removePrefix("app.thetahatch.adb.") ?: ""
        val key = intent.getStringExtra("key")
        val res = runCatching {
            when (cmd) {
                "GET" -> {
                    key ?: error("key がありません")
                    JSONObject().put("key", key).put("value", if (Store.looksSecret(key)) s.hasSecret(key) else get(app, key))
                }
                "SET" -> {
                    key ?: error("key がありません")
                    if (Store.looksSecret(key)) error("秘密は SET_SECRET で")
                    set(app, key, intent.getStringExtra("value") ?: "")
                    app.settingsChanged()
                    JSONObject().put("key", key).put("value", get(app, key))
                }
                "SET_SECRET" -> {
                    key ?: error("key がありません")
                    val path = intent.getStringExtra("file") ?: error("file がありません")
                    val f = File(path).canonicalFile
                    val base = app.getExternalFilesDir(null)?.canonicalFile ?: error("外部の保存場所がありません")
                    if (!f.path.startsWith(base.path)) error("file は ${base.path} の下に置いてください")
                    val v = f.readText().trim()
                    f.delete()
                    s.setSecret(key, v)
                    app.settingsChanged()
                    JSONObject().put("key", key).put("set", v.isNotEmpty())
                }
                "OPEN" -> {
                    app.startActivity(Intent(app, MainActivity::class.java).putExtra(MainActivity.EXTRA_SCREEN, intent.getStringExtra("screen") ?: "home")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
                    JSONObject().put("opened", intent.getStringExtra("screen") ?: "home")
                }
                "LAUNCH" -> {
                    val i = intent.getStringExtra("uri")?.let { Entries.parse(it) }
                        ?: Entries.all(app, s).firstOrNull { it.key == key }?.intent
                        ?: error("入口がありません")
                    app.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    JSONObject().put("launched", i.toUri(Intent.URI_INTENT_SCHEME))
                }
                "PROBE" -> Status.probe(app)
                "CHECK_UPDATE" -> {
                    app.scope.launch { runCatching { UpdateHub.check(app) }.onFailure { Errors.add("check: ${it.message}") } }
                    JSONObject().put("accepted", true)
                }
                "UPDATE" -> {
                    val pkg = intent.getStringExtra("packageName")
                    app.scope.launch {
                        runCatching {
                            UpdateHub.check(app)
                            if (pkg == null) UpdateHub.runAll(app, install = true, mobile = true, fromWorker = false)
                            else Catalog.byPackage(pkg)?.let { UpdateHub.update(app, it) }
                        }.onFailure { Errors.add("update: ${it.message}") }
                    }
                    JSONObject().put("accepted", true).put("packageName", pkg)
                }
                else -> null
            }
        }
        val out = res.getOrElse { JSONObject().put("error", it.message ?: it.toString()) }
        if (out == null) { resultCode = 1; resultData = JSONObject().put("error", "unknown command").toString(); return }
        resultCode = if (res.isSuccess) 0 else 1
        resultData = out.toString()
    }

    private fun get(app: TvHomeApp, key: String): Any? {
        val s = app.store
        return when {
            key == "ui.skin" -> s.skin
            key == "ui.layout" -> s.layout
            key == "kids.theme" -> s.kidTheme
            key == "weather.place" -> s.weatherPlace
            key == "kids.mode" -> s.kidsMode
            key == "apps.favorites" -> s.favorites.joinToString(",")
            key == "apps.hidden" -> s.hidden.joinToString(",")
            key == "update.auto" -> s.autoUpdate
            key.startsWith("entry.") -> s.entryUri(key.removePrefix("entry."))
            key.startsWith("label.") -> s.label(key.removePrefix("label."))
            else -> s.get(key)
        }
    }

    private fun set(app: TvHomeApp, key: String, value: String) {
        val s = app.store
        fun list(v: String) = v.split('\n', ',').map { it.trim() }.filter { it.isNotEmpty() }
        when {
            key == "ui.skin" -> s.skin = value
            key == "ui.layout" -> { if (value !in listOf("kids", "bar", "simple")) error("kids・bar・simple のどれか"); s.layout = value }
            key == "kids.theme" -> s.kidTheme = value
            key == "weather.place" -> { if (value.isNotBlank() && Place.decode(value) == null) error("\"緯度,経度,名前\" の形で"); s.weatherPlace = value }
            key == "kids.mode" -> s.kidsMode = value == "true" || value == "1"
            key == "kids.pin" -> s.setPin(value)
            key == "apps.favorites" -> s.favorites = list(value)
            key == "apps.hidden" -> s.hidden = list(value).toSet()
            key == "update.auto" -> s.autoUpdate = value == "true" || value == "1"
            key.startsWith("entry.") -> {
                if (value.isNotBlank() && Entries.parse(value) == null) error("Intent の URI として読めません")
                s.setEntryUri(key.removePrefix("entry."), value)
            }
            key.startsWith("label.") -> s.setLabel(key.removePrefix("label."), value)
            else -> s.put(key, value)
        }
    }
}
