package eloom.holybean.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import eloom.holybean.data.model.CartItem
import eloom.holybean.data.model.MenuItem
import eloom.holybean.data.model.Order
import eloom.holybean.data.repository.LambdaRepository
import eloom.holybean.data.repository.MenuDB
import eloom.holybean.interfaces.OrderDialogListener
import eloom.holybean.printer.HomePrinter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val lambdaRepository: LambdaRepository,
    private val menuDB: MenuDB,
    private val dispatcher: CoroutineDispatcher
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
        viewModelScope.launch(dispatcher) {
            val menus = getMenuList()
            _uiState.value = _uiState.value.copy(
                allMenuItems = menus,
                filteredMenuItems = menus
            )
            refreshOrderNumber()
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

    fun getMenuList(): ArrayList<MenuItem> {
        return ArrayList(menuDB.getMenuList())
    }

    fun refreshOrderNumber() {
        viewModelScope.launch(dispatcher) {
            val id = lambdaRepository.getOrderNumber()
            _uiState.value = _uiState.value.copy(orderId = id)
        }
    }

    fun onCategorySelected(index: Int) {
        viewModelScope.launch(dispatcher) {
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
        viewModelScope.launch(dispatcher) {
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
        viewModelScope.launch(dispatcher) {
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
            viewModelScope.launch(dispatcher) {
                _uiEvent.emit(UiEvent.ShowToast("올바른 금액이 아닙니다"))
            }
            return
        }
        viewModelScope.launch(dispatcher) {
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
        viewModelScope.launch(dispatcher) {
            try {
                lambdaRepository.postOrder(data)
                _uiEvent.emit(UiEvent.NavigateHome)
                // Print receipts without blocking UI
                try {
                    printReceipt(data, takeOption)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiEvent.emit(UiEvent.ShowToast("주문 처리 중 오류가 발생했습니다."))
            }
        }
    }

    // 영수증 출력은 요청만 보낸 후 완료 여부는 기다리지 않음
    private suspend fun printReceipt(data: Order, takeOption: String) {
        withContext(dispatcher) {
            try {
                val printer = HomePrinter()
                val receiptForCustomer = printer.receiptTextForCustomer(data)
                val receiptForPOS = printer.receiptTextForPOS(data, takeOption)
                try {
                    printer.print(receiptForCustomer)
                    printer.print(receiptForPOS)
                } finally {
                    printer.disconnect()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
