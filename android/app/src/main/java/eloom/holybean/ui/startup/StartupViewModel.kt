package eloom.holybean.ui.startup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import eloom.holybean.data.repository.FirestoreRepository
import eloom.holybean.data.repository.MenuRepository
import eloom.holybean.printer.PrintClient
import eloom.holybean.printer.transport.PrintTransportSelector
import eloom.holybean.util.launchSafely
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

enum class StepStatus { Loading, Success, Failed }

@HiltViewModel
class StartupViewModel @Inject constructor(
    private val menuRepository: MenuRepository,
    private val firestoreRepository: FirestoreRepository,
    private val printClient: PrintClient,
    private val transportSelector: PrintTransportSelector,
) : ViewModel() {

    data class UiState(
        val data: StepStatus = StepStatus.Loading,
        val printer: StepStatus = StepStatus.Loading,
    ) {
        /** 데이터가 성공이면 진입 가능(프린터는 경고일 뿐 진입을 막지 않음). */
        val canEnter: Boolean get() = data == StepStatus.Success
        /** 데이터·프린터 모두 성공이면 자동 진입. */
        val autoEnter: Boolean get() = data == StepStatus.Success && printer == StepStatus.Success
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        check()
    }

    /** 두 작업을 병렬 실행. retry 시 동일 로직으로 재실행. */
    fun check() {
        _uiState.update { it.copy(data = StepStatus.Loading, printer = StepStatus.Loading) }
        viewModelScope.launchSafely(onError = {
            _uiState.update { it.copy(data = StepStatus.Failed) }
        }) {
            menuRepository.getMenuListSync()                 // 실패 시 throw → onError → Failed
            val ok = firestoreRepository.getOrderNumber() > 0
            _uiState.update { it.copy(data = if (ok) StepStatus.Success else StepStatus.Failed) }
        }
        viewModelScope.launchSafely(onError = {
            _uiState.update { it.copy(printer = StepStatus.Failed) }
        }) {
            transportSelector.probe()                        // USB 권한 요청 + 연결 워밍업
            val ok = printClient.checkHealth()               // checkHealth 는 throw 안 함(Boolean)
            _uiState.update { it.copy(printer = if (ok) StepStatus.Success else StepStatus.Failed) }
        }
    }

    fun retry() = check()
}
