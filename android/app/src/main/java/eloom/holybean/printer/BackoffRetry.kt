package eloom.holybean.printer

import kotlin.math.min
import kotlin.math.pow

class BackoffRetry(
    val maxAttempts: Int,
    val initialDelayMs: Long,
    val multiplier: Double = 2.0,
    val maxDelayMs: Long = initialDelayMs,
) {

    init {
        require(maxAttempts > 0) { "maxAttempts must be > 0" }
        require(initialDelayMs >= 0) { "initialDelayMs must be >= 0" }
        require(multiplier >= 1.0) { "multiplier must be >= 1.0" }
        require(maxDelayMs >= initialDelayMs) { "maxDelayMs must be >= initialDelayMs" }
    }

    fun nextDelay(attempt: Int): Long {
        if (attempt <= 0) return initialDelayMs
        if (attempt == 1) return initialDelayMs
        val rawDelay = initialDelayMs.toDouble() * multiplier.pow(attempt - 1)
        return min(maxDelayMs.toDouble(), rawDelay).toLong()
    }

    fun <T> run(block: (attempt: Int) -> T): T {
        var attempt = 1
        var lastError: Throwable? = null
        while (attempt <= maxAttempts) {
            try {
                return block(attempt)
            } catch (error: Throwable) {
                lastError = error
                if (attempt >= maxAttempts) {
                    throw error
                }
                sleepSafely(nextDelay(attempt))
            }
            attempt++
        }
        throw lastError ?: IllegalStateException("BackoffRetry finished without result")
    }

    private fun sleepSafely(delayMs: Long) {
        if (delayMs <= 0) return
        try {
            Thread.sleep(delayMs)
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            throw interrupted
        }
    }
}
