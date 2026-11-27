package app.thetahatch.tvhome.link

import android.content.Context
import android.net.ConnectivityManager
import app.thetahatch.tvhome.data.Store
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.security.SecureRandom
import kotlin.concurrent.thread

/**
 * スマホで入れる（Theta Link の最初の形）：長い設定（NAS・Gemini のキーなど）を、スマホで貼り付けて送る。
 * - テレビが家の LAN の中だけで、小さな入力ページを開く（その場で決めたポート、1 回限りの合言葉つき）
 * - テレビに QR コード（http://<テレビの IP>:<ポート>/?t=<合言葉>）を出す。スマホで読んで、入れて「おくる」
 * - 受け取ったら閉じる。5 分で自動で閉じる。外のサーバーは通さない
 * - 秘密（パスワード・キー）は Store の暗号化に入れる。空のまま送れば変えない
 * 気をつけること：LAN の中は暗号化していない（HTTP）。家の Wi-Fi で使う
 */
object PhoneInput {
    data class Field(val key: String, val label: String, val secret: Boolean = false, val hint: String = "")
    data class Session(val url: String, val title: String, val fields: List<Field>, val until: Long, val done: List<String>? = null, val error: String? = null)

    private val _state = MutableStateFlow<Session?>(null)
    val state: StateFlow<Session?> = _state
    @Volatile private var server: ServerSocket? = null
    @Volatile private var token = ""

    fun start(context: Context, store: Store, title: String, fields: List<Field>, onSaved: (List<String>) -> Unit) {
        stop()
        val ip = lanIp(context) ?: run { _state.value = Session("", title, fields, 0, error = "テレビが LAN につながっていません"); return }
        val ss = ServerSocket()
        ss.reuseAddress = true
        ss.bind(InetSocketAddress(ip, 0))
        ss.soTimeout = 1000
        server = ss
        token = ByteArray(12).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        val until = System.currentTimeMillis() + 5 * 60_000L
        _state.value = Session("http://${ip.hostAddress}:${ss.localPort}/?t=$token", title, fields, until)
        thread(name = "phone-input", isDaemon = true) {
            while (server === ss && System.currentTimeMillis() < until) {
                val sock = runCatching { ss.accept() }.getOrNull() ?: continue
                runCatching { handle(sock, store, title, fields, onSaved) }
                runCatching { sock.close() }
            }
            if (server === ss) stop()
        }
    }

    fun stop() {
        runCatching { server?.close() }
        server = null
        if (_state.value?.done == null) _state.value = null
    }

    fun dismiss() { stop(); _state.value = null }

    private fun handle(sock: Socket, store: Store, title: String, fields: List<Field>, onSaved: (List<String>) -> Unit) {
        sock.soTimeout = 10_000
        val r = BufferedReader(InputStreamReader(sock.getInputStream(), Charsets.UTF_8))
        val line = r.readLine() ?: return
        val (method, target) = line.split(' ').let { (it.getOrNull(0) ?: "") to (it.getOrNull(1) ?: "/") }
        var length = 0
        while (true) {
            val h = r.readLine() ?: break
            if (h.isEmpty()) break
            if (h.lowercase().startsWith("content-length:")) length = h.substringAfter(':').trim().toIntOrNull() ?: 0
        }
        val query = parse(target.substringAfter('?', ""))
        if (query["t"] != token) return reply(sock, 403, page(title, "<p>この ページは つかえません。テレビの QR コードを もういちど よんでください。</p>"))
        if (method == "GET") return reply(sock, 200, page(title, form(fields, store)))
        if (method != "POST" || length > 64 * 1024) return reply(sock, 400, page(title, "<p>おくれませんでした</p>"))
        val buf = CharArray(length)
        var n = 0
        while (n < length) { val k = r.read(buf, n, length - n); if (k < 0) break; n += k }
        val form = parse(String(buf, 0, n))
        val saved = ArrayList<String>()
        for (f in fields) {
            val v = form[f.key]?.trim() ?: continue
            if (f.secret) { if (v.isNotEmpty()) { store.setSecret(f.key, v); saved += f.label } }
            else if (v != (store.get(f.key) ?: "")) { store.put(f.key, v); saved += f.label }
        }
        reply(sock, 200, page(title, "<p class=ok>テレビに おくりました ✓</p><p>${if (saved.isEmpty()) "かわった ところは ありません" else esc(saved.joinToString("・"))}</p><p>この ページは とじて だいじょうぶです。</p>"))
        _state.value = _state.value?.copy(done = saved)
        runCatching { server?.close() }
        server = null
        onSaved(saved)
    }

    private fun form(fields: List<Field>, store: Store) = buildString {
        append("<form method=post><p class=sub>テレビ（Theta TVhome Hatch）に おくります。空のまま おくった ところは かわりません。</p>")
        for (f in fields) {
            val cur = if (f.secret) "" else esc(store.get(f.key).orEmpty())
            val ph = if (f.secret) (if (store.hasSecret(f.key)) "設定済み（かえるときだけ）" else "未設定") else esc(f.hint)
            append("<label>${esc(f.label)}<input name=\"${esc(f.key)}\" ${if (f.secret) "type=password autocomplete=off" else "type=text autocapitalize=off"} value=\"$cur\" placeholder=\"$ph\"></label>")
        }
        append("<button>テレビに おくる</button></form>")
    }

    private fun page(title: String, body: String) = """<!doctype html><html lang=ja><meta charset=utf-8><meta name=viewport content="width=device-width,initial-scale=1"><title>${esc(title)}</title>
<style>body{font:16px system-ui,sans-serif;margin:0;padding:20px;background:#0c0e12;color:#eceff3}h1{font-size:20px}label{display:block;margin:14px 0;color:#8a93a0;font-size:14px}
input{display:block;width:100%;box-sizing:border-box;margin-top:6px;padding:12px;font-size:16px;border-radius:8px;border:1px solid #333;background:#161a21;color:#eceff3}
button{width:100%;padding:14px;margin-top:16px;font-size:17px;border:0;border-radius:10px;background:#ff8a1f;color:#1a0f05;font-weight:700}.sub{color:#8a93a0;font-size:13px}.ok{color:#3ddc84;font-size:20px}</style>
<h1>${esc(title)}</h1>$body</html>"""

    private fun reply(sock: Socket, code: Int, html: String) {
        val b = html.toByteArray(Charsets.UTF_8)
        val head = "HTTP/1.1 $code ${if (code == 200) "OK" else "Error"}\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${b.size}\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n"
        sock.getOutputStream().apply { write(head.toByteArray()); write(b); flush() }
    }

    fun parse(s: String): Map<String, String> = s.split('&').filter { '=' in it }.associate {
        URLDecoder.decode(it.substringBefore('='), "UTF-8") to URLDecoder.decode(it.substringAfter('='), "UTF-8")
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    /** テレビの LAN の IPv4（Wi-Fi か有線。VPN は使わない）。 */
    fun lanIp(context: Context): Inet4Address? {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return null
        return cm.allNetworks.asSequence().mapNotNull { n ->
            val caps = cm.getNetworkCapabilities(n) ?: return@mapNotNull null
            if (caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN)) return@mapNotNull null
            cm.getLinkProperties(n)?.linkAddresses?.map { it.address }?.filterIsInstance<Inet4Address>()?.firstOrNull { !it.isLoopbackAddress }
        }.firstOrNull()
    }
}
