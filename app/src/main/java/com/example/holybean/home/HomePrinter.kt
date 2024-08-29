package com.example.holybean.home

import com.example.holybean.common.Printer
import com.example.holybean.home.dto.OrderData

class HomePrinter : Printer() {

    fun receiptTextForCustomer(data: OrderData): String {
        var result = "[C]=====================================\n"
        result += "[L]\n"
        result += "[C]<u><font size='big'>주문번호 : ${data.orderNum}</font></u>\n"
        result += "[L]\n"
        result += "[C]-------------------------------------\n"
        result += "[L]\n"
        for (item in data.basketList) {
            result += "[L]<b>${item.name}</b>[R]${item.count}\n"
        }
        result += "[L]\n"
        result += "[C]====================================="
        return result
    }

    fun receiptTextForPOS(data: OrderData): String {
        var result = "[C]=====================================\n"
        result += "[L]\n"
        result += "[C]<u><font size='big'>주문번호 : ${data.orderNum}</font></u>\n"
        result += "[L]\n"
        result += "[L]<font size='big'>${data.takeOption}</font>\n"
        result += "[L]\n"
        result += "[R]주문자 : ${data.customer}\n"
        result += "[C]-------------------------------------\n"
        result += "[L]\n"
        for (item in data.basketList) {
            result += "[L]<b>${item.name}</b>[R]${item.count}\n"
        }
        result += "[L]\n"
        result += "[R]합계 : ${data.totalPrice}\n"
        result += "[C]-------------------------------------\n"
        result += "[R]결제수단 : ${data.orderMethod}\n"
        result += "[R]${data.date}\n"
        result += "[C]====================================="
        return result
    }

}