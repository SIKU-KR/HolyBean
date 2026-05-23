package eloom.holybean.ui.startup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import eloom.holybean.data.repository.FirestoreRepository
import eloom.holybean.data.repository.MenuRepository
import eloom.holybean.printer.PiPrintClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Named

enum class StepStatus { Loading, Success, Failed }

@HiltViewModel
class StartupViewModel @Inject constructor(
    private val menuRepository: MenuRepository,
    private val firestoreRepository: FirestoreRepository,
    private val piPrintClient: PiPrintClient,
    @Named("IO") private val ioDispatcher: CoroutineDispatcher,
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

    init { check() }

    /** 두 작업을 병렬 실행. retry 시 동일 로직으로 재실행. */
    fun check() {
        _uiState.update { it.copy(data = StepStatus.Loading, printer = StepStatus.Loading) }
        viewModelScope.launch(ioDispatcher) { loadData() }
        viewModelScope.launch(ioDispatcher) { checkPrinter() }
    }

    fun retry() = check()

    private suspend fun loadData() {
        // 메뉴 페치가 예외 없이 끝나고(캐시도 채워짐) 주문번호가 유효(>0)하면 성공.
        // getOrderNumber()는 실패 시 예외 대신 -1을 반환한다.
        val ok = runCatching {
            menuRepository.getMenuListSync()
            firestoreRepository.getOrderNumber() > 0
        }.getOrDefault(false)
        _uiState.update { it.copy(data = if (ok) StepStatus.Success else StepStatus.Failed) }
    }

    private suspend fun checkPrinter() {
        val ok = piPrintClient.checkHealth()
        _uiState.update { it.copy(printer = if (ok) StepStatus.Success else StepStatus.Failed) }
    }
}
