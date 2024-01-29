package com.example.holybean

data class OrdersDetailItem(
    val id: Int,
    val name: String,
    var count: Int,
    var subtotal: Int
)