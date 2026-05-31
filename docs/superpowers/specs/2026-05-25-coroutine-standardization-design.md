# 코루틴 표준화 (Coroutine Standardization) — 설계 문서

- 날짜: 2026-05-25
- 브랜치: v3
- 범위: `android/app` 전체 (ViewModel 7개, Repository 2개, DI, Printer, 테스트)
- 목표: 안드로이드 프로젝트가 코틀린 코루틴 Android 표준을 전 영역에서 일관되게 따르도록 한다. 코드 품질 향상과 더불어 **모든 화면의 비동기 동작 기대값(에러/취소/타임아웃 처리)이 동일**하도록 만든다.

---

## 1. 배경과 문제 정의

현 코드베이스는 코루틴 도입이 아키텍처적으로 이미 양호하다 (GlobalScope 없음, runBlocking 없음, Dispatcher DI 주입, StateFlow/SharedFlow 패턴 통일, Firestore의 `await()`/`callbackFlow` 변환, `runTest`/`TestDispatcher` 기반 테스트). 그러나 다음 **일관성 균열**로 인해 화면마다 동작 기대값이 달라진다.

1. **에러 책임의 이중화** — `FirestoreRepository`가 예외를 삼키고 sentinel 값(`-1`, `emptyList()`, `false`, `SalesReport(emptyList(), mapOf("총합" to 0))`)을 반환하는 곳이 있고, 동시에 ViewModel도 try-catch로 잡는다. 실패가 "조용히 빈 화면"으로 보이거나 "토스트"로 보이는 등 화면마다 다르다.
2. **ViewModel 에러 관용구 혼용** — `try-catch`(ReportViewModel, CreditsViewModel 등)와 `runCatching`(OrdersViewModel, StartupViewModel, DevToolsViewModel)이 섞여 있다. `runCatching`은 `CancellationException`까지 삼켜, 화면 정상 종료 시에도 에러로 처리될 위험이 있다(현재 HomeViewModel만 `catch (e: CancellationException) { throw e }`로 올바르게 처리).
3. **이벤트 발행 혼용** — `_uiEvent.emit()`(HomeViewModel, suspend·블로킹)과 `_uiEvent.tryEmit()`(나머지)이 섞여 있다.
4. **Dispatcher 책임 위치** — main-safety가 호출부 `launch(ioDispatcher)`에 의존한다. suspend 함수 자체는 호출 스레드를 가정하므로, 다른 컨텍스트에서 호출하면 깨질 수 있다.
5. **Crashlytics 이중 로깅** — Repository에서 `recordException`, ViewModel에서도 catch 후 다시 기록하는 구조라 동일 예외가 두 번 기록될 수 있다.
6. **applicationScope 수명** — 프린터 작업이 화면 이탈 후에도 살아있는 동작이 의도된 설계지만 문서화/일관 처리가 없다.
7. **stringly-typed DI 한정자** — `@Named("IO")` / `@Named("Printer")`는 오타에 취약하다.

---

## 2. 설계 원칙 (Android 표준 + 본 프로젝트 의견 레이어)

이 프로젝트가 따를 코루틴 규칙. 괄호 안은 "Android 공식 권장(std)"인지 "본 프로젝트가 얹은 의견(opinion)"인지 표시.

- **R1. CancellationException은 절대 삼키지 않는다.** 잡았다면 즉시 재전파한다. (std)
- **R2. suspend 함수는 main-safe하다.** 호출부가 어떤 디스패처에 있든 안전해야 하며, 디스패처 전환 책임은 호출부가 아니라 함수 내부(`withContext`)에 있다. (std)
- **R3. 에러는 한 레이어에서만 처리한다.** Repository는 예외를 던지고(throw), ViewModel이 단일 지점에서 잡아 UI 상태/이벤트로 변환한다. Crashlytics 로깅도 그 단일 지점에서 한 번만 한다. (opinion: "log once, at the boundary")
- **R4. ViewModel의 모든 비동기 작업은 `launchSafely`를 통한다.** 취소 안전 + 공통 로깅 + 에러 콜백이 한 곳에 강제된다. (opinion)
- **R5. 일회성 이벤트(`SharedFlow`)는 `tryEmit`으로 발행한다.** 버퍼(`extraBufferCapacity=16`, `DROP_OLDEST`)가 있어 논-블로킹으로 충분하다. (opinion)
- **R6. best-effort 헬스 체크는 예외 대신 상태값(Boolean)을 반환한다.** R3의 예외: 연결/프린터 점검처럼 "실패가 곧 결과"인 probe는 throw하지 않는다. 코드에 의도를 주석으로 명시한다. (opinion)
- **R7. DI 디스패처/스코프는 타입 안전 한정자 애노테이션을 쓴다.** `@IoDispatcher`, `@PrinterDispatcher`, `@ApplicationScope`. (std)

