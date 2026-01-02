package eloom.holybean.printer.polymorphism

import eloom.holybean.data.model.OrdersDetailItem
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Text formatter for order list screen reprints.
 * Generates ESC/POS formatted text for receipt reprints.
 */
@Singleton
class OrdersPrinter @Inject constructor() {

    fun makeText(orderNum: Int, basketList: ArrayList<OrdersDetailItem>): String = buildString {
        appendLine("[R]영수증 재출력")
        appendLine("[C]=====================================")
        appendLine("[L]")
        appendLine("[C]<u><font size='big'>주문번호 : ${orderNum}</font></u>")
        appendLine("[L]")
        appendLine("[C]-------------------------------------")
        basketList.forEach { item ->
            appendLine("[L]<b>${item.name}</b>[R]${item.count}")
        }
        appendLine("[L]")
        append("[C]=====================================")
    }
}
