package app.thetahatch.tvhome

import app.thetahatch.tvhome.rescue.Rescue
import app.thetahatch.tvhome.rescue.RescueActivity
import app.thetahatch.tvhome.media.NowPlaying
import android.content.ActivityNotFoundException
import android.content.Intent
import app.thetahatch.tvhome.data.AppItem
import app.thetahatch.tvhome.music.Music
import app.thetahatch.tvhome.music.MusicLib
import app.thetahatch.tvhome.nas.Dsm
import app.thetahatch.tvhome.link.PhoneInput
import app.thetahatch.tvhome.link.PhoneInput.Field as PhoneField
import app.thetahatch.tvhome.nas.Kind
import app.thetahatch.tvhome.nas.Index as NasIndex
import app.thetahatch.tvhome.nas.NasEntry
import app.thetahatch.tvhome.nas.OtpRequired
import app.thetahatch.tvhome.nas.Unreachable
import app.thetahatch.tvhome.data.AppList
import app.thetahatch.tvhome.data.Entries
import app.thetahatch.tvhome.data.Entry
import app.thetahatch.tvhome.data.Event
import app.thetahatch.tvhome.data.Forecast
import app.thetahatch.tvhome.data.GCal
import app.thetahatch.tvhome.data.Ics
import app.thetahatch.tvhome.data.Net
import app.thetahatch.tvhome.data.Place
import app.thetahatch.tvhome.data.Weather
import app.thetahatch.tvhome.hub.Catalog
import app.thetahatch.tvhome.hub.UpdateHub
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZoneId
import java.time.ZonedDateTime

/** ホームの画面に出すもの。重い読み込みは裏で、表示は StateFlow で。 */
class HomeState(private val app: TvHomeApp) {
    private val store get() = app.store

    val apps = MutableStateFlow<List<AppItem>>(emptyList())
    val entries = MutableStateFlow<List<Entry>>(emptyList())
    val forecast = MutableStateFlow<Forecast?>(null)
    val events = MutableStateFlow<List<Event>>(emptyList())
    val calendarError = MutableStateFlow<String?>(null)
    /** Google の許可の画面（MainActivity が出す）。 */
    val consent = MutableStateFlow<android.app.PendingIntent?>(null)
    val gcal by lazy { GCal(app) }
    val dsm by lazy { Dsm(store) }
    val music by lazy { Music(app, dsm) }
    /** NAS の棚（種類ごと。共有フォルダの直下）。 */
    val shelves: Map<Kind, MutableStateFlow<List<NasEntry>>> = Kind.entries.associateWith { MutableStateFlow(emptyList()) }
    /** NAS の困りごと（届かない・2 段階認証 など）。 */
    val nasError = MutableStateFlow<String?>(null)
    val nasNeedsOtp = MutableStateFlow(false)
    /** NAS の画面で開く物：種類・フォルダ（空ならいちばん上）・見せ方（browse・recent・search）・探すことば。 */
    data class NasView(val kind: Kind, val path: String, val mode: String = "browse", val query: String = "")
    val nasOpen = MutableStateFlow(NasView(Kind.MUSIC, ""))
    /** きく：見せ方（search・maps・image）と、こえで始めるか。 */
    val askOpen = MutableStateFlow("search" to true)
    /** 決定の長押しのたびに増える（きく の画面にいるときも、もう一度 声を聞く）。 */
    val voiceTick = MutableStateFlow(0)

    /** 索引（さいきん ついか・さがす）。 */
    val indexes: Map<Kind, NasIndex> by lazy { Kind.entries.associateWith { NasIndex(app, it) } }
    /** 索引を作っている途中：種類 → 見つけた数。 */
    val indexing = MutableStateFlow<Map<Kind, Int>>(emptyMap())
    private val shelvesAt = HashMap<Kind, Long>()
    /** 設定が変わるたびに増える（画面の読み直し用）。 */
    private val _tick = MutableStateFlow(0)
    val tick: StateFlow<Int> = _tick

