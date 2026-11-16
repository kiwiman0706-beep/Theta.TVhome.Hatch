package app.thetahatch.tvhome

import app.thetahatch.tvhome.rescue.Rescue
import app.thetahatch.tvhome.rescue.RescueActivity
import app.thetahatch.tvhome.media.NowPlaying
import android.content.ActivityNotFoundException
import android.content.Intent
import app.thetahatch.tvhome.data.AppItem
import app.thetahatch.tvhome.data.AppList
import app.thetahatch.tvhome.data.Entries
import app.thetahatch.tvhome.data.Entry
import app.thetahatch.tvhome.data.Event
import app.thetahatch.tvhome.data.Forecast
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

    fun refreshCalendar(force: Boolean = false) {
        if (app.safeMode) return
        if (!store.hasSecret("calendar.ics")) { events.value = emptyList(); calendarError.value = null; return }
        if (!force && System.currentTimeMillis() - calendarAt < 30 * 60_000L) return
        calendarAt = System.currentTimeMillis()
        app.scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val urls = store.secret("calendar.ics").split('\n', ' ').map { it.trim() }.filter { it.startsWith("http") }
                    val zone = ZoneId.systemDefault()
                    // 今週の月曜の 4 週前から 20 週（帯は 2 週、カレンダーは 4 週を 4 週前〜12 週先まで動かせる）
                    val today = ZonedDateTime.now(zone).toLocalDate()
                    val from = today.minusDays((today.dayOfWeek.value - 1).toLong()).minusWeeks(4).atStartOfDay(zone)
                    urls.flatMap { u -> Ics.events(Net.get(u.replaceFirst("webcal://", "https://")), from, 140, zone) }.sortedBy { it.start }
                }
            }.onSuccess { events.value = it; calendarError.value = null }
                .onFailure { calendarAt = 0; calendarError.value = it.message ?: "読めません" }
        }
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
