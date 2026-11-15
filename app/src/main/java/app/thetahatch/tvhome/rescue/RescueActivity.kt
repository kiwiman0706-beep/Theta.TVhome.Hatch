package app.thetahatch.tvhome.rescue

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import app.thetahatch.tvhome.MainActivity

/**
 * レスキュー画面。本体と別のプロセスで、Compose を使わず Android の素の部品だけで描く（本体が壊れていても開けるように）。
 * リモコンの十字キーで選べるよう、ボタンは選ぶと色が変わる。
 */
class RescueActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val d = resources.displayMetrics.density
        fun px(v: Int) = (v * d).toInt()
        val orange = Color.rgb(0xFF, 0x8A, 0x1F)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(64), px(32), px(64), px(32))
        }
        fun text(s: String, size: Float, color: Int = Color.WHITE, bold: Boolean = false) = TextView(this).apply {
            text = s; textSize = size; setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
            setPadding(0, px(4), 0, px(4))
            col.addView(this)
        }
        var first: View? = null
        fun button(label: String, primary: Boolean = false, onClick: () -> Unit) = Button(this).apply {
            text = label; isAllCaps = false; textSize = 18f
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            setPadding(px(20), px(10), px(20), px(10))
            fun paint(focused: Boolean) {
                background = GradientDrawable().apply {
                    cornerRadius = px(10).toFloat()
                    setColor(if (focused) orange else Color.rgb(0x22, 0x24, 0x28))
                    if (primary && !focused) setStroke(px(2), orange)
                }
                setTextColor(if (focused) Color.BLACK else Color.WHITE)
            }
            paint(false)
            isFocusable = true
            setOnFocusChangeListener { _, f -> paint(f) }
            setOnClickListener { onClick() }
            col.addView(this, LinearLayout.LayoutParams(px(560), LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = px(10) })
            if (first == null) first = this
        }

        text("[ RESCUE ]", 13f, orange, bold = true)
        text("テレビのホームのレスキュー", 26f, bold = true)
        val trouble = Rescue.recentTrouble(this)
        text(
            if (trouble >= Rescue.LIMIT) "ホームが続けて止まったので、代わりにこの画面を出しています。テレビは壊れていません。"
            else "ホームがおかしいときに、ここから逃げられます。テレビは壊れていません。",
            16f, Color.rgb(0xCC, 0xCC, 0xCC),
        )
        Rescue.lastTrouble(this)?.let { text("最後の不具合：$it", 13f, Color.rgb(0x88, 0x88, 0x88)) }

        button("いつもの（純正の）ホームを開く", primary = true) {
            if (!Rescue.openStockHome(this)) Rescue.chooseHome(this)
        }
        button("既定のホームを選び直す（Android の設定）") { Rescue.chooseHome(this) }
        button("セーフモードで開く（ネット・写真・動きを止める）") {
            Rescue.reset(this)
            startActivity(Intent(this, MainActivity::class.java).putExtra(Rescue.EXTRA_SAFE, true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            finish()
        }
        button("普通に開き直す") {
            Rescue.reset(this)
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            finish()
        }
        button("Android の設定を開く") { runCatching { startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
        button("最新版のページを開く（ブラウザがあれば）") { runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Rescue.RELEASES)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
        text("PC から直すとき：adb shell cmd package set-home-activity <純正のホーム>、または adb uninstall app.thetahatch.tvhome（消せば純正のホームに戻ります）。", 13f, Color.rgb(0x99, 0x99, 0x99))
        text("リモコンの「戻る」を すばやく 7 回押すと、いつでもこの画面を開けます。", 13f, Color.rgb(0x99, 0x99, 0x99))

        setContentView(ScrollView(this).apply { setBackgroundColor(Color.rgb(0x0D, 0x0E, 0x10)); addView(col) })
        first?.requestFocus()
    }
}
