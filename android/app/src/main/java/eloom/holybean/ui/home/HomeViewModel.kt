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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
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
    private val piPrintClient: PiPrintClient,
    private val homePrinter: HomePrinter,
) : ViewModel() {

    sealed class SubmitError {
        abstract val seq: Long
        data class SaveFailed(override val seq: Long) : SubmitError()
        data class PrintFailed(val reason: PrintFailureReason, override val seq: Long) : SubmitError()
    }

    data class UiState(
        val allMenuItems: List<MenuItem> = emptyList(),
        val filteredMenuItems: List<MenuItem> = emptyList(),
        val selectedCategoryIndex: Int = 0,
        val basketItems: List<CartItem> = emptyList(),
        val orderId: Int = 0,
        val totalPrice: Int = 0,
        val currentDate: String = currentDateString(),
        val submitError: SubmitError? = null,
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
    // 재시도 시 이미 끝난 단계를 다시 하지 않도록 단계별 완료 여부를 추적한다.
    // 저장(postOrder)은 멱등이 아니고(집계 이중 계상), 인쇄도 멱등이 아니므로(영수증 이중 출력) 둘 다 가드한다.
    private var orderSaved: Boolean = false
    private var printDone: Boolean = false
    // 동일 실패가 연속될 때도 StateFlow가 다시 발화하도록 하는 단조 증가 시퀀스
    private var submitSeq: Long = 0L

    init {
        // Load initial data — 스플래시가 채운 캐시를 우선 사용하고, 없으면 네트워크 페치로 폴백
        viewModelScope.launch(ioDispatcher) {
            val menus = menuRepository.getCachedMenu() ?: menuRepository.getMenuListSync()
            _uiState.update { it.copy(
                allMenuItems = menus,
                filteredMenuItems = menus
            ) }
            refreshOrderNumber()
        }
    }

    fun refreshOrderNumber() {
        viewModelScope.launch(ioDispatcher) {
            val id = firestoreRepository.getOrderNumber()
            _uiState.update { it.copy(orderId = id) }
        }
    }

    fun onCategorySelected(index: Int) {
        viewModelScope.launch(ioDispatcher) {
            val filtered = if (index == 0) {
                _uiState.value.allMenuItems
            } else {
                _uiState.value.allMenuItems.filter { it.id / 1000 == index }
            }
            _uiState.update { it.copy(
                selectedCategoryIndex = index,
                filteredMenuItems = filtered
            ) }
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
            _uiState.update { it.copy(
                basketItems = currentBasket,
                totalPrice = total
            ) }
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
            _uiState.update { it.copy(
                basketItems = currentBasket,
                totalPrice = total
            ) }
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
            _uiState.update { it.copy(
                basketItems = currentBasket,
                totalPrice = total
            ) }
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
        lastOrder = data to takeOption
        orderSaved = false
        printDone = false
        _uiState.update { it.copy(isSubmitting = true, submitError = null) }
        runSubmission(data, takeOption)
    }

    fun retrySubmission() {
        val (data, takeOption) = lastOrder ?: return
        if (_uiState.value.isSubmitting) return
        _uiState.update { it.copy(isSubmitting = true, submitError = null) }
        runSubmission(data, takeOption)
    }

    // 인쇄만 실패한(저장은 끝난) 주문을 영수증 없이 정상 완료 처리하고 홈으로 복귀한다.
    fun skipPrintAndComplete() {
        if (!orderSaved) return
        if (_uiState.value.isSubmitting) return
        _uiState.update { it.copy(isSubmitting = true) }
        viewModelScope.launch(ioDispatcher) {
            try {
                completeAndNavigate()
            } finally {
                _uiState.update { it.copy(isSubmitting = false) }
            }
        }
    }

    // 성공/인쇄생략 공통: 다음 주문번호 채번 + 장바구니 리셋 + 홈 이동.
    private suspend fun completeAndNavigate() {
        val nextOrderId = firestoreRepository.getOrderNumber()
        _uiState.update { it.copy(basketItems = emptyList(), totalPrice = 0, orderId = nextOrderId, submitError = null) }
        _uiEvent.emit(UiEvent.NavigateHome)
    }

    // 저장(서버 ack 대기)과 인쇄를 병렬 실행하고 둘 다 성공해야 홈으로 전환한다.
    // 저장이 한 번 성공하면 orderSaved=true 가 되어, 재시도 시 중복 저장(집계 이중 계상)을 막는다.
    private fun runSubmission(data: Order, takeOption: String) {
        viewModelScope.launch(ioDispatcher) {
            try {
                coroutineScope {
                    // 이미 출력된 영수증을 재시도 때 다시 찍지 않도록 printDone 으로 가드.
                    val printDeferred = async { if (!printDone) { printReceipt(data, takeOption); printDone = true } }
                    // postOrder를 print await보다 먼저 await → 저장 실패가 인쇄 실패보다 우선 보고된다(저장 실패가 더 중요).
                    if (!orderSaved) {
                        firestoreRepository.postOrder(data)
                        orderSaved = true
                    }
                    printDeferred.await()
                }
                completeAndNavigate()
            } catch (e: PrintServerException) {
                _uiState.update { it.copy(submitError = SubmitError.PrintFailed(e.reason, ++submitSeq)) }
                FirebaseCrashlytics.getInstance().apply {
                    setCustomKey("orderNum", data.orderNum)
                    setCustomKey("print_reason", e.reason.name)
                    recordException(e)
                }
            } catch (e: TimeoutCancellationException) {
                // 서버 ack 타임아웃(오프라인 등) → 저장 실패로 처리
                _uiState.update { it.copy(submitError = SubmitError.SaveFailed(++submitSeq)) }
                FirebaseCrashlytics.getInstance().recordException(e)
            } catch (e: CancellationException) {
                throw e   // ViewModel teardown 등 정상 취소는 삼키지 않는다
            } catch (e: Exception) {
                _uiState.update { it.copy(submitError = SubmitError.SaveFailed(++submitSeq)) }
                FirebaseCrashlytics.getInstance().recordException(e)
            } finally {
                _uiState.update { it.copy(isSubmitting = false) }
            }
        }
    }

    private suspend fun printReceipt(data: Order, takeOption: String) {
        val customerCommands = homePrinter.receiptForCustomer(data)
        val posCommands = homePrinter.receiptForPOS(data, takeOption)
        piPrintClient.print(customerCommands)
        piPrintClient.print(posCommands)
    }
}
