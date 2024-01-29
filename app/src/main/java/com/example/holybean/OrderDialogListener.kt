package com.example.holybean

interface OrderDialogListener {
    fun onOrderConfirmed(orderMethod: String, orderName: String)
}