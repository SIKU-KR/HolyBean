package eloom.holybean.printer

import eloom.holybean.printer.network.PrintFailureReason
import eloom.holybean.printer.network.PrintServerException
import eloom.holybean.printer.network.PrinterAddressResolver
import eloom.holybean.printer.transport.CountingFailTransport
import eloom.holybean.printer.transport.FakePrintTransport
import eloom.holybean.printer.transport.FakePrinterTransportStore
import eloom.holybean.printer.transport.FakeUsbPermissionRequester
import eloom.holybean.printer.transport.FastFailReason
import eloom.holybean.printer.transport.PrintMethod
import eloom.holybean.printer.transport.PrintTransport
import eloom.holybean.printer.transport.PrintTransportSelector
import eloom.holybean.printer.transport.PrinterTransportStore
import eloom.holybean.printer.transport.UsbFastFailException
import eloom.holybean.printer.transport.UsbPartialPrintException
import eloom.holybean.printer.transport.UsbPermissionRequester
import io.mockk.coVerify
import io.mockk.mockk
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
class PiPrintClientTest {

    private val resolver: PrinterAddressResolver = mockk(relaxed = true)

    private fun client(
        selector: PrintTransportSelector,
        dispatcher: CoroutineDispatcher,
    ) = PiPrintClient(selector, resolver, dispatcher)

    private fun TestScope.selector(
        usb: PrintTransport,
        pi: PrintTransport,
        store: PrinterTransportStore = FakePrinterTransportStore(),
        requester: UsbPermissionRequester = FakeUsbPermissionRequester(),
    ) = PrintTransportSelector(
        usb = usb,
        pi = pi,
        store = store,
        permissionRequester = requester,
        appScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
    )

    @Test
    fun `delegates print to active transport`() = runTest {
        val pi = FakePrintTransport(PrintMethod.PI_HTTP)
        val selector = selector(
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
        )
        val pi = FakePrintTransport(PrintMethod.PI_HTTP)
        val selector = selector(usb = usb, pi = pi)
        selector.probe()
        assertEquals(PrintMethod.USB_DIRECT, selector.selection.value.method)

        val client = client(selector, UnconfinedTestDispatcher(testScheduler))
        client.print(emptyList())

        assertEquals(1, usb.printCalls)
        assertEquals(1, pi.printCalls)
    }

    @Test
    fun `partial usb write is not retried and does not fall back`() = runTest {
        val usb = FakePrintTransport(
            PrintMethod.USB_DIRECT,
            onPrint = { throw UsbPartialPrintException(bytesSent = 128, totalBytes = 512) },
        )
        val pi = FakePrintTransport(PrintMethod.PI_HTTP)
        val selector = selector(usb = usb, pi = pi)
        selector.probe()
        val client = client(selector, UnconfinedTestDispatcher(testScheduler))

        val ex = runCatching { client.print(emptyList()) }.exceptionOrNull()

        assertTrue(ex is UsbPartialPrintException)
        assertEquals(1, usb.printCalls)
        assertEquals(0, pi.printCalls)
    }

    @Test
    fun `zero-byte usb write falls back to pi without duplicate usb attempts`() = runTest {
        val usb = FakePrintTransport(
            PrintMethod.USB_DIRECT,
            onPrint = { throw UsbFastFailException(FastFailReason.CLAIM_FAILED, "first write rejected") },
        )
        val pi = FakePrintTransport(PrintMethod.PI_HTTP)
        val selector = selector(usb = usb, pi = pi)
        selector.probe()
        val client = client(selector, UnconfinedTestDispatcher(testScheduler))

        client.print(emptyList())

        assertEquals(1, usb.printCalls)
        assertEquals(1, pi.printCalls)
    }

    @Test
    fun `generic usb failure retries without pi fallback`() = runTest {
        val usb = FakePrintTransport(
            PrintMethod.USB_DIRECT,
            onPrint = { throw RuntimeException("boom") },
        )
        val pi = FakePrintTransport(PrintMethod.PI_HTTP)
        val selector = selector(usb = usb, pi = pi)
        selector.probe()
        val client = client(selector, UnconfinedTestDispatcher(testScheduler))

        val ex = runCatching { client.print(emptyList()) }.exceptionOrNull()

        assertTrue(ex is RuntimeException)
        assertEquals(3, usb.printCalls)
        assertEquals(0, pi.printCalls)
    }

