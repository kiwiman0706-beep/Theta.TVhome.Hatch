package app.thetahatch.tvhome.music

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import app.thetahatch.tvhome.media.NowPlaying
import app.thetahatch.tvhome.nas.Dsm
import app.thetahatch.tvhome.nas.NasEntry
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** ホームの側から、裏の音楽（PlayerService）を操作する。 */
class Music(private val context: Context, private val dsm: Dsm) {
    @Volatile private var controller: MediaController? = null

    private suspend fun controller(): MediaController {
        controller?.takeIf { it.isConnected }?.let { return it }
        val token = SessionToken(context, ComponentName(context, PlayerService::class.java))
        val c = withContext(Dispatchers.Main) {
            val f = MediaController.Builder(context, token).buildAsync()
            kotlinx.coroutines.suspendCancellableCoroutine<MediaController> { cont ->
                f.addListener({ runCatching { f.get() }.onSuccess { cont.resumeWith(Result.success(it)) }.onFailure { cont.resumeWith(Result.failure(it)) } }, MoreExecutors.directExecutor())
            }
        }
        c.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) = publish(player)
        })
        controller = c
        return c
    }

    /** そのフォルダの曲を、start の曲から鳴らす。 */
    suspend fun playFolder(folder: String, folderName: String, songs: List<NasEntry>, start: Int, cover: NasEntry?) {
        val items = withContext(Dispatchers.IO) {
            val art = cover?.let { Uri.parse(dsm.downloadUrl(it.path)) }
            songs.map { s ->
                MediaItem.Builder().setMediaId(s.path).setUri(dsm.downloadUrl(s.path))
                    .setMediaMetadata(MediaMetadata.Builder().setTitle(MusicLib.title(s.name)).setAlbumTitle(folderName)
                        .setArtist(folder.trimEnd('/').substringBeforeLast('/').substringAfterLast('/').removePrefix("▶"))
                        .setArtworkUri(art).build())
                    .build()
            }
        }
        val c = controller()
        withContext(Dispatchers.Main) {
            c.setMediaItems(items, start, 0)
            c.prepare()
            c.play()
        }
    }

    suspend fun toggle() { val c = controller(); withContext(Dispatchers.Main) { if (c.isPlaying) c.pause() else c.play() } }
    suspend fun next() { val c = controller(); withContext(Dispatchers.Main) { c.seekToNextMediaItem() } }
    suspend fun prev() { val c = controller(); withContext(Dispatchers.Main) { c.seekToPreviousMediaItem() } }
    suspend fun stop() { val c = controller(); withContext(Dispatchers.Main) { c.stop(); c.clearMediaItems() } }

    /** 自分の再生を左上に（通知へのアクセスが無くても出るように）。 */
    private fun publish(p: Player) {
        val m = p.mediaMetadata
        val title = m.title?.toString()
        NowPlaying.setOwn(if (title.isNullOrBlank() || p.playbackState == Player.STATE_IDLE) null else
            app.thetahatch.tvhome.media.Playing(title, listOfNotNull(m.artist?.toString(), m.albumTitle?.toString()).filter { it.isNotBlank() }.joinToString(" ・ "),
                null, context.packageName, "おんがく", p.isPlaying, p.currentPosition, p.duration.coerceAtLeast(0), System.currentTimeMillis()))
    }
}
