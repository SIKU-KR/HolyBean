# 결제 완료 Await-All 전환 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `결제 완료` 시 주문 저장(서버 ack)과 영수증 인쇄가 모두 성공한 뒤에만 홈으로 전환하고, 실패 시 결제 화면에서 재시도할 수 있게 한다.

**Architecture:** `HomeViewModel`에서 저장과 인쇄를 `coroutineScope` 안에서 병렬 실행하고 둘 다 await한다. `postOrder`는 commit ack까지 대기(타임아웃 포함)하도록 suspend로 바꾼다. 재시도 시 중복 저장을 막기 위해 `orderSaved` 플래그로 가드한다. 실패 UI는 홈 스낵바에서 결제 화면 AlertDialog로 옮긴다.

**Tech Stack:** Kotlin, Coroutines (`coroutineScope`/`async`/`withTimeout`), Jetpack Compose, Firebase Firestore KTX (`Task.await()`), Hilt, JUnit4 + MockK + kotlinx-coroutines-test.

**테스트 실행 명령(이 환경 공통):**
```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:testDebugUnitTest --tests "eloom.holybean.ui.home.HomeViewModelTest"
```
**컴파일 확인:**
```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:compileDebugKotlin
```

---

## File Structure

- **Modify** `app/src/main/java/eloom/holybean/data/repository/FirestoreRepository.kt` — `postOrder`를 `suspend` + `commit().await()` + `withTimeout`로 변경 (Task 1).
- **Modify** `app/src/main/java/eloom/holybean/ui/home/HomeViewModel.kt` — `SubmitError`/`orderSaved` 도입, `onOrderConfirmed`/`runSubmission`/`retrySubmission`/`skipPrintAndComplete` 작성, `printFailure`·`launchPrint`·`reprintLastOrder`·`dismissPrintFailure` 제거, `applicationScope` 주입 제거 (Task 2).
- **Modify** `app/src/test/kotlin/eloom/holybean/ui/home/HomeViewModelTest.kt` — 생성자 호출 갱신, `postOrder` 스텁을 `coEvery`로, 실패/재시도 테스트를 `submitError`/`retrySubmission` 기준으로 재작성 (Task 2).
- **Modify** `app/src/main/java/eloom/holybean/ui/payment/PaymentScreen.kt` — `submitError` AlertDialog + 재시도/홈 콜백 (Task 3).
- **Modify** `app/src/main/java/eloom/holybean/ui/home/HomeScreen.kt` — `printFailure` 스낵바/`printFailureMessage` 제거 (Task 4).

> 사전 확인: `postOrder` 호출처는 `HomeViewModel`과 테스트뿐임을 보장한다.
> ```bash
> grep -rn "postOrder" app/src --include="*.kt"
> ```
> 결과가 `FirestoreRepository.kt`(정의), `HomeViewModel.kt`(호출), `HomeViewModelTest.kt`(스텁) 외에 없어야 한다. 다른 호출처가 있으면 그 호출도 suspend 컨텍스트에서 호출되도록 함께 수정한다.

---

## Task 1: `postOrder`를 서버 ack 대기로 변경

**Files:**
- Modify: `app/src/main/java/eloom/holybean/data/repository/FirestoreRepository.kt:152-188`

> `postOrder`는 실제 Firestore SDK(`db.batch()`)에 의존하며 기존에도 단위 테스트가 없다(`FirestoreRepository`는 Firebase 실인스턴스 필요). 따라서 이 Task는 컴파일 + Task 2의 ViewModel 테스트(목 레포)로 간접 검증한다.

- [ ] **Step 1: import 확인**

파일 상단에 다음 import가 이미 있는지 확인하고 없으면 추가한다. (`getOrderNumber`가 `.await()`를, `checkConnection`이 `withTimeout`을 이미 쓰므로 둘 다 존재할 가능성이 높다.)

```kotlin
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
```

확인:
```bash
grep -n "kotlinx.coroutines.tasks.await\|kotlinx.coroutines.withTimeout" app/src/main/java/eloom/holybean/data/repository/FirestoreRepository.kt
```

- [ ] **Step 2: `postOrder` 시그니처와 commit 변경**

