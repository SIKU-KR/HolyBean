# 코루틴 컨벤션 (Coroutine Conventions)

이 문서는 `app` 모듈의 코틀린 코루틴 사용 표준이다. 새 코드 작성·리뷰 시 기준점으로 삼는다. 모든 화면의 비동기 동작 기대값(에러·취소·타임아웃 처리)이 동일하도록 만드는 것이 목적이다.

배경 설계: `docs/superpowers/specs/2026-05-25-coroutine-standardization-design.md`

---

## R1. CancellationException은 절대 삼키지 않는다

잡았다면 즉시 재전파한다. 화면이 닫혀 코루틴이 정상 취소될 때 에러로 오인하지 않기 위함이다. `runCatching` 을 ViewModel에서 직접 쓰지 않는 이유이기도 하다(`runCatching` 은 `CancellationException` 까지 잡는다).

`TimeoutCancellationException` 도 `CancellationException` 의 하위 타입이므로 재전파된다. 타임아웃을 "에러"로 다뤄야 하는 곳은 호출 대상이 도메인 예외로 변환해 던진다(R3, `DataException.Timeout` 참고).

## R2. suspend 함수는 main-safe하다

호출부가 어떤 디스패처에 있든 안전해야 하며, 디스패처 전환 책임은 **호출부가 아니라 함수 내부**에 있다. Repository 의 모든 suspend 함수는 본문을 `withContext(ioDispatcher)` 로 감싼다.

```kotlin
// data/repository/FirestoreRepository.kt
suspend fun getOrderNumber(): Int = withContext(ioDispatcher) {
    val snap = db.collection(FirestoreSchema.DAY_SUMMARIES).document(today()).get().await()
    (snap.getLong("lastOrderNum") ?: 0L).toInt() + 1
}
```

따라서 ViewModel은 `viewModelScope.launchSafely { }`(= `Dispatchers.Main`)에서 repo를 호출하면 되고, **디스패처를 주입받거나 `launch`에 넘기지 않는다.**

## R3. 에러는 한 레이어에서만 처리한다 (log once, at the boundary)

- Repository는 실패 시 **예외를 던진다.** sentinel 값(`-1`, `emptyList()`, `false`)을 반환하지 않는다. Repository 안에서 `recordException`/`printStackTrace` 를 하지 않는다.
- ViewModel이 단일 지점(`launchSafely`)에서 잡아 UI 상태/이벤트로 변환한다. Crashlytics 기록도 이 지점에서 **한 번만** 일어난다.

`exception/DataException.kt` — Repository가 타입으로 실패를 표현할 때 쓰는 경량 sealed 예외. 과도한 분류는 하지 않는다(현재 `Timeout`, `Unknown`만).

```kotlin
// data/repository/FirestoreRepository.kt — postOrder
try {
    withTimeout(POST_ORDER_ACK_TIMEOUT_MS) { batch.commit().await() }
} catch (e: TimeoutCancellationException) {
    throw DataException.Timeout(e)   // 정상 취소가 아니라 저장 실패로 다룬다(R1 충돌 회피)
}
```

### 예외(R3): best-effort probe는 예외 대신 상태값을 반환한다 (R6)

연결/프린터 점검처럼 "실패가 곧 결과"인 probe는 throw하지 않고 Boolean을 반환한다. 의도를 주석으로 명시한다.

```kotlin
/** 개발자도구용 best-effort 연결 점검. 실패도 결과이므로 예외를 던지지 않고 false 반환. */
suspend fun checkConnection(): Boolean = withContext(ioDispatcher) {
    try { withTimeout(3000) { ... }; true } catch (e: Exception) { false }
}
```

## R4. ViewModel의 모든 비동기 작업은 `launchSafely`를 통한다

`util/CoroutineExtensions.kt` 의 `launchSafely` 가 단일 진입점이다. 취소 안전(R1) + Crashlytics 1회 기록(R3) + `onError` 위임을 한 곳에 강제한다.

```kotlin
fun CoroutineScope.launchSafely(
    context: CoroutineContext = EmptyCoroutineContext,
    onError: (Throwable) -> Unit,
    block: suspend CoroutineScope.() -> Unit,
): Job
```

기본 사용:

```kotlin
// ui/credits/CreditsViewModel.kt
fun loadCredits() {
    viewModelScope.launchSafely(onError = { e ->
        _uiState.update { it.copy(isLoading = false) }
        _uiEvent.tryEmit(CreditsUiEvent.ShowToast("외상 목록을 불러오는 중 오류가 발생했습니다: ${e.message}"))
    }) {
        _uiState.update { it.copy(isLoading = true) }
        val creditsList = firestoreRepository.getCreditsList()
        _uiState.update { it.copy(creditsList = creditsList, isLoading = false) }
    }
}
```

화면별 차등 처리가 필요하면 `onError` 에서 예외 타입으로 분기한다. `onError` 안에서 `recordException` 을 다시 부르지 않는다(launchSafely가 이미 기록).

