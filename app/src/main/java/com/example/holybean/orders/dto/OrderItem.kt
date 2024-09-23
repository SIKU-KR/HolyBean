package com.example.holybean.orders.dto

data class OrderItem(
    val rowId: String,
    val orderId: Int,
    val totalAmount: Int,
    val method: String,
    val orderer: String
)