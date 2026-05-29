package eloom.holybean.printer.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pi 주소 단일 해석기. 우선순위: 수동 override > 영속 lastGood > mDNS 재탐색.
 * current()는 인메모리 캐시를 동기 반환(인터셉터가 매 요청에서 블로킹 없이 읽음).
 */
@Singleton
class PrinterAddressResolver @Inject constructor(
    private val store: PrinterAddressStore,
    private val discovery: MdnsDiscovery,
) {
    @Volatile
    private var cached: PrinterAddress? = null

    private val _status = MutableStateFlow<PrinterStatus>(PrinterStatus.Unknown)
    val status: StateFlow<PrinterStatus> = _status.asStateFlow()

    init {
        cached = PrinterAddress.parse(store.override ?: store.lastGood)
        cached?.let { _status.value = PrinterStatus.Connected(it) }
    }

    /** 인메모리 캐시. 인터셉터 동기 호출용. */
    fun current(): PrinterAddress? = cached

    /** override > lastGood 시드 후, override 없으면 mDNS 탐색. 성공 시 lastGood 영속화. */
    suspend fun rediscover(): PrinterAddress? {
        PrinterAddress.parse(store.override)?.let { setCache(it); return it }

        _status.value = PrinterStatus.Resolving
        val found = discovery.discover(DISCOVERY_TIMEOUT_MS)
        if (found != null) {
            store.lastGood = found.toAuthority()
            setCache(found)
            return found
        }
        // 탐색 실패: 기존 캐시 유지(stale), 없으면 Unreachable.
        _status.value = cached?.let { PrinterStatus.Connected(it) } ?: PrinterStatus.Unreachable
        return cached
    }

    /** 수동 주소 설정/해제. null이면 lastGood로 폴백. */
    fun setManualOverride(value: String?) {
        store.override = value
        val parsed = PrinterAddress.parse(value) ?: PrinterAddress.parse(store.lastGood)
        if (parsed != null) {
            setCache(parsed)
        } else {
            cached = null
            _status.value = PrinterStatus.Unknown
        }
    }

    private fun setCache(addr: PrinterAddress) {
        cached = addr
        _status.value = PrinterStatus.Connected(addr)
    }

    companion object {
        const val DISCOVERY_TIMEOUT_MS = 4_000L
    }
}