    private var weatherAt = 0L
    private var calendarAt = 0L

    fun reloadSettings() { _tick.value++; reloadApps(); refreshWeather(force = true); refreshCalendar(force = true) }

    fun reloadApps() {
        app.scope.launch {
            val (a, e) = withContext(Dispatchers.IO) { AppList.load(app) to Entries.all(app, store) }
            apps.value = a
            entries.value = e
        }
    }

    /** 画面が出るたびに呼ぶ。30 分より古ければ読み直す。 */
    fun onShown() {
        reloadApps()
        refreshShelves()
        refreshWeather()
        refreshCalendar()
        // セーフモード、または最近落ちた・固まったときは、自動の更新をしない（壊れた版を重ねて入れないため）
        if (app.safeMode || Rescue.recentTrouble(app) > 0) return
        // 12 時間より前に確かめたきりなら、更新も確かめる（Worker が止められている端末のため）
        if (store.autoUpdate && System.currentTimeMillis() - store.lastUpdateCheck > 12 * 3600_000L) {
            store.lastUpdateCheck = System.currentTimeMillis()
            app.scope.launch { runCatching { UpdateHub.runAll(app, install = true, mobile = store.updateMobile, fromWorker = true) } }
        } else app.scope.launch { runCatching { Catalog.load(app); UpdateHub.refreshInstalled(app) } }
    }

    fun refreshWeather(force: Boolean = false) {
        if (app.safeMode) return
        val place = Place.decode(store.weatherPlace) ?: run { forecast.value = null; return }
        if (!force && System.currentTimeMillis() - weatherAt < 30 * 60_000L) return
        weatherAt = System.currentTimeMillis()
        app.scope.launch {
            runCatching { withContext(Dispatchers.IO) { Weather.fetch(place) } }.onSuccess { forecast.value = it }.onFailure { weatherAt = 0 }
        }
    }

    /**
     * 予定：Google カレンダーにつないでいれば API（テレビの Google アカウント）、
     * つないでいなくて iCal の非公開アドレスが入っていればそれ（予備。docs/GOOGLE_ACCOUNT.md）。
     * 今週の月曜の 4 週前から 20 週（帯は 2 週、カレンダーは 4 週を 4 週前〜12 週先まで動かせる）。
     */
    fun refreshCalendar(force: Boolean = false, interactive: Boolean = false) {
        if (app.safeMode) return
        val google = store.googleCalendar
        if (!google && !store.hasSecret("calendar.ics")) { events.value = emptyList(); calendarError.value = null; return }
        if (!force && System.currentTimeMillis() - calendarAt < 30 * 60_000L) return
        calendarAt = System.currentTimeMillis()
        app.scope.launch {
            val zone = ZoneId.systemDefault()
            val today = ZonedDateTime.now(zone).toLocalDate()
            val from = today.minusDays((today.dayOfWeek.value - 1).toLong()).minusWeeks(4).atStartOfDay(zone)
            runCatching {
                withContext(Dispatchers.IO) {
                    if (google) gcal.events(from, 140, zone)
                    else store.secret("calendar.ics").split('\n', ' ').map { it.trim() }.filter { it.startsWith("http") || it.startsWith("webcal") }
                        .flatMap { u -> Ics.events(Net.get(u.replaceFirst("webcal://", "https://")), from, 140, zone) }.sortedBy { it.start }
                }
            }.onSuccess { events.value = it; calendarError.value = null }
                .onFailure { e ->
                    calendarAt = 0
                    if (e is GCal.NeedConsent) {
                        // 許可の画面は、せっていで「つなぐ」を押したときだけ自分から出す（ふだんは知らせるだけ）
                        if (interactive) consent.value = e.intent
                        calendarError.value = "Google の許可が要ります（せってい → Google カレンダー）"
                    } else calendarError.value = e.message ?: "読めません"
                }
        }
    }

