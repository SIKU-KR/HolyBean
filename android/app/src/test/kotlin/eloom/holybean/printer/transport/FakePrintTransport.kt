package eloom.holybean.printer.transport

import eloom.holybean.printer.network.PrintCommandDto

class FakePrintTransport(
    override val method: PrintMethod,
    private val onPrint: (suspend (List<PrintCommandDto>) -> Unit)? = null,
    private val healthResult: Boolean = true,
    private val fastFail: FastFailReason? = null,
) : PrintTransport {

    var printCalls = 0
        private set

    override suspend fun print(commands: List<PrintCommandDto>) {
        printCalls++
        onPrint?.invoke(commands)
    }

    override suspend fun checkHealth(): Boolean = healthResult

    override suspend fun probeFastFail(): FastFailReason? = fastFail
}

class CountingFailTransport(
    override val method: PrintMethod,
    private val failFirst: Int,
    private val error: () -> Throwable,
) : PrintTransport {

    var printCalls = 0
        private set

    override suspend fun print(commands: List<PrintCommandDto>) {
        printCalls++
        if (printCalls <= failFirst) throw error()
    }

    override suspend fun checkHealth(): Boolean = true

    override suspend fun probeFastFail(): FastFailReason? = null
}
