package com.example.holybean.data.model

data class OrderDialogData(
    val basketList: ArrayList<BasketItem>,
    val orderNum: Int,
    val totalPrice: Int,
    val date: String
)
