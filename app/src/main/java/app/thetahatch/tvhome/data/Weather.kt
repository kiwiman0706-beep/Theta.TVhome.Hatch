package app.thetahatch.tvhome.data

import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale

data class Place(val name: String, val lat: Double, val lon: Double) {
    fun encode() = "%.4f,%.4f,%s".format(Locale.US, lat, lon, name)

    companion object {
        fun decode(s: String): Place? {
            val p = s.split(',', limit = 3)
            if (p.size < 2) return null
            val lat = p[0].trim().toDoubleOrNull() ?: return null
            val lon = p[1].trim().toDoubleOrNull() ?: return null
            return Place(p.getOrNull(2)?.trim().orEmpty(), lat, lon)
        }
    }
}

data class Day(val date: Long, val code: Int, val max: Double, val min: Double, val rain: Int)
data class Forecast(val temp: Double, val code: Int, val isDay: Boolean, val days: List<Day>, val at: Long)

/** 天気（Open-Meteo。キー不要）。送るのは緯度・経度（小数 2 桁）だけ。Home の Weather.kt を軽くしたもの。 */
object Weather {
    fun fetch(place: Place): Forecast {
        val lat = "%.2f".format(Locale.US, place.lat)
        val lon = "%.2f".format(Locale.US, place.lon)
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
            "&current=temperature_2m,weather_code,is_day" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max" +
            "&timezone=auto&forecast_days=4&timeformat=unixtime"
        return parse(Net.get(url), System.currentTimeMillis())
    }

    fun search(name: String): List<Place> {
        val o = JSONObject(Net.get("https://geocoding-api.open-meteo.com/v1/search?count=8&language=ja&name=" + URLEncoder.encode(name.trim(), "UTF-8")))
        val a = o.optJSONArray("results") ?: return emptyList()
        return (0 until a.length()).map { a.getJSONObject(it) }.map { r ->
            val label = listOfNotNull(r.optString("name").ifEmpty { null }, r.optString("admin1").ifEmpty { null }).distinct().joinToString(" / ")
            Place(label, r.getDouble("latitude"), r.getDouble("longitude"))
        }
    }

    fun parse(text: String, now: Long): Forecast {
        val o = JSONObject(text)
        val c = o.getJSONObject("current")
        val d = o.getJSONObject("daily")
        val t = d.getJSONArray("time")
        val days = (0 until t.length()).map { i ->
            Day(
                t.getLong(i) * 1000, d.getJSONArray("weather_code").optInt(i),
                d.getJSONArray("temperature_2m_max").optDouble(i), d.getJSONArray("temperature_2m_min").optDouble(i),
                d.getJSONArray("precipitation_probability_max").optInt(i),
            )
        }
        return Forecast(c.optDouble("temperature_2m"), c.optInt("weather_code"), c.optInt("is_day", 1) == 1, days, now)
    }

    /** WMO の天気コード → 絵と言葉（子どもにも分かるように）。 */
    fun describe(code: Int, day: Boolean = true): Pair<String, String> = when (code) {
        0 -> (if (day) "☀" else "☾") to "はれ"
        1, 2 -> (if (day) "🌤" else "☾") to "はれ・くもり"
        3 -> "☁" to "くもり"
        45, 48 -> "🌫" to "きり"
        in 51..57 -> "🌦" to "きりさめ"
        in 61..67, in 80..82 -> "☂" to "あめ"
        in 71..77, 85, 86 -> "❄" to "ゆき"
        in 95..99 -> "⚡" to "かみなり"
        else -> "・" to ""
    }
}
