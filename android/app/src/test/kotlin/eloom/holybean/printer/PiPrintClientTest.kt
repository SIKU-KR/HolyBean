package eloom.holybean.printer

import eloom.holybean.printer.network.PrintRequestDto
import eloom.holybean.printer.network.PrintServerApi
import eloom.holybean.printer.network.PrintServerException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

@ExperimentalCoroutinesApi
class PiPrintClientTest {

    private val api: PrintServerApi = mockk()

    @Test
    fun `posts commands and succeeds on 200`() = runTest {
        val client = PiPrintClient(api, StandardTestDispatcher(testScheduler))
        coEvery { api.print(any()) } returns Response.success(Unit)
        client.print(emptyList())
        coVerify(exactly = 1) { api.print(any<PrintRequestDto>()) }
    }

    @Test
    fun `retries transient failures up to three attempts`() = runTest {
        val client = PiPrintClient(api, StandardTestDispatcher(testScheduler))
        coEvery { api.print(any()) } throws RuntimeException("network") andThenThrows
            RuntimeException("network") andThen Response.success(Unit)
        client.print(emptyList())
        advanceUntilIdle()
        coVerify(exactly = 3) { api.print(any<PrintRequestDto>()) }
    }

    @Test
    fun `throws after exhausting retries`() = runTest {
        val client = PiPrintClient(api, StandardTestDispatcher(testScheduler))
        coEvery { api.print(any()) } returns Response.error(503, okhttp3.ResponseBody.create(null, ""))
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
}
