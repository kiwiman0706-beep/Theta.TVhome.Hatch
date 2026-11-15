package app.thetahatch.tvhome.rescue

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings

/**
 * テレビが「文鎮」にならないための逃げ道（Home.Hatch の Rescue を TV 用にしたもの）。
 *
 * 1. 落ちたら（例外）記録。起動して 8 秒もたずに終わった回数も数える
 * 2. 主スレッドが 8 秒以上応えなければ「固まった」と記録
 * 3. 5 分のうちに 3 回続いたら、次の起動ではホームの代わりにレスキュー画面（別プロセス・素の部品だけ）
 * 4. リモコンの「戻る」を 4 秒のうちに 7 回でも、レスキュー画面を開ける
 * 5. ADB：`OPEN --es screen rescue`、純正のホームに戻す手順は docs/ADB.md §6
 * 記録は設定と別のファイルに、その場で書く（commit）。
 */
object Rescue {
    private const val PREFS = "rescue"
    private const val WINDOW = 5 * 60_000L
    const val LIMIT = 3
    const val EXTRA_SAFE = "safe_mode"
    const val RELEASES = "https://github.com/kiwiman0706-beep/Theta.TVhome.Hatch/releases"

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun install(c: Context) {
        val app = c.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching { record(app, "crash", e.javaClass.simpleName + ": " + (e.message ?: "")) }
            previous?.uncaughtException(t, e)
        }
    }

    fun record(c: Context, kind: String, detail: String) {
        val p = prefs(c)
        val now = System.currentTimeMillis()
        val times = (p.getString("times", "") ?: "").split(',').mapNotNull { it.toLongOrNull() }.filter { now - it < WINDOW } + now
        p.edit().putString("times", times.takeLast(10).joinToString(",")).putString("last", "$kind · $detail").putLong("lastAt", now).commit()
    }

    fun recentTrouble(c: Context): Int {
        val now = System.currentTimeMillis()
        return (prefs(c).getString("times", "") ?: "").split(',').mapNotNull { it.toLongOrNull() }.count { now - it < WINDOW }
    }

    fun lastTrouble(c: Context): String? = prefs(c).getString("last", null)

    /** ホームの起動のはじめに。前の起動が「無事」の印なしで終わっていたら不具合に数える。true ならレスキュー画面へ。 */
    fun onHomeStart(c: Context): Boolean {
        val p = prefs(c)
        if (p.getBoolean("starting", false)) record(c, "start", "起動して間もなく止まりました")
        p.edit().putBoolean("starting", true).commit()
        return recentTrouble(c) >= LIMIT
    }

    fun onHomeHealthy(c: Context) { prefs(c).edit().putBoolean("starting", false).commit() }

    fun reset(c: Context) { prefs(c).edit().clear().commit() }

    private var watching = false

    fun watchMainThread(c: Context) {
        if (watching) return
        watching = true
        val app = c.applicationContext
        val main = Handler(Looper.getMainLooper())
        Thread({
            var reported = false
            while (true) {
                val answered = java.util.concurrent.atomic.AtomicBoolean(false)
                main.post { answered.set(true) }
                Thread.sleep(8000)
                if (!answered.get() && !reported) { runCatching { record(app, "freeze", "画面が 8 秒以上応えませんでした") }; reported = true }
                else if (answered.get()) reported = false
            }
        }, "tvhome-watchdog").apply { isDaemon = true }.start()
    }

    /** このアプリ以外のホーム（純正のホーム）。Android TV の Google のホームを先に。 */
    fun otherHomes(c: Context): List<ComponentName> {
        val pm = c.packageManager
        val list = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_ALL)
            .mapNotNull { it.activityInfo }.filter { it.packageName != c.packageName && it.packageName != "android" && it.packageName != "com.android.settings" }
            .map { ComponentName(it.packageName, it.name) }
        val known = listOf("com.google.android.tvlauncher", "com.google.android.leanbacklauncher", "com.google.android.apps.tv.launcherx")
        return list.sortedBy { cn -> known.indexOf(cn.packageName).let { if (it < 0) 99 else it } }
    }

    /** 純正のホームを開く（既定のホームは変えない。いまだけ逃げる）。開けたら true。 */
    fun openStockHome(c: Context): Boolean {
        for (cn in otherHomes(c)) {
            val ok = runCatching {
                c.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setComponent(cn).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.isSuccess
            if (ok) return true
        }
        return false
    }

    /** 既定のホームを選ぶ画面。無ければ設定のトップ。 */
    fun chooseHome(c: Context) {
        val flags = Intent.FLAG_ACTIVITY_NEW_TASK
        runCatching { c.startActivity(Intent(Settings.ACTION_HOME_SETTINGS).addFlags(flags)) }
            .recoverCatching { c.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(flags)) }
    }
}

/** 「戻る」を 4 秒のうちに 7 回：レスキューの合図。 */
class BackCounter(private val n: Int = 7, private val windowMs: Long = 4000) {
    private val times = ArrayDeque<Long>()
    fun hit(now: Long = System.currentTimeMillis()): Boolean {
        times.addLast(now)
        while (times.isNotEmpty() && now - times.first() > windowMs) times.removeFirst()
        if (times.size >= n) { times.clear(); return true }
        return false
    }
}
