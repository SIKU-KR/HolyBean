package com.example.holybean.data.model

data class OrderItem(
    val rowId: String,
    val orderId: Int,
    val totalAmount: Int,
    val method: String,
    val orderer: String
)