---

## 3. 컴포넌트 설계

### 3.1 코루틴 확장 유틸 — `util/CoroutineExtensions.kt` (신규)

핵심 메커니즘. 모든 ViewModel 비동기 작업의 단일 진입점.

```kotlin
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
 * - CancellationException은 절대 삼키지 않고 재전파한다(정상 취소 보존). [R1]
 * - 그 외 Throwable은 Crashlytics에 한 번 기록하고 onError로 위임한다. [R3]
 * - onError에서 예외 타입별 분기(when)로 화면별 차등 처리 가능(예: 결제 제출).
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
```

- 인터페이스: `scope.launchSafely(onError = { ... }) { ... }`. `context`로 디스패처 오버라이드 가능하나, R2 적용 후에는 대부분 불필요(suspend 함수가 스스로 IO 전환).
- 의존: `FirebaseCrashlytics`, `kotlinx.coroutines`. 테스트는 Crashlytics 호출을 검증하지 않고 onError 호출과 상태 전이만 검증한다(Crashlytics는 `FirebaseCrashlytics.getInstance()` 정적 호출이라 단위 테스트에서 부작용 없이 통과하도록 try 안에서만 호출).
- **차등 처리 패턴(HomeViewModel 제출 흐름):**
  ```kotlin
  viewModelScope.launchSafely(onError = { e ->
      val err = when (e) {
          is PrintServerException -> SubmitError.PrintFailed(e.reason, ++submitSeq)
          else                    -> SubmitError.SaveFailed(++submitSeq) // DataException.Timeout 포함
      }
      _uiState.update { it.copy(submitError = err) }
  }) {
      coroutineScope {
          val printDeferred = async { if (!printDone) { printReceipt(...); printDone = true } }
          if (!orderSaved) { firestoreRepository.postOrder(data); orderSaved = true }
          printDeferred.await()
      }
      completeAndNavigate()
  }
  ```
  주의: `TimeoutCancellationException`은 `CancellationException`의 하위 타입이므로 `launchSafely`의 `catch (CancellationException)`가 **먼저 잡아 재전파**한다. 따라서 타임아웃을 에러로 다뤄야 하는 `postOrder`는 함수 내부에서 `withTimeout` 결과를 잡아 **도메인 예외로 재던진다**(3.2 참조) — `launchSafely`까지 `CancellationException`으로 전파되지 않게 한다.
  - `isSubmitting`/`isLoading` 같은 finally 정리는 `launchSafely` 밖에서 표현할 수 없으므로, 해당 흐름은 block 내부 `try { ... } finally { ... }`로 상태 정리를 유지하거나 onError + 성공 경로 양쪽에서 정리한다. 구현 시 ViewModel별로 가장 단순한 쪽을 택한다.

### 3.2 Repository — 예외 전파로 통일

`FirestoreRepository`의 데이터 조회/변경 메서드에서 try-catch + sentinel 반환 + Crashlytics 기록을 **제거**하고, 예외가 호출부로 전파되게 한다. 동시에 R2(main-safety)를 위해 본문을 `withContext(ioDispatcher)`로 감싼다.

대상 메서드와 변경:

| 메서드 | 현재 실패 시 | 변경 후 |
| --- | --- | --- |
| `getOrderNumber()` | `-1` 반환 | 예외 throw, `withContext(io)` |
| `getOrdersOfDay()` | `arrayListOf()` | 예외 throw, `withContext(io)` |
| `getOrderDetail()` | `arrayListOf()` | 예외 throw, `withContext(io)` |
| `getCreditsList()` | `arrayListOf()` | 예외 throw, `withContext(io)` |
| `getReport()` | `SalesReport(empty, 총합 0)` | 예외 throw, `withContext(io)` |
| `setCreditOrderPaid()` | swallow(Unit) | 예외 throw, `withContext(io)` |
| `deleteOrder()` | `false` | **성공 의미의 Boolean은 유지**(주문 없음=false), 실패는 throw, `withContext(io)` |
| `postOrder()` | 이미 throw (모범) | 유지. `withTimeout` 후 `TimeoutCancellationException`을 잡아 도메인 예외(`DataException.Timeout`)로 재던져 R1 충돌 회피 |
| `checkConnection()` | `false` (best-effort) | **유지**(R6). 주석으로 "probe라 throw 안 함" 명시 |

