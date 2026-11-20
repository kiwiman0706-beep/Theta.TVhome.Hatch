package app.thetahatch.tvhome

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.work.Configuration
import app.thetahatch.tvhome.data.Store
import app.thetahatch.tvhome.hub.Catalog
import app.thetahatch.tvhome.hub.UpdateWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class TvHomeApp : Application(), Configuration.Provider {
    lateinit var store: Store
        private set
    lateinit var state: HomeState
        private set
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override val workManagerConfiguration: Configuration get() = Configuration.Builder().build()

    /** セーフモード：ネット・写真・動き・再生中を止める（レスキュー画面から）。 */
    @Volatile var safeMode = false

    override fun onCreate() {
        super.onCreate()
        val proc = if (android.os.Build.VERSION.SDK_INT >= 28) Application.getProcessName() else ""
        // 再生のプロセス（":player"）は、落ちてもホームのレスキューに数えない。本体の準備もしない
        if (proc.endsWith(":player")) return
        app.thetahatch.tvhome.rescue.Rescue.install(this)
        // レスキューのプロセスでは本体の準備をしない（本体が壊れていても開けるように）
        if (android.os.Build.VERSION.SDK_INT >= 28 && Application.getProcessName().endsWith(":rescue")) return
        app.thetahatch.tvhome.rescue.Rescue.watchMainThread(this)
        store = Store(this)
        state = HomeState(this)
        Catalog.load(this)
        UpdateWorker.schedule(this)
        runCatching { app.thetahatch.tvhome.data.NetStatus.start(this) }
        runCatching { app.thetahatch.tvhome.media.NowPlaying.start(this) }
    }

    /** 設定が外（ADB・ほかのアプリ）から変わった。 */
    fun settingsChanged() = Handler(Looper.getMainLooper()).post { state.reloadSettings() }

    fun toast(text: String) = Handler(Looper.getMainLooper()).post { Toast.makeText(this, text, Toast.LENGTH_SHORT).show() }
}
