package com.example.holybean.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.holybean.data.model.CartItem
import com.example.holybean.data.model.Order
import com.example.holybean.interfaces.MainActivityListener
import com.example.holybean.interfaces.OrderDialogListener
import com.example.holybean.network.LambdaConnection
import com.example.holybean.network.RetrofitClient
import com.example.holybean.printer.HomePrinter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class HomeViewModel : OrderDialogListener, ViewModel() {

    private var mainListener: MainActivityListener? = null
    private val lambdaConnection = RetrofitClient.retrofit.create(LambdaConnection::class.java)

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
                withContext(Dispatchers.Main) {
                    mainListener?.replaceHomeFragment()
                }
            } catch (e: Exception) {
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

    private suspend fun printReceipt(data: Order, takeOption: String) {
        withContext(Dispatchers.IO) {
            val printer = HomePrinter()
            val receiptForCustomer = printer.receiptTextForCustomer(data)
            val receiptForPOS = printer.receiptTextForPOS(data, takeOption)

            try {
                printer.print(receiptForCustomer)
                delay(500) // 500ms 대기
                printer.print(receiptForPOS)
                delay(2000) // 2000ms 대기
            } finally {
                printer.disconnect()
            }
        }
    }
}