package app.thetahatch.tvhome.data

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

object Net {
    const val UA = "ThetaTVhomeHatch"

    fun open(url: String, accept: String? = null): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 30_000
        c.instanceFollowRedirects = true
        c.setRequestProperty("User-Agent", UA)
        accept?.let { c.setRequestProperty("Accept", it) }
        if (c.responseCode !in 200..299) throw IOException("HTTP ${c.responseCode}")
        return c
    }

    fun get(url: String, accept: String? = null): String = open(url, accept).inputStream.bufferedReader().use { it.readText() }
}