- `MenuRepository`의 suspend 함수(`getMenuListSync`, `writeAll`, `overwriteMenuList`, `saveMenuOrders`, `updateSpecificMenu`, `addMenu`, `isValidMenuName` 등)도 동일 원칙: 예외 전파 + `withContext(io)`. `getMenuList()`의 `callbackFlow`는 이미 `close(err)`로 에러 전파하므로 유지.
- **도메인 예외 레이어(opinion, 경량):** 신규 `exception/DataException.kt`에 `sealed class DataException` 정의 — 최소한 `Timeout`(ack 타임아웃)과 `Unknown(cause)`. Repository는 raw Firestore 예외를 `DataException`으로 감싸 던진다(스택은 `cause`로 보존). 이유: ViewModel·Crashlytics가 메시지 문자열이 아니라 타입으로 분기하게. 과도한 분류는 하지 않는다(YAGNI). 기존 `ApiException`, `PrintServerException`은 그대로 둔다.
- Repository에서 `e.printStackTrace()`와 `FirebaseCrashlytics.getInstance().recordException(e)` 호출을 제거한다(R3: 로깅은 `launchSafely` 단일 지점).

### 3.3 ViewModel 일괄 정렬 (7개)

각 ViewModel에서:
- `viewModelScope.launch(ioDispatcher) { try/catch | runCatching ... }` → `viewModelScope.launchSafely(onError = { ... }) { ... }`로 치환. R2 적용 후 `ioDispatcher`를 launch에 넘기지 않는다(suspend 함수가 스스로 IO 전환). `onError`는 기존 catch가 만들던 UI 상태/이벤트를 그대로 매핑.
- `_uiEvent.emit(...)` → `_uiEvent.tryEmit(...)` (R5). HomeViewModel 3곳(약 166, 189, 226줄).
- Repository sentinel 분기(`if (result == -1)`, `if (list.isEmpty())`를 "에러"로 해석하던 코드)가 있으면, 이제 예외 경로로 옮겨 `onError`에서 처리. "비었음"과 "실패"를 구분.
- `getNextAvailableId()` 등 ViewModel의 suspend 메서드는 그대로 두되 호출부가 `launchSafely` 안인지 확인.

대상: `HomeViewModel`, `OrdersViewModel`, `ReportViewModel`, `MenuManagementViewModel`, `CreditsViewModel`, `StartupViewModel`, `DevToolsViewModel`.

### 3.4 DI — 타입 안전 한정자 (R7)

신규 `di/Qualifiers.kt`:

```kotlin
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class IoDispatcher
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class PrinterDispatcher
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class ApplicationScope
```

`CoroutineModule`의 `@Named("IO"/"Printer"/"ApplicationScope")`를 위 애노테이션으로 교체하고, 모든 주입 지점(생성자 `@Named(...)` 파라미터)을 함께 교체한다. 동작은 동일, 컴파일 타임 안전성만 향상.

### 3.5 applicationScope — 수명·에러 처리 명시화 (R6 보강)

- `reprint()`, `printTodayReport()`, `printReport()`가 `applicationScope.launch { ... }`로 화면 이탈 후에도 인쇄를 마치는 동작을 **주석으로 문서화**한다("ViewModel 생명주기와 분리—사용자가 화면을 떠나도 인쇄 완료").
- 이 작업들도 `applicationScope.launchSafely(onError = { ... })`로 통일해 취소 안전 + Crashlytics 로깅 일관 적용. UI가 살아있지 않을 수 있으므로 `onError`는 토스트 대신 로깅 위주(이벤트 발행은 best-effort `tryEmit`).

### 3.6 컨벤션 문서 — `android/docs/coroutine-conventions.md` (신규, 레퍼런스)

§2의 규칙(R1–R7)을 근거·예시와 함께 정리한 영구 기준점. 신규 코드 리뷰 시 참조. 포함: launchSafely 사용법, Repository throw 규칙, best-effort probe 예외, tryEmit 규칙, DI 한정자, 테스트 표준(§4).

