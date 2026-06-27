package eloom.holybean.printer.transport

import eloom.holybean.config.FeatureFlags
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class TransportSelection(
    val method: PrintMethod,
    val fallbackReason: FastFailReason? = null,
)

@Singleton
class PrintTransportSelector @Inject constructor(
    @eloom.holybean.di.UsbTransport private val usb: PrintTransport,
    @eloom.holybean.di.PiTransport private val pi: PrintTransport,
    private val store: PrinterTransportStore,
) {
    private val _selection = MutableStateFlow(TransportSelection(PrintMethod.PI_HTTP))
    val selection: StateFlow<TransportSelection> = _selection.asStateFlow()

    private var usbDisabled = false

    fun requireActive(): PrintTransport = when (_selection.value.method) {
        PrintMethod.USB_DIRECT -> usb
        PrintMethod.PI_HTTP -> pi
    }

    suspend fun probe() {
        if (!FeatureFlags.useUsbDirect || store.forcePi || usbDisabled) {
            selectPi(if (store.forcePi || usbDisabled) FastFailReason.DISABLED else null)
            return
        }
        val fastFail = usb.probeFastFail()
        if (fastFail == null) {
            _selection.value = TransportSelection(PrintMethod.USB_DIRECT)
        } else {
            selectPi(fastFail)
        }
    }

    suspend fun reprobeOnFastFail(reason: FastFailReason): PrintTransport {
        selectPi(reason)
        return pi
    }

    fun disableUsbForSession() {
        usbDisabled = true
        selectPi(FastFailReason.DISABLED)
    }

    private fun selectPi(reason: FastFailReason?) {
        _selection.value = TransportSelection(PrintMethod.PI_HTTP, reason)
    }
}
