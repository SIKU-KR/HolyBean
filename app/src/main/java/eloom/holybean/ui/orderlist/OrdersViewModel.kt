package eloom.holybean.ui.orderlist

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import eloom.holybean.data.model.OrdersDetailItem
import eloom.holybean.data.repository.LambdaRepository
import eloom.holybean.printer.OrdersPrinter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@HiltViewModel
class OrdersViewModel @Inject constructor(
    private val lambdaRepository: LambdaRepository
) : ViewModel() {

    private val _orderDetails = MutableLiveData<List<OrdersDetailItem>>()
    val orderDetails: LiveData<List<OrdersDetailItem>> get() = _orderDetails

    private val _error = MutableLiveData<String>()
    val error: LiveData<String> get() = _error

    private val _deleteStatus = MutableLiveData<DeleteStatus>()
    val deleteStatus: LiveData<DeleteStatus> get() = _deleteStatus

    private val _deleteResult = MutableLiveData<Boolean?>()
    val deleteResult: LiveData<Boolean?> get() = _deleteResult

    sealed class DeleteStatus {
        object Idle : DeleteStatus()
        object Loading : DeleteStatus()
        object Success : DeleteStatus()
        data class Error(val message: String) : DeleteStatus()
    }

    fun getCurrentDate(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd")
        val currentDate = Date()
        return dateFormat.format(currentDate)
    }

    fun reprint(orderNum: Int, basketList: ArrayList<OrdersDetailItem>) {
        val printer = OrdersPrinter()
        val text = printer.makeText(orderNum, basketList)
        viewModelScope.launch {
            try {
                printer.print(text)
                delay(2000)
            } catch (e: Exception) {
                _error.postValue("Printer error: ${e.message}")
            } finally {
                printer.disconnect()
            }
        }
    }

    fun fetchOrderDetail(orderNumber: Int) {
        viewModelScope.launch {
            try {
                val fetchedBasketList = lambdaRepository.getOrderDetail(getCurrentDate(), orderNumber)
                if (fetchedBasketList.isEmpty()) {
                    _error.postValue("주문 내역이 없습니다.")
                } else {
                    _orderDetails.postValue(fetchedBasketList)
                }
            } catch (e: Exception) {
                _error.postValue("주문 조회 중 오류가 발생했습니다: ${e.message}")
            }
        }
    }

    fun deleteOrder(orderNumber: Int) {
        viewModelScope.launch {
            try {
                _deleteStatus.postValue(DeleteStatus.Loading)
                val result = lambdaRepository.deleteOrder(getCurrentDate(), orderNumber)
                
                if (result == true) {
                    _deleteStatus.postValue(DeleteStatus.Success)
                    _deleteResult.postValue(true)
                } else {
                    _deleteStatus.postValue(DeleteStatus.Error("주문 삭제에 실패했습니다."))
                    _deleteResult.postValue(false)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _deleteStatus.postValue(DeleteStatus.Error("오류가 발생했습니다. 다시 시도해주세요."))
                _deleteResult.postValue(false)
            }
        }
    }

    fun resetDeleteStatus() {
        _deleteStatus.postValue(DeleteStatus.Idle)
        _deleteResult.postValue(null)
    }
}