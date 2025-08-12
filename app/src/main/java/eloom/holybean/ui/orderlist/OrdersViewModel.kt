package eloom.holybean.ui.orderlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import eloom.holybean.data.model.OrderItem
import eloom.holybean.data.model.OrdersDetailItem
import eloom.holybean.data.repository.LambdaRepository
import eloom.holybean.printer.OrdersPrinter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.BufferOverflow
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@HiltViewModel
class OrdersViewModel @Inject constructor(
    private val lambdaRepository: LambdaRepository,
    private val dispatcher: CoroutineDispatcher
) : ViewModel() {

    private val _uiState = MutableStateFlow(OrdersUiState())
    val uiState: StateFlow<OrdersUiState> = _uiState.asStateFlow()

    private val _uiEvent = MutableSharedFlow<OrdersUiEvent>(
        replay = 1,
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val uiEvent: SharedFlow<OrdersUiEvent> = _uiEvent.asSharedFlow()

    data class OrdersUiState(
        val ordersList: List<OrderItem> = emptyList(),
        val selectedOrderNumber: Int = 0,
        val selectedOrderTotal: Int = 0,
        val orderDetails: List<OrdersDetailItem> = emptyList(),
        val isLoading: Boolean = false,
        val deleteStatus: DeleteStatus = DeleteStatus.Idle
    )

    sealed class OrdersUiEvent {
        data class ShowToast(val message: String) : OrdersUiEvent()
        object RefreshOrders : OrdersUiEvent()
    }

    sealed class DeleteStatus {
        object Idle : DeleteStatus()
        object Loading : DeleteStatus()
        object Success : DeleteStatus()
        data class Error(val message: String) : DeleteStatus()
    }

    init {
        loadOrdersOfDay()
    }

    fun getCurrentDate(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val currentDate = Date()
        return dateFormat.format(currentDate)
    }

    fun loadOrdersOfDay() {
        viewModelScope.launch(dispatcher) {
            try {
                _uiState.update { it.copy(isLoading = true) }
                val ordersList = lambdaRepository.getOrdersOfDay()
                _uiState.update { 
                    it.copy(
                        ordersList = ordersList,
                        isLoading = false
                    )
                }
                
                // Auto-select first order if available
                if (ordersList.isNotEmpty()) {
                    val firstOrder = ordersList.first()
                    selectOrder(firstOrder.orderId, firstOrder.totalAmount)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false) }
                _uiEvent.tryEmit(OrdersUiEvent.ShowToast("주문 목록을 불러오는 중 오류가 발생했습니다: ${e.message}"))
            }
        }
    }

    fun selectOrder(orderNumber: Int, totalAmount: Int) {
        _uiState.update { 
            it.copy(
                selectedOrderNumber = orderNumber,
                selectedOrderTotal = totalAmount,
                orderDetails = emptyList() // Clear previous details
            )
        }
        fetchOrderDetail(orderNumber)
    }

    fun reprint() {
        val currentState = _uiState.value
        if (currentState.orderDetails.isEmpty()) {
            viewModelScope.launch(dispatcher) {
                _uiEvent.tryEmit(OrdersUiEvent.ShowToast("주문 조회 후 클릭해주세요"))
            }
            return
        }

        val printer = OrdersPrinter()
        val orderDetailsArrayList = ArrayList(currentState.orderDetails)
        val text = printer.makeText(currentState.selectedOrderNumber, orderDetailsArrayList)
        viewModelScope.launch(dispatcher) {
            try {
                printer.print(text)
            } catch (e: Exception) {
                _uiEvent.tryEmit(OrdersUiEvent.ShowToast("Printer error: ${e.message}"))
            } finally {
                printer.disconnect()
            }
        }
    }

    fun fetchOrderDetail(orderNumber: Int) {
        viewModelScope.launch(dispatcher) {
            try {
                val fetchedBasketList = lambdaRepository.getOrderDetail(getCurrentDate(), orderNumber)
                if (fetchedBasketList.isEmpty()) {
                    _uiEvent.tryEmit(OrdersUiEvent.ShowToast("주문 내역이 없습니다."))
                } else {
                    _uiState.update { it.copy(orderDetails = fetchedBasketList) }
                }
            } catch (e: Exception) {
                _uiEvent.tryEmit(OrdersUiEvent.ShowToast("주문 조회 중 오류가 발생했습니다: ${e.message}"))
            }
        }
    }

    fun deleteOrder() {
        val currentState = _uiState.value
        if (currentState.orderDetails.isEmpty()) {
            viewModelScope.launch(dispatcher) {
                _uiEvent.tryEmit(OrdersUiEvent.ShowToast("주문 조회 후 클릭해주세요"))
            }
            return
        }

        viewModelScope.launch(dispatcher) {
            try {
                _uiState.update { it.copy(deleteStatus = DeleteStatus.Loading) }
                val result = lambdaRepository.deleteOrder(getCurrentDate(), currentState.selectedOrderNumber)
                
                if (result) {
                    _uiState.update { it.copy(deleteStatus = DeleteStatus.Success) }
                    _uiEvent.tryEmit(OrdersUiEvent.ShowToast("주문이 성공적으로 삭제되었습니다."))
                    _uiEvent.tryEmit(OrdersUiEvent.RefreshOrders)
                } else {
                    _uiState.update { it.copy(deleteStatus = DeleteStatus.Error("주문 삭제에 실패했습니다.")) }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update { it.copy(deleteStatus = DeleteStatus.Error("오류가 발생했습니다. 다시 시도해주세요.")) }
            }
        }
    }

    fun resetDeleteStatus() {
        _uiState.update { it.copy(deleteStatus = DeleteStatus.Idle) }
    }
}