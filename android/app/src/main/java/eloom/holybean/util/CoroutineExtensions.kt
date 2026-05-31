package eloom.holybean.util

import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.cancellation.CancellationException

/**
 * ViewModel/ApplicationScope 비동기 작업의 표준 진입점.
 * - CancellationException 은 절대 삼키지 않고 재전파한다(정상 취소 보존). [R1]
 *   (TimeoutCancellationException 도 CancellationException 하위 타입이므로 재전파된다.
 *    타임아웃을 에러로 다뤄야 하는 곳은 호출 대상이 DataException.Timeout 으로 변환해 던진다.)
 * - 그 외 Throwable 은 Crashlytics 에 한 번 기록하고 onError 로 위임한다. [R3]
 */
fun CoroutineScope.launchSafely(
    context: CoroutineContext = EmptyCoroutineContext,
    onError: (Throwable) -> Unit,
    block: suspend CoroutineScope.() -> Unit,
): Job = launch(context) {
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        FirebaseCrashlytics.getInstance().recordException(e)
        onError(e)
    }
}
