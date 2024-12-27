package com.example.holybean.ui.orderlist

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.holybean.data.model.OrderItem
import com.example.holybean.data.model.OrdersDetailItem
import com.example.holybean.network.LambdaConnection
import com.example.holybean.network.RetrofitClient
import com.example.holybean.network.dto.ResponseOrder
import com.example.holybean.network.dto.ResponseOrderItem
import com.example.holybean.printer.OrdersPrinter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class OrdersViewModel : ViewModel() {

    private val lambdaConnection = RetrofitClient.retrofit.create(LambdaConnection::class.java)

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

    suspend fun getOrderList(): ArrayList<OrderItem> {
        val listOrders: ArrayList<OrderItem> = ArrayList()
        try {
            val response = lambdaConnection.getOrderOfDay(getCurrentDate())
            if (response.isSuccessful) {
                val orders: List<ResponseOrder>? = response.body()
                orders?.forEach { order ->
                    listOrders.add(OrderItem(order.orderNum, order.totalAmount, order.orderMethod, order.customerName))
                }
            } else {
                System.err.println("When fetching orderlist" + response.errorBody())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        println(listOrders)
        return listOrders
    }

    fun fetchOrderDetail(orderNumber: Int) {
        viewModelScope.launch {
            try {
                // 데이터를 네트워크에서 비동기로 가져옴
                val fetchedBasketList = withContext(Dispatchers.IO) {
                    getOrderDetailFromNetwork(orderNumber)
                }
                // LiveData 업데이트
                _orderDetails.postValue(fetchedBasketList)
            } catch (e: Exception) {
                e.printStackTrace()
                // 에러 처리 시 빈 리스트 전달
                _orderDetails.postValue(emptyList())
            }
        }
    }

    private suspend fun getOrderDetailFromNetwork(orderNumber: Int): List<OrdersDetailItem> {
        val ordersDetail = ArrayList<OrdersDetailItem>()
        try {
            val response = lambdaConnection.getSpecificOrder(getCurrentDate(), orderNumber)
            if (response.isSuccessful) {
                val items: List<ResponseOrderItem>? = response.body()?.orderItems
                items?.forEach { item ->
                    ordersDetail.add(OrdersDetailItem(item.itemName, item.quantity, item.subtotal))
                }
            } else {
                System.err.println("When fetching order detail: " + response.errorBody())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        println(ordersDetail)
        return ordersDetail
    }

    suspend fun deleteOrder(num: Int): Boolean {
        return try {
            val response = lambdaConnection.deleteOrder(getCurrentDate(), num)
            println(response.message())
            response.isSuccessful
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

}