package com.example.holybean.home.dto

data class OrderData(
    val basketList: ArrayList<BasketItem>,
    val orderNum: Int,
    var totalPrice: Int,
    val takeOption: String,
    val customer: String,
    val orderMethod: String,
    val date: String
)
