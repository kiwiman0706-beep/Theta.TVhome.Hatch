package app.thetahatch.tvhome.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

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

    fun ask(key: String, model: String, turns: List<Turn>, mode: String, place: Place?, kids: Boolean): Answer {
        if (key.isBlank()) throw IOException("Gemini の API キーが未設定です（せってい → Gemini）")
        val contents = JSONArray()
        turns.forEach { t -> contents.put(JSONObject().put("role", if (t.user) "user" else "model").put("parts", JSONArray().put(JSONObject().put("text", t.text)))) }
        val style = if (kids) "子どもに話すように、やさしく、ひらがなを多めに、短く（3〜5 行）答える。こわい話・大人向けの話はしない。"
            else "テレビの画面で読みやすいように、短く（5〜8 行）答える。"
        val body = JSONObject()
            .put("contents", contents)
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text",
                "日本語で答える。$style 箇条書きは「・」で。マークダウンの記号（** や # や表）は使わない。調べた内容に基づいて答える。"))))
            .put("tools", JSONArray().put(JSONObject().put(if (mode == "maps") "google_maps" else "google_search", JSONObject())))
        if (mode == "maps" && place != null) body.put("toolConfig", JSONObject().put("retrievalConfig", JSONObject()
            .put("latLng", JSONObject().put("latitude", place.lat).put("longitude", place.lon)).put("languageCode", "ja_JP")))
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
        return parse(JSONObject(text))
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