`FirestoreRepository.kt:152-153`의 KDoc과 시그니처를 아래로 교체한다.

기존:
```kotlin
    /** 핫패스: 로컬에 즉시 적용되고 동기화는 백그라운드(NFR-7). 서버 ack를 await하지 않는다. */
    fun postOrder(data: Order) {
```
변경:
```kotlin
    /**
     * 주문을 저장하고 서버 commit ack까지 대기한다(결제 완료 흐름이 저장 성공을 보장받아야 하므로).
     * 오프라인 등으로 ack가 지연되면 무한 대기하지 않도록 타임아웃을 둔다 — 타임아웃 시 예외를 던져
     * 호출부가 저장 실패로 처리(재시도)하게 한다.
     */
    suspend fun postOrder(data: Order) {
```

그리고 `FirestoreRepository.kt:187`의 commit 라인을 교체한다.

기존:
```kotlin
        batch.commit().addOnFailureListener { FirebaseCrashlytics.getInstance().recordException(it) }  // await 하지 않음 — 로컬 즉시 반영, 동기화는 SDK가 큐잉
```
변경:
```kotlin
        withTimeout(POST_ORDER_ACK_TIMEOUT_MS) { batch.commit().await() }  // 서버 ack까지 대기(타임아웃 시 예외)
```

- [ ] **Step 3: 타임아웃 상수 추가**

`FirestoreRepository` 클래스 안(예: `postOrder` 정의 바로 위 또는 클래스 상단 프로퍼티 영역)에 상수를 추가한다. 이미 `companion object`가 있으면 그 안에, 없으면 아래 형태로 추가한다.

```kotlin
    private companion object {
        const val POST_ORDER_ACK_TIMEOUT_MS = 10_000L
    }
```

> 파일에 이미 `companion object`가 있으면 새로 만들지 말고 그 안에 `const val POST_ORDER_ACK_TIMEOUT_MS = 10_000L`만 추가한다.

- [ ] **Step 4: 컴파일 확인**

Run:
```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:compileDebugKotlin
```
Expected: `HomeViewModel.kt`에서 `postOrder` 호출이 suspend 컨텍스트가 아니라는 에러가 날 수 있다. 그 경우 Task 2에서 해소되므로, 여기서는 `FirestoreRepository.kt` 자체에 에러가 없는지만 확인한다(Task 2 완료 후 전체가 통과). 단독으로 통과시키려면 Task 1·2를 한 커밋으로 묶어도 된다.

- [ ] **Step 5: 커밋**

```bash
git add app/src/main/java/eloom/holybean/data/repository/FirestoreRepository.kt
git commit -m "refactor(orders): await Firestore commit ack in postOrder with timeout"
```

---

## Task 2: `HomeViewModel` 저장·인쇄 await-all + 상태 재구성

**Files:**
- Modify: `app/src/main/java/eloom/holybean/ui/home/HomeViewModel.kt`
- Test: `app/src/test/kotlin/eloom/holybean/ui/home/HomeViewModelTest.kt`

### 2A. 테스트 먼저 재작성

- [ ] **Step 1: 테스트의 생성자 호출과 `postOrder` 스텁 갱신**

`applicationScope` 인자가 제거되므로 `HomeViewModelTest.kt`의 모든 `HomeViewModel(...)` 생성자 호출에서 **scope 인자를 삭제**한다. 대상: `setUp()`(58행 부근), 그리고 인라인 재구성 4곳(`successful order resets basket`, `init uses cached menu`, `isSubmitting`, `second concurrent`). 변경 후 형태:

```kotlin
homeViewModel = HomeViewModel(
    firestoreRepository,
    menuRepository,
    testDispatcher,
    piPrintClient,
    homePrinter,
)
```
그리고 `StandardTestDispatcher`를 쓰는 2곳:
```kotlin
val vm = HomeViewModel(
    firestoreRepository, menuRepository, std, piPrintClient, homePrinter,
)
```

또한 `postOrder`가 `suspend`가 되었으므로 `every { firestoreRepository.postOrder(any()) } returns Unit` / `... throws ...` 형태를 **모두 `coEvery`** 로 바꾼다. (파일 전체에서 `every { firestoreRepository.postOrder` → `coEvery { firestoreRepository.postOrder`.) `verify(... ) { firestoreRepository.postOrder(...) }` 는 `coVerify`로 바꾼다.