```kotlin
// ui/home/HomeViewModel.kt — runSubmission
viewModelScope.launchSafely(onError = { e ->
    when (e) {
        is PrintServerException -> {
            _uiState.update { it.copy(submitError = SubmitError.PrintFailed(e.reason, ++submitSeq)) }
            FirebaseCrashlytics.getInstance().apply {
                setCustomKey("orderNum", data.orderNum)
                setCustomKey("print_reason", e.reason.name)
            }
        }
        else -> _uiState.update { it.copy(submitError = SubmitError.SaveFailed(++submitSeq)) } // DataException.Timeout 포함
    }
}) {
    try {
        coroutineScope {
            val printDeferred = async { if (!printDone) { printReceipt(data, takeOption); printDone = true } }
            if (!orderSaved) { firestoreRepository.postOrder(data); orderSaved = true }
            printDeferred.await()
        }
        completeAndNavigate()
    } finally {
        _uiState.update { it.copy(isSubmitting = false) }   // 성공/실패/취소 공통 정리는 block 내부 finally로
    }
}
```

- 즉시 반환되는 가드(입력 검증 등)는 `launch` 없이 직접 `tryEmit(...) ; return` 한다.
- 로딩 플래그를 성공·실패 모두에서 정리해야 하면 block 내부 `try { ... } finally { ... }` 를 쓴다(위 예시).

## R5. 일회성 이벤트(SharedFlow)는 tryEmit으로 발행한다

`_uiEvent` 는 `replay = 0, extraBufferCapacity = 16, onBufferOverflow = DROP_OLDEST` 로 구성한다. 발행은 항상 `tryEmit`(논-블로킹) 을 쓴다. `emit`(suspend) 은 쓰지 않는다.

## R6. best-effort 헬스 체크는 상태값 반환

R3의 명시적 예외. 위 `checkConnection` 참조. `PiPrintClient.checkHealth()` 도 throw하지 않고 Boolean을 반환한다.

## R7. DI 디스패처/스코프는 타입 안전 한정자를 쓴다

`di/Qualifiers.kt` 의 `@IoDispatcher`, `@PrinterDispatcher`, `@AppScope` 를 쓴다. stringly-typed `@Named("IO")` 등은 쓰지 않는다(오타에 취약). (단, 네트워크 모듈의 `@Named("PrintServer")` 처럼 코루틴과 무관한 기존 한정자는 별개다.)

```kotlin
// di/CoroutineModule.kt
@Provides @Singleton @IoDispatcher
fun provideIODispatcher(): CoroutineDispatcher = Dispatchers.IO
```

### applicationScope 수명

`@AppScope` 스코프(`SupervisorJob() + printerDispatcher`)는 ViewModel 생명주기와 분리된 작업용이다. 대표적으로 프린터 작업은 사용자가 화면을 떠나도 인쇄가 완료돼야 하므로 이 스코프에서 돈다. 이 작업들도 `applicationScope.launchSafely(...)` 로 실행해 취소 안전·로깅 일관성을 지킨다. UI가 살아있지 않을 수 있으므로 `onError` 는 로깅 위주이며 이벤트 발행은 best-effort다.

```kotlin
// ui/orderlist/OrdersViewModel.kt — reprint
applicationScope.launchSafely(onError = { e ->
    _uiEvent.tryEmit(OrdersUiEvent.ShowToast("Printer error: ${e.message}"))
}) {
    piPrintClient.print(commands)
}
```

---

## 테스트 표준

- **`util/MainDispatcherRule`** 를 쓴다. `viewModelScope` 가 `Dispatchers.Main` 을 쓰므로(R2로 ViewModel에서 디스패처 주입 제거), 단위 테스트는 이 룰로 Main을 테스트 디스패처로 교체한다.
  ```kotlin
  @get:Rule val mainDispatcherRule = MainDispatcherRule()
  ```
- `launchSafely` 가 에러 경로에서 `FirebaseCrashlytics.getInstance()` 를 호출하므로, ViewModel 테스트 `setUp` 에서 정적 모킹한다.
  ```kotlin
  mockkStatic(FirebaseCrashlytics::class)
  every { FirebaseCrashlytics.getInstance() } returns mockk(relaxed = true)
  ```
- **이벤트 수집기는 viewModelScope/Main과 같은 디스패처에 고정**한다(`launch(mainDispatcherRule.dispatcher) { uiEvent.collect { ... } }`). 다른 스케줄러에서 수집하면 `advanceUntilIdle()` 후에도 이벤트가 관측되지 않는다.
- 각 ViewModel은 최소: (1) 성공 경로, (2) Repository가 throw할 때 표준 에러 이벤트/상태가 나오는지, (3) 취소 시 에러 이벤트가 나오지 않는지를 검증한다.
- Repository 테스트는 실패 시 sentinel이 아니라 예외가 던져지는지 검증한다(best-effort probe는 false 반환 검증).

검증: `cd android && ./gradlew testDebugUnitTest`
