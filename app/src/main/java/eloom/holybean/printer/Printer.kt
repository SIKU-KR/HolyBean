package eloom.holybean.printer

import android.annotation.SuppressLint
import eloom.holybean.escpos.EscPosCharsetEncoding
import eloom.holybean.escpos.EscPosPrinter
import eloom.holybean.escpos.connection.bluetooth.BluetoothConnection
import eloom.holybean.escpos.connection.bluetooth.BluetoothPrintersConnections
import eloom.holybean.escpos.exceptions.PrinterConnectionException
import kotlin.jvm.Volatile

abstract class Printer(
    private val bluetoothPrintersConnections: BluetoothPrintersConnections,
) {

    private val encoding = EscPosCharsetEncoding("EUC-KR", 13)
    private val discoveryBackoff = BackoffRetry(
        maxAttempts = 3,
        initialDelayMs = 500,
        multiplier = 1.5,
        maxDelayMs = 2_000,
    )
    private val printBackoff = BackoffRetry(
        maxAttempts = 3,
        initialDelayMs = 300,
        multiplier = 2.0,
        maxDelayMs = 1_500,
    )
    @Volatile
    private var lastSuccessfulAddress: String? = null
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
        try {
            printWithRetry(data)
        } catch (error: InterruptedException) {
            throw error
        } catch (error: PrinterConnectionException.ConnectionFailed) {
            throw error
        } catch (error: Exception) {
            throw PrinterConnectionException.ConnectionFailed(error)
        }
    }

    @Synchronized
    @SuppressLint("MissingPermission")
    private fun ensureConnection(): BluetoothConnection {
        val existing = activeConnection?.takeIf { it.isConnected() }
        if (existing != null) {
            return existing
        }
        return discoveryBackoff.run { _ ->
            val connections = refreshAvailableConnections()
            val prioritizedConnections = prioritizeConnections(connections)
            connectToFirstReachable(prioritizedConnections)
        }
    }

    @Synchronized
    private fun ensurePrinter(): EscPosPrinter {
        val connection = ensureConnection()
        val printer = activePrinter
        if (printer != null) {
            return printer
        }
        return EscPosPrinter(connection, 180, 72f, 32, encoding).also(::replacePrinter)
    }

    private fun printWithRetry(data: String) {
        printBackoff.run { attempt ->
            try {
                sendPrintJob(data)
            } catch (error: Exception) {
                handlePrintFailure(error, attempt)
            }
        }
    }

    private fun sendPrintJob(data: String) {
        ensurePrinter().printFormattedTextAndCut(data, 500)
    }

    private fun handlePrintFailure(error: Exception, attempt: Int): Nothing {
        disconnect()
        if (attempt >= printBackoff.maxAttempts) {
            throw PrinterConnectionException.ConnectionFailed(error)
        }
        throw error
    }

    private fun refreshAvailableConnections(): List<BluetoothConnection> {
        val connections = bluetoothPrintersConnections.getList()
        if (connections.isEmpty()) {
            throw PrinterConnectionException.BluetoothUnavailable
        }
        return connections
    }

    private fun prioritizeConnections(connections: List<BluetoothConnection>): List<BluetoothConnection> {
        val target = lastSuccessfulAddress ?: return connections
        return connections.sortedBy { connection -> if (connection.address == target) 0 else 1 }
    }

    private fun connectToFirstReachable(candidates: List<BluetoothConnection>): BluetoothConnection {
        var lastFailure: PrinterConnectionException? = null
        for (candidate in candidates) {
            try {
                candidate.connect()
                onConnectionEstablished(candidate)
                return candidate
            } catch (error: PrinterConnectionException) {
                lastFailure = error
            }
        }
        throw lastFailure ?: PrinterConnectionException.ConnectionFailed(
            IllegalStateException("Unable to establish Bluetooth connection"),
        )
    }

    private fun onConnectionEstablished(connection: BluetoothConnection) {
        invalidatePrinter()
        activeConnection = connection
        lastSuccessfulAddress = connection.address
    }

    private fun invalidatePrinter() {
        activePrinter = null
    }

    private fun replacePrinter(printer: EscPosPrinter) {
        activePrinter = printer
    }
}