import 추가:
```kotlin
import io.mockk.coVerify
```
(이미 있으면 생략) 그리고 사용하지 않게 되는 `import io.mockk.verify`는 남은 `verify` 사용처가 없으면 제거한다(컴파일 경고 방지, 필수 아님).

- [ ] **Step 2: 실패/재시도 테스트를 `submitError` 기준으로 교체**

기존 print/printFailure 관련 테스트들(`print failure sets printFailure with reason`, `successful print leaves printFailure null`, `new order resets prior printFailure`, `dismissPrintFailure clears state`, `reprintLastOrder retries print and clears failure on success`, `consecutive identical print failures ...`, `onOrderConfirmed should handle repository error and emit toast`)을 아래 테스트들로 **대체**한다.

```kotlin
@Test
fun `print failure keeps user on screen with PrintFailed error and no navigate`() = runTest(testDispatcher) {
    val order = createTestOrder()
    coEvery { firestoreRepository.postOrder(any()) } returns Unit
    coEvery { piPrintClient.print(any<List<PrintCommandDto>>()) } throws
        eloom.holybean.printer.network.PrintServerException(
            eloom.holybean.printer.network.PrintFailureReason.PrinterOffline, "offline"
        )
    val events = mutableListOf<HomeViewModel.UiEvent>()
    val job: Job = launch { homeViewModel.uiEvent.collect { events.add(it) } }

    homeViewModel.onOrderConfirmed(order, "포장")
    advanceUntilIdle()

    val err = homeViewModel.uiState.value.submitError
    assertTrue(err is HomeViewModel.SubmitError.PrintFailed)
    assertEquals(
        eloom.holybean.printer.network.PrintFailureReason.PrinterOffline,
        (err as HomeViewModel.SubmitError.PrintFailed).reason,
    )
    assertTrue(events.none { it is HomeViewModel.UiEvent.NavigateHome })
    assertEquals(false, homeViewModel.uiState.value.isSubmitting)
    job.cancel()
}

@Test
fun `successful submit leaves submitError null and navigates`() = runTest(testDispatcher) {
    val order = createTestOrder()
    coEvery { firestoreRepository.postOrder(any()) } returns Unit
    val events = mutableListOf<HomeViewModel.UiEvent>()
    val job: Job = launch { homeViewModel.uiEvent.collect { events.add(it) } }

    homeViewModel.onOrderConfirmed(order, "포장")
    advanceUntilIdle()

    assertEquals(null, homeViewModel.uiState.value.submitError)
    assertTrue(events.any { it is HomeViewModel.UiEvent.NavigateHome })
    job.cancel()
}

@Test
fun `save failure sets SaveFailed and does not navigate`() = runTest(testDispatcher) {
    val order = createTestOrder()
    coEvery { firestoreRepository.postOrder(any()) } throws Exception("commit failed")
    val events = mutableListOf<HomeViewModel.UiEvent>()
    val job: Job = launch { homeViewModel.uiEvent.collect { events.add(it) } }

    homeViewModel.onOrderConfirmed(order, "포장")
    advanceUntilIdle()

    assertTrue(homeViewModel.uiState.value.submitError is HomeViewModel.SubmitError.SaveFailed)
    assertTrue(events.none { it is HomeViewModel.UiEvent.NavigateHome })
    job.cancel()
}

@Test
fun `retry after print failure reprints only and does not re-save order`() = runTest(testDispatcher) {
    val order = createTestOrder()
    coEvery { firestoreRepository.postOrder(any()) } returns Unit
    coEvery { piPrintClient.print(any<List<PrintCommandDto>>()) } throws
        eloom.holybean.printer.network.PrintServerException(
            eloom.holybean.printer.network.PrintFailureReason.PrinterOffline, "offline"
        )
    homeViewModel.onOrderConfirmed(order, "포장")
    advanceUntilIdle()
    assertTrue(homeViewModel.uiState.value.submitError is HomeViewModel.SubmitError.PrintFailed)

    // 프린터 복구 후 재시도 → 인쇄만 재실행, postOrder는 추가 호출되지 않음
    coEvery { piPrintClient.print(any<List<PrintCommandDto>>()) } returns Unit
    val events = mutableListOf<HomeViewModel.UiEvent>()
    val job: Job = launch { homeViewModel.uiEvent.collect { events.add(it) } }
    homeViewModel.retrySubmission()
    advanceUntilIdle()

    coVerify(exactly = 1) { firestoreRepository.postOrder(order) } // 최초 1회뿐
    assertEquals(null, homeViewModel.uiState.value.submitError)
    assertTrue(events.any { it is HomeViewModel.UiEvent.NavigateHome })
    job.cancel()
}

@Test
fun `retry after save failure re-saves order`() = runTest(testDispatcher) {
    val order = createTestOrder()
    coEvery { firestoreRepository.postOrder(any()) } throws Exception("commit failed")
    homeViewModel.onOrderConfirmed(order, "포장")
    advanceUntilIdle()
    assertTrue(homeViewModel.uiState.value.submitError is HomeViewModel.SubmitError.SaveFailed)

    coEvery { firestoreRepository.postOrder(any()) } returns Unit
    homeViewModel.retrySubmission()
    advanceUntilIdle()

    coVerify(exactly = 2) { firestoreRepository.postOrder(order) } // 실패분 + 재시도분
    assertEquals(null, homeViewModel.uiState.value.submitError)
}

@Test
fun `skipPrintAndComplete completes saved order without reprint`() = runTest(testDispatcher) {
    val order = createTestOrder()
    coEvery { firestoreRepository.postOrder(any()) } returns Unit
    coEvery { piPrintClient.print(any<List<PrintCommandDto>>()) } throws
        eloom.holybean.printer.network.PrintServerException(
            eloom.holybean.printer.network.PrintFailureReason.PrinterError, "err"
        )
    homeViewModel.onOrderConfirmed(order, "포장")
    advanceUntilIdle()
    assertTrue(homeViewModel.uiState.value.submitError is HomeViewModel.SubmitError.PrintFailed)

    val events = mutableListOf<HomeViewModel.UiEvent>()
    val job: Job = launch { homeViewModel.uiEvent.collect { events.add(it) } }
    homeViewModel.skipPrintAndComplete()
    advanceUntilIdle()

    assertEquals(null, homeViewModel.uiState.value.submitError)
    assertTrue(events.any { it is HomeViewModel.UiEvent.NavigateHome })
    job.cancel()
}
```

