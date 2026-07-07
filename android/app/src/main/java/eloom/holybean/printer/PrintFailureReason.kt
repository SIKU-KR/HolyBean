package eloom.holybean.printer

/** 인쇄 실패의 최종 원인. 실패 UX 메시지 분기에 사용된다. */
enum class PrintFailureReason {
    PrinterOffline, // 프린터 미연결/응답 없음 (전원·USB)
    PrinterError,   // 쓰기/전송 실패 (용지·덮개, 부분 출력)
    Unknown,        // 그 외
}
