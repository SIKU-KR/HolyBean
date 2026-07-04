package eloom.holybean.printer.transport

import eloom.holybean.config.FeatureFlags
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    permissionRequester: UsbPermissionRequester,
    @eloom.holybean.di.AppScope appScope: CoroutineScope,
) {
    private val _selection = MutableStateFlow(TransportSelection(PrintMethod.PI_HTTP))
    val selection: StateFlow<TransportSelection> = _selection.asStateFlow()

    init {
        // 사용자가 USB 권한 다이얼로그에서 허용하면 즉시 재탐색해 USB로 전환한다.
        // probe가 예기치 못한 예외(OEM USB 스택 등)를 던져도 수집기가 죽지 않아야
        // 이후의 권한 허용이 계속 전환을 트리거할 수 있다.
        appScope.launch {
            permissionRequester.permissionGrants.collect { runCatching { probe() } }
        }
    }

    fun requireActive(): PrintTransport = when (_selection.value.method) {
        PrintMethod.USB_DIRECT -> usb
        PrintMethod.PI_HTTP -> pi
    }

    // 동시 probe(권한 수집기, 출력 직전 재탐색, 스플래시, DevTools)가 뒤섞일 때
    // 오래된 탐색 결과가 최신 선택을 덮어쓰지 않도록 탐색과 선택 갱신을 한 단위로 직렬화한다
    private val probeMutex = Mutex()

    suspend fun probe(requestPermission: Boolean = true) = probeMutex.withLock {
        if (!FeatureFlags.useUsbDirect || store.forcePi) {
            selectPi(if (store.forcePi) FastFailReason.DISABLED else null)
            return@withLock
        }
        val fastFail = usb.probeFastFail(requestPermission)
        if (fastFail == null) {
            _selection.value = TransportSelection(PrintMethod.USB_DIRECT)
        } else {
            selectPi(fastFail)
        }
    }

    // 출력 직전 USB 재고려: 일시적 장애로 Pi에 폴백된 상태가 세션 내내 고정되지 않게 한다.
    // 출력 경로에서는 권한 다이얼로그를 절대 띄우지 않는다.
    suspend fun reprobeForPrint() {
        if (_selection.value.method == PrintMethod.PI_HTTP && FeatureFlags.useUsbDirect && !store.forcePi) {
            probe(requestPermission = false)
        }
    }

    fun fallbackToPi(reason: FastFailReason): PrintTransport {
        selectPi(reason)
        return pi
    }

    private fun selectPi(reason: FastFailReason?) {
        _selection.value = TransportSelection(PrintMethod.PI_HTTP, reason)
    }
}
