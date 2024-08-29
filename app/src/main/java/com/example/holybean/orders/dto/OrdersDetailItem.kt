package com.example.holybean.orders.dto

data class OrdersDetailItem(
    val id: Int,
    val name: String,
    var count: Int,
    var subtotal: Int
)