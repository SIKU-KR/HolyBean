package eloom.holybean.printer

import eloom.holybean.di.PrinterDispatcher
import eloom.holybean.printer.network.PrintCommandDto
import eloom.holybean.printer.network.PrintFailureReason
import eloom.holybean.printer.network.PrintServerException
import eloom.holybean.printer.network.PrinterAddressResolver
import eloom.holybean.printer.transport.PrintMethod
import eloom.holybean.printer.transport.PrintTransport
import eloom.holybean.printer.transport.PrintTransportSelector
import eloom.holybean.printer.transport.UsbFastFailException
import eloom.holybean.printer.transport.UsbPartialPrintException
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
            // 직접 Pi 출력과 USB 폴백 공용: ServerUnreachable이면 같은 시도 안에서
            // rediscover 후 예외를 전파해 다음 재시도가 갱신된 주소를 쓰게 한다
            suspend fun printViaPi(piTransport: PrintTransport) {
                try {
                    piTransport.print(commands)
                } catch (e: PrintServerException) {
                    if (e.reason == PrintFailureReason.ServerUnreachable && !rediscovered) {
                        rediscovered = true
                        resolver.rediscover()
                    }
                    throw e
                }
            }

            selector.reprobeForPrint()
            withRetry {
                val transport = selector.requireActive()
                if (transport.method == PrintMethod.USB_DIRECT) {
                    try {
                        transport.print(commands)
                    } catch (e: UsbFastFailException) {
                        // 아무것도 인쇄되지 않은 실패만 여기로 오므로 Pi 폴백이 안전하다
                        printViaPi(selector.fallbackToPi(e.reason))
                    }
                } else {
                    printViaPi(transport)
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
