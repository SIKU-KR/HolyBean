package eloom.holybean.ui.credits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import eloom.holybean.data.model.CreditItem
import eloom.holybean.data.model.OrdersDetailItem
import eloom.holybean.data.repository.LambdaRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Named

@HiltViewModel
class CreditsViewModel @Inject constructor(
    private val lambdaRepository: LambdaRepository,
    @Named("IO") private val dispatcher: CoroutineDispatcher
) : ViewModel() {

    data class CreditsUiState(
        val creditsList: List<CreditItem> = emptyList(),
        val selectedOrderNumber: Int = 0,
        val selectedOrderTotal: Int = 0,
        val selectedOrderDate: String = "",
        val orderDetails: List<OrdersDetailItem> = emptyList(),
        val isLoading: Boolean = false
    )

    sealed class CreditsUiEvent {
        data class ShowToast(val message: String) : CreditsUiEvent()
        object RefreshCredits : CreditsUiEvent()
    }

    private val _uiState = MutableStateFlow(CreditsUiState())
    val uiState: StateFlow<CreditsUiState> = _uiState.asStateFlow()

    private val _uiEvent = MutableSharedFlow<CreditsUiEvent>(
        replay = 1,
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val uiEvent: SharedFlow<CreditsUiEvent> = _uiEvent.asSharedFlow()

    init {
        loadCredits()
    }

    fun loadCredits() {
        viewModelScope.launch(dispatcher) {
            try {
                _uiState.update { it.copy(isLoading = true) }
                val creditsList = lambdaRepository.getCreditsList()
                _uiState.update {
                    it.copy(
                        creditsList = creditsList,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false) }
                _uiEvent.tryEmit(CreditsUiEvent.ShowToast("외상 목록을 불러오는 중 오류가 발생했습니다: ${e.message}"))
            }
        }
    }

    fun selectOrder(orderNumber: Int, totalAmount: Int, date: String) {
        _uiState.update {
            it.copy(
                selectedOrderNumber = orderNumber,
                selectedOrderTotal = totalAmount,
                selectedOrderDate = date,
                orderDetails = emptyList() // Clear previous details
            )
        }
    }

    fun fetchOrderDetail() {
        val currentState = _uiState.value
        if (currentState.selectedOrderNumber == 0) {
            viewModelScope.launch(dispatcher) {
                _uiEvent.tryEmit(CreditsUiEvent.ShowToast("주문을 선택해주세요"))
            }
            return
        }

        viewModelScope.launch(dispatcher) {
            try {
                val fetchedBasketList = lambdaRepository.getOrderDetail(
                    currentState.selectedOrderDate,
                    currentState.selectedOrderNumber
                )
                if (fetchedBasketList.isEmpty()) {
                    _uiEvent.tryEmit(CreditsUiEvent.ShowToast("주문 내역이 없습니다."))
                } else {
                    _uiState.update { it.copy(orderDetails = fetchedBasketList) }
                }
            } catch (e: Exception) {
                _uiEvent.tryEmit(CreditsUiEvent.ShowToast("주문 조회 중 오류가 발생했습니다: ${e.message}"))
            }
        }
    }

    fun handleDeleteButton() {
        val currentState = _uiState.value
        if (currentState.selectedOrderNumber == 0) {
            viewModelScope.launch(dispatcher) {
                _uiEvent.tryEmit(CreditsUiEvent.ShowToast("주문을 선택해주세요"))
            }
            return
        }

        viewModelScope.launch(dispatcher) {
            try {
                lambdaRepository.setCreditOrderPaid(currentState.selectedOrderDate, currentState.selectedOrderNumber)
                _uiEvent.tryEmit(CreditsUiEvent.ShowToast("외상이 성공적으로 처리되었습니다."))
                _uiEvent.tryEmit(CreditsUiEvent.RefreshCredits)
            } catch (e: Exception) {
                _uiEvent.tryEmit(CreditsUiEvent.ShowToast("외상 처리 중 오류가 발생했습니다: ${e.message}"))
            }
        }
    }
}