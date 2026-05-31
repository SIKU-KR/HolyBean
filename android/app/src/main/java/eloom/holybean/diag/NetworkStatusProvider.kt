package eloom.holybean.diag

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

data class NetworkStatus(val connected: Boolean, val info: String)

interface NetworkStatusProvider {
    fun current(): NetworkStatus
}

class AndroidNetworkStatusProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) : NetworkStatusProvider {
    override fun current(): NetworkStatus {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return NetworkStatus(false, "알 수 없음")
        val network = cm.activeNetwork ?: return NetworkStatus(false, "연결 없음")
        val caps = cm.getNetworkCapabilities(network) ?: return NetworkStatus(false, "연결 없음")
        val connected = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val type = when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "셀룰러"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "이더넷"
            else -> "기타"
        }
        return NetworkStatus(connected, if (connected) type else "연결 없음")
    }
}