또한 기존 `onOrderConfirmed should call piPrintClient with receipt commands` 테스트의 `every { firestoreRepository.postOrder(any()) } returns Unit` 는 `coEvery`로 바꾼 채 그대로 둔다(인쇄 2회 호출 검증은 유효).

- [ ] **Step 3: 테스트 실행 → 실패 확인**

Run:
```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:testDebugUnitTest --tests "eloom.holybean.ui.home.HomeViewModelTest"
```
Expected: 컴파일 실패 또는 테스트 실패 (`SubmitError`/`retrySubmission`/`skipPrintAndComplete` 미정의, 생성자 인자 수 불일치). 이는 정상 — 2B에서 구현한다.

### 2B. ViewModel 구현

- [ ] **Step 4: import와 생성자 정리**

`HomeViewModel.kt` 상단 import에 추가:
```kotlin
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
```
사용하지 않게 되는 import 제거: `import kotlinx.coroutines.CoroutineScope`.

생성자에서 `applicationScope` 파라미터 줄을 삭제한다. 변경 후:
```kotlin
class HomeViewModel @Inject constructor(
    private val firestoreRepository: FirestoreRepository,
    private val menuRepository: MenuRepository,
    @Named("IO") private val ioDispatcher: CoroutineDispatcher,
    private val piPrintClient: PiPrintClient,
    private val homePrinter: HomePrinter,
) : ViewModel() {
```

- [ ] **Step 5: `SubmitError` 도입 및 `PrintFailure` 제거**

`data class PrintFailure(...)` (36행) 를 삭제하고 그 자리에 추가:
```kotlin
    sealed class SubmitError {
        abstract val seq: Long
        data class SaveFailed(override val seq: Long) : SubmitError()
        data class PrintFailed(val reason: PrintFailureReason, override val seq: Long) : SubmitError()
    }
```

