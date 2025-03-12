package com.example.holybean.network.dto

import com.example.holybean.data.model.MenuItem

data class ResponseMenuList(
    val timestamp: String,
    val menulist: List<MenuItem>
)
