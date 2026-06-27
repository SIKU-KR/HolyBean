package eloom.holybean.printer

import eloom.holybean.di.PrinterDispatcher
import eloom.holybean.printer.network.PrintCommandDto
import eloom.holybean.printer.network.PrintFailureReason
import eloom.holybean.printer.network.PrintServerException
import eloom.holybean.printer.network.PrinterAddressResolver
import eloom.holybean.printer.transport.PrintTransportSelector
import eloom.holybean.printer.transport.UsbFastFailException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PiPrintClient @Inject constructor(
    private val selector: PrintTransportSelector,
    private val resolver: PrinterAddressResolver,
    @PrinterDispatcher private val printerDispatcher: CoroutineDispatcher,
) {
    private val mutex = Mutex()
    private val retry = BackoffRetry(
        maxAttempts = 3,
        initialDelayMs = 300,
        multiplier = 2.0,
        maxDelayMs = 1_500,
    )

    suspend fun print(commands: List<PrintCommandDto>) = withContext(printerDispatcher) {
        mutex.withLock {
            var rediscovered = false
            withRetry {
                val transport = selector.requireActive()
                try {
                    transport.print(commands)
                } catch (e: UsbFastFailException) {
                    val newTransport = selector.reprobeOnFastFail(e.reason)
                    newTransport.print(commands)
                } catch (e: PrintServerException) {
                    if (e.reason == PrintFailureReason.ServerUnreachable && !rediscovered) {
                        rediscovered = true
                        resolver.rediscover()
                    }
                    throw e
                }
            }
        }
    }

    suspend fun checkHealth(): Boolean = withContext(printerDispatcher) {
        selector.requireActive().checkHealth()
    }

    suspend fun printTestReceipt() = print(
        listOf(
            PrintCommandDto(type = "text", content = "HolyBean 테스트 출력", align = "center", bold = true),
            PrintCommandDto(type = "text", content = java.time.LocalDateTime.now().toString(), align = "center"),
            PrintCommandDto(type = "cut"),
        )
    )

    private suspend fun <T> withRetry(block: suspend () -> T): T {
        var attempt = 1
        while (true) {
            try {
                return block()
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                if (attempt >= retry.maxAttempts) {
                    throw error
                }
                delay(retry.nextDelay(attempt))
                attempt++
            }
        }
    }
}