`UiState`에서 `val printFailure: PrintFailure? = null,` 를 `val submitError: SubmitError? = null,` 로 교체한다.

- [ ] **Step 6: 필드 교체**

`private var printFailureSeq = 0L` (78행 부근) 을 아래로 교체:
```kotlin
    private var orderSaved: Boolean = false
    // 동일 실패가 연속될 때도 StateFlow가 다시 발화하도록 하는 단조 증가 시퀀스
    private var submitSeq: Long = 0L
```
`private var lastOrder: Pair<Order, String>? = null` 는 그대로 둔다.

- [ ] **Step 7: `onOrderConfirmed` 본문 교체**

`onOrderConfirmed` (177-206행) 전체를 아래로 교체:
```kotlin
    fun onOrderConfirmed(data: Order, takeOption: String) {
        if (data.orderNum <= 0) {
            viewModelScope.launch(ioDispatcher) {
                _uiEvent.emit(UiEvent.ShowToast("주문번호를 불러오지 못했습니다. 다시 시도해 주세요."))
            }
            return
        }
        if (_uiState.value.isSubmitting) return
        lastOrder = data to takeOption
        orderSaved = false
        _uiState.update { it.copy(isSubmitting = true, submitError = null) }
        runSubmission(data, takeOption)
    }

    fun retrySubmission() {
        val (data, takeOption) = lastOrder ?: return
        if (_uiState.value.isSubmitting) return
        _uiState.update { it.copy(isSubmitting = true, submitError = null) }
        runSubmission(data, takeOption)
    }

    fun dismissSubmitError() {
        _uiState.update { it.copy(submitError = null) }
    }

    // 인쇄만 실패한(저장은 끝난) 주문을 영수증 없이 정상 완료 처리하고 홈으로 복귀한다.
    fun skipPrintAndComplete() {
        if (!orderSaved) return
        viewModelScope.launch(ioDispatcher) {
            val nextOrderId = firestoreRepository.getOrderNumber()
            _uiState.update { it.copy(basketItems = emptyList(), totalPrice = 0, orderId = nextOrderId, submitError = null) }
            _uiEvent.emit(UiEvent.NavigateHome)
        }
    }

    // 저장(서버 ack 대기)과 인쇄를 병렬 실행하고 둘 다 성공해야 홈으로 전환한다.
    // 저장이 한 번 성공하면 orderSaved=true 가 되어, 재시도 시 중복 저장(집계 이중 계상)을 막는다.
    private fun runSubmission(data: Order, takeOption: String) {
        viewModelScope.launch(ioDispatcher) {
            try {
                coroutineScope {
                    val printDeferred = async { printReceipt(data, takeOption) }
                    if (!orderSaved) {
                        firestoreRepository.postOrder(data)
                        orderSaved = true
                    }
                    printDeferred.await()
                }
                val nextOrderId = firestoreRepository.getOrderNumber()
                _uiState.update { it.copy(basketItems = emptyList(), totalPrice = 0, orderId = nextOrderId) }
                _uiEvent.emit(UiEvent.NavigateHome)
            } catch (e: PrintServerException) {
                _uiState.update { it.copy(submitError = SubmitError.PrintFailed(e.reason, ++submitSeq)) }
                FirebaseCrashlytics.getInstance().apply {
                    setCustomKey("orderNum", data.orderNum)
                    setCustomKey("print_reason", e.reason.name)
                    recordException(e)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(submitError = SubmitError.SaveFailed(++submitSeq)) }
                FirebaseCrashlytics.getInstance().recordException(e)
            } finally {
                _uiState.update { it.copy(isSubmitting = false) }
            }
        }
    }
```

- [ ] **Step 8: 죽은 메서드 제거**

`launchPrint`, `reprintLastOrder`, `dismissPrintFailure`, `reportPrintFailure` 를 삭제한다. `printReceipt` 는 **남긴다**(아래 형태로, 이미 동일하면 그대로):
```kotlin
    private suspend fun printReceipt(data: Order, takeOption: String) {
        val customerCommands = homePrinter.receiptForCustomer(data)
        val posCommands = homePrinter.receiptForPOS(data, takeOption)
        piPrintClient.print(customerCommands)
        piPrintClient.print(posCommands)
    }
```

