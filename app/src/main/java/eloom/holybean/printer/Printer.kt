package eloom.holybean.printer

import eloom.holybean.escpos.EscPosCharsetEncoding
import eloom.holybean.escpos.EscPosPrinter
import eloom.holybean.escpos.connection.bluetooth.BluetoothPrintersConnections

abstract class Printer {

    private val printer: EscPosPrinter

    init {
        printer = EscPosPrinter(
            BluetoothPrintersConnections.selectFirstPaired(),
            180,
            72f,
            32,
            EscPosCharsetEncoding("EUC-KR", 13)
        )
    }

    fun print(data: String) {
        printer.printFormattedTextAndCut(data, 500)
    }

    fun disconnect() {
        printer.disconnectPrinter()
    }
}