package com.example.holybean.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.holybean.data.model.CartItem
import com.example.holybean.data.model.Order
import com.example.holybean.interfaces.MainActivityListener
import com.example.holybean.interfaces.OrderDialogListener
import com.example.holybean.network.LambdaConnection
import com.example.holybean.network.RetrofitClient
import com.example.holybean.printer.HomePrinter
import kotlinx.coroutines.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class HomeViewModel : OrderDialogListener, ViewModel() {

    private var mainListener: MainActivityListener? = null
    private val lambdaConnection = RetrofitClient.retrofit.create(LambdaConnection::class.java)

    // LiveData for loading state and error messages
    private val _loadingState = MutableLiveData<Boolean>()
    val loadingState: LiveData<Boolean> get() = _loadingState

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
            _loadingState.postValue(true) // 로딩 시작
            try {
                withTimeout(5000) { // 5초 타임아웃 설정
                    // 1. POST 요청
                    requestPostOrder(data)
                    // 2. 영수증 출력
                    printReceipt(data, takeOption)
                    // 3. 홈 프래그먼트 전환
                    withContext(Dispatchers.Main) {
                        mainListener?.replaceHomeFragment()
                    }
                }
            } catch (e: TimeoutCancellationException) {
                e.printStackTrace()
                _errorMessage.postValue("주문 처리 시간이 초과되었습니다.")
            } catch (e: Exception) {
                e.printStackTrace()
                _errorMessage.postValue("주문 처리 중 오류가 발생했습니다.")
            } finally {
                _loadingState.postValue(false) // 로딩 종료
            }
        }
    }

    private suspend fun requestPostOrder(data: Order) {
        try {
            println("uploading order: " + data.toString())
            val response = lambdaConnection.postOrder(data)
            if (response.isSuccessful) {
                println("Order posted successfully with status: ${response.code()}")
            } else {
                println("Error: ${response.code()} - ${response.message()}")
                throw Exception("주문 서버 응답 실패")
            }
        } catch (e: Exception) {
            println("Network error occurred: ${e.localizedMessage}")
            e.printStackTrace()
            throw e
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

    fun clearErrorMessage() {
        _errorMessage.value = null
    }
}
