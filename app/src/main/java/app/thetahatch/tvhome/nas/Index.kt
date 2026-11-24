package app.thetahatch.tvhome.nas

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * NAS のライブラリの索引（種類ごと）。「さいきん ついか」と「さがす」に使う。
 * 共有フォルダを File Station でたどって、フォルダとファイルの名前・時刻を覚える（テレビの中のファイル）。
 * 6 時間より古ければ、開いたときに作り直す。深さ 6・2 万件まで（NAS に負担をかけすぎない）。
 */
class Index(private val context: Context, val kind: Kind) {
    data class Item(val path: String, val name: String, val dir: Boolean, val mtime: Long)

    @Volatile var items: List<Item> = emptyList(); private set
    @Volatile var builtAt = 0L; private set
    private val file get() = File(context.filesDir, "index-${kind.key}.json")

    fun load() {
        if (items.isNotEmpty()) return
        runCatching {
            val o = JSONObject(file.readText())
            builtAt = o.optLong("at")
            val a = o.getJSONArray("items")
            items = (0 until a.length()).map { a.getJSONArray(it) }.map { Item(it.getString(0), it.getString(1), it.getInt(2) == 1, it.getLong(3)) }
        }
    }

    val stale get() = System.currentTimeMillis() - builtAt > 6 * 3600_000L

    /** 作り直す（重い。裏で）。progress に見つけた数を知らせる。 */
    fun build(dsm: Dsm, root: String, progress: (Int) -> Unit = {}) {
        val out = ArrayList<Item>()
        var level = kind.shelves(dsm.list(root)).map { it.path }
        var depth = 0
        while (level.isNotEmpty() && depth < 6 && out.size < 20_000) {
            val next = ArrayList<String>()
            for (dir in level) {
                val list = runCatching { dsm.list(dir) }.getOrDefault(emptyList())
                for (e in list) {
                    if (e.dir) { if (!kind.hidden(e)) { out += Item(e.path, e.name, true, e.mtime); next += e.path } }
                    else if (kind.isItem(e)) out += Item(e.path, e.name, false, e.mtime)
                }
                progress(out.size)
                if (out.size >= 20_000) break
            }
            level = next; depth++
        }
        items = out
        builtAt = System.currentTimeMillis()
        runCatching {
            val a = JSONArray()
            out.forEach { a.put(JSONArray().put(it.path).put(it.name).put(if (it.dir) 1 else 0).put(it.mtime)) }
            file.writeText(JSONObject().put("at", builtAt).put("items", a).toString())
        }
    }

    /**
     * さいきん ついか：新しい順。えいがは作品のフォルダ（話が増えた作品）、おんがく・ほん・しゃしんはファイルの入ったフォルダでまとめる。
     */
    fun recent(n: Int = 40): List<Item> = recentOf(items, kind, n)

    /** さがす：名前（とその上のフォルダの名前）に、ことばが全部入っている物。フォルダが先。 */
    fun search(q: String, n: Int = 80): List<Item> = searchOf(items, q, n)

    companion object {
    fun recentOf(items: List<Item>, kind: Kind, n: Int = 40): List<Item> {
        val files = items.filter { !it.dir }.sortedByDescending { it.mtime }
        val seen = LinkedHashMap<String, Item>()
        for (f in files) {
            val parent = f.path.substringBeforeLast('/')
            val key = if (kind == Kind.VIDEO && Names.season(parent.substringAfterLast('/')) != null) parent.substringBeforeLast('/') else parent
            if (key !in seen) seen[key] = Item(key, key.substringAfterLast('/'), true, f.mtime)
            if (seen.size >= n) break
        }
        return seen.values.toList()
    }

    fun searchOf(items: List<Item>, q: String, n: Int = 80): List<Item> {
        val words = q.split(Regex("\\s+")).map { Names.norm(it) }.filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()
        return items.asSequence()
            .filter { it -> val k = Names.norm(it.path.substringAfterLast('/', it.name) + " " + it.path.substringBeforeLast('/').substringAfterLast('/')); words.all { w -> k.contains(w) } }
            .sortedWith(compareBy<Item>({ !it.dir }, { it.name.length }))
            .take(n).toList()
    }
}
}
