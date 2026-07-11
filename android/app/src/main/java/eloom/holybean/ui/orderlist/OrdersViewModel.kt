package eloom.holybean.ui.orderlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import eloom.holybean.data.model.OrderItem
import eloom.holybean.data.model.OrdersDetailItem
import eloom.holybean.data.model.PrinterDTO
import eloom.holybean.data.repository.FirestoreRepository
import eloom.holybean.printer.PrintClient
import eloom.holybean.printer.polymorphism.OrdersPrinter
import eloom.holybean.di.AppScope
import eloom.holybean.printer.polymorphism.ReportPrinter
import eloom.holybean.util.launchSafely
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@HiltViewModel
class OrdersViewModel @Inject constructor(
    private val firestoreRepository: FirestoreRepository,
    @AppScope private val applicationScope: CoroutineScope,
    private val printClient: PrintClient,
    private val ordersPrinter: OrdersPrinter,
    private val reportPrinter: ReportPrinter,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OrdersUiState())
    val uiState: StateFlow<OrdersUiState> = _uiState.asStateFlow()

    // One-shot events (ShowToast/RefreshOrders) must not replay to new subscribers
    // (e.g. screen re-entry would re-fire a stale toast). replay = 0; tryEmit still
    // buffers via extraBufferCapacity while the screen is actively collecting.
    private val _uiEvent = MutableSharedFlow<OrdersUiEvent>(
        replay = 0,
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
        val deleteStatus: DeleteStatus = DeleteStatus.Idle,
        val todaySummary: TodaySummary = TodaySummary()
    )

    data class TodaySummary(
        val totalSales: Int = 0,
        val orderCount: Int = 0,
        val drinkCount: Int = 0
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
        loadTodaySummary()
    }

    fun getCurrentDate(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val currentDate = Date()
        return dateFormat.format(currentDate)
    }

    fun loadOrdersOfDay() {
        viewModelScope.launchSafely(onError = { e ->
            _uiState.update { it.copy(isLoading = false) }
            _uiEvent.tryEmit(OrdersUiEvent.ShowToast("주문 목록을 불러오는 중 오류가 발생했습니다: ${e.message}"))
        }) {
            _uiState.update { it.copy(isLoading = true) }
            val ordersList = firestoreRepository.getOrdersOfDay()
            _uiState.update { it.copy(ordersList = ordersList, isLoading = false) }
            if (ordersList.isNotEmpty()) {
                val first = ordersList.first()
                selectOrder(first.orderId, first.totalAmount)
            } else {
                _uiState.update { it.copy(selectedOrderNumber = 0, selectedOrderTotal = 0, orderDetails = emptyList()) }
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
            _uiEvent.tryEmit(OrdersUiEvent.ShowToast("주문 조회 후 클릭해주세요"))
            return
        }

        val commands = ordersPrinter.makeCommands(currentState.selectedOrderNumber, currentState.orderDetails.toList())
        // Printer I/O - ViewModel 생명주기와 독립(사용자가 화면 떠나도 인쇄 완료)
        applicationScope.launchSafely(onError = { e ->
            _uiEvent.tryEmit(OrdersUiEvent.ShowToast("Printer error: ${e.message}"))
        }) {
            printClient.print(commands)
        }
    }

    fun fetchOrderDetail(orderNumber: Int) {
        viewModelScope.launchSafely(onError = { e ->
            _uiEvent.tryEmit(OrdersUiEvent.ShowToast("주문 조회 중 오류가 발생했습니다: ${e.message}"))
        }) {
            val fetched = firestoreRepository.getOrderDetail(getCurrentDate(), orderNumber)
            if (fetched.isEmpty()) {
                _uiEvent.tryEmit(OrdersUiEvent.ShowToast("주문 내역이 없습니다."))
            } else {
                _uiState.update { it.copy(orderDetails = fetched) }
            }
        }
    }

    fun deleteOrder() {
        val currentState = _uiState.value
        if (currentState.orderDetails.isEmpty()) {
            _uiEvent.tryEmit(OrdersUiEvent.ShowToast("주문 조회 후 클릭해주세요"))
            return
        }

        viewModelScope.launchSafely(onError = { e ->
            _uiState.update { it.copy(deleteStatus = DeleteStatus.Error("오류가 발생했습니다. 다시 시도해주세요.")) }
        }) {
            _uiState.update { it.copy(deleteStatus = DeleteStatus.Loading) }
            val deleted = firestoreRepository.deleteOrder(getCurrentDate(), currentState.selectedOrderNumber)
            if (deleted) {
                _uiState.update { it.copy(deleteStatus = DeleteStatus.Success) }
                _uiEvent.tryEmit(OrdersUiEvent.ShowToast("주문이 성공적으로 삭제되었습니다."))
                _uiEvent.tryEmit(OrdersUiEvent.RefreshOrders)
                loadTodaySummary()
            } else {
                _uiState.update { it.copy(deleteStatus = DeleteStatus.Error("주문 삭제에 실패했습니다.")) }
            }
        }
    }

    fun loadTodaySummary() {
        viewModelScope.launchSafely(onError = { e ->
            _uiEvent.tryEmit(OrdersUiEvent.ShowToast("매출 요약을 불러오지 못했습니다: ${e.message}"))
        }) {
            val today = getCurrentDate()
            val report = firestoreRepository.getReport(today, today)
            val orders = firestoreRepository.getOrdersOfDay()
            _uiState.update { it.copy(todaySummary = TodaySummary(
                totalSales = report.paymentSales["총합"] ?: 0,
                orderCount = orders.size,
                drinkCount = report.menuSales.filter { it.name != "쿠폰" }.sumOf { it.quantity },
            )) }
        }
    }

    fun printTodayReport() {
        applicationScope.launchSafely(onError = { e ->
            _uiEvent.tryEmit(OrdersUiEvent.ShowToast("보고서 출력 실패: ${e.message}"))
        }) {
            val today = getCurrentDate()
            val report = firestoreRepository.getReport(today, today)
            val dto = PrinterDTO(today, today, report.paymentSales, report.menuSales)
            printClient.print(reportPrinter.makeCommands(dto))
            _uiEvent.tryEmit(OrdersUiEvent.ShowToast("보고서 출력 완료"))
        }
    }

    fun resetDeleteStatus() {
        _uiState.update { it.copy(deleteStatus = DeleteStatus.Idle) }
    }
}
