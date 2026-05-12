package eloom.holybean.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import eloom.holybean.data.model.CartItem
import eloom.holybean.data.model.MenuItem
import eloom.holybean.data.model.Order
import eloom.holybean.data.repository.LambdaRepository
import eloom.holybean.data.repository.MenuRepository
import eloom.holybean.interfaces.OrderDialogListener
import eloom.holybean.printer.PrinterConnectionManager
import eloom.holybean.printer.polymorphism.HomePrinter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Named

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val lambdaRepository: LambdaRepository,
    private val menuRepository: MenuRepository,
    @Named("IO") private val ioDispatcher: CoroutineDispatcher,
    @Named("ApplicationScope") private val applicationScope: CoroutineScope,
    private val printerConnectionManager: PrinterConnectionManager,
    private val homePrinter: HomePrinter,
) : ViewModel(), OrderDialogListener {

    data class UiState(
        val allMenuItems: List<MenuItem> = emptyList(),
        val filteredMenuItems: List<MenuItem> = emptyList(),
        val selectedCategoryIndex: Int = 0,
        val basketItems: List<CartItem> = emptyList(),
        val orderId: Int = 0,
        val totalPrice: Int = 0,
        val currentDate: String = currentDateString()
    ) {
        companion object {
            private fun currentDateString(): String {
                val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
                return LocalDate.now().format(formatter)
            }
        }
    }

    sealed class UiEvent {
        data class ShowToast(val message: String) : UiEvent()
        object NavigateHome : UiEvent()
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _uiEvent = MutableSharedFlow<UiEvent>(
        replay = 1,
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val uiEvent: SharedFlow<UiEvent> = _uiEvent.asSharedFlow()

    init {
        // Load initial data
        viewModelScope.launch(ioDispatcher) {
            val menus = menuRepository.getMenuListSync()
            _uiState.value = _uiState.value.copy(
                allMenuItems = menus,
                filteredMenuItems = menus
            )
            refreshOrderNumber()
        }
    }

    fun startPrinter() {
        applicationScope.launch {
            runCatching { printerConnectionManager.connect() }
                .onFailure { error ->
                    _uiEvent.emit(
                        UiEvent.ShowToast("프린터 연결 실패: ${error.message ?: "알 수 없는 오류"}")
                    )
                }
        }
    }

    fun stopPrinter() {
        applicationScope.launch {
            runCatching { printerConnectionManager.disconnect() }
        }
    }

    fun getCurrentDate(): String {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        return LocalDate.now().format(formatter)
    }

    fun getTotal(basketList: ArrayList<CartItem>): Int {
        basketList.forEach { it.total = it.count * it.price }
        return basketList.sumOf { it.total }
    }

    fun refreshOrderNumber() {
        viewModelScope.launch(ioDispatcher) {
            val id = lambdaRepository.getOrderNumber()
            _uiState.value = _uiState.value.copy(orderId = id)
        }
    }

    fun onCategorySelected(index: Int) {
        viewModelScope.launch(ioDispatcher) {
            val filtered = if (index == 0) {
                _uiState.value.allMenuItems
            } else {
                _uiState.value.allMenuItems.filter { it.id / 1000 == index }
            }
            _uiState.value = _uiState.value.copy(
                selectedCategoryIndex = index,
                filteredMenuItems = filtered
            )
        }
    }

    fun addToBasket(id: Int) {
        viewModelScope.launch(ioDispatcher) {
            val currentBasket = _uiState.value.basketItems.toMutableList()
            val existing = currentBasket.find { it.id == id }
            if (existing == null) {
                val target = _uiState.value.allMenuItems.find { it.id == id } ?: return@launch
                currentBasket.add(CartItem(id, target.name, target.price, 1, target.price))
            } else {
                val updated = existing.copy(count = existing.count + 1)
                updated.total = updated.count * updated.price
                val idx = currentBasket.indexOf(existing)
                currentBasket[idx] = updated
            }
            val total = currentBasket.sumOf { it.count * it.price }
            _uiState.value = _uiState.value.copy(
                basketItems = currentBasket,
                totalPrice = total
            )
        }
    }

    fun deleteFromBasket(id: Int) {
        viewModelScope.launch(ioDispatcher) {
            val currentBasket = _uiState.value.basketItems.toMutableList()
            val item = currentBasket.find { it.id == id } ?: return@launch
            if (item.count <= 1) {
                currentBasket.remove(item)
            } else {
                val updated = item.copy(count = item.count - 1)
                updated.total = updated.count * updated.price
                val idx = currentBasket.indexOf(item)
                currentBasket[idx] = updated
            }
            val total = currentBasket.sumOf { it.count * it.price }
            _uiState.value = _uiState.value.copy(
                basketItems = currentBasket,
                totalPrice = total
            )
        }
    }

    fun addCoupon(amount: Int) {
        if (amount <= 0) {
            viewModelScope.launch(ioDispatcher) {
                _uiEvent.emit(UiEvent.ShowToast("올바른 금액이 아닙니다"))
            }
            return
        }
        viewModelScope.launch(ioDispatcher) {
            val currentBasket = _uiState.value.basketItems.toMutableList()
            currentBasket.add(CartItem(999, "쿠폰", amount, 1, amount))
            val total = currentBasket.sumOf { it.count * it.price }
            _uiState.value = _uiState.value.copy(
                basketItems = currentBasket,
                totalPrice = total
            )
        }
    }

    override fun onOrderConfirmed(data: Order, takeOption: String) {
        // Network I/O - 완료 대기 후 UI 업데이트
        viewModelScope.launch(ioDispatcher) {
            try {
                lambdaRepository.postOrder(data)
                _uiEvent.emit(UiEvent.NavigateHome)
            } catch (e: Exception) {
                e.printStackTrace()
                _uiEvent.emit(UiEvent.ShowToast("주문 처리 중 오류가 발생했습니다."))
            }
        }

        // Printer I/O - Application Scope에서 실행 (ViewModel 생명주기와 독립)
        // PrinterConnectionManager가 내부 Mutex로 동기화 보장
        applicationScope.launch {
            runCatching {
                printReceipt(data, takeOption)
            }.onFailure { error ->
                error.printStackTrace()
            }
        }
    }

    // 영수증 출력은 독립적으로 실행 (Network 완료와 무관)
    private suspend fun printReceipt(data: Order, takeOption: String) {
        val receiptForCustomer = homePrinter.receiptTextForCustomer(data)
        val receiptForPOS = homePrinter.receiptTextForPOS(data, takeOption)
        printerConnectionManager.print(receiptForCustomer)
        printerConnectionManager.print(receiptForPOS)
    }
}
