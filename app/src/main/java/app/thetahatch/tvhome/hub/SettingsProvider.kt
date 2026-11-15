package app.thetahatch.tvhome.hub

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import app.thetahatch.tvhome.TvHomeApp
import app.thetahatch.tvhome.data.Store
import java.time.Instant

/**
 * 設定のハブ（THETA_SHARED_SETTINGS §4.0。返し方は Home 1.0.0 と同じ）。content://app.thetahatch.tvhome.theta/…
 * 権限 app.thetahatch.permission.SHARE（signature）なので、シリーズ共通の鍵で署名したアプリだけが読める。
 * - query /info → key・value・by・at
 * - query /secret/<キー> → key・value（gemini.key・nas.password・nas.deviceToken）
 * - update /info → 書き戻し（秘密は受け付けない）
 */
class SettingsProvider : ContentProvider() {
    private val store get() = (context!!.applicationContext as TvHomeApp).store

    override fun onCreate() = true

    private fun caller(): String {
        val pkg = callingPackage ?: return "?"
        Catalog.load(context!!)
        return Catalog.byPackage(pkg)?.id ?: pkg
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? {
        val seg = uri.pathSegments
        val s = store
        return when {
            seg.firstOrNull() == "info" -> MatrixCursor(arrayOf("key", "value", "by", "at")).apply {
                addRow(arrayOf("ui.skin", s.skin, s.prefs.getString("by.ui.skin", "tvhome"), s.prefs.getString("at.ui.skin", "")))
                s.extraKeys().filterNot { Store.looksSecret(it) }.forEach { k ->
                    addRow(arrayOf(k, s.get(k), s.prefs.getString("by.$k", ""), s.prefs.getString("at.$k", "")))
                }
            }
            seg.size == 2 && seg[0] == "secret" && seg[1] in SECRETS -> MatrixCursor(arrayOf("key", "value")).apply {
                if (s.hasSecret(seg[1])) {
                    addRow(arrayOf(seg[1], s.secret(seg[1])))
                    s.prefs.edit().putString("lent.${seg[1]}", "${caller()} ${Instant.now()}").apply()
                }
            }
            else -> null
        }
    }

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int {
        if (uri.pathSegments.firstOrNull() != "info" || values == null) return 0
        val by = caller()
        var n = 0
        for (k in values.keySet()) {
            if (Store.looksSecret(k)) continue
            val v = values.getAsString(k) ?: continue
            if (k == "ui.skin") store.skin = v else store.put(k, v)
            store.prefs.edit().putString("by.$k", by).putString("at.$k", Instant.now().toString()).apply()
            n++
        }
        if (n > 0) (context!!.applicationContext as TvHomeApp).settingsChanged()
        return n
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0

    companion object {
        val SECRETS = listOf("gemini.key", "nas.password", "nas.deviceToken")
    }
}
