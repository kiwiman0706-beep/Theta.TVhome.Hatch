package app.thetahatch.tvhome.data

import android.content.Context
import android.content.SharedPreferences
import java.security.MessageDigest

/**
 * 端末の中の設定。共通設定（ui.skin など）はハブとして他の Hatch アプリにも配る（hub/SettingsProvider）。
 * 秘密（*.password・calendar.ics など）は Secret で暗号化して持ち、書き出し・dumpsys には値を出さない。
 */
class Store(context: Context) {
    val prefs: SharedPreferences = context.getSharedPreferences("tvhome", Context.MODE_PRIVATE)

    /** TV では暗いスキンが既定（THETA_TV_SPEC §2）。 */
    var skin: String
        get() = prefs.getString("ui.skin", "dark") ?: "dark"
        set(v) = prefs.edit().putString("ui.skin", v).apply()

    /** ホームの形：bar（クロスバー＋計器盤、既定）・kids（キッズホーム風）・simple（棚）。 */
    var layout: String
        get() = prefs.getString("ui.layout", "bar") ?: "bar"
        set(v) = prefs.edit().putString("ui.layout", v).apply()

    /** キッズ風のときの色とマスコット（usagi・neko・kuma・dino・unicorn・umi・hoshi）。 */
    var kidTheme: String
        get() = prefs.getString("kids.theme", "usagi") ?: "usagi"
        set(v) = prefs.edit().putString("kids.theme", v).apply()

    var autoUpdate: Boolean
        get() = prefs.getBoolean("update.auto", true)
        set(v) = prefs.edit().putBoolean("update.auto", v).apply()

    var updateMobile: Boolean
        get() = prefs.getBoolean("update.mobile", false)
        set(v) = prefs.edit().putBoolean("update.mobile", v).apply()

    var lastUpdateCheck: Long
        get() = prefs.getLong("update.last", 0)
        set(v) = prefs.edit().putLong("update.last", v).apply()

    /** 天気の場所 "緯度,経度,名前"。空なら天気を出さない。 */
    var weatherPlace: String
        get() = prefs.getString("weather.place", "") ?: ""
        set(v) = prefs.edit().putString("weather.place", v).apply()

    /** すきなアプリ（パッケージ名、並び順どおり）。 */
    var favorites: List<String>
        get() = (prefs.getString("apps.favorites", "") ?: "").split('\n').filter { it.isNotBlank() }
        set(v) = prefs.edit().putString("apps.favorites", v.distinct().joinToString("\n")).apply()

    /** 一覧から隠すアプリ・入口。 */
    var hidden: Set<String>
        get() = prefs.getStringSet("apps.hidden", emptySet()) ?: emptySet()
        set(v) = prefs.edit().putStringSet("apps.hidden", v).apply()

    /** 子どもモード：「すべてのアプリ」と設定をおうちの人の番号で守る。 */
    var kidsMode: Boolean
        get() = prefs.getBoolean("kids.mode", false)
        set(v) = prefs.edit().putBoolean("kids.mode", v).apply()

    fun hasPin() = !prefs.getString("kids.pin", "").isNullOrEmpty()
    fun setPin(pin: String) = prefs.edit().putString("kids.pin", if (pin.isEmpty()) "" else hash(pin)).apply()
    fun checkPin(pin: String) = !hasPin() || prefs.getString("kids.pin", "") == hash(pin)
    private fun hash(s: String) = MessageDigest.getInstance("SHA-256").digest(("tvhome:" + s).toByteArray()).joinToString("") { "%02x".format(it) }

    /** 入口の Intent（URI 形式）を手で決めたもの。キーは Entries の key。 */
    fun entryUri(key: String): String? = prefs.getString("entry.$key", null)?.takeIf { it.isNotBlank() }
    fun setEntryUri(key: String, uri: String?) = prefs.edit().apply { if (uri.isNullOrBlank()) remove("entry.$key") else putString("entry.$key", uri) }.apply()

    /** 入口の名前を付け替えたもの（「HDMI 2」→「ゲーム」）。 */
    fun label(key: String): String? = prefs.getString("label.$key", null)?.takeIf { it.isNotBlank() }
    fun setLabel(key: String, v: String?) = prefs.edit().apply { if (v.isNullOrBlank()) remove("label.$key") else putString("label.$key", v) }.apply()

    // ---- 秘密 ----

    fun secret(key: String): String = Secret.open(prefs.getString("secret.$key", ""))
    fun hasSecret(key: String) = !prefs.getString("secret.$key", "").isNullOrEmpty()
    fun setSecret(key: String, value: String) = prefs.edit().putString("secret.$key", Secret.seal(value)).apply()

    // ---- そのほかの設定（ADB の SET・ハブの info） ----

    fun get(key: String): String? = prefs.all["x.$key"]?.toString()
    fun put(key: String, value: String) = prefs.edit().putString("x.$key", value).apply()
    fun extraKeys(): List<String> = prefs.all.keys.filter { it.startsWith("x.") }.map { it.removePrefix("x.") }

    companion object {
        /** 秘密として扱うキー（値を表に出さない）。 */
        val SECRETS = listOf("calendar.ics", "nas.password", "nas.deviceToken", "gemini.key", "switchbot.token", "switchbot.secret")

        fun looksSecret(key: String) = key in SECRETS ||
            listOf("key", "token", "secret", "password", ".ics").any { key.lowercase().contains(it) }
    }
}
