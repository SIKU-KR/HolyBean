package eloom.holybean.printer.transport

enum class FastFailReason {
    DISABLED,
    NO_DEVICE,
    NO_PERMISSION,
    NO_PRINTER_INTERFACE,
    CLAIM_FAILED,
}

class UsbFastFailException(
    val reason: FastFailReason,
    message: String,
) : Exception(message)
