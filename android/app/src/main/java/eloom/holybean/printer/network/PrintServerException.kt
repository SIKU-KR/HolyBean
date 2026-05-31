package eloom.holybean.printer.network

/** 인쇄 실패의 최종 원인. Pi 재시도 소진 후 HTTP 상태/예외로 판정된다. */
enum class PrintFailureReason {
    ServerUnreachable, // 연결 거부/타임아웃 — Pi 박스 다운/네트워크
    PrinterOffline,    // HTTP 503 — /dev/usb/lp0 미존재 (전원·USB)
    PrinterError,      // HTTP 500 — 쓰기/flush 실패 (용지·덮개)
    Unknown,           // 그 외
}

class PrintServerException(
    val reason: PrintFailureReason,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
