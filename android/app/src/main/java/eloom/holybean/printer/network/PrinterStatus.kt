package eloom.holybean.printer.network

/** 프린터 연결 상태(설정 화면 표시용). */
sealed class PrinterStatus {
    data object Unknown : PrinterStatus()       // 아직 해석된 주소 없음
    data object Resolving : PrinterStatus()      // mDNS 탐색 중
    data class Connected(val address: PrinterAddress) : PrinterStatus()
    data object Unreachable : PrinterStatus()    // 탐색 실패 + 캐시 없음
}
