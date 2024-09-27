package com.example.holybean.common.dto

data class MenuItem(
    val id:Int,
    var name:String,
    var price:Int,
    var placement:Int,
    var inuse:Boolean
)
