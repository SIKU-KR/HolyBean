package com.example.holybean.common

import java.text.SimpleDateFormat
import java.util.Date

fun getCurrentDate(): String {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd")
    val currentDate = Date()
    return dateFormat.format(currentDate)
}