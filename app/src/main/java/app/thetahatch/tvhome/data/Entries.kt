package app.thetahatch.tvhome.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.net.Uri

/**
 * テレビの入口：テレビ（放送）・地上波・BS・録画・HDMI など。
 *
 * - HDMI などの外部入力は TvInputManager で見つけ、`ACTION_VIEW content://android.media.tv/passthrough/<入力>` で切り替える
 *   （Android TV の決まった方法。テレビのアプリが受けて切り替える）。
 * - 地上波・BS・録画の入口は機種ごとに違う（AQUOS の場合は未確認）。ADB で `SET entry.<key> <Intent の URI>` を入れれば使える。
 *   決まるまでは候補を自動で探し（テレビのアプリを開く）、それも無ければ「未設定」で出す。
 */
data class Entry(
    val key: String,
    val label: String,
    val icon: String,
    val intent: Intent?,
    /** "set"（ADB・設定で決めた）・"auto"（自動で見つけた）・"none"（まだない） */
    val source: String,
    val kind: String,
)

object Entries {
    /** 決まった入口（並び順どおり）。 */
    val FIXED = listOf(
        Triple("tv", "テレビ", "📺"),
        Triple("terrestrial", "ちデジ", "📡"),
        Triple("bs", "BS", "🛰"),
        Triple("recordings", "ろくが", "⏺"),
    )

    fun inputs(context: Context): List<TvInputInfo> = runCatching {
        context.getSystemService(TvInputManager::class.java)?.tvInputList.orEmpty()
    }.getOrDefault(emptyList())

    /** 外部入力（HDMI・コンポジットなど）。 */
    fun passthrough(context: Context) = inputs(context).filter { it.isPassthroughInput && it.parentId == null }

    fun tuners(context: Context) = inputs(context).filter { it.type == TvInputInfo.TYPE_TUNER }

    fun inputIntent(id: String): Intent =
        Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(id)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** テレビのアプリ（passthrough の VIEW を受けるもの）。 */
    fun tvApp(context: Context): ComponentName? {
        val probe = Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput("probe"))
        val r = context.packageManager.resolveActivity(probe, PackageManager.MATCH_DEFAULT_ONLY) ?: return null
        val ai = r.activityInfo ?: return null
        if (ai.packageName == "android") return null
        return ComponentName(ai.packageName, ai.name)
    }

    fun parse(uri: String?): Intent? = uri?.let { runCatching { Intent.parseUri(it, Intent.URI_INTENT_SCHEME) }.getOrNull() }
        ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); selector = null }

    fun all(context: Context, store: Store): List<Entry> {
        val out = ArrayList<Entry>()
        val tvApp = tvApp(context)
        for ((key, label, icon) in FIXED) {
            val set = parse(store.entryUri(key))
            val auto: Intent? = when (key) {
                // テレビのアプリをそのまま開く。チューナーの channel の VIEW は、機種によってはアナログ放送を開いて落ちる（R4-SH で確認）ので使わない。
                // 機種ごとの正しい入口は ADB で entry.tv に入れる（docs/device/<型番>.md）
                "tv" -> tvApp?.let { context.packageManager.getLeanbackLaunchIntentForPackage(it.packageName) ?: context.packageManager.getLaunchIntentForPackage(it.packageName) }
                else -> null
            }
            val intent = set ?: auto
            out += Entry(key, store.label(key) ?: label, icon, intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), if (set != null) "set" else if (auto != null) "auto" else "none", "fixed")
        }
        passthrough(context).forEachIndexed { i, info ->
            val key = "input:${info.id}"
            val name = store.label(key) ?: runCatching { info.loadLabel(context).toString() }.getOrNull()?.takeIf { it.isNotBlank() } ?: "入力 ${i + 1}"
            val set = parse(store.entryUri(key))
            out += Entry(key, name, "🎮", set ?: inputIntent(info.id), if (set != null) "set" else "auto", "input")
        }
        return out
    }

    fun resolves(context: Context, i: Intent) = context.packageManager.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY)
        ?.activityInfo?.packageName?.let { it != "android" } == true

    /** 調べた結果（ADB の PROBE・dumpsys probe）。AQUOS で何が使えるかを ADB のスレッドが見る。 */
    fun probe(context: Context): org.json.JSONObject {
        val pm = context.packageManager
        val o = org.json.JSONObject()
        o.put("tvApp", tvApp(context)?.flattenToShortString())
        o.put("inputs", org.json.JSONArray().apply {
            inputs(context).forEach { info ->
                put(org.json.JSONObject()
                    .put("id", info.id).put("type", info.type).put("passthrough", info.isPassthroughInput)
                    .put("parent", info.parentId).put("label", runCatching { info.loadLabel(context).toString() }.getOrNull())
                    .put("service", info.serviceInfo?.packageName + "/" + info.serviceInfo?.name)
                    .put("viewResolves", resolves(context, inputIntent(info.id))))
            }
        })
        // 放送・録画の画面を持っていそうなアプリ（名前で拾う）
        val tvish = pm.getInstalledPackages(0).map { it.packageName }.filter { p ->
            listOf("tv", "sharp", "aquos", "record", "rec", "dtv", "tuner").any { p.lowercase().contains(it) }
        }.sorted()
        o.put("tvishPackages", org.json.JSONArray(tvish))
        val keyed = listOf(
            "android.intent.action.VIEW content://android.media.tv/channel" to Intent(Intent.ACTION_VIEW, TvContract.Channels.CONTENT_URI),
            "android.media.tv.action.VIEW_RECORDING_SCHEDULES" to Intent("android.media.tv.action.VIEW_RECORDING_SCHEDULES"),
            "android.intent.action.VIEW content://android.media.tv/recorded_program" to Intent(Intent.ACTION_VIEW, Uri.parse("content://android.media.tv/recorded_program")),
            "android.settings.SETTINGS" to Intent(android.provider.Settings.ACTION_SETTINGS),
        )
        o.put("candidates", org.json.JSONObject().apply {
            keyed.forEach { (name, i) ->
                put(name, pm.queryIntentActivities(i, 0).joinToString(",") { it.activityInfo.packageName + "/" + it.activityInfo.name })
            }
        })
        return o
    }
}
