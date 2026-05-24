package eloom.holybean.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.crashlytics.FirebaseCrashlytics
import dagger.hilt.android.lifecycle.HiltViewModel
import eloom.holybean.data.model.CartItem
import eloom.holybean.data.model.MenuItem
import eloom.holybean.data.model.Order
import eloom.holybean.data.repository.FirestoreRepository
import eloom.holybean.data.repository.MenuRepository
import eloom.holybean.printer.PiPrintClient
import eloom.holybean.printer.network.PrintFailureReason
import eloom.holybean.printer.network.PrintServerException
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

    data class PrintFailure(val orderNum: Int, val reason: PrintFailureReason, val seq: Long = 0L)

    data class UiState(
        val allMenuItems: List<MenuItem> = emptyList(),
        val filteredMenuItems: List<MenuItem> = emptyList(),
        val selectedCategoryIndex: Int = 0,
        val basketItems: List<CartItem> = emptyList(),
        val orderId: Int = 0,
        val totalPrice: Int = 0,
        val currentDate: String = currentDateString(),
        val printFailure: PrintFailure? = null,
        val isSubmitting: Boolean = false,
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

    private var lastOrder: Pair<Order, String>? = null

    // 동일 실패가 연속될 때도 StateFlow/Snackbar가 다시 발화하도록 하는 단조 증가 시퀀스
    private var printFailureSeq = 0L

    init {
        // Load initial data — 스플래시가 채운 캐시를 우선 사용하고, 없으면 네트워크 페치로 폴백
        viewModelScope.launch(ioDispatcher) {
            val menus = menuRepository.getCachedMenu() ?: menuRepository.getMenuListSync()
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
        if (data.orderNum <= 0) {
            viewModelScope.launch(ioDispatcher) {
                _uiEvent.emit(UiEvent.ShowToast("주문번호를 불러오지 못했습니다. 다시 시도해 주세요."))
            }
            return
        }
        if (_uiState.value.isSubmitting) return
        // 새 주문 시작 — 직전 실패 스낵바 제거 + 제출 잠금(동기, 메인 스레드 단독)
        lastOrder = data to takeOption
        _uiState.value = _uiState.value.copy(isSubmitting = true, printFailure = null)

        viewModelScope.launch(ioDispatcher) {
            try {
                firestoreRepository.postOrder(data)
                val nextOrderId = firestoreRepository.getOrderNumber()
                _uiState.value = _uiState.value.copy(
                    basketItems = emptyList(), totalPrice = 0, orderId = nextOrderId,
                )
                _uiEvent.emit(UiEvent.NavigateHome)
            } catch (e: Exception) {
                FirebaseCrashlytics.getInstance().recordException(e) // 파트 B
                _uiEvent.emit(UiEvent.ShowToast("주문 처리 중 오류가 발생했습니다."))
            } finally {
                _uiState.value = _uiState.value.copy(isSubmitting = false)
            }
        }

        launchPrint(data, takeOption)
    }

    fun reprintLastOrder() {
        val (order, takeOption) = lastOrder ?: return
        launchPrint(order, takeOption)
    }

    fun dismissPrintFailure() {
        _uiState.value = _uiState.value.copy(printFailure = null)
    }

    // 인쇄는 ViewModel 생명주기와 독립인 applicationScope에서 실행(홈 복귀를 막지 않음).
    private fun launchPrint(order: Order, takeOption: String) {
        applicationScope.launch {
            try {
                printReceipt(order, takeOption)
                // 성공: 이 주문에 대한 실패 표시가 남아 있으면 해제(재출력 성공 케이스)
                _uiState.value = _uiState.value.let {
                    if (it.printFailure?.orderNum == order.orderNum) it.copy(printFailure = null) else it
                }
            } catch (e: PrintServerException) {
                reportPrintFailure(order.orderNum, e.reason, e)
            } catch (e: Exception) {
                reportPrintFailure(order.orderNum, PrintFailureReason.Unknown, e)
            }
        }
    }

    private fun reportPrintFailure(orderNum: Int, reason: PrintFailureReason, e: Throwable) {
        _uiState.value = _uiState.value.copy(printFailure = PrintFailure(orderNum, reason, ++printFailureSeq))
        FirebaseCrashlytics.getInstance().apply {
            setCustomKey("orderNum", orderNum)
            setCustomKey("print_reason", reason.name)
            recordException(e)
        }
    }

    private suspend fun printReceipt(data: Order, takeOption: String) {
        val customerCommands = homePrinter.receiptForCustomer(data)
        val posCommands = homePrinter.receiptForPOS(data, takeOption)
        piPrintClient.print(customerCommands)
        piPrintClient.print(posCommands)
    }
}
