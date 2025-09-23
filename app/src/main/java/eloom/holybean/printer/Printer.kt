package eloom.holybean.printer

import eloom.holybean.escpos.EscPosCharsetEncoding
import eloom.holybean.escpos.EscPosPrinter
import eloom.holybean.escpos.connection.PrinterConnectionException
import eloom.holybean.escpos.connection.bluetooth.BluetoothConnection
import eloom.holybean.escpos.connection.bluetooth.BluetoothPrintersConnections

abstract class Printer(
    private val bluetoothPrintersConnections: BluetoothPrintersConnections,
) {

    private val encoding = EscPosCharsetEncoding("EUC-KR", 13)
    private var activeConnection: BluetoothConnection? = null

    fun print(data: String) {
        val connection = bluetoothPrintersConnections.getList().firstOrNull()
            ?: throw PrinterConnectionException.BluetoothUnavailable

        connection.connect()
        activeConnection = connection

        val printer = EscPosPrinter(connection, 180, 72f, 32, encoding)

        try {
            printer.printFormattedTextAndCut(data, 500)
        } finally {
            printer.disconnectPrinter()
            activeConnection = null
        }
    }

    fun disconnect() {
        activeConnection?.disconnect()
        activeConnection = null
    }
}
