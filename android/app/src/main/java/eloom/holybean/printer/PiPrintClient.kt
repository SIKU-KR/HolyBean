package eloom.holybean.printer

import eloom.holybean.printer.network.PrintCommandDto
import eloom.holybean.printer.network.PrintRequestDto
import eloom.holybean.printer.network.PrintServerApi
import eloom.holybean.printer.network.PrintServerException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Pi 프린트 서버로 구조화 JSON을 전송하는 클라이언트.
 * 모든 출력은 내부 Mutex로 직렬화되어 영수증이 섞이지 않는다.
 * 일시적 실패는 BackoffRetry 정책으로 최대 3회 재시도한다.
 */
@Singleton
class PiPrintClient @Inject constructor(
    private val api: PrintServerApi,
    @Named("Printer") private val printerDispatcher: CoroutineDispatcher,
) {
    private val mutex = Mutex()
    private val retry = BackoffRetry(
        maxAttempts = 3,
        initialDelayMs = 300,
        multiplier = 2.0,
        maxDelayMs = 1_500,
    )

    /**
     * 명령 배열 1개(영수증 1장)를 출력한다. 실패 시 PrintServerException.
     */
    suspend fun print(commands: List<PrintCommandDto>) = withContext(printerDispatcher) {
        mutex.withLock {
            withRetry {
                val response = api.print(PrintRequestDto(commands))
                if (!response.isSuccessful) {
                    throw PrintServerException("print server returned HTTP ${response.code()}")
                }
            }
        }
    }

    /** /health 핑. 예외/실패는 false. */
    suspend fun checkHealth(): Boolean = withContext(printerDispatcher) {
        runCatching { api.health().isSuccessful }.getOrDefault(false)
    }

    /** 진단용 테스트 영수증 1장 출력. */
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
