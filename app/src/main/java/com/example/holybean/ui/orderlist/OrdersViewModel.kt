package com.example.holybean.ui.orderlist

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.holybean.data.model.OrdersDetailItem
import com.example.holybean.data.repository.LambdaRepository
import com.example.holybean.network.ApiService
import com.example.holybean.network.RetrofitClient
import com.example.holybean.network.dto.ResponseOrderItem
import com.example.holybean.printer.OrdersPrinter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@HiltViewModel
class OrdersViewModel @Inject constructor(
    private val lambdaRepository: LambdaRepository
): ViewModel() {

    private val apiService = RetrofitClient.retrofit.create(ApiService::class.java)

    // LiveData 추가
    private val _orderDetails = MutableLiveData<List<OrdersDetailItem>>()
    val orderDetails: LiveData<List<OrdersDetailItem>> get() = _orderDetails

    fun getCurrentDate(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd")
        val currentDate = Date()
        return dateFormat.format(currentDate)
    }

    fun reprint(orderNum: Int, basketList: ArrayList<OrdersDetailItem>) {
        val printer = OrdersPrinter()
        val text = printer.makeText(orderNum, basketList)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                printer.print(text)
                delay(2000)
            } finally {
                printer.disconnect()
            }
        }
    }

    fun fetchOrderDetail(orderNumber: Int) {
        viewModelScope.launch {
            // 데이터를 네트워크에서 비동기로 가져옴
            val fetchedBasketList = withContext(Dispatchers.IO) {
                lambdaRepository.getOrderDetail(getCurrentDate(), orderNumber)
            }
            // LiveData 업데이트
            _orderDetails.postValue(fetchedBasketList)
        }
    }
}