package com.example.holybean.network.dto

import com.google.gson.annotations.SerializedName

data class ResponseSalesReport(
    @SerializedName("menuSales")
    val menuSales: Map<String, ResponseMenuSales>,

    @SerializedName("paymentMethodSales")
    val paymentMethodSales: Map<String, Int>
)