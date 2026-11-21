package app.thetahatch.tvhome.video

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import dev.jdtech.mpv.MPVLib

/**
 * えいが：mpv で再生する（Theta Movie Hatch と同じく、mkv・HEVC・DTS・二か国語の .ts も端末でそのまま）。
 * ホームと**別のプロセス**（":video"）。mpv が落ちてもホームは落ちない。
 *
 * リモコン：決定・再生ボタン＝一時停止／再生、←→＝10 秒もどる／30 秒すすむ、↑＝音声（二か国語）、↓＝字幕、戻る＝おわる。
 * 終わるとき、見たところ（秒）を結果で返す（続きから）。
 */
class VideoActivity : Activity() {
    private var mpv: MPVLib? = null
    private lateinit var info: TextView
    private lateinit var bar: ProgressBar
    private lateinit var panel: LinearLayout
    private val main = Handler(Looper.getMainLooper())
    private val hide = Runnable { panel.visibility = View.GONE }
    @Volatile private var pos = 0.0
    @Volatile private var dur = 0.0
    private var title = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val url = intent.getStringExtra(EXTRA_URL) ?: return finish()
        title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val start = intent.getDoubleExtra(EXTRA_START, 0.0)

        val d = resources.displayMetrics.density
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        val surface = SurfaceView(this)
        root.addView(surface, FrameLayout.LayoutParams(-1, -1))
        panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((32 * d).toInt(), (16 * d).toInt(), (32 * d).toInt(), (20 * d).toInt())
            background = GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, intArrayOf(0xCC000000.toInt(), 0x00000000))
        }
        info = TextView(this).apply { setTextColor(Color.WHITE); textSize = 18f }
        bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 1000 }
        panel.addView(info)
        panel.addView(bar, LinearLayout.LayoutParams(-1, (6 * d).toInt()).apply { topMargin = (8 * d).toInt() })
        root.addView(panel, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        setContentView(root)

        val m = MPVLib.create(this) ?: run { finish(); return }
        mpv = m
        m.setOptionString("config", "no")
        m.setOptionString("vo", "gpu")
        m.setOptionString("gpu-context", "android")
        m.setOptionString("opengl-es", "yes")
        m.setOptionString("hwdec", "mediacodec,mediacodec-copy")
        m.setOptionString("hwdec-codecs", "h264,hevc,mpeg4,mpeg2video,vp8,vp9,av1")
        m.setOptionString("ao", "audiotrack,opensles")
        m.setOptionString("cache", "yes")
        m.setOptionString("demuxer-max-bytes", "64MiB")
        m.setOptionString("demuxer-max-back-bytes", "16MiB")
        m.setOptionString("alang", "ja,jpn")
        m.setOptionString("slang", "ja,jpn")
        m.setOptionString("sub-font-size", "46")
        m.setOptionString("force-window", "no")
        m.setOptionString("keep-open", "no")
        m.setOptionString("idle", "once")
        m.init()
        m.observeProperty("time-pos", FORMAT_DOUBLE)
        m.observeProperty("duration", FORMAT_DOUBLE)
        m.observeProperty("pause", FORMAT_FLAG)
        m.addObserver(object : MPVLib.EventObserver {
            override fun eventProperty(property: String, value: Double) {
                if (property == "time-pos") pos = value else if (property == "duration") dur = value
            }
            override fun eventProperty(property: String, value: Boolean) { if (property == "pause") main.post { show() } }
            override fun eventProperty(property: String) {}
            override fun eventProperty(property: String, value: Long) {}
            override fun eventProperty(property: String, value: String) {}
            // 最後まで見た（または読めなかった）。最後の 1 分なら「見おわった」として続きからを消す
            override fun event(eventId: Int) { if (eventId == EVENT_END_FILE || eventId == EVENT_SHUTDOWN) main.post { done(ended = dur <= 0 || pos > dur - 60) } }
        })

        surface.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                m.attachSurface(holder.surface)
                m.setOptionString("force-window", "yes")
                m.setPropertyString("vo", "gpu")
                if (start > 5) m.setOptionString("start", "%.0f".format(start))
                m.command(arrayOf("loadfile", url))
                show()
            }
            override fun surfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) { m.setPropertyString("android-surface-size", "${w}x$h") }
            override fun surfaceDestroyed(holder: SurfaceHolder) {
                m.setPropertyString("vo", "null")
                m.setOptionString("force-window", "no")
                m.detachSurface()
            }
        })
        tick()
    }

    private fun tick() {
        main.postDelayed({
            if (panel.visibility == View.VISIBLE) update()
            if (!isFinishing) tick()
        }, 1000)
    }

    private fun fmt(s: Double): String { val t = s.toInt(); return if (t >= 3600) "%d:%02d:%02d".format(t / 3600, t / 60 % 60, t % 60) else "%d:%02d".format(t / 60, t % 60) }

    private fun update() {
        val paused = mpv?.getPropertyBoolean("pause") == true
        val aid = mpv?.getPropertyString("current-tracks/audio/lang") ?: ""
        info.text = "${if (paused) "⏸" else "▶"}  $title    ${fmt(pos)} / ${fmt(dur)}" + if (aid.isNotBlank()) "    音声 $aid" else ""
        bar.progress = if (dur > 0) (pos / dur * 1000).toInt() else 0
    }

    private fun show(ms: Long = 4000) {
        panel.visibility = View.VISIBLE
        update()
        main.removeCallbacks(hide)
        main.postDelayed(hide, ms)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val m = mpv ?: return super.onKeyDown(keyCode, event)
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_SPACE -> m.command(arrayOf("cycle", "pause"))
            KeyEvent.KEYCODE_MEDIA_PLAY -> m.setPropertyBoolean("pause", false)
            KeyEvent.KEYCODE_MEDIA_PAUSE -> m.setPropertyBoolean("pause", true)
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> m.command(arrayOf("seek", "-10"))
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> m.command(arrayOf("seek", "30"))
            KeyEvent.KEYCODE_DPAD_UP -> { m.command(arrayOf("cycle", "audio")); toast("音声を切り替えました") }
            KeyEvent.KEYCODE_DPAD_DOWN -> { m.command(arrayOf("cycle", "sub")); toast("字幕を切り替えました") }
            KeyEvent.KEYCODE_MEDIA_STOP -> { done(false); return true }
            else -> return super.onKeyDown(keyCode, event)
        }
        show()
        return true
    }

    private fun toast(s: String) = android.widget.Toast.makeText(this, s, android.widget.Toast.LENGTH_SHORT).show()

    override fun onBackPressed() { done(false) }

    private var finished = false
    private fun done(ended: Boolean) {
        if (finished) return
        finished = true
        setResult(RESULT_OK, Intent().putExtra(EXTRA_PATH, intent.getStringExtra(EXTRA_PATH)).putExtra(EXTRA_POS, if (ended) 0.0 else pos))
        finish()
    }

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        runCatching { mpv?.destroy() }
        mpv = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_URL = "url"
        const val EXTRA_TITLE = "title"
        const val EXTRA_PATH = "path"
        const val EXTRA_START = "start"
        const val EXTRA_POS = "pos"
        private const val FORMAT_FLAG = 3
        private const val FORMAT_DOUBLE = 5
        private const val EVENT_SHUTDOWN = 1
        private const val EVENT_END_FILE = 7
    }
}
