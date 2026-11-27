package app.thetahatch.tvhome.data

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Google カレンダー（ふだんは読むだけ。きく で頼まれたときだけ予定・やることを書く。書く許可は初めて書くときに別に取る）。テレビに入っている Google アカウント（家族の共用アカウント）で 1 回許可すれば、あとは画面を出さずに読める。
 * Google Cloud の準備：シリーズのプロジェクトで Calendar API を有効に、テストユーザーに家族のアカウント、
 * Android の OAuth クライアント（app.thetahatch.tvhome＋共通の鍵の SHA-1）。docs/GOOGLE_ACCOUNT.md。
 * 出すカレンダーは、Google カレンダーの画面で「表示」にしているもの（calendarList の selected）。
 */
class GCal(private val context: Context) {
    class NeedConsent(val intent: PendingIntent) : IOException("Google の許可が要ります")

    @Volatile private var token: String? = null
    @Volatile private var tokenAt = 0L

    @Volatile private var writeToken: String? = null
    @Volatile private var writeAt = 0L

    private fun request(write: Boolean) = AuthorizationRequest.builder()
        .setRequestedScopes((if (write) listOf(SCOPE, SCOPE_EVENTS, SCOPE_TASKS) else listOf(SCOPE)).map { Scope(it) }).build()

    private suspend fun authorize(write: Boolean = false): AuthorizationResult = suspendCancellableCoroutine { cont ->
        Identity.getAuthorizationClient(context).authorize(request(write))
            .addOnSuccessListener { cont.resume(it) }
            .addOnFailureListener { cont.resumeWithException(it) }
    }

    /** 許可済みなら画面を出さずにトークン。まだなら NeedConsent（許可の画面を出す）。 */
    suspend fun token(): String {
        token?.takeIf { System.currentTimeMillis() - tokenAt < 45 * 60_000 }?.let { return it }
        val r = try { authorize() } catch (e: Exception) { throw IOException("Google の許可を取れませんでした（${e.message}）", e) }
        if (r.hasResolution()) throw NeedConsent(r.pendingIntent ?: throw IOException("許可の画面を出せません"))
        return accept(r)
    }