---

## 4. 테스트 전략 (TDD)

이 작업은 동작 변경(Repository throw)을 포함하므로, ViewModel별 기대 동작을 테스트로 먼저 고정한 뒤 리팩토링한다. 기존 `runTest`/`UnconfinedTestDispatcher`/`advanceUntilIdle` 인프라를 재사용.

- **공통 테스트 룰:** `MainDispatcherRule`(JUnit `TestWatcher`로 `Dispatchers.setMain/resetMain`) 신규 추가. `viewModelScope`가 `Dispatchers.Main`을 쓰므로, `launch(ioDispatcher)` 제거(R2) 후에도 단위 테스트가 안정적이게 한다.
- **`launchSafely` 단위 테스트:** (a) 정상 블록 실행, (b) 일반 예외 → onError 호출, (c) `CancellationException` → onError 미호출 + 재전파, (d) `TimeoutCancellationException` → 재전파(onError 미호출).
- **각 ViewModel 테스트(7개)에 동작 동일성 케이스 추가/정렬:**
  - 성공 경로: 기존 유지.
  - 실패 경로: Repository가 예외를 던지도록 목(mock)하고, ViewModel이 **표준 에러 이벤트/상태**를 내는지 검증("빈 리스트로 조용히 통과"가 아님).
  - 취소: 스코프 취소 시 에러 이벤트가 **발행되지 않음** 검증.
- **Repository 테스트:** 실패 시 sentinel이 아니라 예외가 던져지는지(또는 `DataException`으로 감싸지는지) 검증. `deleteOrder`의 "주문 없음→false"와 "실패→throw" 구분 검증. `checkConnection`은 실패 시에도 false 반환(throw 안 함) 검증.

검증 명령: `cd android && ./gradlew testDebugUnitTest` (전체 통과가 완료 기준).

---

## 5. 작업 순서 (구현 플랜에서 단계화)

1. **기반:** `Qualifiers.kt`, `DataException.kt`, `CoroutineExtensions.launchSafely`, `MainDispatcherRule` 추가 + `launchSafely` 단위 테스트(TDD).
2. **DI 교체:** `@Named` → 타입 한정자, 전 주입 지점 갱신, 빌드 통과.
3. **Repository:** `FirestoreRepository`·`MenuRepository`를 throw + `withContext(io)` + Crashlytics 제거로 전환, Repository 테스트.
4. **ViewModel 정렬:** 화면 단위로 끊어 (a) 테스트로 기대 동작 고정 → (b) `launchSafely`/`tryEmit`/sentinel 분기 제거 적용 → (c) 테스트 통과. HomeViewModel(제출 차등 처리)을 마지막에.
5. **applicationScope:** 프린터 작업 `launchSafely` 전환 + 주석 문서화.
6. **컨벤션 문서** 작성.
7. 전체 `testDebugUnitTest` 통과 확인 후 마무리.

---

## 6. 범위 밖 (Non-goals)

- LiveData 도입/제거 (이미 미사용).
- 새 기능 추가, UI 레이아웃 변경.
- Compose 화면(Screen) 컴포저블의 코루틴(LaunchedEffect 등) 재설계 — ViewModel 이벤트 수집 패턴은 현행 유지.
- 무관한 리팩토링(네이밍 대청소, 모듈 분리 등).
- Repository 반환 타입을 전면 `Result<T>`/sealed로 바꾸는 것 — 본 스펙은 "throw + ViewModel 단일 처리"를 택했고, 도메인 예외는 경량 `DataException`까지만.

---

## 7. 성공 기준

- 모든 ViewModel의 비동기 작업이 `launchSafely`를 거치고, `CancellationException`을 삼키는 코드 경로가 없다.
- Repository 데이터 메서드는 sentinel 대신 예외를 던진다(best-effort probe 제외).
- `_uiEvent` 발행이 전부 `tryEmit`이다.
- DI 디스패처/스코프 주입이 전부 타입 한정자다.
- Crashlytics 기록이 ViewModel/applicationScope 경계 한 곳에서만 일어난다.
- `android/docs/coroutine-conventions.md`가 존재하고 R1–R7을 담는다.
- `./gradlew testDebugUnitTest` 전체 통과.
