package eloom.holybean.printer

import android.annotation.SuppressLint
import eloom.holybean.escpos.EscPosCharsetEncoding
import eloom.holybean.escpos.EscPosPrinter
import eloom.holybean.escpos.connection.bluetooth.BluetoothConnection
import eloom.holybean.escpos.connection.bluetooth.BluetoothPrintersConnections
import eloom.holybean.escpos.exceptions.PrinterConnectionException

abstract class Printer(
    private val bluetoothPrintersConnections: BluetoothPrintersConnections,
) {

    private val encoding = EscPosCharsetEncoding("EUC-KR", 13)
    private var activeConnection: BluetoothConnection? = null
    private var activePrinter: EscPosPrinter? = null

    @Synchronized
    fun connect() {
        ensurePrinter()
    }

    @Synchronized
    fun disconnect() {
        runCatching { activePrinter?.disconnectPrinter() }
        activeConnection = null
        activePrinter = null
    }

    @Synchronized
    fun print(data: String) {
        val printer = ensurePrinter()
        try {
            printer.printFormattedTextAndCut(data, 500)
        } catch (error: Exception) {
            retryPrint(data, error)
        }
    }

    @Synchronized
    @SuppressLint("MissingPermission")
    private fun ensureConnection(): BluetoothConnection {
        val existing = activeConnection?.takeIf { it.isConnected() }
        if (existing != null) {
            return existing
        }
        val connection = bluetoothPrintersConnections.getList().firstOrNull()
            ?: throw PrinterConnectionException.BluetoothUnavailable
        connection.connect()
        activeConnection = connection
        return connection
    }

    @Synchronized
    private fun ensurePrinter(): EscPosPrinter {
        val connection = ensureConnection()
        val printer = activePrinter
        if (printer != null) {
            return printer
        }
        return EscPosPrinter(connection, 180, 72f, 32, encoding).also {
            activePrinter = it
        }
    }

    private fun retryPrint(data: String, cause: Exception) {
        disconnect()
        connect()
        try {
            ensurePrinter().printFormattedTextAndCut(data, 500)
        } catch (error: Exception) {
            throw PrinterConnectionException.ConnectionFailed(error)
        }
    }
}
