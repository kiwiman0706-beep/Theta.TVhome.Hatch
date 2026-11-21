package app.thetahatch.tvhome.viewer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.os.Build
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer

/** 画面に合う大きさで絵を読む（大きな写真でもメモリを食わないように）。 */
object Pictures {
    fun fetch(url: String, timeoutMs: Int = 30_000): ByteArray {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000; c.readTimeout = timeoutMs
        if (c.responseCode != 200 || c.contentType?.startsWith("application/json") == true) throw IOException("読めません（HTTP ${c.responseCode}）")
        return c.inputStream.use { it.readBytes() }
    }

    /** ファイルに落とす（本。大きいので一度だけ）。同じ大きさのものがあれば使う。 */
    fun download(url: String, out: File, onProgress: (Float) -> Unit = {}): File {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000; c.readTimeout = 60_000
        if (c.responseCode != 200 || c.contentType?.startsWith("application/json") == true) throw IOException("読めません（HTTP ${c.responseCode}）")
        val total = c.contentLengthLong
        if (out.exists() && total > 0 && out.length() == total) { c.disconnect(); return out }
        val tmp = File(out.path + ".part")
        c.inputStream.use { input -> tmp.outputStream().use { o ->
            val buf = ByteArray(256 * 1024); var done = 0L
            while (true) { val n = input.read(buf); if (n < 0) break; o.write(buf, 0, n); done += n; if (total > 0) onProgress(done.toFloat() / total) }
        } }
        tmp.renameTo(out)
        return out
    }

    fun decode(bytes: ByteArray, maxW: Int, maxH: Int): Bitmap? {
        if (Build.VERSION.SDK_INT >= 28) {
            return runCatching {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { dec, info, _ ->
                    val s = maxOf(1f, info.size.width / maxW.toFloat(), info.size.height / maxH.toFloat())
                    dec.setTargetSize((info.size.width / s).toInt().coerceAtLeast(1), (info.size.height / s).toInt().coerceAtLeast(1))
                    dec.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            }.getOrNull()
        }
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
        var sample = 1
        while (o.outWidth / (sample * 2) >= maxW || o.outHeight / (sample * 2) >= maxH) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }
}
