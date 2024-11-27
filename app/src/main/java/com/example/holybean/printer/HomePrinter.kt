package com.example.holybean.printer

import com.example.holybean.data.model.Order

class HomePrinter : Printer() {
    fun receiptTextForCustomer(data: Order): String {
        var result = "[C]=====================================\n"
        result += "[L]\n"
        result += "[C]<u><font size='big'>주문번호 : ${data.orderNum}</font></u>\n"
        result += "[L]\n"
        result += "[C]-------------------------------------\n"
        result += "[L]\n"
        for (item in data.orderItems) {
            result += "[L]<b>${item.name}</b>[R]${item.count}\n"
        }
        result += "[L]\n"
        result += "[C]====================================="
        return result
    }

    fun receiptTextForPOS(data: Order, option: String): String {
        var result = "[C]=====================================\n"
        result += "[L]\n"
        result += "[C]<u><font size='big'>주문번호 : ${data.orderNum}</font></u>\n"
        result += "[L]\n"
        result += "[L]<font size='big'>${option}</font>\n"
        result += "[L]\n"
        result += "[R]주문자 : ${data.cumstomerName}\n"
        result += "[C]-------------------------------------\n"
        result += "[L]\n"
        for (item in data.orderItems) {
            result += "[L]<b>${item.name}</b>[R]${item.count}\n"
        }
        result += "[L]\n"
        result += "[R]합계 : ${data.totalAmount}\n"
        result += "[R]${data.orderDate}\n"
        result += "[C]====================================="
        return result
    }

}