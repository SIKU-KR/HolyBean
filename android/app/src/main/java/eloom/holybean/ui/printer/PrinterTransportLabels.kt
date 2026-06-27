package eloom.holybean.ui.printer

import eloom.holybean.printer.transport.FastFailReason
import eloom.holybean.printer.transport.PrintMethod
import eloom.holybean.printer.transport.TransportSelection

fun TransportSelection.toDisplayText(): String = when (method) {
    PrintMethod.USB_DIRECT -> "USB 직연결"
    PrintMethod.PI_HTTP -> "Pi 서버" + (fallbackReason?.toDisplayText() ?: "")
}

private fun FastFailReason.toDisplayText(): String = when (this) {
    FastFailReason.DISABLED -> " (USB 비활성)"
    FastFailReason.NO_DEVICE -> " (USB 장치 없음)"
    FastFailReason.NO_PERMISSION -> " (USB 권한 없음)"
    FastFailReason.NO_PRINTER_INTERFACE -> " (USB 인터페이스 없음)"
    FastFailReason.CLAIM_FAILED -> " (USB 연결 실패)"
}
