package app.thetahatch.adb

import android.app.Service
import android.content.Intent
import android.os.IBinder
import app.thetahatch.tvhome.TvHomeApp
import java.io.FileDescriptor
import java.io.PrintWriter

/**
 * ADB の窓口：状態（THETA_ADB_SPEC §3.1）。1 行の JSON を返す。秘密は有無だけ。
 *   adb shell dumpsys activity service app.thetahatch.tvhome/app.thetahatch.adb.StatusService
 *   adb shell dumpsys activity service app.thetahatch.tvhome/app.thetahatch.adb.StatusService probe   （テレビの入口を調べる）
 */
class StatusService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun dump(fd: FileDescriptor?, writer: PrintWriter, args: Array<out String>?) {
        val app = applicationContext as TvHomeApp
        val json = if (args?.firstOrNull() == "probe") Status.probe(app) else Status.status(app)
        writer.println(json.toString())
        writer.flush()
    }
}
