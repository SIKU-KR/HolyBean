package eloom.holybean.printer

import eloom.holybean.printer.network.PrinterAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PrinterAddressTest {

    @Test
    fun `parse host with explicit port`() {
        val addr = PrinterAddress.parse("192.168.0.27:9100")
        assertEquals(PrinterAddress("192.168.0.27", 9100), addr)
    }

    @Test
    fun `parse host without port defaults to 9100`() {
        val addr = PrinterAddress.parse("holybean.local")
        assertEquals(PrinterAddress("holybean.local", 9100), addr)
    }

    @Test
    fun `parse trims whitespace`() {
        assertEquals(PrinterAddress("10.0.0.5", 9100), PrinterAddress.parse("  10.0.0.5  "))
    }

    @Test
    fun `parse blank returns null`() {
        assertNull(PrinterAddress.parse("   "))
        assertNull(PrinterAddress.parse(null))
    }

    @Test
    fun `parse invalid port returns null`() {
        assertNull(PrinterAddress.parse("10.0.0.5:notaport"))
    }

    @Test
    fun `toAuthority round trips`() {
        assertEquals("192.168.0.27:9100", PrinterAddress("192.168.0.27", 9100).toAuthority())
    }
}
