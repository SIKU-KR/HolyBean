package eloom.holybean.printer

import android.annotation.SuppressLint
import eloom.holybean.escpos.EscPosCharsetEncoding
import eloom.holybean.escpos.EscPosPrinter
import eloom.holybean.escpos.connection.bluetooth.BluetoothConnection
import eloom.holybean.escpos.connection.bluetooth.BluetoothPrintersConnections
import eloom.holybean.escpos.exceptions.PrinterConnectionException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlin.jvm.Volatile

/**
 * Centralized manager for Bluetooth printer connection.
 *
 * This singleton ensures that only one Bluetooth connection exists across the entire app,
 * preventing connection conflicts between different ViewModels.
 *
 * All printer operations (connect, print, disconnect) are serialized via internal Mutex.
 */
@Singleton
class PrinterConnectionManager @Inject constructor(
    private val bluetoothPrintersConnections: BluetoothPrintersConnections,
    @Named("Printer") private val printerDispatcher: CoroutineDispatcher,
) {
    private val encoding = EscPosCharsetEncoding("EUC-KR", 13)
    private val mutex = Mutex()

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

    /**
     * Establishes a Bluetooth connection to the printer.
     * If already connected, this is a no-op.
     * Thread-safe via internal mutex.
     */
    suspend fun connect() = mutex.withLock {
        ensurePrinter()
    }

    /**
     * Disconnects from the printer and releases resources.
     * Thread-safe via internal mutex.
     */
    suspend fun disconnect() = mutex.withLock {
        disconnectInternal()
    }

    /**
     * Prints the given formatted text.
     * Automatically ensures connection before printing.
     * Thread-safe via internal mutex.
     *
     * @param data ESC/POS formatted text to print
     * @throws PrinterConnectionException if connection or printing fails
     */
    @SuppressLint("MissingPermission")
    suspend fun print(data: String) = withTimeout(PRINT_TIMEOUT_MS) {
        withContext(printerDispatcher) {
            mutex.withLock {
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
        }
    }

    /**
     * Connects, prints, and disconnects in a single atomic operation.
     * Useful for one-off print jobs from OrdersViewModel or ReportViewModel.
     *
     * @param data ESC/POS formatted text to print
     */
    suspend fun printAndDisconnect(data: String) {
        try {
            print(data)
        } finally {
            runCatching { disconnect() }
        }
    }

    private fun disconnectInternal() {
        runCatching { activePrinter?.disconnectPrinter() }
        activeConnection = null
        activePrinter = null
    }

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

    private fun ensurePrinter(): EscPosPrinter {
        val connection = ensureConnection()
        val printer = activePrinter
        if (printer != null) {
            return printer
        }
        return EscPosPrinter(connection, 180, 72f, 32, encoding).also { newPrinter ->
            activePrinter = newPrinter
        }
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
        disconnectInternal()
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
        activePrinter = null
        activeConnection = connection
        lastSuccessfulAddress = connection.address
    }

    private companion object {
        const val PRINT_TIMEOUT_MS = 30_000L // 30 seconds
    }
}
