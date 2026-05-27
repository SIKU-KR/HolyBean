package eloom.holybean.printer.network

import android.util.Log
import retrofit2.Response

/**
 * debug 빌드 전용. 실제 Pi 프린트 서버를 호출하지 않고 항상 성공을 반환한다.
 * 출력될 영수증 명령은 Logcat에 찍어 디버깅을 돕는다.
 */
class FakePrintServerApi : PrintServerApi {

    override suspend fun print(body: PrintRequestDto): Response<Unit> {
        Log.d(TAG, "no-op print: ${body.commands.size}개 명령")
        body.commands.forEach { Log.d(TAG, "  $it") }
        return Response.success(Unit)
    }

    override suspend fun health(): Response<Unit> = Response.success(Unit)

    private companion object {
        const val TAG = "FakePrintServerApi"
    }
}
