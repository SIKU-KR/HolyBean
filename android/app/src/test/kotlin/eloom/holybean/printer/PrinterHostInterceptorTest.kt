package eloom.holybean.printer

import eloom.holybean.printer.network.FakePrinterAddressStore
import eloom.holybean.printer.network.MdnsDiscovery
import eloom.holybean.printer.network.PrinterAddress
import eloom.holybean.printer.network.PrinterAddressResolver
import eloom.holybean.printer.network.PrinterHostInterceptor
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

class PrinterHostInterceptorTest {

    private class FixedDiscovery(val addr: PrinterAddress?) : MdnsDiscovery {
        override suspend fun discover(timeoutMs: Long): PrinterAddress? = addr
    }

    private fun resolverWith(lastGood: String?): PrinterAddressResolver =
        PrinterAddressResolver(FakePrinterAddressStore(lastGood = lastGood), FixedDiscovery(null))

    private fun chainFor(request: Request, captured: (Request) -> Unit): Interceptor.Chain {
        val chain = mockk<Interceptor.Chain>()
        val slot = slot<Request>()
        every { chain.request() } returns request
        every { chain.proceed(capture(slot)) } answers {
            captured(slot.captured)
            Response.Builder()
                .request(slot.captured).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK").build()
        }
        return chain
    }

    @Test
    fun `rewrites request host to resolved address`() {
        val interceptor = PrinterHostInterceptor(resolverWith("192.168.0.27:9100"))
        val request = Request.Builder().url("http://holybean.invalid/print").build()
        var seen: Request? = null
        interceptor.intercept(chainFor(request) { seen = it })
        assertEquals("192.168.0.27", seen!!.url().host())
        assertEquals(9100, seen!!.url().port())
        assertEquals("http://192.168.0.27:9100/print", seen!!.url().toString())
    }

    @Test
    fun `throws IOException when no address resolved`() {
        val interceptor = PrinterHostInterceptor(resolverWith(null))
        val request = Request.Builder().url("http://holybean.invalid/print").build()
        val chain = mockk<Interceptor.Chain>()
        every { chain.request() } returns request
        assertThrows(IOException::class.java) { interceptor.intercept(chain) }
    }
}
