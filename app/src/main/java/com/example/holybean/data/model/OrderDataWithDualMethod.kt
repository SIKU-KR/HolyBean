package com.example.holybean.data.model

data class OrderDataWithDualMethod(
    val basketList: ArrayList<BasketItem>,
    val orderNum: Int,
    val totalPrice: Int,
    val takeOption: String,
    val customer: String,
    val firstMethod: String,
    val secondMethod: String,
    val firstAmount: Int,
    val secondAmount: Int,
    val date: String
)
