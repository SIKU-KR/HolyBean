package com.example.holybean.dataclass

data class OrdersDetailItem(
    val id: Int,
    val name: String,
    var count: Int,
    var subtotal: Int
)