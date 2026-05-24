# 결제 완료: 저장·인쇄 완료 후 홈 전환 (Await-All)

## 배경

현재 `결제 완료`를 누르면 화면이 곧바로 홈으로 돌아가고, 영수증 인쇄와 서버 동기화는 백그라운드에서 별도로 진행된다.

- `HomeViewModel.onOrderConfirmed()` (HomeViewModel.kt:177)
  - `firestoreRepository.postOrder(data)` — `batch.commit()`을 await하지 않음 (로컬 즉시 반영, FirestoreRepository.kt:152)
  - `getOrderNumber()` 후 `NavigateHome` emit → 홈으로 `popBackStack`
  - `launchPrint()` 는 `applicationScope`에서 독립 실행 → 홈 복귀를 막지 않음 (HomeViewModel.kt:218)
- 인쇄 실패는 홈 화면 스낵바 + `재출력`(`reprintLastOrder`)로 처리 (HomeScreen.kt:88-100)

## 목표

`결제 완료` 시 **주문 저장(서버 ack)과 영수증 인쇄가 모두 성공**한 뒤에만 홈으로 전환한다. 하나라도 실패하면 결제 화면에 머무르며 재시도할 수 있게 한다. 코루틴으로 저장·인쇄를 병렬 실행해 체감 속도를 유지한다.

### 확정된 동작 결정

1. **인쇄 실패 시**: 홈으로 이동하지 않고 결제 화면에 머무르며 재시도 노출.
2. **주문 저장**: 서버 commit ack까지 대기(`batch.commit().await()`). 오프라인 무한 대기를 막기 위해 타임아웃 안전장치를 둔다.
3. **홈의 기존 인쇄실패 UI 제거**: 실패 처리가 결제 화면으로 옮겨가므로 홈 스낵바/`reprintLastOrder`/`dismissPrintFailure`/`PrintFailure` 상태 등 더 이상 쓰이지 않는 코드를 정리한다.

## 설계

### 1. 저장·인쇄 병렬 실행 (속도)

`viewModelScope.launch(ioDispatcher)` 안에서 인쇄와 저장을 동시에 띄우고 둘 다 기다린다. `runCatching`으로 각 결과를 수집해, 인쇄를 중간에 취소하지 않고 **저장 실패 → 인쇄 실패 순으로 결정적으로** 보고한다.

```kotlin
coroutineScope {
    val printResult = async { runCatching { printReceipt(data, takeOption) } }   // 병렬
    val saveResult  = runCatching { if (!orderSaved) firestoreRepository.postOrder(data) }
    if (saveResult.isSuccess) orderSaved = true
    val pr = printResult.await()   // 인쇄도 끝까지 대기
    saveResult.getOrThrow()        // 저장 실패 우선 보고
    pr.getOrThrow()                // 그다음 인쇄 실패
}
// 둘 다 성공:
val nextOrderId = firestoreRepository.getOrderNumber()   // commit 이후라야 정확
_uiState.update { it.copy(basketItems = emptyList(), totalPrice = 0, orderId = nextOrderId) }
_uiEvent.emit(UiEvent.NavigateHome)
```

가장 느린 작업(인쇄 또는 commit ack) 시간만 소요된다. `getOrderNumber`는 `DAY_SUMMARIES.lastOrderNum`을 읽으므로(FirestoreRepository.kt:30) 반드시 저장 commit 이후 1회 호출한다.

### 2. 재시도 시 중복 저장 방지 (핵심)

`postOrder`는 **멱등이 아니다**: 정산(`CREDIT_SETTLED`) 주문은 `applyRollupDelta(sign = 1)`로 집계를 증분하므로(FirestoreRepository.kt:169-170) 같은 주문을 두 번 저장하면 매출 집계가 이중 계상된다.

→ ViewModel에 `orderSaved: Boolean` 플래그를 두어, 저장이 한 번 성공한 뒤의 재시도는 **인쇄만** 재실행한다. 새 주문(`onOrderConfirmed`) 진입 시 `orderSaved = false`로 리셋한다.

| 시나리오 | 재시도 동작 |
|---|---|
| 저장 실패 (commit/timeout) | 저장 + 인쇄 모두 재실행 |
| 저장 성공 + 인쇄 실패 | 인쇄만 재실행 (저장 스킵) |

> 알려진 경미한 트레이드오프: 저장은 실패했으나 인쇄는 물리적으로 완료된 드문 경우, 재시도 시 영수증이 한 번 더 출력될 수 있다. 저장 실패(Firestore commit 실패/타임아웃)는 드물어 허용한다.

### 3. `postOrder`를 서버 확정 대기로 변경

`FirestoreRepository`:
- `fun postOrder(data: Order)` → `suspend fun postOrder(data: Order)`
- `batch.commit().addOnFailureListener { ... }` → `batch.commit().await()` (commit ack까지 대기, 실패 시 throw)
- 오프라인 무한 대기 방지를 위해 `withTimeout(10_000)`으로 감싼다. 타임아웃 시 예외 → 저장 실패로 처리되어 재시도 가능.
- KDoc("핫패스: ... 서버 ack를 await하지 않는다.")을 새 동작에 맞게 갱신.