    /** 許可の画面から戻ってきたとき。 */
    fun acceptResult(data: Intent?): Boolean = runCatching { accept(Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data)) }.isSuccess

    private fun accept(r: AuthorizationResult): String {
        val t = r.accessToken ?: throw IOException("トークンを受け取れませんでした")
        token = t; tokenAt = System.currentTimeMillis()
        if (r.grantedScopes.contains(SCOPE_EVENTS) && r.grantedScopes.contains(SCOPE_TASKS)) { writeToken = t; writeAt = tokenAt }
        return t
    }

    /** 書き込み（予定を入れる・やることを足す）のトークン。初めては許可の画面（NeedConsent）。 */
    suspend fun writeToken(): String {
        writeToken?.takeIf { System.currentTimeMillis() - writeAt < 45 * 60_000 }?.let { return it }
        val r = try { authorize(write = true) } catch (e: Exception) { throw IOException("Google の許可を取れませんでした（${e.message}）", e) }
        if (r.hasResolution()) throw NeedConsent(r.pendingIntent ?: throw IOException("許可の画面を出せません"))
        accept(r)
        return writeToken ?: throw IOException("予定・やることを書く許可がありません")
    }

    /** 予定を 1 件入れる（家族のアカウントのメインのカレンダー）か、やることを 1 件足す（いつもの ToDo リスト）。 */
    suspend fun add(a: Gemini.Action, zone: ZoneId) {
        val t = writeToken()
        if (a.task) send(t, "$TASKS/lists/@default/tasks", taskBody(a))
        else send(t, "$API/calendars/primary/events", eventBody(a, zone))
    }

    private fun send(t: String, url: String, body: JSONObject) {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000; c.readTimeout = 20_000
        c.requestMethod = "POST"; c.doOutput = true
        c.setRequestProperty("Authorization", "Bearer $t")
        c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        c.outputStream.use { it.write(body.toString().toByteArray()) }
        val code = c.responseCode
        if (code == 401) { runCatching { GoogleAuthUtil.clearToken(context, t) }; writeToken = null; token = null; throw IOException("Google の許可が切れました。もう一度ためしてね") }
        if (code / 100 != 2) {
            val err = runCatching { c.errorStream?.use { String(it.readBytes()) } }.getOrNull().orEmpty()
            val msg = runCatching { JSONObject(err).getJSONObject("error").getString("message") }.getOrDefault("HTTP $code")
            throw IOException(if (msg.contains("has not been used", true) || msg.contains("disabled", true))
                "Google Cloud で ${if (url.startsWith(TASKS)) "Tasks" else "Calendar"} API を有効にしてください" else "Google：$msg")
        }
    }

    /** from から days 日の予定（表示にしているカレンダーぜんぶ）。 */
    suspend fun events(from: ZonedDateTime, days: Long, zone: ZoneId): List<Event> {
        val t = token()
        val list = JSONObject(get(t, "$API/users/me/calendarList?minAccessRole=reader&maxResults=100&fields=items(id,summary,backgroundColor,selected,hidden)"))
        val items = list.optJSONArray("items") ?: return emptyList()
        val cals = (0 until items.length()).map { items.getJSONObject(it) }.filter { !it.optBoolean("hidden") }
        val shown = cals.filter { it.optBoolean("selected") }.ifEmpty { cals }
        val min = from.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        val max = from.plusDays(days).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        val out = ArrayList<Event>()
        for (c in shown) {
            val id = URLEncoder.encode(c.getString("id"), "UTF-8")
            val color = c.optString("backgroundColor")
            var page: String? = null
            do {
                val url = "$API/calendars/$id/events?singleEvents=true&orderBy=startTime&maxResults=2500" +
                    "&timeMin=" + URLEncoder.encode(min, "UTF-8") + "&timeMax=" + URLEncoder.encode(max, "UTF-8") +
                    "&fields=nextPageToken,items(status,summary,location,start,end)" + (page?.let { "&pageToken=$it" } ?: "")
                val o = runCatching { JSONObject(get(t, url)) }.getOrNull() ?: break
                o.optJSONArray("items")?.let { a -> (0 until a.length()).mapNotNull { parse(a.getJSONObject(it), zone, color) }.forEach { out += it } }
                page = o.optString("nextPageToken").ifEmpty { null }
            } while (page != null)
        }
        return out.distinctBy { it.title to it.start }.sortedBy { it.start }
    }

    private fun get(t: String, url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 20_000
        c.setRequestProperty("Authorization", "Bearer $t")
        c.setRequestProperty("User-Agent", Net.UA)
        val code = c.responseCode
        if (code == 401) {
            runCatching { GoogleAuthUtil.clearToken(context, t) }
            token = null
            throw IOException("Google の許可が切れました。もう一度読み込みます")
        }
        if (code / 100 != 2) {
            val body = runCatching { c.errorStream?.use { String(it.readBytes()) } }.getOrNull().orEmpty()
            val msg = runCatching { JSONObject(body).getJSONObject("error").getString("message") }.getOrDefault("HTTP $code")
            throw IOException(if (msg.contains("has not been used", true) || msg.contains("disabled", true)) "Google Cloud で Calendar API を有効にしてください" else "カレンダー：$msg")
        }
        return c.inputStream.use { String(it.readBytes(), Charsets.UTF_8) }
    }

    companion object {
        const val SCOPE = "https://www.googleapis.com/auth/calendar.readonly"
        const val SCOPE_EVENTS = "https://www.googleapis.com/auth/calendar.events"
        const val SCOPE_TASKS = "https://www.googleapis.com/auth/tasks"
        private const val API = "https://www.googleapis.com/calendar/v3"
        private const val TASKS = "https://tasks.googleapis.com/tasks/v1"

        fun eventBody(a: Gemini.Action, zone: ZoneId): JSONObject {
            val d = a.date ?: throw IOException("日付がありません")
            val o = JSONObject().put("summary", a.title)
            if (a.location.isNotBlank()) o.put("location", a.location)
            if (a.start == null) {
                o.put("start", JSONObject().put("date", d.toString())).put("end", JSONObject().put("date", d.plusDays(1).toString()))
            } else {
                val f = DateTimeFormatter.ISO_LOCAL_DATE_TIME
                val end = a.end ?: a.start.plusHours(1)
                val endDay = if (end.isAfter(a.start)) d else d.plusDays(1)
                o.put("start", JSONObject().put("dateTime", d.atTime(a.start).format(f)).put("timeZone", zone.id))
                    .put("end", JSONObject().put("dateTime", endDay.atTime(end).format(f)).put("timeZone", zone.id))
            }
            return o
        }

        /** Tasks の due は日付だけが使われる（時刻は捨てられる）ので、その日の 0 時 UTC。 */
        fun taskBody(a: Gemini.Action) = JSONObject().put("title", a.title).also { o ->
            if (a.note.isNotBlank()) o.put("notes", a.note)
            a.date?.let { o.put("due", it.toString() + "T00:00:00.000Z") }
        }

        /** 予定 1 件（Calendar API の events の 1 つ）。中止の予定は null。 */
        fun parse(o: JSONObject, zone: ZoneId, color: String = ""): Event? {
            if (o.optString("status") == "cancelled") return null
            val s = o.optJSONObject("start") ?: return null
            val e = o.optJSONObject("end")
            fun time(x: JSONObject?): ZonedDateTime? = when {
                x == null -> null
                x.has("dateTime") -> OffsetDateTime.parse(x.getString("dateTime")).atZoneSameInstant(zone)
                x.has("date") -> LocalDate.parse(x.getString("date")).atStartOfDay(zone)
                else -> null
            }
            val start = time(s) ?: return null
            return Event(o.optString("summary").ifBlank { "（題なし）" }, start, time(e), s.has("date") && !s.has("dateTime"), o.optString("location"), color)
        }
    }
}