- [ ] **Step 9: 테스트 실행 → 통과 확인**

Run:
```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:testDebugUnitTest --tests "eloom.holybean.ui.home.HomeViewModelTest"
```
Expected: PASS (모든 테스트 통과). 실패 시 메시지에 따라 수정.

- [ ] **Step 10: 커밋**

```bash
git add app/src/main/java/eloom/holybean/ui/home/HomeViewModel.kt app/src/test/kotlin/eloom/holybean/ui/home/HomeViewModelTest.kt
git commit -m "feat(orders): await save+print before navigating home, retry on failure"
```

---

## Task 3: 결제 화면에 실패 다이얼로그 + 재시도

**Files:**
- Modify: `app/src/main/java/eloom/holybean/ui/payment/PaymentScreen.kt`

> 이 모듈에는 Compose UI 테스트 인프라가 없어 컴파일 + 수동 확인으로 검증한다.

- [ ] **Step 1: `PaymentRoute`에 submitError 다이얼로그 연결**

`PaymentRoute` (PaymentScreen.kt:45-80)에서 `PaymentScreen(...)` 호출 뒤(같은 `@Composable` 본문 안)에 다이얼로그를 추가한다. `state.submitError` 를 읽어 분기한다.

`PaymentScreen(...)` 호출 블록 **다음**에 추가:
```kotlin
    when (val err = state.submitError) {
        is HomeViewModel.SubmitError.SaveFailed ->
            SubmitErrorDialog(
                message = "주문 저장에 실패했습니다. 네트워크를 확인하고 다시 시도해 주세요.",
                onRetry = { sharedViewModel.retrySubmission() },
                onSecondary = null,
                secondaryLabel = null,
            )
        is HomeViewModel.SubmitError.PrintFailed ->
            SubmitErrorDialog(
                message = printFailureMessage(err.reason),
                onRetry = { sharedViewModel.retrySubmission() },
                onSecondary = { sharedViewModel.skipPrintAndComplete() },
                secondaryLabel = "영수증 없이 홈으로",
            )
        null -> Unit
    }
```

- [ ] **Step 2: 다이얼로그 컴포저블과 메시지 헬퍼 추가**

`PaymentScreen.kt` 파일 하단(다른 `private @Composable` 들과 같은 위치)에 추가한다. 메시지 문구는 기존 `HomeScreen.printFailureMessage`(HomeScreen.kt:271-281)의 매핑을 그대로 옮긴 것이다.

```kotlin
@Composable
private fun SubmitErrorDialog(
    message: String,
    onRetry: () -> Unit,
    onSecondary: (() -> Unit)?,
    secondaryLabel: String?,
) {
    AlertDialog(
        onDismissRequest = {},   // 임의 닫기 방지: 명시적 선택만 허용
        title = { Text("처리 실패") },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onRetry) { Text("재시도") }
        },
        dismissButton = if (onSecondary != null && secondaryLabel != null) {
            { TextButton(onClick = onSecondary) { Text(secondaryLabel) } }
        } else null,
    )
}

private fun printFailureMessage(reason: eloom.holybean.printer.network.PrintFailureReason): String =
    when (reason) {
        eloom.holybean.printer.network.PrintFailureReason.ServerUnreachable ->
            "프린터 서버에 연결되지 않았어요. Pi 전원·네트워크 확인 후 재출력하세요."
        eloom.holybean.printer.network.PrintFailureReason.PrinterOffline ->
            "프린터가 응답하지 않아요. 전원·USB 연결 확인 후 재출력하세요."
        eloom.holybean.printer.network.PrintFailureReason.PrinterError ->
            "출력에 실패했어요. 용지·덮개 상태 확인 후 재출력하세요."
        eloom.holybean.printer.network.PrintFailureReason.Unknown ->
            "다시 출력해 주세요."
    }
```

> 위 문구는 HomeScreen.kt:273-281의 원본 문자열을 그대로 옮긴 것이다(원본의 `"${orderNum}번 영수증 출력 실패 — "` 접두사는 결제 화면 다이얼로그 맥락상 생략). Task 4에서 원본을 지우기 전 문자열이 위와 일치하는지 확인한다.