    /** 種類ごとの共有フォルダ（せってい →「NAS」。既定 /music・/video・/book・/photo）。 */
    fun root(k: Kind) = store.get(k.rootKey)?.ifBlank { null } ?: k.defaultRoot

    fun refreshShelves(force: Boolean = false) {
        if (app.safeMode || !dsm.configured) return
        for (k in Kind.entries) {
            val flow = shelves.getValue(k)
            if (!force && System.currentTimeMillis() - (shelvesAt[k] ?: 0) < 30 * 60_000L && flow.value.isNotEmpty()) continue
            shelvesAt[k] = System.currentTimeMillis()
            app.scope.launch {
                runCatching { withContext(Dispatchers.IO) { k.shelves(dsm.list(root(k))) } }
                    .onSuccess { flow.value = it; nasError.value = null; nasNeedsOtp.value = false }
                    .onFailure { e -> shelvesAt[k] = 0; if (k == Kind.MUSIC || e is OtpRequired || e is Unreachable) nasProblem(e) }
            }
        }
    }

    fun openNas(k: Kind, path: String, mode: String = "browse", query: String = "") { nasOpen.value = NasView(k, path, mode, query) }

    /** 索引を読み、古ければ裏で作り直す。終わったら done。 */
    fun ensureIndex(k: Kind, force: Boolean = false, done: () -> Unit = {}) {
        val ix = indexes.getValue(k)
        app.scope.launch {
            withContext(Dispatchers.IO) { ix.load() }
            if (ix.items.isNotEmpty()) done()
            if ((ix.stale || force) && k !in indexing.value && dsm.configured && !app.safeMode) {
                indexing.value = indexing.value + (k to 0)
                runCatching { withContext(Dispatchers.IO) { ix.build(dsm, root(k)) { n -> indexing.value = indexing.value + (k to n) } } }
                    .onFailure { nasProblem(it) }
                indexing.value = indexing.value - k
                done()
            }
        }
    }

    fun nasProblem(e: Throwable) {
        nasNeedsOtp.value = e is OtpRequired
        nasError.value = e.message ?: "NAS につながりません"
    }

    /** スマホで入れる（QR コード）。group：nas・gemini・all。 */
    fun phoneInput(group: String) {
        val nas = listOf(
            PhoneField("nas.url", "NAS の URL", hint = "例 http://nas.tailXXXX.ts.net:5000"),
            PhoneField("nas.user", "NAS のユーザー名"),
            PhoneField("nas.password", "NAS のパスワード", secret = true),
        ) + Kind.entries.map { PhoneField(it.rootKey, "${it.label}の 共有フォルダ", hint = it.defaultRoot) }
        val gemini = listOf(PhoneField("gemini.key", "Gemini の API キー", secret = true), PhoneField("gemini.model", "Gemini のモデル", hint = "gemini-2.5-flash"))
        val (title, fields) = when (group) { "nas" -> "NAS の せってい" to nas; "gemini" -> "Gemini の せってい" to gemini; else -> "TVhome の せってい" to nas + gemini }
        PhoneInput.start(app, store, title, fields) { saved ->
            app.scope.launch {
                if (saved.isNotEmpty()) { dsm.reset(); refreshShelves(force = true); app.settingsChanged() }
            }
        }
    }

    /** せってい：Google カレンダーにつなぐ（許可の画面を出す）。 */
    fun connectGoogle() {
        store.googleCalendar = true
        refreshCalendar(force = true, interactive = true)
    }

    /** 許可の画面から戻った。 */
    fun onConsent(data: Intent?) {
        consent.value = null
        if (gcal.acceptResult(data)) refreshCalendar(force = true) else calendarError.value = "許可されませんでした"
    }

    fun launch(intent: Intent?): Boolean {
        if (intent == null) { app.toast("まだ設定されていません"); return false }
        return try {
            app.startActivity(Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
        } catch (e: ActivityNotFoundException) {
            app.toast("開けません"); false
        } catch (e: SecurityException) {
            app.toast("開けません（権限）"); false
        }
    }
}
