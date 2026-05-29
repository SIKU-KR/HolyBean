package eloom.holybean.printer.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import kotlin.coroutines.resume

/** mDNS(Bonjour/avahi)로 Pi 인쇄서버를 탐색한다. */
interface MdnsDiscovery {
    /** 성공 시 PrinterAddress, 타임아웃/미발견 시 null. */
    suspend fun discover(timeoutMs: Long): PrinterAddress?
}

class NsdMdnsDiscovery @Inject constructor(
    @ApplicationContext private val context: Context,
) : MdnsDiscovery {

    override suspend fun discover(timeoutMs: Long): PrinterAddress? {
        val nsd = context.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return null
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val lock = wifi?.createMulticastLock("holybean-mdns")?.apply {
            setReferenceCounted(false)
            acquire()
        }
        return try {
            withTimeoutOrNull(timeoutMs) { runDiscovery(nsd) }
        } catch (e: TimeoutCancellationException) {
            null
        } finally {
            lock?.takeIf { it.isHeld }?.release()
        }
    }

    private suspend fun runDiscovery(nsd: NsdManager): PrinterAddress? =
        suspendCancellableCoroutine { cont ->
            var resumed = false
            fun finish(result: PrinterAddress?) {
                if (!resumed) {
                    resumed = true
                    if (cont.isActive) cont.resume(result)
                }
            }

            val resolveListener = object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    Log.w(TAG, "resolve failed: $errorCode")
                    finish(null)
                }

                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    @Suppress("DEPRECATION")
                    val host = serviceInfo.host?.hostAddress
                    val port = serviceInfo.port
                    finish(host?.let { PrinterAddress(it, port) })
                }
            }

            val discoveryListener = object : NsdManager.DiscoveryListener {
                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) = finish(null)
                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
                override fun onDiscoveryStarted(serviceType: String) {}
                override fun onDiscoveryStopped(serviceType: String) {}
                override fun onServiceLost(serviceInfo: NsdServiceInfo) {}
                override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                    @Suppress("DEPRECATION")
                    nsd.resolveService(serviceInfo, resolveListener)
                }
            }

            nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
            cont.invokeOnCancellation {
                runCatching { nsd.stopServiceDiscovery(discoveryListener) }
            }
        }

    private companion object {
        const val TAG = "NsdMdnsDiscovery"
        const val SERVICE_TYPE = "_holybean-print._tcp."
    }
}
