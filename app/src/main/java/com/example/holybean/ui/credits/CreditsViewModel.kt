package com.example.holybean.ui.credits

import androidx.lifecycle.ViewModel
import com.example.holybean.data.model.CreditItem
import com.example.holybean.network.ApiService
import com.example.holybean.network.RetrofitClient

class CreditsViewModel : ViewModel() {

    private val apiService = RetrofitClient.retrofit.create(ApiService::class.java)

    suspend fun fetchCreditsOrders(): ArrayList<CreditItem> {
        val result = arrayListOf<CreditItem>()
        try {
            val response = apiService.getAllCreditOrders()
            if (!response.isSuccessful) {
                println("error: ${response.code()} - ${response.errorBody()}")
                throw Exception("error on fetch credits")
            }
            response.body()!!.forEach { i ->
                result.add(
                    CreditItem(
                        orderId = i.orderNum, totalAmount = i.totalAmount, date = i.orderDate, orderer = i.customerName
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            throw Exception("error on fetch credits")
        }
        return result
    }
}