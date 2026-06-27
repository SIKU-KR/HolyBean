package eloom.holybean.printer.transport

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrinterTransportStoreTest {

    @Test
    fun `fake store reflects forcePi value`() {
        val store: PrinterTransportStore = FakePrinterTransportStore(forcePi = true)
        assertTrue(store.forcePi)

        store.forcePi = false
        assertFalse(store.forcePi)
    }
}
