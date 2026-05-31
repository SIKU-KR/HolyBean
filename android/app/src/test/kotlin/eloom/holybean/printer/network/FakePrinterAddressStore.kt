package eloom.holybean.printer.network

class FakePrinterAddressStore(
    override var override: String? = null,
    override var lastGood: String? = null,
) : PrinterAddressStore
