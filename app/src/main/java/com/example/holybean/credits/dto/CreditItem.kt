package com.example.holybean.credits.dto

data class CreditItem (
    val rowId: Long,
    val orderId: Int,
    val totalAmount: Int,
    val date: String,
    val orderer: String
)