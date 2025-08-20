package eloom.holybean.printer

import eloom.holybean.data.model.Order

object HomePrinter {
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
        result += "[R]주문자 : ${data.customerName}\n"
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