### 4. ViewModel 상태/이벤트 변경

`UiState`:
- 추가: `submitError: SubmitError? = null`
- 제거: `printFailure: PrintFailure?`

```kotlin
sealed class SubmitError {
    abstract val seq: Long           // 동일 실패 연속 시에도 재발화하도록 단조 증가
    data class SaveFailed(override val seq: Long) : SubmitError()
    data class PrintFailed(val reason: PrintFailureReason, override val seq: Long) : SubmitError()
}
```

메서드:
- `onOrderConfirmed(data, takeOption)`: 가드 후 `lastOrder` 저장, `orderSaved = false`, `isSubmitting = true`, `submitError = null` 세팅 → `runSubmission()` 호출.
- `runSubmission(data, takeOption)` (private): 위 1·2의 병렬 로직. `catch (PrintServerException)` → `submitError = PrintFailed`; `catch (Exception)` → `submitError = SaveFailed`; `finally { isSubmitting = false }`. 각 실패는 Crashlytics에 기록.
- `retrySubmission()`: `lastOrder`로 `isSubmitting = true`, `submitError = null` 후 `runSubmission()` 재호출(`orderSaved` 가드가 중복 저장 방지).
- 제거: `launchPrint`(applicationScope 분기), `reportPrintFailure`, `reprintLastOrder`, `dismissPrintFailure`, `PrintFailure` 데이터 클래스, `printFailureSeq`. `printReceipt`는 유지(이제 `runSubmission`에서 직접 호출).

> `applicationScope`는 `HomeViewModel` 내에서 `launchPrint`에서만 쓰이므로(HomeViewModel.kt:219) 해당 분기 제거 후 생성자 주입 파라미터에서도 제거한다. DI provider(`@Named("ApplicationScope")`)는 `OrdersViewModel`·`ReportViewModel`이 계속 사용하므로 그대로 둔다.

### 5. UI 변경

**PaymentScreen/PaymentRoute** (PaymentScreen.kt):
- `state.submitError`를 받아 AlertDialog로 표시.
  - `SaveFailed`: 메시지 + `재시도`(→ `retrySubmission()`). 닫기 없음(닫으면 주문 유실).
  - `PrintFailed`: 메시지(사유별 문구) + `재시도(재출력)`(→ `retrySubmission()`) + `영수증 없이 홈으로`(→ `skipPrintAndComplete()`, 저장은 완료됨). 사유별 문구는 기존 `printFailureMessage`(HomeScreen.kt:271) 로직을 결제 화면으로 이전.
- `skipPrintAndComplete()`(ViewModel): 저장된 주문(`orderSaved`)에 한해 성공 경로와 동일하게 바구니 비우기 + `getOrderNumber` 갱신 + `submitError` 해제 후 `NavigateHome`. 인쇄만 포기하고 홈으로 정상 복귀.
- 기존 `isSubmitting` 오버레이/`처리 중…` 버튼은 그대로 유지.

**HomeScreen** (HomeScreen.kt):
- `printFailure` 관련 `LaunchedEffect`(88-100)와 `printFailureMessage`(271) 제거. 인쇄실패 전용으로만 쓰이던 `SnackbarHostState`가 다른 용도가 없으면 함께 정리.

## 데이터 흐름

```
결제 완료 클릭
  └─ onOrderConfirmed: isSubmitting=true, orderSaved=false, submitError=null
       └─ runSubmission (viewModelScope.io)
            ├─ async { printReceipt }            ┐ 병렬
            ├─ postOrder (commit().await, 10s)   ┘
            ├─ 둘 다 성공 → getOrderNumber → 상태갱신 → NavigateHome → 홈
            ├─ 저장 실패  → submitError=SaveFailed  → 결제 화면 유지(재시도)
            └─ 인쇄 실패  → submitError=PrintFailed → 결제 화면 유지(재시도/홈)
  └─ 재시도: retrySubmission → runSubmission (orderSaved=true면 인쇄만)
```

## 테스트

`HomeViewModel` 단위 테스트(가짜 `FirestoreRepository`/프린터 주입):
- 저장·인쇄 모두 성공 → `NavigateHome` emit, 바구니 비움, `orderId` 갱신.
- 저장 성공 + 인쇄 실패 → `NavigateHome` 미발생, `submitError = PrintFailed`, `isSubmitting=false`.
- 위 상태에서 `retrySubmission` → `postOrder` **재호출 안 됨**(중복 저장 방지), 인쇄만 재시도, 성공 시 `NavigateHome`.
- 저장 실패 → `submitError = SaveFailed`, `NavigateHome` 미발생. 재시도 시 `postOrder` 재호출됨.
- `postOrder` 타임아웃 → 저장 실패로 귀결.
- 중복 제출 가드: `isSubmitting` 중 재호출 무시.

## 범위 밖 (YAGNI)

- 부분 성공의 영구 큐/오프라인 재시도 큐(저장은 Firestore SDK 오프라인 지속성에 위임, 사용자 재시도로 충분).
- 별도의 "주문완료" 확인 화면 신설(기존처럼 홈으로 복귀).
- 인쇄 외 후처리(알림 등) 추가.
