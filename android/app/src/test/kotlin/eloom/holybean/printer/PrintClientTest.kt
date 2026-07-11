package eloom.holybean.printer

import eloom.holybean.printer.transport.FakePrintTransport
import eloom.holybean.printer.transport.FakeUsbPermissionRequester
import eloom.holybean.printer.transport.FastFailReason
import eloom.holybean.printer.transport.PrintTransport
import eloom.holybean.printer.transport.PrintTransportSelector
import eloom.holybean.printer.transport.UsbFastFailException
import eloom.holybean.printer.transport.UsbPartialPrintException
import eloom.holybean.printer.transport.UsbPermissionRequester
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@ExperimentalCoroutinesApi
class PrintClientTest {

    private fun client(
        selector: PrintTransportSelector,
        dispatcher: CoroutineDispatcher,
    ) = PrintClient(selector, dispatcher)

    private fun TestScope.selector(
        usb: PrintTransport,
        requester: UsbPermissionRequester = FakeUsbPermissionRequester(),
    ) = PrintTransportSelector(
        usb = usb,
        permissionRequester = requester,
        appScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
    )

    @Test
    fun `delegates print to usb transport`() = runTest {
        val usb = FakePrintTransport()
        val client = client(selector(usb), UnconfinedTestDispatcher(testScheduler))
        client.print(emptyList())
        assertEquals(1, usb.printCalls)
    }

    @Test
    fun `partial usb write is not retried`() = runTest {
        val usb = FakePrintTransport(
            onPrint = { throw UsbPartialPrintException(bytesSent = 128, totalBytes = 512) },
        )
        val client = client(selector(usb), UnconfinedTestDispatcher(testScheduler))

        val ex = runCatching { client.print(emptyList()) }.exceptionOrNull()

        assertTrue(ex is UsbPartialPrintException)
        assertEquals(1, usb.printCalls)
    }

    @Test
    fun `fast-fail is retried up to max attempts then propagates`() = runTest {
        val usb = FakePrintTransport(
            onPrint = { throw UsbFastFailException(FastFailReason.NO_DEVICE, "no device") },
        )
        val client = client(selector(usb), UnconfinedTestDispatcher(testScheduler))

        val ex = runCatching { client.print(emptyList()) }.exceptionOrNull()

        assertTrue(ex is UsbFastFailException)
        assertEquals(3, usb.printCalls)
    }

    @Test
    fun `generic failure retries then propagates`() = runTest {
        val usb = FakePrintTransport(
            onPrint = { throw RuntimeException("boom") },
        )
        val client = client(selector(usb), UnconfinedTestDispatcher(testScheduler))

        val ex = runCatching { client.print(emptyList()) }.exceptionOrNull()

        assertTrue(ex is RuntimeException)
        assertEquals(3, usb.printCalls)
    }

    @Test
    fun `transient failure recovers within retries`() = runTest {
        var attempts = 0
        val usb = FakePrintTransport(
            onPrint = { if (++attempts < 2) throw UsbFastFailException(FastFailReason.CLAIM_FAILED, "flaky") },
        )
        val client = client(selector(usb), UnconfinedTestDispatcher(testScheduler))

        client.print(emptyList())

        assertEquals(2, usb.printCalls)
    }

    @Test
    fun `permission grant triggers probe`() = runTest {
        val usb = FakePrintTransport()
        val requester = FakeUsbPermissionRequester()
        selector(usb = usb, requester = requester)

        requester.onPermissionResult(granted = true)

        assertEquals(1, usb.probeCalls)
    }

    @Test
    fun `grant collector survives unexpected probe exception`() = runTest {
        val usb = FakePrintTransport()
        usb.onProbe = { throw IllegalStateException("flaky OEM usb stack") }
        val requester = FakeUsbPermissionRequester()
        selector(usb = usb, requester = requester)

        // probe가 예외를 던져도 수집기는 죽지 않고 다음 허용을 처리해야 한다
        requester.onPermissionResult(granted = true)
        usb.onProbe = null
        requester.onPermissionResult(granted = true)

        assertEquals(2, usb.probeCalls)
    }

    @Test
    fun `permission denial does not probe`() = runTest {
        val usb = FakePrintTransport()
        val requester = FakeUsbPermissionRequester()
        selector(usb = usb, requester = requester)

        requester.onPermissionResult(granted = false)

        assertEquals(0, usb.probeCalls)
    }

    @Test
    fun `concurrent probes are serialized`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val usb = FakePrintTransport()
        usb.onProbe = { gate.await() }
        val selector = selector(usb = usb)

        val probeA = launch { selector.probe() }
        advanceUntilIdle()
        usb.onProbe = null
        usb.fastFail = FastFailReason.NO_DEVICE
        val probeB = launch { selector.probe() }
        gate.complete(Unit)
        probeA.join()
        probeB.join()

        // 두 probe가 겹치지 않고 순차 실행되어야 한다
        assertEquals(2, usb.probeCalls)
    }
}
