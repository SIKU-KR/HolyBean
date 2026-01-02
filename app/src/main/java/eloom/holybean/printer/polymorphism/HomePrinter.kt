package eloom.holybean.printer.polymorphism

import eloom.holybean.data.model.Order
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Text formatter for home screen receipts.
 * Generates ESC/POS formatted text for customer and POS receipts.
 */
@Singleton
class HomePrinter @Inject constructor() {

    fun receiptTextForCustomer(data: Order): String = buildString {
        appendLine("[C]=====================================")
        appendLine("[L]")
        appendLine("[C]<u><font size='big'>주문번호 : ${data.orderNum}</font></u>")
        appendLine("[L]")
        appendLine("[C]-------------------------------------")
        appendLine("[L]")
        data.orderItems.forEach { item ->
            appendLine("[L]<b>${item.name}</b>[R]${item.count}")
        }
        appendLine("[L]")
        append("[C]=====================================")
    }

    fun receiptTextForPOS(data: Order, option: String): String = buildString {
        appendLine("[C]=====================================")
        appendLine("[L]")
        appendLine("[C]<u><font size='big'>주문번호 : ${data.orderNum}</font></u>")
        appendLine("[L]")
        appendLine("[L]<font size='big'>${option}</font>")
        appendLine("[L]")
        appendLine("[R]주문자 : ${data.customerName}")
        appendLine("[C]-------------------------------------")
        appendLine("[L]")
        data.orderItems.forEach { item ->
            appendLine("[L]<b>${item.name}</b>[R]${item.count}")
        }
        appendLine("[L]")
        appendLine("[R]합계 : ${data.totalAmount}")
        appendLine("[R]${data.orderDate}")
        append("[C]=====================================")
    }
}
