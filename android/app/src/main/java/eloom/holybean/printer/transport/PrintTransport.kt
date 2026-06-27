package eloom.holybean.printer.transport

import eloom.holybean.printer.network.PrintCommandDto

enum class PrintMethod {
    USB_DIRECT,
    PI_HTTP,
}

interface PrintTransport {
    val method: PrintMethod
    suspend fun print(commands: List<PrintCommandDto>)
    suspend fun checkHealth(): Boolean
    suspend fun probeFastFail(): FastFailReason? = null
}
