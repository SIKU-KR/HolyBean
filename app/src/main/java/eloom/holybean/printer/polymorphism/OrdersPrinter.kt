package eloom.holybean.printer.polymorphism

import eloom.holybean.data.model.OrdersDetailItem
import eloom.holybean.escpos.connection.bluetooth.BluetoothPrintersConnections
import eloom.holybean.printer.Printer
import javax.inject.Inject

class OrdersPrinter @Inject constructor(
    bluetoothPrintersConnections: BluetoothPrintersConnections,
) : Printer(bluetoothPrintersConnections) {
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
