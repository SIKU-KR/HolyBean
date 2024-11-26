package com.example.holybean.data.model

data class OrderDialogData(
    val cartItems: List<CartItem>,
    val orderNum: Int,
    val totalPrice: Int,
    val date: String
)
