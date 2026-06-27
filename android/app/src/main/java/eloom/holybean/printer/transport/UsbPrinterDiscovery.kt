package eloom.holybean.printer.transport

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager

private const val PRINTER_INTERFACE_CLASS = UsbConstants.USB_CLASS_PRINTER
private const val SEWOO_VENDOR_ID = 1317
private const val SEWOO_SLK_TS400B_PRODUCT_ID = 42752

fun UsbManager.findPrinterDevice(): UsbDevice? {
    val devices = deviceList.values
    return devices.firstOrNull { it.isKnownPrinter() && it.findPrinterInterface() != null }
        ?: devices.firstOrNull { it.findPrinterInterface() != null }
}

fun UsbDevice.hasPrinterInterface(): Boolean {
    return findPrinterInterface() != null
}

fun UsbDevice.findPrinterInterface(): UsbInterface? {
    val interfaces = (0 until interfaceCount).map { getInterface(it) }
    return interfaces.firstOrNull {
        it.isPrinterInterface() && it.findBulkOutEndpoint() != null
    } ?: if (isKnownPrinter() || deviceClass == PRINTER_INTERFACE_CLASS) {
        interfaces.firstOrNull { it.findBulkOutEndpoint() != null }
    } else {
        null
    }
}

fun UsbInterface.isPrinterInterface(): Boolean {
    return interfaceClass == PRINTER_INTERFACE_CLASS
}

fun UsbInterface.findBulkOutEndpoint(): UsbEndpoint? {
    return (0 until endpointCount).firstNotNullOfOrNull { index ->
        val endpoint = getEndpoint(index)
        endpoint.takeIf {
            it.type == UsbConstants.USB_ENDPOINT_XFER_BULK &&
                it.direction == UsbConstants.USB_DIR_OUT
        }
    }
}

private fun UsbDevice.isKnownPrinter(): Boolean {
    return vendorId == SEWOO_VENDOR_ID && productId == SEWOO_SLK_TS400B_PRODUCT_ID
}
