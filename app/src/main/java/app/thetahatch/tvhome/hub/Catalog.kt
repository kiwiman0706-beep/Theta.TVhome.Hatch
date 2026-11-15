package app.thetahatch.tvhome.hub

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * シリーズのアプリ 1 つ（Theta.Hatch.Series の catalog/apps.json の 1 行）。
 * currentPackage は今の ID、targetPackage は揃えた後の ID（移行が済めば同じ）。
 */
data class CatalogApp(
    val id: String,
    val name: String,
    val nickname: String?,
    val repo: String?,
    val currentPackage: String?,
    val targetPackage: String?,
    val android: Boolean,
    val bridge: Boolean,
    val updateHub: Boolean,
    val status: String?,
) {
    val releasesUrl: String? get() = repo?.let { "https://github.com/${Catalog.OWNER}/$it/releases" }

    /** 端末に入っている方の ID（新しい ID を先に見る）。 */
    fun installedPackage(versionOf: (String) -> String?): String? =
        targetPackage?.takeIf { versionOf(it) != null } ?: currentPackage?.takeIf { versionOf(it) != null }

    /** 古い ID が入っている（新しい版を入れて、古いアプリを消す必要がある）。 */
    fun needsMigration(versionOf: (String) -> String?): Boolean =
        currentPackage != null && targetPackage != null && currentPackage != targetPackage && versionOf(currentPackage) != null

    /** 入れるときの ID（新しい ID があればそちら）。 */
    val packageName: String? get() = targetPackage ?: currentPackage
}

/**
 * シリーズのアプリの一覧。APK に同梱した assets/apps.json と、ネットで読んだ最新（filesDir に保存）の新しい方を使う。
 */
object Catalog {
    const val OWNER = "kiwiman0706-beep"
    const val URL = "https://raw.githubusercontent.com/$OWNER/Theta.Hatch.Series/HEAD/catalog/apps.json"
    private const val CACHE = "catalog.json"

    private val _apps = MutableStateFlow<List<CatalogApp>>(emptyList())
    val apps: StateFlow<List<CatalogApp>> = _apps

    /** 一覧に出すもの（構想中・設計中・試作は出さない）。 */
    val shown: List<CatalogApp> get() = _apps.value.filter { it.status == null }

    /** Android 版がある（更新・移行の対象）。 */
    val android: List<CatalogApp> get() {
        val list = _apps.value.filter { it.android && it.repo != null && it.packageName != null }
        return if (list.any { it.packageName == SELF.packageName }) list else list + SELF
    }

    /** このアプリ自身。catalog にまだ載っていなくても自分は更新する。 */
    val SELF = CatalogApp(
        id = "tvhome", name = "Theta TVhome Hatch", nickname = "テレビのホーム", repo = "Theta.TVhome.Hatch",
        currentPackage = "app.thetahatch.tvhome", targetPackage = "app.thetahatch.tvhome",
        android = true, bridge = false, updateHub = true, status = null,
    )

    fun byPackage(pkg: String): CatalogApp? = android.firstOrNull { it.currentPackage == pkg || it.targetPackage == pkg }

    /** シリーズのアプリの ID（古い ID も含む）。 */
    val packages: Set<String> get() = _apps.value.flatMap { listOfNotNull(it.currentPackage, it.targetPackage) }.toSet()

    fun load(context: Context) {
        if (_apps.value.isNotEmpty()) return
        val bundled = runCatching { context.assets.open("apps.json").bufferedReader().use { it.readText() } }.getOrNull()
        val cached = runCatching { File(context.filesDir, CACHE).takeIf { it.exists() }?.readText() }.getOrNull()
        _apps.value = newer(bundled, cached)?.let { parse(it) } ?: emptyList()
    }

    /** ネットから読み直す。読めなければ今のまま。 */
    suspend fun refresh(context: Context) = withContext(Dispatchers.IO) {
        load(context)
        val text = runCatching {
            val c = URL(URL).openConnection() as HttpURLConnection
            c.connectTimeout = 10_000
            c.readTimeout = 15_000
            c.setRequestProperty("User-Agent", app.thetahatch.tvhome.data.Net.UA)
            if (c.responseCode !in 200..299) null else c.inputStream.bufferedReader().use { it.readText() }
        }.getOrNull() ?: return@withContext
        val list = runCatching { parse(text) }.getOrNull()?.takeIf { it.isNotEmpty() } ?: return@withContext
        runCatching { File(context.filesDir, CACHE).writeText(text) }
        _apps.value = list
    }

    /** "updated"（日付）が新しい方。 */
    internal fun newer(a: String?, b: String?): String? {
        fun date(s: String?) = s?.let { runCatching { JSONObject(it).optString("updated") }.getOrNull() } ?: ""
        if (a == null) return b
        if (b == null) return a
        return if (date(b) >= date(a)) b else a
    }

    fun parse(text: String): List<CatalogApp> {
        val arr = JSONObject(text).getJSONArray("apps")
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val cur = o.optJSONObject("current")
            val tgt = o.optJSONObject("target")
            val platforms = o.optJSONArray("platforms")
            val android = platforms != null && (0 until platforms.length()).any { platforms.optString(it) == "android" }
            fun JSONObject?.str(k: String) = this?.takeUnless { it.isNull(k) }?.optString(k)?.takeIf { it.isNotBlank() }
            CatalogApp(
                id = o.getString("id"),
                name = o.optString("name"),
                nickname = o.str("nickname"),
                repo = tgt.str("repo") ?: cur.str("repo"),
                currentPackage = cur.str("packageName"),
                targetPackage = tgt.str("packageName"),
                android = android,
                bridge = o.optJSONObject("migration")?.optBoolean("bridge") == true,
                updateHub = o.optBoolean("updateHub"),
                status = o.str("status"),
            )
        }
    }

    /** 試験用：一覧を差し替える。 */
    internal fun setForTest(list: List<CatalogApp>) { _apps.value = list }
}
