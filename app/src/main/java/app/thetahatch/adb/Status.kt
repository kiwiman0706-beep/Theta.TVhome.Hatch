package app.thetahatch.adb

import app.thetahatch.tvhome.rescue.Rescue
import app.thetahatch.tvhome.rescue.RescueActivity
import app.thetahatch.tvhome.media.NowPlaying
import android.content.pm.PackageManager
import android.os.Build
import app.thetahatch.tvhome.TvHomeApp
import app.thetahatch.tvhome.data.Entries
import app.thetahatch.tvhome.data.Store
import app.thetahatch.tvhome.hub.Catalog
import app.thetahatch.tvhome.hub.UpdateHub
import org.json.JSONArray
import org.json.JSONObject

/** dumpsys・ADB の命令で返す JSON。 */
object Status {
    fun status(app: TvHomeApp): JSONObject {
        val s = app.store
        val info = runCatching { app.packageManager.getPackageInfo(app.packageName, 0) }.getOrNull()
        @Suppress("DEPRECATION")
        val code = info?.let { if (Build.VERSION.SDK_INT >= 28) it.longVersionCode else it.versionCode.toLong() }
        val o = JSONObject()
            .put("app", "tvhome")
            .put("packageName", app.packageName)
            .put("versionName", info?.versionName)
            .put("versionCode", code)
            .put("skin", s.skin)
            .put("device", JSONObject().put("model", Build.MODEL).put("manufacturer", Build.MANUFACTURER).put("sdk", Build.VERSION.SDK_INT)
                .put("release", Build.VERSION.RELEASE).put("fingerprint", Build.FINGERPRINT))
            .put("defaultHome", UpdateHub.defaultHome(app))
            .put("stockHomes", JSONArray(Rescue.otherHomes(app).map { it.flattenToShortString() }))
            .put("rescue", JSONObject().put("recentTrouble", Rescue.recentTrouble(app)).put("last", Rescue.lastTrouble(app)).put("safeMode", app.safeMode))
            .put("nowPlaying", NowPlaying.state.value?.let { JSONObject().put("title", it.title).put("pkg", it.pkg) })
            .put("hub", JSONObject()
                .put("owner", UpdateHub.isOwner(app))
                .put("canInstall", app.packageManager.canRequestPackageInstalls())
                .put("autoUpdate", s.autoUpdate)
                .put("lastCheck", s.lastUpdateCheck))
        val settings = JSONObject()
            .put("ui.layout", s.layout)
            .put("kids.theme", s.kidTheme)
            .put("calendar.google", s.googleCalendar)
            .put("calendar.error", app.state.calendarError.value)
            .put("calendar.events", app.state.events.value.size)
            .put("weather.place", s.weatherPlace)
            .put("kids.mode", s.kidsMode)
            .put("kids.pin", s.hasPin())
            .put("apps.favorites", JSONArray(s.favorites))
            .put("apps.hidden", JSONArray(s.hidden.toList()))
        s.extraKeys().filterNot { Store.looksSecret(it) }.forEach { settings.put(it, s.get(it)) }
        Store.SECRETS.forEach { settings.put(it, s.hasSecret(it)) }
        o.put("settings", settings)
        o.put("entries", JSONArray().apply {
            app.state.entries.value.ifEmpty { Entries.all(app, s) }.forEach { e ->
                put(JSONObject().put("key", e.key).put("label", e.label).put("source", e.source).put("intent", e.intent?.toUri(android.content.Intent.URI_INTENT_SCHEME)))
            }
        })
        val v = UpdateHub.versionOf(app)
        o.put("hatch", JSONArray().apply {
            Catalog.android.forEach { a ->
                val pkg = a.installedPackage(v) ?: return@forEach
                val st = UpdateHub.get(a.id)
                put(JSONObject().put("id", a.id).put("packageName", pkg).put("installed", v(pkg)).put("latest", st.latest?.version)
                    .put("error", st.error).put("result", st.result))
            }
        })
        o.put("permissions", JSONArray().apply {
            if (!app.packageManager.canRequestPackageInstalls()) put("REQUEST_INSTALL_PACKAGES")
            if (Build.VERSION.SDK_INT >= 33 && app.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) put("POST_NOTIFICATIONS")
        })
        o.put("errors", JSONArray(Errors.recent()))
        return o
    }

    fun probe(app: TvHomeApp): JSONObject = Entries.probe(app)
}

/** 直近のエラー（10 件）。 */
object Errors {
    private val list = ArrayDeque<String>()
    fun add(msg: String) = synchronized(list) { list.addFirst("${java.time.Instant.now()} $msg"); while (list.size > 10) list.removeLast() }
    fun recent(): List<String> = synchronized(list) { list.toList() }
}
