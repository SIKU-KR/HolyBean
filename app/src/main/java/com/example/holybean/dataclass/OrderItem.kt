package com.example.holybean.dataclass

data class OrderItem(
    val orderId: Int,
    val totalAmount: Int,
    val method: String,
    val orderer: String,
)