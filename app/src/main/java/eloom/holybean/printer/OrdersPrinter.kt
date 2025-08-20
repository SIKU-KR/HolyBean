package eloom.holybean.printer

import eloom.holybean.data.model.OrdersDetailItem

object OrdersPrinter {
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