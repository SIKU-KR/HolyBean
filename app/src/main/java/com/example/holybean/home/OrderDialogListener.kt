package com.example.holybean.home

interface OrderDialogListener {
    fun onOrderConfirmed(takeOption: String, ordererName: String, orderMethod: String)
    fun onOrderConfirmed(takeOption: String, ordererName: String, firstMethod: String, secondMethod: String, firstAmount: Int, secondAmount: Int)
}