package eloom.holybean.printer.transport

import eloom.holybean.printer.network.PrintCommandDto

interface PrintTransport {
    suspend fun print(commands: List<PrintCommandDto>)
    suspend fun checkHealth(): Boolean

    // requestPermission=false면 권한 다이얼로그를 띄우지 않는다 (출력 경로 재탐색용)
    suspend fun probeFastFail(requestPermission: Boolean = true): FastFailReason? = null
}
