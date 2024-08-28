package com.example.holybean.home.dto

import com.example.holybean.dataclass.BasketItem

data class OrderDialogData(
    val basketList: ArrayList<BasketItem>,
    val orderNum: Int,
    val totalPrice: Int,
    val date: String
)
