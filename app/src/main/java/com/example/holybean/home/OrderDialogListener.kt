package com.example.holybean.home

interface OrderDialogListener {
    fun onOrderConfirmed(orderMethod: String, orderName: String)
}