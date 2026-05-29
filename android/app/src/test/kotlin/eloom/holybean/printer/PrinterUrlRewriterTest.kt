package eloom.holybean.printer

import eloom.holybean.printer.network.PrinterAddress
import eloom.holybean.printer.network.rewritePrinterUrl
import okhttp3.HttpUrl
import org.junit.Assert.assertEquals
import org.junit.Test

class PrinterUrlRewriterTest {

    @Test
    fun `rewrites host and port keeping path`() {
        val original = HttpUrl.parse("http://holybean.invalid/print")!!
        val out = rewritePrinterUrl(original, PrinterAddress("192.168.0.27", 9100))
        assertEquals("192.168.0.27", out.host())
        assertEquals(9100, out.port())
        assertEquals("http://192.168.0.27:9100/print", out.toString())
    }

    @Test
    fun `rewrites to custom port`() {
        val original = HttpUrl.parse("http://holybean.invalid/health")!!
        val out = rewritePrinterUrl(original, PrinterAddress("10.0.0.5", 8080))
        assertEquals("10.0.0.5", out.host())
        assertEquals(8080, out.port())
        assertEquals("http://10.0.0.5:8080/health", out.toString())
    }
}
