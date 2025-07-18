package eloom.holybean.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eloom.holybean.data.model.CartItem
import eloom.holybean.data.model.MenuItem
import eloom.holybean.data.model.Order
import eloom.holybean.data.repository.LambdaRepository
import eloom.holybean.data.repository.MenuDB
import eloom.holybean.interfaces.MainActivityListener
import eloom.holybean.interfaces.OrderDialogListener
import eloom.holybean.printer.HomePrinter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val lambdaRepository: LambdaRepository,
    private val menudb: MenuDB
) : OrderDialogListener, ViewModel() {

    private var mainListener: MainActivityListener? = null

    // LiveData for error messages
    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> get() = _errorMessage

    fun setMainActivityListener(listener: MainActivityListener) {
        this.mainListener = listener
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
        return ArrayList(menudb.getMenuList())
    }

    override fun onOrderConfirmed(data: Order, takeOption: String) {
        // 주문 POST 요청은 별도의 스레드에서 동기적으로 처리
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 1. POST 요청
                lambdaRepository.postOrder(data)

                // 주문 완료 후 바로 홈 프래그먼트 전환 (UI 스레드에서)
                withContext(Dispatchers.Main) {
                    mainListener?.replaceHomeFragment()
                }

                // 2. 영수증 출력은 비동기적으로 요청만 보냄 (별도 try-catch로 격리)
                try {
                    printReceipt(data, takeOption)
                } catch (e: Exception) {
                    // 영수증 출력 실패는 주문 처리에 영향을 주지 않음
                    e.printStackTrace()
                }

            } catch (e: Exception) {
                e.printStackTrace()
                _errorMessage.postValue("주문 처리 중 오류가 발생했습니다.")
            }
        }
    }

    // 영수증 출력은 요청만 보낸 후 완료 여부는 기다리지 않음
    private suspend fun printReceipt(data: Order, takeOption: String) {
        withContext(Dispatchers.IO) {
            try {
                val printer = HomePrinter()
                val receiptForCustomer = printer.receiptTextForCustomer(data)
                val receiptForPOS = printer.receiptTextForPOS(data, takeOption)

                try {
                    // 고객용 영수증 출력 요청
                    printer.print(receiptForCustomer)
                    // POS용 영수증 출력 요청
                    printer.print(receiptForPOS)
                } finally {
                    // 프린터 연결 해제
                    printer.disconnect()
                }
            } catch (e: Exception) {
                // 프린터 초기화 또는 출력 중 오류가 발생하면 로그로 기록
                e.printStackTrace()
            }
        }
    }

    fun clearErrorMessage() {
        _errorMessage.value = null
    }
}
