package com.example.holybean.network.dto

import com.google.gson.annotations.SerializedName

data class ResponseMenuSales(
    @SerializedName("quantitySold")
    val quantitySold: Int,

    @SerializedName("totalSales")
    val totalSales: Int
)
