package com.example.holybean.home.dto

import com.example.holybean.dataclass.BasketItem

data class OrderData(
    val basketList: ArrayList<BasketItem>,
    val orderNum: Int,
    var totalPrice: Int,
    val takeOption: String,
    val customer: String,
    val orderMethod: String,
    val date: String
)
