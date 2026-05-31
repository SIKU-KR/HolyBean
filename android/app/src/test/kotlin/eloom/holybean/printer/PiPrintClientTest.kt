package eloom.holybean.printer

import eloom.holybean.printer.network.PrintFailureReason
import eloom.holybean.printer.network.PrintRequestDto
import eloom.holybean.printer.network.PrintServerApi
import eloom.holybean.printer.network.PrintServerException
import eloom.holybean.printer.network.PrinterAddressResolver
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import java.io.IOException

@ExperimentalCoroutinesApi
class PiPrintClientTest {

    private val api: PrintServerApi = mockk()
    private val resolver: PrinterAddressResolver = mockk(relaxed = true)

    @Test
    fun `posts commands and succeeds on 200`() = runTest {
        val client = PiPrintClient(api, resolver, StandardTestDispatcher(testScheduler))
        coEvery { api.print(any()) } returns Response.success(Unit)
        client.print(emptyList())
        coVerify(exactly = 1) { api.print(any<PrintRequestDto>()) }
    }

    @Test
    fun `retries transient failures up to three attempts`() = runTest {
        val client = PiPrintClient(api, resolver, StandardTestDispatcher(testScheduler))
        coEvery { api.print(any()) } throws RuntimeException("network") andThenThrows
            RuntimeException("network") andThen Response.success(Unit)
        client.print(emptyList())
        advanceUntilIdle()
        coVerify(exactly = 3) { api.print(any<PrintRequestDto>()) }
    }

    @Test
    fun `throws after exhausting retries`() = runTest {
        val client = PiPrintClient(api, resolver, StandardTestDispatcher(testScheduler))
        coEvery { api.print(any()) } returns Response.error(503, ResponseBody.create(null, ""))
        var thrownException: Exception? = null
        try {
            client.print(emptyList())
        } catch (e: Exception) {
            thrownException = e
        }
        advanceUntilIdle()
        assertTrue("Expected PrintServerException", thrownException is PrintServerException)
        coVerify(exactly = 3) { api.print(any<PrintRequestDto>()) }
    }

    @Test
    fun `http 503 maps to PrinterOffline`() = runTest {
        val client = PiPrintClient(api, resolver, StandardTestDispatcher(testScheduler))
        coEvery { api.print(any()) } returns Response.error(503, ResponseBody.create(null, ""))
        val ex = runCatching { client.print(emptyList()) }.exceptionOrNull()
        assertTrue(ex is PrintServerException)
        assertEquals(PrintFailureReason.PrinterOffline, (ex as PrintServerException).reason)
    }

    @Test
    fun `http 500 maps to PrinterError`() = runTest {
        val client = PiPrintClient(api, resolver, StandardTestDispatcher(testScheduler))
        coEvery { api.print(any()) } returns Response.error(500, ResponseBody.create(null, ""))
        val ex = runCatching { client.print(emptyList()) }.exceptionOrNull()
        assertEquals(PrintFailureReason.PrinterError, (ex as PrintServerException).reason)
    }

    @Test
    fun `IOException maps to ServerUnreachable`() = runTest {
        val client = PiPrintClient(api, resolver, StandardTestDispatcher(testScheduler))
        coEvery { api.print(any()) } throws IOException("connection refused")
        val ex = runCatching { client.print(emptyList()) }.exceptionOrNull()
        assertEquals(PrintFailureReason.ServerUnreachable, (ex as PrintServerException).reason)
    }

    @Test
    fun `checkHealth returns false on api failure (best-effort)`() = runTest {
        val client = PiPrintClient(api, resolver, StandardTestDispatcher(testScheduler))
        coEvery { api.health() } throws IOException("down")
        assertEquals(false, client.checkHealth())
    }

    @Test
    fun `checkHealth rethrows CancellationException instead of returning false`() = runTest {
        val client = PiPrintClient(api, resolver, StandardTestDispatcher(testScheduler))
        coEvery { api.health() } throws CancellationException("cancelled")
        var thrown: Throwable? = null
        try {
            client.checkHealth()
        } catch (e: CancellationException) {
            thrown = e
        }
        assertTrue("checkHealth must rethrow CancellationException", thrown is CancellationException)
    }

    @Test
    fun `rediscovers once on unreachable then retries`() = runTest {
        val client = PiPrintClient(api, resolver, StandardTestDispatcher(testScheduler))
        coEvery { api.print(any()) } throws IOException("refused") andThen Response.success(Unit)
        client.print(emptyList())
        advanceUntilIdle()
        coVerify(exactly = 1) { resolver.rediscover() }
        coVerify(exactly = 2) { api.print(any<PrintRequestDto>()) }
    }

    @Test
    fun `does not rediscover on http error (only on IOException)`() = runTest {
        val client = PiPrintClient(api, resolver, StandardTestDispatcher(testScheduler))
        coEvery { api.print(any()) } returns Response.error(503, ResponseBody.create(null, ""))
        runCatching { client.print(emptyList()) }
        advanceUntilIdle()
        coVerify(exactly = 0) { resolver.rediscover() }
    }
}
