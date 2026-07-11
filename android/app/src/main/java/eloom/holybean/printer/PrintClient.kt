package eloom.holybean.printer

import eloom.holybean.di.PrinterDispatcher
import eloom.holybean.printer.network.PrintCommandDto
import eloom.holybean.printer.transport.PrintTransportSelector
import eloom.holybean.printer.transport.UsbPartialPrintException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PrintClient @Inject constructor(
    private val selector: PrintTransportSelector,
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
            withRetry {
                selector.requireActive().print(commands)
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
                // 일부가 이미 인쇄된 실패는 재시도하면 영수증이 중복 출력된다
                if (error is UsbPartialPrintException) throw error
                if (attempt >= retry.maxAttempts) {
                    throw error
                }
                delay(retry.nextDelay(attempt))
                attempt++
            }
        }
    }
}
