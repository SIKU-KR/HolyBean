package eloom.holybean.ui.orderlist

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import eloom.holybean.data.model.OrdersDetailItem
import eloom.holybean.data.repository.LambdaRepository
import eloom.holybean.network.ApiService
import eloom.holybean.network.RetrofitClient
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

    private val apiService = RetrofitClient.retrofit.create(ApiService::class.java)

    // LiveData 추가
    private val _orderDetails = MutableLiveData<List<OrdersDetailItem>>()
    val orderDetails: LiveData<List<OrdersDetailItem>> get() = _orderDetails

    private val _error = MutableLiveData<String>()
    val error: LiveData<String> get() = _error

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
                _orderDetails.postValue(fetchedBasketList)
            } catch (e: Exception) {
                _error.postValue("Failed to fetch order details: ${e.message}")
            }
        }
    }
}