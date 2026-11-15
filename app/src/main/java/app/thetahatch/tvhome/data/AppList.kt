package app.thetahatch.tvhome.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable

/** ホームに並べるアプリ 1 つ。banner はテレビ用の横長の絵（無ければ icon）。 */
data class AppItem(val pkg: String, val label: String, val intent: Intent, val hasBanner: Boolean)

/**
 * 入っているアプリの一覧（テレビ用 LEANBACK_LAUNCHER を先に、無ければふつうの LAUNCHER）。
 * 絵は使うときに読み、小さく縮めてメモリに置く（テレビの少ないメモリでも軽く）。
 */
object AppList {
    fun load(context: Context): List<AppItem> {
        val pm = context.packageManager
        val self = context.packageName
        val seen = LinkedHashMap<String, AppItem>()
        fun add(list: List<ResolveInfo>, leanback: Boolean) {
            for (r in list) {
                val ai = r.activityInfo ?: continue
                if (ai.packageName == self || ai.packageName in seen) continue
                val cat = if (leanback) Intent.CATEGORY_LEANBACK_LAUNCHER else Intent.CATEGORY_LAUNCHER
                val i = Intent(Intent.ACTION_MAIN).addCategory(cat).setClassName(ai.packageName, ai.name)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                val banner = leanback && (ai.banner != 0 || ai.applicationInfo.banner != 0)
                seen[ai.packageName] = AppItem(ai.packageName, r.loadLabel(pm).toString(), i, banner)
            }
        }
        add(pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0), true)
        add(pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0), false)
        return seen.values.sortedBy { it.label.lowercase() }
    }

    private val cache = object : android.util.LruCache<String, Bitmap>(12 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    /** バナー（無ければアイコン）を幅 w ピクセルの絵に。 */
    fun art(context: Context, item: AppItem, w: Int): Bitmap? {
        val k = "${item.pkg}@$w"
        cache.get(k)?.let { return it }
        val pm = context.packageManager
        val d: Drawable = runCatching {
            val ai = pm.getActivityInfo(item.intent.component!!, 0)
            (if (item.hasBanner) ai.loadBanner(pm) else null) ?: ai.loadIcon(pm)
        }.getOrNull() ?: runCatching { pm.getApplicationIcon(item.pkg) }.getOrNull() ?: return null
        val b = toBitmap(d, w, if (item.hasBanner) w * 9 / 16 else w)
        cache.put(k, b)
        return b
    }

    fun clear() = cache.evictAll()

    private fun toBitmap(d: Drawable, w: Int, h: Int): Bitmap {
        if (d is BitmapDrawable && d.bitmap != null && d.bitmap.width <= w * 2) return d.bitmap
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        d.setBounds(0, 0, w, h)
        d.draw(c)
        return b
    }

    fun exists(context: Context, pkg: String) = try {
        context.packageManager.getPackageInfo(pkg, 0); true
    } catch (_: PackageManager.NameNotFoundException) { false }
}
