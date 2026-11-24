package app.thetahatch.tvhome.nas

import app.thetahatch.tvhome.data.Net
import app.thetahatch.tvhome.data.Store
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder

open class NasException(msg: String) : IOException(msg)
class OtpRequired(msg: String) : NasException(msg)
/** NAS に届かない（Tailscale がつながっていない・家の外 など）。 */
class Unreachable(msg: String) : NasException(msg)

data class NasEntry(val path: String, val name: String, val dir: Boolean, val size: Long, val mtime: Long)

/**
 * NAS（Synology DSM）の File Station。Theta Movie Hatch の DsmSource を Kotlin にしたもの（ログインの手順も同じ）。
 * SYNO.API.Info で API の場所 → SYNO.API.Auth でログイン。2 段階認証は初回だけ確認コード、以後はデバイストークン（nas.deviceToken）。
 * 接続先は共通設定（nas.url・nas.user）、パスワード・トークンは秘密（暗号化）。TV ではこのアプリが設定のハブなので自分で持つ。
 */
class Dsm(private val store: Store) {
    @Volatile private var apis: JSONObject? = null
    @Volatile private var sid: String? = null
    @Volatile private var otp: String? = null
    @Volatile private var signature = ""

    val configured get() = base.isNotEmpty() && user.isNotEmpty() && store.hasSecret("nas.password")
    val base get() = normalize(store.get("nas.url").orEmpty())
    private val user get() = store.get("nas.user").orEmpty()

    /** 次のログインで 1 回だけ使う確認コード。 */
    fun setOtp(code: String?) { otp = code?.trim()?.ifEmpty { null }; if (otp != null) sid = null }

    fun reset() { sid = null; apis = null }

    private fun post(url: String, form: Map<String, String>, timeoutMs: Int = 20_000, tries: Int = 3): JSONObject {
        val body = form.entries.joinToString("&") { URLEncoder.encode(it.key, "UTF-8") + "=" + URLEncoder.encode(it.value, "UTF-8") }
        var last: Exception? = null
        for (i in 0 until tries) {
            try {
                val c = URL(url).openConnection() as HttpURLConnection
                c.connectTimeout = minOf(timeoutMs, 8000); c.readTimeout = timeoutMs
                c.requestMethod = "POST"; c.doOutput = true
                c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                c.setRequestProperty("User-Agent", Net.UA)
                c.outputStream.use { it.write(body.toByteArray()) }
                if (c.responseCode >= 500 && i + 1 < tries) { Thread.sleep(800L * (i + 1) * (i + 1)); continue }
                if (c.responseCode != 200) throw NasException("DSM HTTP ${c.responseCode}")
                val text = c.inputStream.use { String(it.readBytes(), Charsets.UTF_8) }
                return runCatching { JSONObject(text) }.getOrElse { throw NasException("DSM の応答ではありません") }
            } catch (e: NasException) { throw e } catch (e: Exception) {
                last = e
                if (i + 1 < tries) Thread.sleep(800L * (i + 1) * (i + 1))
            }
        }
        throw if (last is SocketTimeoutException || last is java.net.ConnectException || last is java.net.UnknownHostException || last is java.net.NoRouteToHostException)
            Unreachable("NAS に届きません（家の外なら Tailscale をつないでください）")
        else NasException("NAS につながりません：${last?.message}")
    }

    private fun api(name: String) = apis?.optJSONObject(name) ?: throw NasException("$name が使えません")
    private fun ver(name: String, want: Int): Int { val a = api(name); return want.coerceIn(a.optInt("minVersion", 1), a.optInt("maxVersion", want)) }
    private fun url(name: String) = "$base/webapi/${api(name).optString("path", "entry.cgi")}"