    @Test
    fun `reprobes usb on next print after fast-fail fallback`() = runTest {
        val usb = FakePrintTransport(
            PrintMethod.USB_DIRECT,
            onPrint = { throw UsbFastFailException(FastFailReason.NO_DEVICE, "no device") },
        )
        val pi = FakePrintTransport(PrintMethod.PI_HTTP)
        val selector = selector(usb = usb, pi = pi)
        selector.probe()
        val client = client(selector, UnconfinedTestDispatcher(testScheduler))

        client.print(emptyList())
        assertEquals(PrintMethod.PI_HTTP, selector.selection.value.method)

        usb.onPrint = null // USB 복구
        client.print(emptyList())

        assertEquals(PrintMethod.USB_DIRECT, selector.selection.value.method)
        assertEquals(2, usb.printCalls)
        assertEquals(1, pi.printCalls)
        // 출력 경로 재탐색은 권한 다이얼로그를 띄우지 않아야 한다
        assertEquals(false, usb.lastProbeRequestPermission)
    }

    @Test
    fun `fallback pi unreachable triggers rediscover in same attempt`() = runTest {
        val usb = FakePrintTransport(
            PrintMethod.USB_DIRECT,
            onPrint = { throw UsbFastFailException(FastFailReason.NO_DEVICE, "no device") },
        )
        val pi = CountingFailTransport(
            PrintMethod.PI_HTTP,
            failFirst = 1,
            error = { PrintServerException(PrintFailureReason.ServerUnreachable, "down") },
        )
        val selector = selector(usb = usb, pi = pi)
        selector.probe()
        val client = client(selector, UnconfinedTestDispatcher(testScheduler))

        client.print(emptyList())

        assertEquals(1, usb.printCalls)
        assertEquals(2, pi.printCalls)
        coVerify(exactly = 1) { resolver.rediscover() }
    }

    @Test
    fun `permission grant triggers probe and selects usb`() = runTest {
        val usb = FakePrintTransport(PrintMethod.USB_DIRECT)
        val requester = FakeUsbPermissionRequester()
        val selector = selector(
            usb = usb,
            pi = FakePrintTransport(PrintMethod.PI_HTTP),
            requester = requester,
        )
        assertEquals(PrintMethod.PI_HTTP, selector.selection.value.method)

        requester.onPermissionResult(granted = true)

        assertEquals(PrintMethod.USB_DIRECT, selector.selection.value.method)
        assertEquals(1, usb.probeCalls)
    }

    @Test
    fun `grant collector survives unexpected probe exception and handles next grant`() = runTest {
        val usb = FakePrintTransport(PrintMethod.USB_DIRECT)
        usb.onProbe = { throw IllegalStateException("flaky OEM usb stack") }
        val requester = FakeUsbPermissionRequester()
        val selector = selector(
            usb = usb,
            pi = FakePrintTransport(PrintMethod.PI_HTTP),
            requester = requester,
        )

        // probe가 UsbFastFailException 외의 예외를 던져도 수집기는 죽지 않아야 한다
        requester.onPermissionResult(granted = true)
        assertEquals(PrintMethod.PI_HTTP, selector.selection.value.method)

        usb.onProbe = null
        requester.onPermissionResult(granted = true)
        assertEquals(PrintMethod.USB_DIRECT, selector.selection.value.method)
        assertEquals(2, usb.probeCalls)
    }

    @Test
    fun `concurrent probes are serialized so stale result cannot overwrite newer one`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val usb = FakePrintTransport(PrintMethod.USB_DIRECT)
        usb.onProbe = { gate.await() } // probe A: 장치 존재(null) 결과를 든 채 중단
        val selector = selector(usb = usb, pi = FakePrintTransport(PrintMethod.PI_HTTP))

        val probeA = launch { selector.probe() }
        advanceUntilIdle() // A가 probeFastFail 내부(gate)에서 중단될 때까지 진행
        // A가 대기하는 동안 장치가 뽑힌 상태에서 probe B 시작
        usb.onProbe = null
        usb.fastFail = FastFailReason.NO_DEVICE
        val probeB = launch { selector.probe() }
        gate.complete(Unit)
        probeA.join()
        probeB.join()

        // 나중 probe(B)의 NO_DEVICE 결과가 최종 선택이어야 한다 — A의 낡은 USB 결과가 덮어쓰면 안 된다
        assertEquals(PrintMethod.PI_HTTP, selector.selection.value.method)
    }

    @Test
    fun `permission denial does not probe`() = runTest {
        val usb = FakePrintTransport(PrintMethod.USB_DIRECT)
        val requester = FakeUsbPermissionRequester()
        val selector = selector(
            usb = usb,
            pi = FakePrintTransport(PrintMethod.PI_HTTP),
            requester = requester,
        )

        requester.onPermissionResult(granted = false)

        assertEquals(0, usb.probeCalls)
        assertEquals(PrintMethod.PI_HTTP, selector.selection.value.method)
    }

    @Test
    fun `rediscovers once on server unreachable then retries`() = runTest {
        val pi = CountingFailTransport(
            PrintMethod.PI_HTTP,
            failFirst = 1,
            error = { PrintServerException(PrintFailureReason.ServerUnreachable, "down") },
        )
        val selector = selector(
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
        val selector = selector(
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
        val selector = selector(
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
