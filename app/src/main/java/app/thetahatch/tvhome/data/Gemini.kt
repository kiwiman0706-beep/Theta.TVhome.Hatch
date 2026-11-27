package app.thetahatch.tvhome.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Gemini に聞く（利用者自身の API キー。シリーズの決まり）。
 * - search：Google 検索で調べて答える（出典のリンクつき）
 * - maps：Google マップで調べて答える（場所の一覧つき。天気の場所を「近く」に使う）
 * 会話は turns に積んで、続けて聞ける。
 */
object Gemini {
    data class Source(val title: String, val uri: String, val place: Boolean)
    data class Answer(val text: String, val sources: List<Source>)
    data class Turn(val user: Boolean, val text: String)

    /** 声で頼まれた「予定を入れる」「やることに足す」。実行の前に必ず確認の画面を出す。 */
    data class Action(val kind: String, val title: String, val date: LocalDate?, val start: LocalTime?, val end: LocalTime?, val location: String = "", val note: String = "") {
        val task get() = kind == TASK
        fun describe(): String {
            val d = date?.format(DateTimeFormatter.ofPattern("M/d（E）", Locale.JAPAN))
            val t = start?.let { s -> " " + s.format(HM) + (end?.let { "〜" + it.format(HM) } ?: "") } ?: if (task) " まで" else "（しゅうじつ）"
            return (if (task) "やること：" else "よてい：") + title + (d?.let { "\n" + it + t } ?: "") + (if (location.isNotBlank()) "\n📍 $location" else "") + (if (note.isNotBlank()) "\n$note" else "")
        }
    }
    /** ask の答え。action があれば確認してから実行する。 */
    data class Plan(val action: Action?, val text: String)

    const val EVENT = "add_event"
    const val TASK = "add_task"
    private val HM = DateTimeFormatter.ofPattern("H:mm")
    private val ACT = Regex("予定|よてい|スケジュール|カレンダー|タスク|やること|ToDo|todo|TODO|リマインド|わすれない|忘れない")
    private val DO = Regex("入れ|いれ|追加|ついか|登録|とうろく|足し|たし|作っ|つくっ|書い|かい|メモ|おいて|して|しといて|おねがい|お願い")

    /** 「予定・タスクを足して」らしい頼み方か（そのときだけ関数つきで聞く。調べ物は今まで通り検索つき）。 */
    fun wantsAction(q: String) = ACT.containsMatchIn(q) && DO.containsMatchIn(q)