    @Synchronized
    fun ensureLogin() {
        val sig = "$base|$user"
        if (sid != null && sig == signature) return
        if (base.isEmpty()) throw NasException("NAS の URL が未設定です（せってい → NAS）")
        val q = mapOf("api" to "SYNO.API.Info", "version" to "1", "method" to "query", "query" to "all")
        val info = runCatching { post("$base/webapi/query.cgi", q, 8000, 2) }.getOrElse { if (it is Unreachable) throw it; post("$base/webapi/entry.cgi", q, 8000, 2) }
        apis = info.optJSONObject("data")
        if (apis?.has("SYNO.API.Auth") != true) throw NasException("DSM の応答ではありません")
        val pw = store.secret("nas.password")
        if (pw.isEmpty()) throw NasException("パスワードが未設定です（せってい → NAS）")
        val did = store.secret("nas.deviceToken")
        val code = otp
        val twoStep = code != null || did.isNotEmpty()
        val p = mutableMapOf(
            "api" to "SYNO.API.Auth", "version" to ver("SYNO.API.Auth", if (twoStep) 6 else 3).toString(), "method" to "login",
            "account" to user, "passwd" to pw, "session" to "FileStation", "format" to "sid",
        )
        if (code != null) { p["otp_code"] = code; p["enable_device_token"] = "yes"; p["device_name"] = "ThetaTVhomeHatch" }
        else if (did.isNotEmpty()) { p["device_id"] = did; p["device_name"] = "ThetaTVhomeHatch" }
        otp = null
        // ログインはやり直さない（失敗が続くと NAS が一時的に拒否するため）
        val r = post(url("SYNO.API.Auth"), p, tries = 1)
        if (!r.optBoolean("success")) {
            when (val e = r.optJSONObject("error")?.optInt("code") ?: 0) {
                403, 406 -> { store.setSecret("nas.deviceToken", ""); throw OtpRequired("2 段階認証の確認コードを入れてください（1 回入れれば次から要りません）") }
                404 -> throw OtpRequired("確認コードが違います")
                400 -> throw NasException("アカウントかパスワードが違います")
                401 -> throw NasException("アカウントが無効です")
                402 -> throw NasException("File Station を使う権限がありません")
                407 -> throw NasException("ログインの失敗が続いたため、NAS が一時的に拒否しています")
                else -> throw NasException("ログインエラー $e")
            }
        }
        val d = r.getJSONObject("data")
        sid = d.getString("sid")
        signature = sig
        val newDid = d.optString("did").ifEmpty { d.optString("device_id") }
        if (newDid.isNotEmpty() && newDid != did) store.setSecret("nas.deviceToken", newDid)
    }

    /** API を呼ぶ。セッション切れ（105〜107・119）なら 1 回だけログインし直す。 */
    fun call(api: String, method: String, params: Map<String, String>, version: Int = 2): JSONObject {
        for (attempt in 0..1) {
            ensureLogin()
            val p = params + mapOf("api" to api, "version" to ver(api, version).toString(), "method" to method, "_sid" to sid!!)
            val r = post(url(api), p, 60_000)
            if (r.optBoolean("success")) return r.optJSONObject("data") ?: JSONObject()
            val code = r.optJSONObject("error")?.optInt("code") ?: 0
            if (attempt == 0 && code in listOf(105, 106, 107, 119)) { sid = null; continue }
            if (code == 408) throw NasException("見つかりません：${params["folder_path"] ?: params["path"] ?: ""}")
            throw NasException("DSM エラー $code（$api.$method）")
        }
        throw NasException("DSM に入れません")
    }

    fun list(dir: String): List<NasEntry> {
        val out = ArrayList<NasEntry>()
        var offset = 0
        while (true) {
            val d = call("SYNO.FileStation.List", "list", mapOf(
                "folder_path" to dir, "offset" to "$offset", "limit" to "1000", "additional" to JSONArray(listOf("size", "time")).toString(),
            ))
            val files = d.optJSONArray("files") ?: JSONArray()
            for (i in 0 until files.length()) {
                val f = files.getJSONObject(i)
                val add = f.optJSONObject("additional")
                out += NasEntry(f.getString("path"), f.optString("name"), f.optBoolean("isdir"), add?.optLong("size") ?: 0,
                    (add?.optJSONObject("time")?.optLong("mtime") ?: 0) * 1000)
            }
            val total = d.optInt("total", out.size)
            if (files.length() == 0 || out.size >= total) break
            offset += 1000
        }
        return out
    }

    /** 再生・画像用の URL（セッション ID を含む。家の中・Tailscale の中だけで使う）。 */
    fun downloadUrl(path: String): String {
        ensureLogin()
        return url("SYNO.FileStation.Download") + "?api=SYNO.FileStation.Download&version=${ver("SYNO.FileStation.Download", 2)}" +
            "&method=download&mode=open&path=" + URLEncoder.encode(path, "UTF-8") + "&_sid=" + sid
    }

    /** 小さな絵（写真・ポスター・ジャケット）。NAS が作ってある縮小版（SYNO.FileStation.Thumb）。size：small・medium・large。 */
    fun thumbUrl(path: String, size: String = "medium"): String {
        ensureLogin()
        return url("SYNO.FileStation.Thumb") + "?api=SYNO.FileStation.Thumb&version=${ver("SYNO.FileStation.Thumb", 2)}" +
            "&method=get&size=$size&rotate_version=2&path=" + URLEncoder.encode(path, "UTF-8") + "&_sid=" + sid
    }

    companion object {
        /** 「100.1.2.3」のように方式を省いたら DSM の既定（http, 5000）を補う。 */
        fun normalize(url: String): String {
            var u = url.trim().trimEnd('/')
            if (u.isEmpty()) return u
            if (!u.startsWith("http://") && !u.startsWith("https://")) {
                val host = u.substringBefore('/')
                val rest = if ('/' in u) "/" + u.substringAfter('/') else ""
                u = "http://$host${if (':' in host) "" else ":5000"}$rest"
            }
            return u
        }
    }
}
