package eloom.holybean.printer.transport

import android.hardware.usb.UsbDevice
import eloom.holybean.printer.network.PrintCommandDto
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

class FakePrintTransport(
    var onPrint: (suspend (List<PrintCommandDto>) -> Unit)? = null,
    private val healthResult: Boolean = true,
    var fastFail: FastFailReason? = null,
    // probeFastFail 도중 임의 지연/예외를 흉내낸다 (결과는 호출 시점의 fastFail로 고정)
    var onProbe: (suspend () -> Unit)? = null,
) : PrintTransport {

    var printCalls = 0
        private set
    var probeCalls = 0
        private set
    var lastProbeRequestPermission: Boolean? = null
        private set

    override suspend fun print(commands: List<PrintCommandDto>) {
        printCalls++
        onPrint?.invoke(commands)
    }

    override suspend fun checkHealth(): Boolean = healthResult

    override suspend fun probeFastFail(requestPermission: Boolean): FastFailReason? {
        probeCalls++
        lastProbeRequestPermission = requestPermission
        val result = fastFail
        onProbe?.invoke()
        return result
    }
}

class CountingFailTransport(
    private val failFirst: Int,
    private val error: () -> Throwable,
) : PrintTransport {

    var printCalls = 0
        private set

    override suspend fun print(commands: List<PrintCommandDto>) {
        printCalls++
        if (printCalls <= failFirst) throw error()
    }

    override suspend fun checkHealth(): Boolean = true

    override suspend fun probeFastFail(requestPermission: Boolean): FastFailReason? = null
}

class FakeUsbPermissionRequester : UsbPermissionRequester {

    private val _permissionGrants = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val permissionGrants: SharedFlow<Unit> = _permissionGrants

    override fun requestPermission(device: UsbDevice) = Unit

    override fun onPermissionResult(granted: Boolean) {
        if (granted) _permissionGrants.tryEmit(Unit)
    }
}
