package eloom.holybean.printer

import eloom.holybean.printer.network.PrintCommandDto
import eloom.holybean.printer.network.PrintFailureReason
import eloom.holybean.printer.network.PrintServerException
import eloom.holybean.printer.network.PrinterAddressResolver
import eloom.holybean.printer.transport.CountingFailTransport
import eloom.holybean.printer.transport.FakePrintTransport
import eloom.holybean.printer.transport.FakePrinterTransportStore
import eloom.holybean.printer.transport.FastFailReason
import eloom.holybean.printer.transport.PrintMethod
import eloom.holybean.printer.transport.PrintTransportSelector
import eloom.holybean.printer.transport.UsbFastFailException
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@ExperimentalCoroutinesApi
class PiPrintClientTest {

    private val resolver: PrinterAddressResolver = mockk(relaxed = true)

    private fun client(
        selector: PrintTransportSelector,
        dispatcher: CoroutineDispatcher = UnconfinedTestDispatcher(),
    ) = PiPrintClient(selector, resolver, dispatcher)

    @Test
    fun `delegates print to active transport`() = runTest {
        val pi = FakePrintTransport(PrintMethod.PI_HTTP)
        val selector = PrintTransportSelector(
            usb = FakePrintTransport(PrintMethod.USB_DIRECT, healthResult = false),
            pi = pi,
            store = FakePrinterTransportStore(forcePi = true),
        )
        val client = client(selector, UnconfinedTestDispatcher(testScheduler))
        client.print(emptyList())
        assertEquals(1, pi.printCalls)
    }

    @Test
    fun `fast-fail switches transport and retries once`() = runTest {
        val usb = FakePrintTransport(
            PrintMethod.USB_DIRECT,
            onPrint = { throw UsbFastFailException(FastFailReason.NO_DEVICE, "no device") },
            fastFail = null,
        )
        val pi = FakePrintTransport(PrintMethod.PI_HTTP)
        val selector = PrintTransportSelector(usb = usb, pi = pi, store = FakePrinterTransportStore())
        selector.probe()
        assertEquals(PrintMethod.USB_DIRECT, selector.selection.value.method)

        val client = client(selector, UnconfinedTestDispatcher(testScheduler))
        client.print(emptyList())

        assertEquals(1, usb.printCalls)
        assertEquals(1, pi.printCalls)
    }

    @Test
    fun `transfer-fail does not fall back for that job`() = runTest {
        val usb = FakePrintTransport(
            PrintMethod.USB_DIRECT,
            onPrint = { throw RuntimeException("mid-transfer") },
        )
        val pi = FakePrintTransport(PrintMethod.PI_HTTP)
        val selector = PrintTransportSelector(usb = usb, pi = pi, store = FakePrinterTransportStore())
        selector.probe()
        val client = client(selector, UnconfinedTestDispatcher(testScheduler))

        val ex = runCatching { client.print(emptyList()) }.exceptionOrNull()
        assertTrue(ex is RuntimeException)
        assertEquals(0, pi.printCalls)
    }

    @Test
    fun `rediscovers once on server unreachable then retries`() = runTest {
        val pi = CountingFailTransport(
            PrintMethod.PI_HTTP,
            failFirst = 1,
            error = { PrintServerException(PrintFailureReason.ServerUnreachable, "down") },
        )
        val selector = PrintTransportSelector(
            usb = FakePrintTransport(PrintMethod.USB_DIRECT, healthResult = false),
            pi = pi,
            store = FakePrinterTransportStore(forcePi = true),
        )
        val client = client(selector, UnconfinedTestDispatcher(testScheduler))
        client.print(emptyList())
        assertEquals(2, pi.printCalls)
        coVerify(exactly = 1) { resolver.rediscover() }
    }

    @Test
    fun `does not rediscover on http error`() = runTest {
        val pi = FakePrintTransport(
            PrintMethod.PI_HTTP,
            onPrint = { throw PrintServerException(PrintFailureReason.PrinterOffline, "503") },
        )
        val selector = PrintTransportSelector(
            usb = FakePrintTransport(PrintMethod.USB_DIRECT, healthResult = false),
            pi = pi,
            store = FakePrinterTransportStore(forcePi = true),
        )
        val client = client(selector, UnconfinedTestDispatcher(testScheduler))
        runCatching { client.print(emptyList()) }
        coVerify(exactly = 0) { resolver.rediscover() }
    }

    @Test
    fun `throws after exhausting retries`() = runTest {
        val pi = CountingFailTransport(
            PrintMethod.PI_HTTP,
            failFirst = 3,
            error = { PrintServerException(PrintFailureReason.ServerUnreachable, "down") },
        )
        val selector = PrintTransportSelector(
            usb = FakePrintTransport(PrintMethod.USB_DIRECT, healthResult = false),
            pi = pi,
            store = FakePrinterTransportStore(forcePi = true),
        )
        val client = client(selector, UnconfinedTestDispatcher(testScheduler))
        val ex = runCatching { client.print(emptyList()) }.exceptionOrNull()
        assertTrue(ex is PrintServerException)
        assertEquals(3, pi.printCalls)
    }
}
