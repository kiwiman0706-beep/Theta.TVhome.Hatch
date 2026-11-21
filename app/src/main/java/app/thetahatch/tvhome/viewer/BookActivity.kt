package app.thetahatch.tvhome.viewer

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile
import kotlin.concurrent.thread

/**
 * ほん：PDF と漫画（CBZ・ZIP の画像）をページ送りで。ホームと別のプロセス（":viewer"）。
 * 一度テレビの中に落としてから読む（次からは速い。古いものから消す）。EPUB はあとの版で。
 * リモコン：→・決定＝つぎ、←＝まえ、↑↓＝10 ページ、戻る＝おわる（読んだページを返す）。
 */
class BookActivity : Activity() {
    private lateinit var image: ImageView
    private lateinit var label: TextView
    private val main = Handler(Looper.getMainLooper())
    private var pdf: PdfRenderer? = null
    private var zip: ZipFile? = null
    private var pages: List<String> = emptyList()
    private var count = 0
    private var page = 0
    @Volatile private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra(EXTRA_URL) ?: return finish()
        val name = intent.getStringExtra(EXTRA_NAME).orEmpty()
        page = intent.getIntExtra(EXTRA_PAGE, 0)
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        image = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
        label = TextView(this).apply { setTextColor(Color.WHITE); textSize = 16f; setShadowLayer(4f, 0f, 0f, Color.BLACK); setPadding(24, 12, 24, 12) }
        root.addView(image, FrameLayout.LayoutParams(-1, -1))
        root.addView(label, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.END))
        setContentView(root)
        label.text = "よみこみ中… $name"

        val ext = name.substringAfterLast('.').lowercase()
        val dir = File(cacheDir, "books").apply { mkdirs() }
        val file = File(dir, sha(url.substringBefore("&_sid=")) + "." + ext)
        thread {
            runCatching {
                trim(dir, keep = file)
                Pictures.download(url, file) { p -> main.post { label.text = "よみこみ中 ${(p * 100).toInt()}%  $name" } }
                when (ext) {
                    "pdf" -> { pdf = PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)); count = pdf!!.pageCount }
                    "cbz", "zip" -> {
                        zip = ZipFile(file)
                        pages = zip!!.entries().asSequence().map { it.name }
                            .filter { n -> n.substringAfterLast('.').lowercase() in setOf("jpg", "jpeg", "png", "webp", "gif") && !n.startsWith("__MACOSX") }
                            .sortedWith(app.thetahatch.tvhome.music.MusicLib.natural).toList()
                        count = pages.size
                    }
                    else -> throw IllegalStateException("この形式（$ext）は、あとの版で読めるようにします")
                }
                page = page.coerceIn(0, (count - 1).coerceAtLeast(0))
            }.onSuccess { main.post { show() } }
                .onFailure { e -> main.post { label.text = "読めません：${e.message}" } }
        }
    }

    private fun show() {
        if (count == 0 || busy) return
        busy = true
        val w = resources.displayMetrics.widthPixels
        val h = resources.displayMetrics.heightPixels
        val n = page
        thread {
            val bmp = runCatching {
                pdf?.let { r ->
                    synchronized(r) {
                        r.openPage(n).use { p ->
                            val s = minOf(w / p.width.toFloat(), h / p.height.toFloat())
                            val b = Bitmap.createBitmap((p.width * s).toInt().coerceAtLeast(1), (p.height * s).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                            b.eraseColor(Color.WHITE)
                            p.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            b
                        }
                    }
                } ?: zip?.let { z -> z.getInputStream(z.getEntry(pages[n])).use { Pictures.decode(it.readBytes(), w, h) } }
            }.getOrNull()
            main.post {
                busy = false
                (image.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap?.let { old -> image.setImageDrawable(null); if (!old.isRecycled) old.recycle() }
                if (bmp != null) image.setImageBitmap(bmp)
                label.text = "${n + 1} / $count"
                label.visibility = View.VISIBLE
                if (n != page) show()
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val d = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT -> 1
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_MEDIA_PREVIOUS -> -1
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_UP -> 10
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_DOWN -> -10
            else -> return super.onKeyDown(keyCode, event)
        }
        if (count > 0) { page = (page + d).coerceIn(0, count - 1); show() }
        return true
    }

    override fun onBackPressed() {
        setResult(RESULT_OK, Intent().putExtra(EXTRA_PATH, intent.getStringExtra(EXTRA_PATH)).putExtra(EXTRA_PAGE, page))
        finish()
    }

    override fun onDestroy() {
        runCatching { pdf?.close() }; runCatching { zip?.close() }
        super.onDestroy()
    }

    /** 落とした本は 2GB まで。古いものから消す。 */
    private fun trim(dir: File, keep: File) {
        val files = dir.listFiles()?.filter { it != keep }?.sortedBy { it.lastModified() } ?: return
        var total = files.sumOf { it.length() }
        for (f in files) { if (total < 2L shl 30) break; total -= f.length(); f.delete() }
    }

    private fun sha(s: String) = MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }.take(16)

    companion object {
        const val EXTRA_URL = "url"
        const val EXTRA_NAME = "name"
        const val EXTRA_PATH = "path"
        const val EXTRA_PAGE = "page"
    }
}
