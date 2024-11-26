package com.example.holybean.data.model

data class OrderData(
    val basketList: List<CartItem>,
    val orderNum: Int,
    var totalPrice: Int,
    val takeOption: String,
    val customer: String,
    val orderMethod: String,
    val date: String,
    val uuid: String
)
