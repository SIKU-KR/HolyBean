package eloom.holybean.printer.transport

enum class FastFailReason {
    NO_DEVICE,
    NO_PERMISSION,
    NO_PRINTER_INTERFACE,
    CLAIM_FAILED,
}

class UsbFastFailException(
    val reason: FastFailReason,
    message: String,
) : Exception(message)

// 일부 바이트가 이미 프린터로 전송된 뒤의 실패.
// 재시도하면 영수증이 중복 출력되므로 즉시 실패로 처리해야 한다.
class UsbPartialPrintException(
    val bytesSent: Int,
    val totalBytes: Int,
) : Exception("USB print failed after $bytesSent/$totalBytes bytes")
