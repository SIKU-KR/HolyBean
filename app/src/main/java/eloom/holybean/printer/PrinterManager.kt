package eloom.holybean.printer

import android.util.Log
import com.dantsu.escposprinter.EscPosPrinter
import com.dantsu.escposprinter.connection.bluetooth.BluetoothPrintersConnections
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PrinterManager @Inject constructor(
    private val printer: EscPosPrinter
) {
    private val _printerState = MutableStateFlow(PrinterState.DISCONNECTED)
    val printerState: StateFlow<PrinterState> = _printerState.asStateFlow()

    companion object {
        private const val TAG = "PrinterManager"
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val RETRY_DELAY_MS = 1000L
    }

    init {
        checkConnectionStatus()
    }

    suspend fun printAsync(formattedText: String): PrintResult = withContext(Dispatchers.IO) {
        var attempt = 0
        var lastException: Exception? = null

        while (attempt < MAX_RETRY_ATTEMPTS) {
            try {
                if (!ensureConnection()) {
                    attempt++
                    delay(RETRY_DELAY_MS)
                    continue
                }
                printer.printFormattedTextAndCut(formattedText, 500)
                _printerState.value = PrinterState.CONNECTED
                Log.d(TAG, "Print successful on attempt ${attempt + 1}")
                return@withContext PrintResult.Success

            } catch (e: IOException) {
                lastException = e
                Log.w(TAG, "Print failed with IOException on attempt ${attempt + 1}: ${e.message}")
                _printerState.value = PrinterState.DISCONNECTED
                attempt++
                if (attempt < MAX_RETRY_ATTEMPTS) {
                    delay(RETRY_DELAY_MS)
                }
            } catch (e: Exception) {
                lastException = e
                Log.e(TAG, "Print failed with unexpected exception on attempt ${attempt + 1}: ${e.message}")
                _printerState.value = PrinterState.ERROR
                break
            }
        }

        Log.e(TAG, "Print failed after $MAX_RETRY_ATTEMPTS attempts")
        return@withContext PrintResult.Failure(lastException?.message ?: "Unknown error occurred")
    }

    private fun ensureConnection(): Boolean {
        return when (_printerState.value) {
            PrinterState.CONNECTED -> {
                // Test if connection is still valid
                testConnection()
            }

            PrinterState.DISCONNECTED, PrinterState.ERROR -> {
                attemptReconnection()
            }

            PrinterState.CONNECTING -> {
                false // Already in progress
            }
        }
    }

    private fun testConnection(): Boolean {
        return try {
            // Simple test to verify connection is still valid
            printer.printFormattedText("", 0) // Send empty string as connection test
            true
        } catch (e: Exception) {
            Log.w(TAG, "Connection test failed: ${e.message}")
            _printerState.value = PrinterState.DISCONNECTED
            false
        }
    }

    private fun attemptReconnection(): Boolean {
        return try {
            _printerState.value = PrinterState.CONNECTING
            Log.d(TAG, "Attempting to reconnect to printer...")

            val connection = BluetoothPrintersConnections.selectFirstPaired()
            if (connection != null) {
                _printerState.value = PrinterState.CONNECTED
                Log.d(TAG, "Reconnection successful")
                true
            } else {
                _printerState.value = PrinterState.ERROR
                Log.e(TAG, "No paired bluetooth printer found")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Reconnection failed: ${e.message}")
            _printerState.value = PrinterState.ERROR
            false
        }
    }

    private fun checkConnectionStatus() {
        try {
            val connection = BluetoothPrintersConnections.selectFirstPaired()
            if (connection != null) {
                _printerState.value = PrinterState.CONNECTED
                Log.d(TAG, "Printer connection detected during initialization")
            } else {
                _printerState.value = PrinterState.DISCONNECTED
                Log.w(TAG, "No printer connection found during initialization")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking initial connection status: ${e.message}")
            _printerState.value = PrinterState.ERROR
        }
    }

    fun disconnect() {
        try {
            printer.disconnectPrinter()
            _printerState.value = PrinterState.DISCONNECTED
            Log.d(TAG, "Printer disconnected successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error during disconnect: ${e.message}")
            _printerState.value = PrinterState.ERROR
        }
    }

    fun getCurrentState(): PrinterState = _printerState.value

    fun forceReconnect() {
        Log.d(TAG, "Force reconnection requested")
        _printerState.value = PrinterState.DISCONNECTED
        attemptReconnection()
    }
}

sealed class PrintResult {
    object Success : PrintResult()
    data class Failure(val errorMessage: String) : PrintResult()
}
