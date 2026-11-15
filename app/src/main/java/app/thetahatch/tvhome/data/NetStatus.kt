package app.thetahatch.tvhome.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** ネットの状態（ホームの右下・下の帯に出す）。 */
data class NetInfo(
    /** wifi・ethernet・none */
    val kind: String = "none",
    /** インターネットに出られるか（Android が確かめた） */
    val online: Boolean = false,
    /** VPN（Tailscale など）がつながっている */
    val vpn: Boolean = false,
    /** Wi-Fi の強さ 0〜4（わからなければ -1） */
    val bars: Int = -1,
    /** 下りの速さの目安（Mbps、わからなければ 0） */
    val downMbps: Int = 0,
)

/** ConnectivityManager の知らせを受けるだけ（定期的に調べない）。 */
object NetStatus {
    private val _state = MutableStateFlow(NetInfo())
    val state: StateFlow<NetInfo> = _state
    private var started = false

    /** 試験用。 */
    fun setForTest(n: NetInfo) { _state.value = n }

    fun start(context: Context) {
        if (started) return
        started = true
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) { update(cm) }
            override fun onLost(network: Network) { update(cm) }
            override fun onAvailable(network: Network) { update(cm) }
        }
        runCatching { cm.registerDefaultNetworkCallback(cb) }
        update(cm)
    }

    private fun update(cm: ConnectivityManager) {
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        if (caps == null) { _state.value = NetInfo(); return }
        val vpn = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ||
            cm.allNetworks.any { cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true }
        val kind = when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "mobile"
            else -> if (vpn) "vpn" else "other"
        }
        val sig = if (android.os.Build.VERSION.SDK_INT >= 29) caps.signalStrength.takeIf { it != NetworkCapabilities.SIGNAL_STRENGTH_UNSPECIFIED } else null
        val bars = sig?.let { rssiBars(it) } ?: -1
        _state.value = NetInfo(kind, caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED), vpn, bars, caps.linkDownstreamBandwidthKbps / 1000)
    }

    /** RSSI（dBm）→ 0〜4。 */
    fun rssiBars(rssi: Int) = when {
        rssi >= -55 -> 4
        rssi >= -66 -> 3
        rssi >= -77 -> 2
        rssi >= -88 -> 1
        else -> 0
    }
}
