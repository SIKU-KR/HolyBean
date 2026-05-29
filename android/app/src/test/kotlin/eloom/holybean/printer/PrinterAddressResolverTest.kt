package eloom.holybean.printer

import eloom.holybean.printer.network.FakePrinterAddressStore
import eloom.holybean.printer.network.MdnsDiscovery
import eloom.holybean.printer.network.PrinterAddress
import eloom.holybean.printer.network.PrinterAddressResolver
import eloom.holybean.printer.network.PrinterStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@ExperimentalCoroutinesApi
class PrinterAddressResolverTest {

    /** 미리 정해진 결과를 반환하는 fake 탐색기. 호출 횟수도 센다. */
    private class FakeDiscovery(var result: PrinterAddress?) : MdnsDiscovery {
        var calls = 0
        override suspend fun discover(timeoutMs: Long): PrinterAddress? {
            calls++
            return result
        }
    }

    @Test
    fun `current seeds from lastGood at construction`() {
        val store = FakePrinterAddressStore(lastGood = "10.0.0.5:9100")
        val resolver = PrinterAddressResolver(store, FakeDiscovery(null))
        assertEquals(PrinterAddress("10.0.0.5", 9100), resolver.current())
    }

    @Test
    fun `current prefers override over lastGood`() {
        val store = FakePrinterAddressStore(override = "1.1.1.1", lastGood = "10.0.0.5:9100")
        val resolver = PrinterAddressResolver(store, FakeDiscovery(null))
        assertEquals(PrinterAddress("1.1.1.1", 9100), resolver.current())
    }

    @Test
    fun `current is null when nothing stored`() {
        val resolver = PrinterAddressResolver(FakePrinterAddressStore(), FakeDiscovery(null))
        assertNull(resolver.current())
    }

    @Test
    fun `rediscover with override short-circuits and does not call mdns`() = runTest {
        val store = FakePrinterAddressStore(override = "2.2.2.2:9999")
        val discovery = FakeDiscovery(PrinterAddress("9.9.9.9", 9100))
        val resolver = PrinterAddressResolver(store, discovery)
        val result = resolver.rediscover()
        assertEquals(PrinterAddress("2.2.2.2", 9999), result)
        assertEquals(0, discovery.calls)
    }

    @Test
    fun `rediscover persists found address to lastGood and caches it`() = runTest {
        val store = FakePrinterAddressStore()
        val discovery = FakeDiscovery(PrinterAddress("192.168.0.27", 9100))
        val resolver = PrinterAddressResolver(store, discovery)
        val result = resolver.rediscover()
        assertEquals(PrinterAddress("192.168.0.27", 9100), result)
        assertEquals("192.168.0.27:9100", store.lastGood)
        assertEquals(PrinterAddress("192.168.0.27", 9100), resolver.current())
        assertEquals(PrinterStatus.Connected(PrinterAddress("192.168.0.27", 9100)), resolver.status.value)
    }

    @Test
    fun `rediscover failure keeps stale cache`() = runTest {
        val store = FakePrinterAddressStore(lastGood = "10.0.0.5:9100")
        val resolver = PrinterAddressResolver(store, FakeDiscovery(null))
        val result = resolver.rediscover()
        assertEquals(PrinterAddress("10.0.0.5", 9100), result)
    }

    @Test
    fun `rediscover failure with no cache sets Unreachable`() = runTest {
        val resolver = PrinterAddressResolver(FakePrinterAddressStore(), FakeDiscovery(null))
        assertNull(resolver.rediscover())
        assertEquals(PrinterStatus.Unreachable, resolver.status.value)
    }

    @Test
    fun `setManualOverride updates cache and store`() = runTest {
        val store = FakePrinterAddressStore()
        val resolver = PrinterAddressResolver(store, FakeDiscovery(null))
        resolver.setManualOverride("3.3.3.3:8080")
        assertEquals("3.3.3.3:8080", store.override)
        assertEquals(PrinterAddress("3.3.3.3", 8080), resolver.current())
    }

    @Test
    fun `clearing override falls back to lastGood`() = runTest {
        val store = FakePrinterAddressStore(override = "3.3.3.3", lastGood = "10.0.0.5:9100")
        val resolver = PrinterAddressResolver(store, FakeDiscovery(null))
        resolver.setManualOverride(null)
        assertNull(store.override)
        assertEquals(PrinterAddress("10.0.0.5", 9100), resolver.current())
    }
}
