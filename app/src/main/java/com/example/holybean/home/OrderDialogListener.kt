package com.example.holybean.home

import com.example.holybean.home.dto.OrderData
import com.example.holybean.home.dto.OrderDataWithDualMethod

interface OrderDialogListener {
    fun onOrderConfirmed(data: OrderData)
    fun onOrderConfirmed(data: OrderDataWithDualMethod)
}