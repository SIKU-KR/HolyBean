package eloom.holybean.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import eloom.holybean.data.model.CartItem
import eloom.holybean.data.model.MenuItem
import eloom.holybean.data.model.Order
import eloom.holybean.data.repository.FirestoreRepository
import eloom.holybean.data.repository.MenuRepository
import eloom.holybean.printer.PiPrintClient
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
    private val firestoreRepository: FirestoreRepository,
    private val menuRepository: MenuRepository,
    @Named("IO") private val ioDispatcher: CoroutineDispatcher,
    @Named("ApplicationScope") private val applicationScope: CoroutineScope,
    private val piPrintClient: PiPrintClient,
    private val homePrinter: HomePrinter,
) : ViewModel() {

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
        object NavigateToPayment : UiEvent()
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    // replay = 0: 내비게이션/토스트 같은 일회성 이벤트이므로 새 구독자(재구성, STOP→START 등)에게
    // 다시 재생되면 안 된다. 재생될 경우 중복 내비게이션이 발생한다.
    private val _uiEvent = MutableSharedFlow<UiEvent>(
        replay = 0,
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val uiEvent: SharedFlow<UiEvent> = _uiEvent.asSharedFlow()

    // 결제 완료 재진입 가드 (메인 스레드 단독 접근이므로 plain var 로 충분)
    private var orderInFlight = false

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

    fun refreshOrderNumber() {
        viewModelScope.launch(ioDispatcher) {
            val id = firestoreRepository.getOrderNumber()
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

    fun onCheckoutClicked() {
        if (_uiState.value.basketItems.isEmpty()) return
        _uiEvent.tryEmit(UiEvent.NavigateToPayment)
    }

    fun onOrderConfirmed(data: Order, takeOption: String) {
        // 주문번호 조회 실패(-1) 등 유효하지 않은 주문번호는 절대 저장하지 않는다
        if (data.orderNum <= 0) {
            viewModelScope.launch(ioDispatcher) {
                _uiEvent.emit(UiEvent.ShowToast("주문번호를 불러오지 못했습니다. 다시 시도해 주세요."))
            }
            return
        }

        // 결제 완료 버튼 더블탭 시 중복 주문/출력을 막는 재진입 가드.
        // onOrderConfirmed 는 버튼 클릭으로 메인 스레드에서 호출되고, 체크+설정이 코루틴
        // 런치 전 동기적으로 일어나므로 동시 메인 스레드 탭이 직렬화되어 plain var 로 안전하다.
        if (orderInFlight) return
        orderInFlight = true

        // Network I/O - 완료 대기 후 UI 업데이트
        viewModelScope.launch(ioDispatcher) {
            try {
                firestoreRepository.postOrder(data)
                // 다음 주문번호를 동기적으로 채번하여 NavigateHome 전에 상태가 확정되도록 한다
                val nextOrderId = firestoreRepository.getOrderNumber()
                _uiState.value = _uiState.value.copy(basketItems = emptyList(), totalPrice = 0, orderId = nextOrderId)
                _uiEvent.emit(UiEvent.NavigateHome)
            } catch (e: Exception) {
                e.printStackTrace()
                _uiEvent.emit(UiEvent.ShowToast("주문 처리 중 오류가 발생했습니다."))
            } finally {
                orderInFlight = false
            }
        }

        // Printer I/O - Application Scope에서 실행 (ViewModel 생명주기와 독립)
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
        val customerCommands = homePrinter.receiptForCustomer(data)
        val posCommands = homePrinter.receiptForPOS(data, takeOption)
        piPrintClient.print(customerCommands)
        piPrintClient.print(posCommands)
    }
}
