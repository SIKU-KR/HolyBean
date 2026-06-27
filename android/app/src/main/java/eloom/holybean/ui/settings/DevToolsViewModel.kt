package eloom.holybean.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import eloom.holybean.data.repository.FirestoreRepository
import eloom.holybean.diag.NetworkStatusProvider
import eloom.holybean.printer.PiPrintClient
import eloom.holybean.printer.network.PrinterAddressResolver
import eloom.holybean.printer.network.PrinterStatus
import eloom.holybean.printer.transport.PrintTransportSelector
import eloom.holybean.printer.transport.PrinterTransportStore
import eloom.holybean.ui.printer.toDisplayText
import eloom.holybean.util.launchSafely
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class DevToolsViewModel @Inject constructor(
    private val piPrintClient: PiPrintClient,
    private val firestoreRepository: FirestoreRepository,
    private val networkStatusProvider: NetworkStatusProvider,
    private val printerAddressResolver: PrinterAddressResolver,
    private val transportSelector: PrintTransportSelector,
    private val transportStore: PrinterTransportStore,
) : ViewModel() {
    data class State(
        val printerOk: Boolean? = null,
        val printerLatencyMs: Long? = null,
        val networkOk: Boolean? = null,
        val networkInfo: String = "",
        val firestoreOk: Boolean? = null,
        val printerStatusText: String = "확인 전",
        val transportMethodText: String = "확인 전",
        val forcePi: Boolean = false,
    )

    private val _uiState = MutableStateFlow(State())
    val uiState: StateFlow<State> = _uiState.asStateFlow()

    // One-shot events (ShowToast) must not replay to new subscribers
    // (e.g. screen re-entry would re-fire a stale toast). replay = 0; tryEmit still
    // buffers via extraBufferCapacity while the screen is actively collecting.
    private val _uiEvent = MutableSharedFlow<DevToolsUiEvent>(
        replay = 0,
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val uiEvent: SharedFlow<DevToolsUiEvent> = _uiEvent.asSharedFlow()

    sealed class DevToolsUiEvent {
        data class ShowToast(val message: String) : DevToolsUiEvent()
    }

    init {
        printerAddressResolver.status
            .onEach { status -> _uiState.update { it.copy(printerStatusText = status.toDisplay()) } }
            .launchIn(viewModelScope)
        transportSelector.selection
            .onEach { selection ->
                _uiState.update {
                    it.copy(
                        transportMethodText = selection.toDisplayText(),
                        forcePi = transportStore.forcePi,
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    private fun PrinterStatus.toDisplay(): String = when (this) {
        is PrinterStatus.Connected -> "연결됨 ${address.toAuthority()}"
        PrinterStatus.Resolving -> "탐색 중…"
        PrinterStatus.Unreachable -> "연결 안 됨"
        PrinterStatus.Unknown -> "확인 전"
    }

    fun rescanPrinter() {
        viewModelScope.launchSafely(onError = {
            _uiEvent.tryEmit(DevToolsUiEvent.ShowToast("프린터 탐색 실패"))
        }) {
            transportSelector.probe()
            printerAddressResolver.rediscover()
        }
    }

    fun setForcePi(force: Boolean) {
        transportStore.forcePi = force
        viewModelScope.launchSafely(onError = {}) {
            transportSelector.probe()
        }
    }

    fun setPrinterOverride(value: String?) {
        viewModelScope.launchSafely(onError = {
            _uiEvent.tryEmit(DevToolsUiEvent.ShowToast("주소 저장 실패"))
        }) {
            printerAddressResolver.setManualOverride(value?.takeIf { it.isNotBlank() })
            _uiEvent.tryEmit(DevToolsUiEvent.ShowToast("프린터 주소를 저장했습니다"))
        }
    }

    fun refresh() {
        viewModelScope.launchSafely(onError = {
            _uiEvent.tryEmit(DevToolsUiEvent.ShowToast("진단 중 오류가 발생했습니다"))
        }) {
            val net = networkStatusProvider.current()
            _uiState.update { it.copy(networkOk = net.connected, networkInfo = net.info) }

            val start = System.currentTimeMillis()
            transportSelector.probe()
            val printerHealthy = piPrintClient.checkHealth()
            val latency = System.currentTimeMillis() - start
            _uiState.update { it.copy(printerOk = printerHealthy, printerLatencyMs = latency) }

            val fs = firestoreRepository.checkConnection()
            _uiState.update { it.copy(firestoreOk = fs) }
        }
    }

    fun testPrint() {
        viewModelScope.launchSafely(onError = { e ->
            _uiEvent.tryEmit(DevToolsUiEvent.ShowToast("테스트 출력 실패: ${e.message}"))
        }) {
            piPrintClient.printTestReceipt()
            _uiEvent.tryEmit(DevToolsUiEvent.ShowToast("테스트 영수증을 출력했습니다"))
        }
    }
}
