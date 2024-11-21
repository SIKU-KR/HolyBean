package com.example.holybean.data.model

data class BasketItem(
    val id: Int,
    val name: String,
    val price: Int,
    var count: Int,
    var total: Int
)