- [ ] **Step 3: 필요한 import 확인**

`AlertDialog`, `TextButton`, `Text` 는 이미 `androidx.compose.material3.*`(PaymentScreen.kt:11)로 들어와 있다. 추가 import 불필요. `HomeViewModel` 도 이미 import 되어 있다(34행).

- [ ] **Step 4: 컴파일 확인**

Run:
```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:compileDebugKotlin
```
Expected: PASS.

- [ ] **Step 5: 커밋**

```bash
git add app/src/main/java/eloom/holybean/ui/payment/PaymentScreen.kt
git commit -m "feat(payment): surface submit failure with retry dialog on payment screen"
```

---

## Task 4: 홈의 인쇄실패 스낵바 제거

**Files:**
- Modify: `app/src/main/java/eloom/holybean/ui/home/HomeScreen.kt`

- [ ] **Step 1: printFailure 스낵바 LaunchedEffect 제거**

`HomeScreen.kt:87-100`의 다음 블록을 삭제한다.
```kotlin
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.printFailure) {
        val failure = state.printFailure ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = printFailureMessage(failure),
            actionLabel = "재출력",
            duration = SnackbarDuration.Indefinite,
            withDismissAction = true,
        )
        when (result) {
            SnackbarResult.ActionPerformed -> sharedViewModel.reprintLastOrder()
            SnackbarResult.Dismissed -> sharedViewModel.dismissPrintFailure()
        }
    }
```

> `snackbarHostState`가 이 화면의 다른 곳(예: `Scaffold(snackbarHost = ...)`)에서 쓰이는지 먼저 확인한다:
> ```bash
> grep -n "snackbarHostState\|SnackbarHost" app/src/main/java/eloom/holybean/ui/home/HomeScreen.kt
> ```
> 인쇄실패 전용이고 다른 사용처가 없으면 `snackbarHostState` 선언째 제거한다. 다른 곳에서 쓰면 `val snackbarHostState = remember { SnackbarHostState() }` 선언은 남기고 `LaunchedEffect`만 제거한다.

- [ ] **Step 2: `printFailureMessage` 헬퍼 제거**

`HomeScreen.kt:271-281`의 `private fun printFailureMessage(f: HomeViewModel.PrintFailure): String { ... }` 전체를 삭제한다. (문구는 Task 3에서 결제 화면으로 이전 완료.)

- [ ] **Step 3: 미사용 import 정리**

삭제로 인해 미사용이 된 import를 제거한다(컴파일 경고/오류 방지). 후보: `SnackbarHostState`, `SnackbarResult`, `SnackbarDuration`, `PrintFailureReason`(HomeScreen에서 `printFailureMessage`에서만 썼다면). 컴파일러 에러/경고를 보고 실제 미사용만 제거한다.

- [ ] **Step 4: 컴파일 확인**

Run:
```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:compileDebugKotlin
```
Expected: PASS. (`state.printFailure`/`reprintLastOrder`/`dismissPrintFailure` 참조가 남아 있으면 에러 — 모두 제거되어야 함.)

- [ ] **Step 5: 전체 단위 테스트 + 커밋**

Run:
```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:testDebugUnitTest
```
Expected: PASS (전 모듈 단위 테스트).

```bash
git add app/src/main/java/eloom/holybean/ui/home/HomeScreen.kt
git commit -m "chore(home): drop print-failure snackbar now handled on payment screen"
```

---

## 최종 검증

- [ ] **전체 컴파일 + 테스트**
```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:compileDebugKotlin :app:testDebugUnitTest
```
- [ ] **수동 확인(에뮬레이터/기기, 가능 시):**
  - 정상: 결제 완료 → 영수증 출력 후 홈 복귀.
  - 프린터 오프라인: 결제 완료 → 결제 화면에 실패 다이얼로그 → 프린터 복구 후 `재시도` → 홈 복귀(중복 저장 없음).
  - 프린터 영구 실패: `영수증 없이 홈으로` → 홈 복귀, 주문은 저장됨.
  - 네트워크 차단: 결제 완료 → 약 10초 후 저장 실패 다이얼로그(무한 대기 없음).
```
```
