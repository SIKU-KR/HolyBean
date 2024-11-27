package com.example.holybean.interfaces

import com.example.holybean.data.model.Order

interface OrderDialogListener {
    fun onOrderConfirmed(data: Order, takeOption: String)
}