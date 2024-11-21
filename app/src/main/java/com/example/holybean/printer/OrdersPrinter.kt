package com.example.holybean.printer

import com.example.holybean.data.model.OrdersDetailItem

class OrdersPrinter: Printer() {
    fun makeText(orderNum: Int, basketList: ArrayList<OrdersDetailItem>): String {
        var result = "[R]영수증 재출력\n"
        result += "[C]=====================================\n"
        result += "[L]\n"
        result += "[C]<u><font size='big'>주문번호 : ${orderNum}</font></u>\n"
        result += "[L]\n"
        result += "[C]-------------------------------------\n"
        for (item in basketList) {
            result += "[L]<b>${item.name}</b>[R]${item.count}\n"
        }
        result += "[L]\n"
        result += "[C]====================================="
        return result
    }
}