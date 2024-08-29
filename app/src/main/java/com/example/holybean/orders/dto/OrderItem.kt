package com.example.holybean.orders.dto

data class OrderItem(
    val rowId: Long,
    val orderId: Int,
    val totalAmount: Int,
    val method: String,
    val orderer: String
)