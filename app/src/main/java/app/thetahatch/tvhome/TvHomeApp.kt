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

    override fun onCreate() {
        super.onCreate()
        store = Store(this)
        state = HomeState(this)
        Catalog.load(this)
        UpdateWorker.schedule(this)
        app.thetahatch.tvhome.data.NetStatus.start(this)
    }

    /** 設定が外（ADB・ほかのアプリ）から変わった。 */
    fun settingsChanged() = Handler(Looper.getMainLooper()).post { state.reloadSettings() }

    fun toast(text: String) = Handler(Looper.getMainLooper()).post { Toast.makeText(this, text, Toast.LENGTH_SHORT).show() }
}
