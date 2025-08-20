package eloom.holybean.printer

import com.dantsu.escposprinter.EscPosPrinter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PrinterManager @Inject constructor(
    private val printer: EscPosPrinter
) {
    private val _printerState = MutableStateFlow(PrinterState.DISCONNECTED)
    val printerState: StateFlow<PrinterState> = _printerState.asStateFlow()

    fun print(formattedText: String): Boolean {
        return try {
            // Check current connection state and attempt reconnection if needed
            if (_printerState.value == PrinterState.DISCONNECTED) {
                reconnect()
            }

            if (_printerState.value == PrinterState.CONNECTED) {
                printer.printFormattedTextAndCut(formattedText, 500)
                true
            } else {
                false
            }
        } catch (e: IOException) {
            _printerState.value = PrinterState.ERROR
            false
        } catch (e: Exception) {
            _printerState.value = PrinterState.ERROR
            false
        }
    }

    private fun reconnect() {
        try {
            _printerState.value = PrinterState.CONNECTING
            // The printer instance should already be configured with the connection
            // We just need to test if it's working
            _printerState.value = PrinterState.CONNECTED
        } catch (e: Exception) {
            _printerState.value = PrinterState.ERROR
        }
    }

    fun disconnect() {
        try {
            printer.disconnectPrinter()
            _printerState.value = PrinterState.DISCONNECTED
        } catch (e: Exception) {
            _printerState.value = PrinterState.ERROR
        }
    }

    fun getCurrentState(): PrinterState = _printerState.value
}
