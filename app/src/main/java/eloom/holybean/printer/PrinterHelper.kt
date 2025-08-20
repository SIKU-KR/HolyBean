package eloom.holybean.printer

import android.util.Log
import eloom.holybean.data.model.Order
import eloom.holybean.data.model.OrdersDetailItem
import eloom.holybean.data.model.PrinterDTO
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Helper class that demonstrates the new printer system usage.
 * This class shows how UI components should interact with the PrinterManager
 * using the utility formatter objects.
 */
@Singleton
class PrinterHelper @Inject constructor(
    private val printerManager: PrinterManager
) {
    companion object {
        private const val TAG = "PrinterHelper"
    }

    /**
     * Print customer receipt using HomePrinter formatter
     */
    fun printCustomerReceipt(order: Order): Boolean {
        return try {
            val formattedText = HomePrinter.receiptTextForCustomer(order)
            val result = printerManager.print(formattedText)
            
            when (result) {
                is PrintResult.Success -> {
                    Log.d(TAG, "Customer receipt printed successfully")
                    true
                }
                is PrintResult.Failure -> {
                    Log.e(TAG, "Failed to print customer receipt: ${result.errorMessage}")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error printing customer receipt: ${e.message}")
            false
        }
    }

    /**
     * Print POS receipt using HomePrinter formatter
     */
    fun printPOSReceipt(order: Order, option: String): Boolean {
        return try {
            val formattedText = HomePrinter.receiptTextForPOS(order, option)
            val result = printerManager.print(formattedText)
            
            when (result) {
                is PrintResult.Success -> {
                    Log.d(TAG, "POS receipt printed successfully")
                    true
                }
                is PrintResult.Failure -> {
                    Log.e(TAG, "Failed to print POS receipt: ${result.errorMessage}")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error printing POS receipt: ${e.message}")
            false
        }
    }

    /**
     * Print order reprint using OrdersPrinter formatter
     */
    fun printOrderReprint(orderNum: Int, basketList: ArrayList<OrdersDetailItem>): Boolean {
        return try {
            val formattedText = OrdersPrinter.makeText(orderNum, basketList)
            val result = printerManager.print(formattedText)
            
            when (result) {
                is PrintResult.Success -> {
                    Log.d(TAG, "Order reprint printed successfully")
                    true
                }
                is PrintResult.Failure -> {
                    Log.e(TAG, "Failed to print order reprint: ${result.errorMessage}")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error printing order reprint: ${e.message}")
            false
        }
    }

    /**
     * Print sales report using ReportPrinter formatter
     */
    fun printSalesReport(reportData: PrinterDTO): Boolean {
        return try {
            val formattedText = ReportPrinter.getPrintingText(reportData)
            val result = printerManager.print(formattedText)
            
            when (result) {
                is PrintResult.Success -> {
                    Log.d(TAG, "Sales report printed successfully")
                    true
                }
                is PrintResult.Failure -> {
                    Log.e(TAG, "Failed to print sales report: ${result.errorMessage}")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error printing sales report: ${e.message}")
            false
        }
    }

    /**
     * Get current printer connection state
     */
    fun getPrinterState(): PrinterState = printerManager.getCurrentState()

    /**
     * Force reconnection to printer
     */
    fun forceReconnect() {
        Log.d(TAG, "Forcing printer reconnection...")
        printerManager.forceReconnect()
    }

    /**
     * Disconnect from printer
     */
    fun disconnect() {
        Log.d(TAG, "Disconnecting from printer...")
        printerManager.disconnect()
    }
}
