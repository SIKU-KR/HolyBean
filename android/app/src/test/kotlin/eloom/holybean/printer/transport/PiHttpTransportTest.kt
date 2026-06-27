package eloom.holybean.printer.transport

import eloom.holybean.printer.network.PrintFailureReason
import eloom.holybean.printer.network.PrintRequestDto
import eloom.holybean.printer.network.PrintServerApi
import eloom.holybean.printer.network.PrintServerException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import java.io.IOException

@ExperimentalCoroutinesApi
class PiHttpTransportTest {

    private val api: PrintServerApi = mockk()
    private val transport = PiHttpTransport(api)

    @Test
    fun `posts commands and succeeds on 200`() = runTest {
        coEvery { api.print(any()) } returns Response.success(Unit)
        transport.print(emptyList())
        coVerify(exactly = 1) { api.print(any<PrintRequestDto>()) }
    }

    @Test
    fun `http 503 maps to PrinterOffline`() = runTest {
        coEvery { api.print(any()) } returns Response.error(503, ResponseBody.create(null, ""))
        val ex = runCatching { transport.print(emptyList()) }.exceptionOrNull()
        assertTrue(ex is PrintServerException)
        assertEquals(PrintFailureReason.PrinterOffline, (ex as PrintServerException).reason)
    }

    @Test
    fun `http 500 maps to PrinterError`() = runTest {
        coEvery { api.print(any()) } returns Response.error(500, ResponseBody.create(null, ""))
        val ex = runCatching { transport.print(emptyList()) }.exceptionOrNull()
        assertEquals(PrintFailureReason.PrinterError, (ex as PrintServerException).reason)
    }

    @Test
    fun `IOException maps to ServerUnreachable`() = runTest {
        coEvery { api.print(any()) } throws IOException("connection refused")
        val ex = runCatching { transport.print(emptyList()) }.exceptionOrNull()
        assertEquals(PrintFailureReason.ServerUnreachable, (ex as PrintServerException).reason)
    }

    @Test
    fun `checkHealth returns false on api failure`() = runTest {
        coEvery { api.health() } throws IOException("down")
        assertFalse(transport.checkHealth())
    }

    @Test
    fun `checkHealth rethrows CancellationException`() = runTest {
        coEvery { api.health() } throws CancellationException("cancelled")
        var thrown: Throwable? = null
        try {
            transport.checkHealth()
        } catch (e: CancellationException) {
            thrown = e
        }
        assertTrue(thrown is CancellationException)
    }
}
