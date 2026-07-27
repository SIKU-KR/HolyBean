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
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.supervisorScope
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class OrdersViewModel @Inject constructor(
    private val firestoreRepository: FirestoreRepository,
    @AppScope private val applicationScope: CoroutineScope,
    private val printClient: PrintClient,
    private val ordersPrinter: OrdersPrinter,
    private val reportPrinter: ReportPrinter,
) : ViewModel() {

    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    private val today: String get() = LocalDate.now().format(dateFormatter)
    private var dateLoadJob: Job? = null
    private var orderDetailJob: Job? = null

    private val _uiState = MutableStateFlow(OrdersUiState(selectedDate = today))
    val uiState: StateFlow<OrdersUiState> = _uiState.asStateFlow()

    // One-shot events must not replay to new subscribers
    // (e.g. screen re-entry would re-fire a stale toast). replay = 0; tryEmit still
    // buffers via extraBufferCapacity while the screen is actively collecting.
    private val _uiEvent = MutableSharedFlow<OrdersUiEvent>(
        replay = 0,
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val uiEvent: SharedFlow<OrdersUiEvent> = _uiEvent.asSharedFlow()

    data class OrdersUiState(
        val selectedDate: String = "",
        val previousOrderDate: String? = null,
        val nextOrderDate: String? = null,
        val nextNavigationDate: String? = null,
        val ordersList: List<OrderItem> = emptyList(),
        val selectedOrderNumber: Int = 0,
        val selectedOrderTotal: Int = 0,
        val orderDetails: List<OrdersDetailItem> = emptyList(),
        val isLoading: Boolean = false,
        val deleteStatus: DeleteStatus = DeleteStatus.Idle,
        val daySummary: DaySummary = DaySummary()
    )

    data class DaySummary(
        val totalSales: Int = 0,
        val orderCount: Int = 0,
        val drinkCount: Int = 0
    )

    sealed class OrdersUiEvent {
        data class ShowToast(val message: String) : OrdersUiEvent()
    }

    sealed class DeleteStatus {
        object Idle : DeleteStatus()
        object Loading : DeleteStatus()
        object Success : DeleteStatus()
        data class Error(val message: String) : DeleteStatus()
    }

    init {
        loadDate(today)
    }

    fun getCurrentDate(): String = today

    fun loadDate(date: String) {
        dateLoadJob?.cancel()
        orderDetailJob?.cancel()
        dateLoadJob = viewModelScope.launchSafely(onError = { e ->
            _uiState.update { it.copy(isLoading = false) }
            _uiEvent.tryEmit(OrdersUiEvent.ShowToast("주문 목록을 불러오는 중 오류가 발생했습니다: ${e.message}"))
        }) {
            _uiState.update {
                it.copy(
                    selectedDate = date,
                    previousOrderDate = null,
                    nextOrderDate = null,
                    nextNavigationDate = null,
                    ordersList = emptyList(),
                    selectedOrderNumber = 0,
                    selectedOrderTotal = 0,
                    orderDetails = emptyList(),
                    isLoading = true,
                    daySummary = DaySummary(),
                )
            }

            val orders = firestoreRepository.getOrdersOfDay(date)
            val first = orders.firstOrNull()
            _uiState.update {
                it.copy(
                    selectedDate = date,
                    ordersList = orders,
                    selectedOrderNumber = first?.orderId ?: 0,
                    selectedOrderTotal = first?.totalAmount ?: 0,
                    isLoading = false,
                    daySummary = DaySummary(orderCount = orders.size),
                )
            }

            supervisorScope {
                launchSafely(onError = { e ->
                    _uiEvent.tryEmit(OrdersUiEvent.ShowToast("매출 요약을 불러오지 못했습니다: ${e.message}"))
                }) {
                    val report = firestoreRepository.getReport(date, date)
                    if (_uiState.value.selectedDate == date) {
                        _uiState.update {
                            it.copy(
                                daySummary = DaySummary(
                                    totalSales = report.paymentSales["총합"] ?: 0,
                                    orderCount = orders.size,
                                    drinkCount = report.menuSales
                                        .filter { sale -> sale.name != "쿠폰" }
                                        .sumOf { sale -> sale.quantity },
                                )
                            )
                        }
                    }
                }
                launchSafely(onError = { e ->
                    _uiEvent.tryEmit(OrdersUiEvent.ShowToast("이전 주문일을 찾지 못했습니다: ${e.message}"))
                }) {
                    val previous = firestoreRepository.getPreviousOrderDate(date)
                    if (_uiState.value.selectedDate == date) {
                        _uiState.update { it.copy(previousOrderDate = previous) }
                    }
                }
                launchSafely(onError = { e ->
                    _uiEvent.tryEmit(OrdersUiEvent.ShowToast("다음 주문일을 찾지 못했습니다: ${e.message}"))
                }) {
                    val nextOrder = firestoreRepository.getNextOrderDate(date)
                    if (_uiState.value.selectedDate == date) {
                        _uiState.update {
                            it.copy(
                                nextOrderDate = nextOrder,
                                nextNavigationDate = nextOrder ?: today.takeIf { currentDate -> date < currentDate },
                            )
                        }
                    }
                }
                first?.let { order ->
                    launchSafely(onError = { e ->
                        _uiEvent.tryEmit(OrdersUiEvent.ShowToast("주문 조회 중 오류가 발생했습니다: ${e.message}"))
                    }) {
                        val details = firestoreRepository.getOrderDetail(date, order.orderId)
                        if (
                            _uiState.value.selectedDate == date &&
                            _uiState.value.selectedOrderNumber == order.orderId &&
                            details.isNotEmpty()
                        ) {
                            _uiState.update { it.copy(orderDetails = details) }
                        }
                    }
                }
            }
        }
    }

    fun goToPreviousOrderDate() = _uiState.value.previousOrderDate?.let(::loadDate) ?: Unit

    fun goToNextOrderDate() = _uiState.value.nextNavigationDate?.let(::loadDate) ?: Unit

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
        val selectedDate = _uiState.value.selectedDate
        orderDetailJob?.cancel()
        orderDetailJob = viewModelScope.launchSafely(onError = { e ->
            _uiEvent.tryEmit(OrdersUiEvent.ShowToast("주문 조회 중 오류가 발생했습니다: ${e.message}"))
        }) {
            val fetched = firestoreRepository.getOrderDetail(selectedDate, orderNumber)
            if (_uiState.value.selectedDate != selectedDate || _uiState.value.selectedOrderNumber != orderNumber) {
                return@launchSafely
            }
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
            val targetDate = if (currentState.ordersList.size == 1) {
                val nextOrderDate = currentState.nextOrderDate
                    ?: firestoreRepository.getNextOrderDate(currentState.selectedDate)
                nextOrderDate
                    ?: currentState.previousOrderDate
                    ?: firestoreRepository.getPreviousOrderDate(currentState.selectedDate)
                    ?: today
            } else {
                currentState.selectedDate
            }
            val deleted = firestoreRepository.deleteOrder(currentState.selectedDate, currentState.selectedOrderNumber)
            if (deleted) {
                _uiState.update { it.copy(deleteStatus = DeleteStatus.Success) }
                _uiEvent.tryEmit(OrdersUiEvent.ShowToast("주문이 성공적으로 삭제되었습니다."))
                loadDate(targetDate)
            } else {
                _uiState.update { it.copy(deleteStatus = DeleteStatus.Error("주문 삭제에 실패했습니다.")) }
            }
        }
    }

    fun printSelectedDateReport() {
        val selectedDate = _uiState.value.selectedDate
        applicationScope.launchSafely(onError = { e ->
            _uiEvent.tryEmit(OrdersUiEvent.ShowToast("보고서 출력 실패: ${e.message}"))
        }) {
            val report = firestoreRepository.getReport(selectedDate, selectedDate)
            val dto = PrinterDTO(selectedDate, selectedDate, report.paymentSales, report.menuSales)
            printClient.print(reportPrinter.makeCommands(dto))
            _uiEvent.tryEmit(OrdersUiEvent.ShowToast("보고서 출력 완료"))
        }
    }

    fun resetDeleteStatus() {
        _uiState.update { it.copy(deleteStatus = DeleteStatus.Idle) }
    }

}
