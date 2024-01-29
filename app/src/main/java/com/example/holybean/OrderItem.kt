package com.example.holybean

data class OrderItem(
    val orderId: Int,
    val totalAmount: Int,
    val method: String,
    val orderer: String,
)