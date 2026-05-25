package eloom.holybean.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import eloom.holybean.BuildConfig
import eloom.holybean.data.repository.FirestoreRepository
import eloom.holybean.diag.NetworkStatusProvider
import eloom.holybean.printer.PiPrintClient
import eloom.holybean.util.launchSafely
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class DevToolsViewModel @Inject constructor(
    private val piPrintClient: PiPrintClient,
    private val firestoreRepository: FirestoreRepository,
    private val networkStatusProvider: NetworkStatusProvider,
) : ViewModel() {
    data class State(
        val printerOk: Boolean? = null,
        val printerLatencyMs: Long? = null,
        val networkOk: Boolean? = null,
        val networkInfo: String = "",
        val firestoreOk: Boolean? = null,
        val printerUrl: String = BuildConfig.PRINT_SERVER_URL,
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

    fun refresh() {
        viewModelScope.launchSafely(onError = {
            _uiEvent.tryEmit(DevToolsUiEvent.ShowToast("진단 중 오류가 발생했습니다"))
        }) {
            val net = networkStatusProvider.current()
            _uiState.update { it.copy(networkOk = net.connected, networkInfo = net.info) }

            val start = System.currentTimeMillis()
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
