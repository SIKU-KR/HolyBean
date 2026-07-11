package eloom.holybean.printer.transport

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * USB 직연결 프린터의 유일한 전송 경로 홀더 겸 프로버.
 * 권한 다이얼로그 허용을 감지해 연결을 재프로브(워밍업)한다.
 */
@Singleton
class PrintTransportSelector @Inject constructor(
    @eloom.holybean.di.UsbTransport private val usb: PrintTransport,
    permissionRequester: UsbPermissionRequester,
    @eloom.holybean.di.AppScope appScope: CoroutineScope,
) {
    // 동시 probe(권한 수집기, 출력 직전 재탐색, 스플래시, DevTools)가 같은 USB 장치를
    // 동시에 열지 않도록 직렬화한다.
    private val probeMutex = Mutex()

    init {
        // 사용자가 USB 권한 다이얼로그에서 허용하면 즉시 재프로브해 연결을 워밍업한다.
        // probe가 예기치 못한 예외(OEM USB 스택 등)를 던져도 수집기가 죽지 않아야
        // 이후의 권한 허용이 계속 재프로브를 트리거할 수 있다.
        appScope.launch {
            permissionRequester.permissionGrants.collect { runCatching { probe() } }
        }
    }

    fun requireActive(): PrintTransport = usb

    /** USB 연결을 프로브한다. 실패해도 예외를 던지지 않고 사유(null=정상)를 반환한다. */
    suspend fun probe(requestPermission: Boolean = true): FastFailReason? = probeMutex.withLock {
        usb.probeFastFail(requestPermission)
    }
}
