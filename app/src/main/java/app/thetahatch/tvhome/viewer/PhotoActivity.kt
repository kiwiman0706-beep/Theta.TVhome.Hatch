package app.thetahatch.tvhome.viewer

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import app.thetahatch.tvhome.nas.Kind
import app.thetahatch.tvhome.video.VideoActivity
import kotlin.concurrent.thread

/**
 * しゃしん：フォルダの写真をスライドショーで（7 秒ごと）。ホームと別のプロセス（":viewer"）。
 * リモコン：←→＝まえ・つぎ、決定＝とめる／うごかす（動画なら再生）、戻る＝おわる。
 */
class PhotoActivity : Activity() {
    private lateinit var image: ImageView
    private lateinit var label: TextView
    private val main = Handler(Looper.getMainLooper())
    private lateinit var urls: List<String>
    private lateinit var names: List<String>
    private var i = 0
    private var playing = true
    private val next = Runnable { go(1) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        urls = intent.getStringArrayListExtra(EXTRA_URLS) ?: return finish()
        names = intent.getStringArrayListExtra(EXTRA_NAMES) ?: urls
        i = intent.getIntExtra(EXTRA_START, 0).coerceIn(0, (urls.size - 1).coerceAtLeast(0))
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        image = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
        label = TextView(this).apply { setTextColor(Color.WHITE); textSize = 16f; setShadowLayer(4f, 0f, 0f, Color.BLACK); setPadding(24, 12, 24, 12) }
        root.addView(image, FrameLayout.LayoutParams(-1, -1))
        root.addView(label, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.START))
        setContentView(root)
        show()
    }

    private fun isVideo(n: String) = n.substringAfterLast('.').lowercase() in setOf("mp4", "mov", "m4v", "3gp")

    private fun show() {
        main.removeCallbacks(next)
        val n = i
        val name = names[n]
        label.text = "${n + 1} / ${urls.size}  ${Kind.PHOTO.title(name)}" + if (isVideo(name)) "  ▶ けっていで さいせい" else "" + if (!playing) "  ⏸" else ""
        val w = resources.displayMetrics.widthPixels
        val h = resources.displayMetrics.heightPixels
        if (isVideo(name)) { image.setImageDrawable(null); if (playing) main.postDelayed(next, 4000); return }
        thread {
            val bmp: Bitmap? = runCatching { Pictures.decode(Pictures.fetch(urls[n]), w, h) }.getOrNull()
            main.post {
                if (n != i) return@post
                (image.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap?.let { old -> image.setImageDrawable(null); if (!old.isRecycled) old.recycle() }
                if (bmp != null) image.setImageBitmap(bmp) else label.text = "${n + 1} / ${urls.size}  読めません：$name"
                image.alpha = 0f; image.animate().alpha(1f).setDuration(600).start()
                if (playing) main.postDelayed(next, 7000)
            }
        }
    }

    private fun go(d: Int) { if (urls.isNotEmpty()) { i = (i + d + urls.size) % urls.size; show() } }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_NEXT -> go(1)
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_PREVIOUS -> go(-1)
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                if (isVideo(names[i])) startActivity(Intent(this, VideoActivity::class.java).putExtra(VideoActivity.EXTRA_URL, urls[i]).putExtra(VideoActivity.EXTRA_TITLE, names[i]))
                else { playing = !playing; show() }
            }
            else -> return super.onKeyDown(keyCode, event)
        }
        return true
    }

    override fun onDestroy() { main.removeCallbacksAndMessages(null); super.onDestroy() }

    companion object {
        const val EXTRA_URLS = "urls"
        const val EXTRA_NAMES = "names"
        const val EXTRA_START = "start"
    }
}
