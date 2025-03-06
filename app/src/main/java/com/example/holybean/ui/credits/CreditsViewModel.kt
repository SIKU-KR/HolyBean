package com.example.holybean.ui.credits

import androidx.lifecycle.ViewModel
import com.example.holybean.network.ApiService
import com.example.holybean.network.RetrofitClient
import com.example.holybean.network.dto.ResponseCredit

class CreditsViewModel : ViewModel() {

    private val apiService = RetrofitClient.retrofit.create(ApiService::class.java)

    fun getCreditsOrders() {

    }

    suspend fun fetchCreditsOrders(): List<ResponseCredit> {
        try {
            val response = apiService.getAllCreditOrders()
            if (!response.isSuccessful) {
                println("error: ${response.code()} - ${response.errorBody()}")
                throw Exception("error on fetch credits")
            }
            return response.body()!!
        } catch (e: Exception) {
            e.printStackTrace()
            throw Exception("error on fetch credits")
        }
    }
}