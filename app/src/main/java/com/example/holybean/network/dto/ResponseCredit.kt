package com.example.holybean.network.dto

data class ResponseCredit(
    val totalAmount: Int,
    val orderNum: Int,
    val orderDate: String,
    val customerName: String
)