    /** 予定・やることを足すための関数つきで聞く。関数を呼ばなければ text だけ（聞き返しなど）。 */
    fun plan(key: String, model: String, turns: List<Turn>, now: ZonedDateTime): Plan {
        if (key.isBlank()) throw IOException("Gemini の API キーが未設定です（せってい → Gemini）")
        val today = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd（E） HH:mm", Locale.JAPAN))
        fun prop(type: String, d: String) = JSONObject().put("type", type).put("description", d)
        val event = JSONObject().put("name", EVENT).put("description", "家族の Google カレンダーに予定を 1 件入れる")
            .put("parameters", JSONObject().put("type", "object").put("required", JSONArray().put("title").put("date"))
                .put("properties", JSONObject()
                    .put("title", prop("string", "予定の名前（短く）"))
                    .put("date", prop("string", "日付 YYYY-MM-DD"))
                    .put("start", prop("string", "始まる時刻 HH:MM（24 時間）。終日なら省く"))
                    .put("end", prop("string", "終わる時刻 HH:MM。分からなければ省く"))
                    .put("location", prop("string", "場所（あれば）"))))
        val task = JSONObject().put("name", TASK).put("description", "Google ToDo リスト（Tasks）にやることを 1 件足す")
            .put("parameters", JSONObject().put("type", "object").put("required", JSONArray().put("title"))
                .put("properties", JSONObject()
                    .put("title", prop("string", "やること（短く）"))
                    .put("due", prop("string", "期限 YYYY-MM-DD（あれば）"))
                    .put("note", prop("string", "メモ（あれば）"))))
        val body = JSONObject()
            .put("contents", contents(turns))
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text",
                "日本語で答える。今は $today（${now.zone.id}）。「明日」「来週の金曜」などはこれを基準に日付にする。" +
                "予定を入れる・やることを足すと頼まれたら、関数を 1 回だけ呼ぶ。日付や名前が分からないときは、関数を呼ばずに短く聞き返す。" +
                "マークダウンの記号は使わない。"))))
            .put("tools", JSONArray().put(JSONObject().put("functionDeclarations", JSONArray().put(event).put(task))))
        val o = post(key, model, body)
        val parts = o.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
        val call = (0 until (parts?.length() ?: 0)).firstNotNullOfOrNull { parts!!.getJSONObject(it).optJSONObject("functionCall") }
        return Plan(call?.let { action(it.optString("name"), it.optJSONObject("args") ?: JSONObject()) },
            if (call == null) runCatching { parse(o).text }.getOrDefault("") else "")
    }

    /** 関数の呼び出し（name・args）を Action に。日付が読めなければ null。 */
    fun action(name: String, a: JSONObject): Action? {
        fun time(k: String) = a.optString(k).takeIf { it.isNotBlank() }?.let { runCatching { LocalTime.parse(it.padStart(5, '0')) }.getOrNull() }
        val title = a.optString("title").trim().ifEmpty { return null }
        return when (name) {
            EVENT -> {
                val date = runCatching { LocalDate.parse(a.optString("date")) }.getOrNull() ?: return null
                val st = time("start")
                Action(EVENT, title, date, st, time("end")?.takeIf { st != null && it != st }, a.optString("location").trim())
            }
            TASK -> Action(TASK, title, runCatching { LocalDate.parse(a.optString("due")) }.getOrNull(), null, null, note = a.optString("note").trim())
            else -> null
        }
    }

    private fun contents(turns: List<Turn>) = JSONArray().also { c ->
        turns.forEach { t -> c.put(JSONObject().put("role", if (t.user) "user" else "model").put("parts", JSONArray().put(JSONObject().put("text", t.text)))) }
    }

    fun ask(key: String, model: String, turns: List<Turn>, mode: String, place: Place?, kids: Boolean): Answer {
        if (key.isBlank()) throw IOException("Gemini の API キーが未設定です（せってい → Gemini）")
        val contents = contents(turns)
        val style = if (kids) "子どもに話すように、やさしく、ひらがなを多めに、短く（3〜5 行）答える。こわい話・大人向けの話はしない。"
            else "テレビの画面で読みやすいように、短く（5〜8 行）答える。"
        val body = JSONObject()
            .put("contents", contents)
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text",
                "日本語で答える。$style 箇条書きは「・」で。マークダウンの記号（** や # や表）は使わない。調べた内容に基づいて答える。"))))
            .put("tools", JSONArray().put(JSONObject().put(if (mode == "maps") "google_maps" else "google_search", JSONObject())))
        if (mode == "maps" && place != null) body.put("toolConfig", JSONObject().put("retrievalConfig", JSONObject()
            .put("latLng", JSONObject().put("latitude", place.lat).put("longitude", place.lon)).put("languageCode", "ja_JP")))
        return parse(post(key, model, body))
    }

    private fun post(key: String, model: String, body: JSONObject): JSONObject {
        val c = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent").openConnection() as HttpURLConnection
        c.connectTimeout = 15_000; c.readTimeout = 90_000
        c.requestMethod = "POST"; c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json")
        c.setRequestProperty("x-goog-api-key", key)
        c.outputStream.use { it.write(body.toString().toByteArray()) }
        val code = c.responseCode
        val text = (if (code / 100 == 2) c.inputStream else c.errorStream)?.use { String(it.readBytes(), Charsets.UTF_8) }.orEmpty()
        if (code / 100 != 2) {
            val msg = runCatching { JSONObject(text).getJSONObject("error").getString("message") }.getOrDefault("HTTP $code")
            throw IOException(when (code) { 400, 403 -> "Gemini：$msg（キー・モデルを確かめて）"; 429 -> "Gemini：使いすぎです。少し待ってね"; else -> "Gemini：$msg" })
        }
        return JSONObject(text)
    }

    fun parse(o: JSONObject): Answer {
        val cand = o.optJSONArray("candidates")?.optJSONObject(0) ?: throw IOException("答えがありません")
        val parts = cand.optJSONObject("content")?.optJSONArray("parts")
        val text = (0 until (parts?.length() ?: 0)).joinToString("") { parts!!.getJSONObject(it).optString("text") }
        val chunks = cand.optJSONObject("groundingMetadata")?.optJSONArray("groundingChunks")
        val sources = (0 until (chunks?.length() ?: 0)).mapNotNull { i ->
            val ch = chunks!!.getJSONObject(i)
            ch.optJSONObject("maps")?.let { Source(it.optString("title").removeSuffix(" - Google Maps"), it.optString("uri"), true) }
                ?: ch.optJSONObject("web")?.let { Source(it.optString("title"), it.optString("uri"), false) }
        }.filter { it.uri.isNotBlank() }.distinctBy { it.uri }
        return Answer(clean(text), sources)
    }

    /** マークダウンの記号を外す（念のため）。 */
    fun clean(s: String) = s.replace("**", "").replace(Regex("(?m)^#+\\s*"), "").replace(Regex("(?m)^\\s*[*-]\\s+"), "・").trim()
}
