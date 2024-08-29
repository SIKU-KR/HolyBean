package com.example.holybean.home.dto

data class OrderDialogData(
    val basketList: ArrayList<BasketItem>,
    val orderNum: Int,
    val totalPrice: Int,
    val date: String
)
