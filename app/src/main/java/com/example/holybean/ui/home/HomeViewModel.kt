package com.example.holybean.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.holybean.interfaces.MainActivityListener
import com.example.holybean.printer.HomePrinter
import com.example.holybean.interfaces.OrderDialogListener
import com.example.holybean.data.model.CartItem
import com.example.holybean.data.model.Order
import com.example.holybean.network.LambdaConnection
import com.example.holybean.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import kotlin.concurrent.thread

class HomeViewModel @Inject constructor(
) : OrderDialogListener, ViewModel() {

    private var mainListener: MainActivityListener? = null
    private val lambdaConnection = RetrofitClient.retrofit.create(LambdaConnection::class.java)

    fun setMainActivityListener(listener: MainActivityListener) {
        this.mainListener = listener
    }

    fun getCurrentDate(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd")
        val currentDate = Date()
        return dateFormat.format(currentDate)
    }

    // Calculating total price business logic
    fun getTotal(basketList: ArrayList<CartItem>): Int {
        var totalPrice = 0
        for (item in basketList) {
            item.total = item.count * item.price
            totalPrice += item.total
        }
        return totalPrice
    }

    fun getUUID(): String {
        val uuid = UUID.randomUUID().toString()
        return uuid
    }

    suspend fun getCurrentOrderNum(): Int {
        return try {
            val response = lambdaConnection.getOrderNumber()
            if (response.isSuccessful) {
                response.body()?.nextOrderNum ?: -1
            } else {
                println("Error: ${response.code()} - ${response.message()}")
                -1
            }
        } catch (e: Exception) {
            e.printStackTrace()
            -1
        }
    }

    override fun onOrderConfirmed(data: Order, takeOption: String) {
        viewModelScope.launch {
            try {
                // 1. POST 요청
                requestPostOrder(data)
                // 2. 영수증 출력
                printReceipt(data, takeOption)
                // 3. 홈 프래그먼트 전환
                mainListener?.replaceHomeFragment()
            } catch (e: Exception) {
                // 예외 처리
                e.printStackTrace()
            }
        }
    }

    private suspend fun requestPostOrder(data: Order) {
        try {
            val response = lambdaConnection.postOrder(data)
            if (response.isSuccessful) {
                println("Order posted successfully with status: ${response.code()}")
            } else {
                println("Error: ${response.code()} - ${response.message()}")
            }
        } catch (e: Exception) {
            println("Network error occurred: ${e.localizedMessage}")
            e.printStackTrace()
        }
    }
    private fun printReceipt(data: Order, takeOption: String) {
        val printer = HomePrinter()
        val receiptForCustomer = printer.receiptTextForCustomer(data)
        val receiptForPOS = printer.receiptTextForPOS(data, takeOption)

        thread {
            printer.print(receiptForCustomer)
            Thread.sleep(500)
            printer.print(receiptForPOS)
            Thread.sleep(2000)
            printer.disconnect()
        }
    }
}