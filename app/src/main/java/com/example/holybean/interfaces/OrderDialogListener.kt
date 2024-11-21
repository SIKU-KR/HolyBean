package com.example.holybean.interfaces

import com.example.holybean.data.model.OrderData
import com.example.holybean.data.model.OrderDataWithDualMethod

interface OrderDialogListener {
    fun onOrderConfirmed(data: OrderData)
    fun onOrderConfirmed(data: OrderDataWithDualMethod)
}