package app.thetahatch.tvhome.media

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** いま再生中のもの（左上に出す）。 */
data class Playing(
    val title: String,
    val sub: String,
    val art: Bitmap?,
    val pkg: String,
    val appLabel: String,
    val playing: Boolean,
    val position: Long,
    val duration: Long,
    val at: Long,
)

/**
 * Android のメディアセッションから、いま再生中の曲・番組を読む。
 * 読むには「通知へのアクセス」の許可が要る（テレビの画面では出せないことが多いので ADB：
 * `adb shell cmd notification allow_listener app.thetahatch.tvhome/app.thetahatch.tvhome.media.MediaListener`）。
 * 許可が無ければ何も出さない（左上は名前とあいさつ）。
 */
object NowPlaying {
    private val _state = MutableStateFlow<Playing?>(null)
    val state: StateFlow<Playing?> = _state
    private var started = false
    private var controller: MediaController? = null
    private val main = Handler(Looper.getMainLooper())

    private val cb = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onSessionDestroyed() { controller = null; _state.value = null }
    }

    private lateinit var ctx: Context

    fun start(context: Context) {
        if (started) return
        ctx = context.applicationContext
        val msm = ctx.getSystemService(MediaSessionManager::class.java) ?: return
        val cn = ComponentName(ctx, MediaListener::class.java)
        runCatching {
            msm.addOnActiveSessionsChangedListener({ list -> pick(list) }, cn, main)
            pick(msm.getActiveSessions(cn))
            started = true
        } // 許可が無いと SecurityException：何もしない
    }

    private fun pick(list: List<MediaController>?) {
        val c = list?.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: list?.firstOrNull()
        if (c?.sessionToken == controller?.sessionToken) return
        controller?.unregisterCallback(cb)
        controller = c
        c?.registerCallback(cb, main)
        publish()
    }

    private fun publish() {
        val c = controller ?: run { _state.value = null; return }
        val m = c.metadata ?: run { _state.value = null; return }
        val st = c.playbackState
        val title = m.getString(MediaMetadata.METADATA_KEY_TITLE) ?: m.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
        if (title.isNullOrBlank()) { _state.value = null; return }
        val sub = listOfNotNull(m.getString(MediaMetadata.METADATA_KEY_ARTIST), m.getString(MediaMetadata.METADATA_KEY_ALBUM)).filter { it.isNotBlank() }.joinToString(" ・ ")
        val art = m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: m.getBitmap(MediaMetadata.METADATA_KEY_ART) ?: m.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        val label = runCatching { ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(c.packageName, 0)).toString() }.getOrDefault(c.packageName)
        _state.value = Playing(
            title, sub, art?.let { Bitmap.createScaledBitmap(it, 128, 128 * it.height / it.width.coerceAtLeast(1), true) }, c.packageName, label,
            st?.state == PlaybackState.STATE_PLAYING, st?.position ?: 0, m.getLong(MediaMetadata.METADATA_KEY_DURATION), System.currentTimeMillis(),
        )
    }

    /** 試験用。 */
    fun setForTest(p: Playing?) { _state.value = p }
}

/** メディアセッションを読むための入口（通知そのものは使わない）。 */
class MediaListener : NotificationListenerService()
