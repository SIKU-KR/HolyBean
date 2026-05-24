# 인쇄 오류 피드백 + 저비용 Android 표준 정합 — 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 영수증 인쇄 실패의 원인별 친절 메시지 + 재출력을 홈 Snackbar로 제공하고, 결제 로딩 표시·Crashlytics 보강·벡터 아이콘 교체로 Android 표준에 맞춘다.

**Architecture:** Pi 서버는 재시도 소진 후 HTTP 상태코드(503/500)로 최종 실패 이유를 이미 반환한다. Android는 이를 폐기하던 것을 `PrintFailureReason`으로 구조화해 `HomeViewModel.uiState`에 노출하고, `HomeRoute`의 `SnackbarHost`가 원인별 안내 + [재출력]을 띄운다. 인쇄는 기존대로 `applicationScope`에서 실행해 홈 복귀를 막지 않는다.

**Tech Stack:** Kotlin, Jetpack Compose (Material3), Hilt, Retrofit2, kotlinx-coroutines, mockk + JUnit4, Firebase Crashlytics, (Pi) Rust + axum.

---

## 파일 구조

**수정:**
- `android/app/src/main/java/eloom/holybean/printer/network/PrintServerException.kt` — `PrintFailureReason` enum + reason 필드 추가 (P1)
- `android/app/src/main/java/eloom/holybean/printer/PiPrintClient.kt` — 상태코드/IOException → reason 매핑 (P1)
- `android/app/src/main/java/eloom/holybean/ui/home/HomeViewModel.kt` — `PrintFailure`, `UiState.printFailure`/`isSubmitting`, `launchPrint`/`reprintLastOrder`/`dismissPrintFailure`, Crashlytics 기록 (P3·A1·B)
- `android/app/src/main/java/eloom/holybean/ui/home/HomeScreen.kt` — `HomeRoute`에 Scaffold+SnackbarHost, `printFailureMessage`, 설정 타일 아이콘 (P4·C1)
- `android/app/src/main/java/eloom/holybean/ui/payment/PaymentScreen.kt` — `isSubmitting` 로딩 버튼·스크림, 취소 아이콘 (A1·C1)
- `android/app/src/main/java/eloom/holybean/ui/orders/OrdersScreen.kt` — 닫기 아이콘 (C1)
- `android/app/src/main/java/eloom/holybean/ui/components/MenuTile.kt` — `icon` 슬롯 (C1)
- `android/app/src/test/kotlin/eloom/holybean/ui/home/HomeViewModelTest.kt` — mockkStatic + 신규 테스트
- `pi/src/http.rs` — 에러 JSON `code` 필드 (P2, 선택)

**생성:**
- `android/app/src/test/kotlin/eloom/holybean/printer/PiPrintClientTest.kt` — reason 매핑 테스트 (P1)

빌드: `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:testDebugUnitTest`,
`... :app:assembleDebug` (메모리 `build-environment-quirks` 참고). Pi: `cargo test`.

---

## Task 1: PiPrintClient 구조화 실패 이유 (P1)

**Files:**
- Modify: `android/app/src/main/java/eloom/holybean/printer/network/PrintServerException.kt`
- Modify: `android/app/src/main/java/eloom/holybean/printer/PiPrintClient.kt`
- Test: `android/app/src/test/kotlin/eloom/holybean/printer/PiPrintClientTest.kt` (create)

- [ ] **Step 1: enum + 예외 필드 추가**

`PrintServerException.kt` 전체를 교체:

```kotlin
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
```

- [ ] **Step 2: 실패 테스트 작성**

`PiPrintClientTest.kt` 생성:

```kotlin
package eloom.holybean.printer

import eloom.holybean.printer.network.PrintFailureReason
import eloom.holybean.printer.network.PrintServerApi
import eloom.holybean.printer.network.PrintServerException
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import java.io.IOException

@ExperimentalCoroutinesApi
class PiPrintClientTest {

    private val api: PrintServerApi = mockk()

    private fun client() = PiPrintClient(api, StandardTestDispatcher())

    @Test
    fun `http 503 maps to PrinterOffline`() = runTest {
        coEvery { api.print(any()) } returns Response.error(503, "".toResponseBody(null))
        val ex = runCatching { client().print(emptyList()) }.exceptionOrNull()
        assertTrue(ex is PrintServerException)
        assertEquals(PrintFailureReason.PrinterOffline, (ex as PrintServerException).reason)
    }

    @Test
    fun `http 500 maps to PrinterError`() = runTest {
        coEvery { api.print(any()) } returns Response.error(500, "".toResponseBody(null))
        val ex = runCatching { client().print(emptyList()) }.exceptionOrNull()
        assertEquals(PrintFailureReason.PrinterError, (ex as PrintServerException).reason)
    }

    @Test
    fun `IOException maps to ServerUnreachable`() = runTest {
        coEvery { api.print(any()) } throws IOException("connection refused")
        val ex = runCatching { client().print(emptyList()) }.exceptionOrNull()
        assertEquals(PrintFailureReason.ServerUnreachable, (ex as PrintServerException).reason)
    }
}
```

참고: `client()`에 `StandardTestDispatcher()`를 주입해 `withRetry`의 `delay()`가
`runTest`의 가상 시계로 즉시 진행된다(실시간 대기 없음).

- [ ] **Step 3: 테스트 실패 확인**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:testDebugUnitTest --tests "*PiPrintClientTest*"`
Expected: 컴파일 실패 — `PrintServerException` 생성자 시그니처 불일치 / `reason` 없음.

- [ ] **Step 4: PiPrintClient 매핑 구현**

`PiPrintClient.kt`의 `print` 함수를 교체(나머지는 유지):

```kotlin
suspend fun print(commands: List<PrintCommandDto>) = withContext(printerDispatcher) {
    mutex.withLock {
        withRetry {
            val response = try {
                api.print(PrintRequestDto(commands))
            } catch (e: java.io.IOException) {
                throw PrintServerException(
                    PrintFailureReason.ServerUnreachable,
                    "print server unreachable",
                    e,
                )
            }
            if (!response.isSuccessful) {
                val reason = when (response.code()) {
                    503 -> PrintFailureReason.PrinterOffline
                    500 -> PrintFailureReason.PrinterError
                    else -> PrintFailureReason.Unknown
                }
                throw PrintServerException(reason, "print server returned HTTP ${response.code()}")
            }
        }
    }
}
```

import 추가: `import eloom.holybean.printer.network.PrintFailureReason`
(`PrintServerException`은 기존 import 유지). `withRetry`가 `PrintServerException`을
재시도 후 마지막에 그대로 rethrow 하므로 reason이 보존된다.

- [ ] **Step 5: 테스트 통과 확인**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:testDebugUnitTest --tests "*PiPrintClientTest*"`
Expected: PASS (3개)

- [ ] **Step 6: 커밋**

```bash
git add android/app/src/main/java/eloom/holybean/printer/network/PrintServerException.kt \
        android/app/src/main/java/eloom/holybean/printer/PiPrintClient.kt \
        android/app/src/test/kotlin/eloom/holybean/printer/PiPrintClientTest.kt
git commit -m "feat(printer): map Pi print failures to structured PrintFailureReason"
```

---

## Task 2: HomeViewModel — 인쇄 실패 상태·재출력 + 결제 로딩 + Crashlytics (P3·A1·B)

**Files:**
- Modify: `android/app/src/main/java/eloom/holybean/ui/home/HomeViewModel.kt`
- Test: `android/app/src/test/kotlin/eloom/holybean/ui/home/HomeViewModelTest.kt`

- [ ] **Step 1: 테스트 setUp에 Crashlytics static mock 추가**

`HomeViewModelTest.kt`의 import에 추가:

```kotlin
import com.google.firebase.crashlytics.FirebaseCrashlytics
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
```

`setUp()` 맨 앞에 추가(다른 stub보다 먼저):

```kotlin
mockkStatic(FirebaseCrashlytics::class)
every { FirebaseCrashlytics.getInstance() } returns mockk(relaxed = true)
```

`tearDown()`에 추가:

```kotlin
unmockkAll()
```

- [ ] **Step 2: 신규 동작 실패 테스트 작성**

`HomeViewModelTest.kt`에 테스트 추가:

```kotlin
@Test
fun `print failure sets printFailure with reason`() = runTest(testDispatcher) {
    val order = createTestOrder()
    every { firestoreRepository.postOrder(any()) } returns Unit
    coEvery { piPrintClient.print(any<List<PrintCommandDto>>()) } throws
        eloom.holybean.printer.network.PrintServerException(
            eloom.holybean.printer.network.PrintFailureReason.PrinterOffline, "offline"
        )

    homeViewModel.onOrderConfirmed(order, "포장")
    advanceUntilIdle()

    val f = homeViewModel.uiState.value.printFailure
    assertEquals(order.orderNum, f?.orderNum)
    assertEquals(
        eloom.holybean.printer.network.PrintFailureReason.PrinterOffline,
        f?.reason,
    )
}

@Test
fun `successful print leaves printFailure null`() = runTest(testDispatcher) {
    val order = createTestOrder()
    every { firestoreRepository.postOrder(any()) } returns Unit

    homeViewModel.onOrderConfirmed(order, "포장")
    advanceUntilIdle()

    assertEquals(null, homeViewModel.uiState.value.printFailure)
}

@Test
fun `new order resets prior printFailure`() = runTest(testDispatcher) {
    val failing = createTestOrder(orderNum = 5)
    every { firestoreRepository.postOrder(any()) } returns Unit
    coEvery { piPrintClient.print(any<List<PrintCommandDto>>()) } throws
        eloom.holybean.printer.network.PrintServerException(
            eloom.holybean.printer.network.PrintFailureReason.PrinterError, "err"
        )
    homeViewModel.onOrderConfirmed(failing, "포장")
    advanceUntilIdle()
    assertTrue(homeViewModel.uiState.value.printFailure != null)

    // 다음 주문은 정상 출력 → 진입 시 이전 실패가 리셋되어야 한다
    coEvery { piPrintClient.print(any<List<PrintCommandDto>>()) } returns Unit
    homeViewModel.onOrderConfirmed(createTestOrder(orderNum = 6), "포장")
    advanceUntilIdle()
    assertEquals(null, homeViewModel.uiState.value.printFailure)
}

@Test
fun `dismissPrintFailure clears state`() = runTest(testDispatcher) {
    val order = createTestOrder()
    every { firestoreRepository.postOrder(any()) } returns Unit
    coEvery { piPrintClient.print(any<List<PrintCommandDto>>()) } throws
        eloom.holybean.printer.network.PrintServerException(
            eloom.holybean.printer.network.PrintFailureReason.Unknown, "x"
        )
    homeViewModel.onOrderConfirmed(order, "포장")
    advanceUntilIdle()
    assertTrue(homeViewModel.uiState.value.printFailure != null)

    homeViewModel.dismissPrintFailure()
    assertEquals(null, homeViewModel.uiState.value.printFailure)
}

@Test
fun `isSubmitting is true after confirm and false after completion`() = runTest {
    val std = StandardTestDispatcher(testScheduler)
    val vm = HomeViewModel(
        firestoreRepository, menuRepository, std,
        CoroutineScope(SupervisorJob() + std), piPrintClient, homePrinter,
    )
    advanceUntilIdle() // init 소비
    every { firestoreRepository.postOrder(any()) } returns Unit

    vm.onOrderConfirmed(createTestOrder(), "포장")
    assertTrue(vm.uiState.value.isSubmitting) // 런치 전 동기 세팅, 본문 미실행

    advanceUntilIdle()
    assertEquals(false, vm.uiState.value.isSubmitting)
}
```

- [ ] **Step 3: 테스트 실패 확인**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:testDebugUnitTest --tests "*HomeViewModelTest*"`
Expected: 컴파일 실패 — `printFailure`/`isSubmitting`/`dismissPrintFailure` 미정의.

- [ ] **Step 4: HomeViewModel 구현**

`HomeViewModel.kt` 변경:

(a) import 추가:

```kotlin
import com.google.firebase.crashlytics.FirebaseCrashlytics
import eloom.holybean.printer.network.PrintFailureReason
import eloom.holybean.printer.network.PrintServerException
```

(b) 중첩 타입 추가(`UiEvent` 옆):

```kotlin
data class PrintFailure(val orderNum: Int, val reason: PrintFailureReason)
```

(c) `UiState`에 필드 추가:

```kotlin
val printFailure: PrintFailure? = null,
val isSubmitting: Boolean = false,
```

(d) `private var orderInFlight = false` 제거하고 `private var lastOrder: Pair<Order, String>? = null` 추가.

(e) `onOrderConfirmed` 전체 교체:

```kotlin
fun onOrderConfirmed(data: Order, takeOption: String) {
    if (data.orderNum <= 0) {
        viewModelScope.launch(ioDispatcher) {
            _uiEvent.emit(UiEvent.ShowToast("주문번호를 불러오지 못했습니다. 다시 시도해 주세요."))
        }
        return
    }
    if (_uiState.value.isSubmitting) return
    // 새 주문 시작 — 직전 실패 스낵바 제거 + 제출 잠금(동기, 메인 스레드 단독)
    lastOrder = data to takeOption
    _uiState.value = _uiState.value.copy(isSubmitting = true, printFailure = null)

    viewModelScope.launch(ioDispatcher) {
        try {
            firestoreRepository.postOrder(data)
            val nextOrderId = firestoreRepository.getOrderNumber()
            _uiState.value = _uiState.value.copy(
                basketItems = emptyList(), totalPrice = 0, orderId = nextOrderId,
            )
            _uiEvent.emit(UiEvent.NavigateHome)
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().recordException(e) // 파트 B
            _uiEvent.emit(UiEvent.ShowToast("주문 처리 중 오류가 발생했습니다."))
        } finally {
            _uiState.value = _uiState.value.copy(isSubmitting = false)
        }
    }

    launchPrint(data, takeOption)
}
```

(f) 기존 `applicationScope.launch { runCatching { printReceipt(...) }... }` 블록과
`printReceipt`는 아래로 교체:

```kotlin
fun reprintLastOrder() {
    val (order, takeOption) = lastOrder ?: return
    launchPrint(order, takeOption)
}

fun dismissPrintFailure() {
    _uiState.value = _uiState.value.copy(printFailure = null)
}

// 인쇄는 ViewModel 생명주기와 독립인 applicationScope에서 실행(홈 복귀를 막지 않음).
private fun launchPrint(order: Order, takeOption: String) {
    applicationScope.launch {
        try {
            printReceipt(order, takeOption)
            // 성공: 이 주문에 대한 실패 표시가 남아 있으면 해제(재출력 성공 케이스)
            _uiState.value = _uiState.value.let {
                if (it.printFailure?.orderNum == order.orderNum) it.copy(printFailure = null) else it
            }
        } catch (e: PrintServerException) {
            reportPrintFailure(order.orderNum, e.reason, e)
        } catch (e: Exception) {
            reportPrintFailure(order.orderNum, PrintFailureReason.Unknown, e)
        }
    }
}

private fun reportPrintFailure(orderNum: Int, reason: PrintFailureReason, e: Throwable) {
    _uiState.value = _uiState.value.copy(printFailure = PrintFailure(orderNum, reason))
    FirebaseCrashlytics.getInstance().apply {
        setCustomKey("orderNum", orderNum)
        setCustomKey("print_reason", reason.name)
        recordException(e)
    }
}

private suspend fun printReceipt(data: Order, takeOption: String) {
    val customerCommands = homePrinter.receiptForCustomer(data)
    val posCommands = homePrinter.receiptForPOS(data, takeOption)
    piPrintClient.print(customerCommands)
    piPrintClient.print(posCommands)
}
```

참고: `_uiState`가 `MutableStateFlow`라 `applicationScope`(IO)에서의 갱신도
스레드 안전하다(별도 Main 전환 불필요). 두 영수증 중 첫 실패에서 예외가 나면
이후 `print`는 호출되지 않아 실패는 주문당 1회로 자연 코얼레스된다.

- [ ] **Step 5: 테스트 통과 확인**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:testDebugUnitTest --tests "*HomeViewModelTest*"`
Expected: PASS (기존 + 신규 전부)

- [ ] **Step 6: 커밋**

```bash
git add android/app/src/main/java/eloom/holybean/ui/home/HomeViewModel.kt \
        android/app/src/test/kotlin/eloom/holybean/ui/home/HomeViewModelTest.kt
git commit -m "feat(home): surface print failure + submit state + record non-fatals"
```

---

## Task 3: 홈 Snackbar 표시 (P4)

**Files:**
- Modify: `android/app/src/main/java/eloom/holybean/ui/home/HomeScreen.kt`

UI 코드라 단위 테스트 대신 빌드 + 육안 확인으로 검증한다.

- [ ] **Step 1: HomeRoute에 SnackbarHost 오버레이 추가**

`HomeScreen.kt`의 `HomeRoute` 안에서 `couponDialog` 블록은 그대로 두고, 그 아래
`HomeScreen(...)` 호출부를 Box 오버레이로 교체한다(Scaffold 대신 — 고정 태블릿
풀스크린 레이아웃의 inset/padding을 건드리지 않기 위함):

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

Box(Modifier.fillMaxSize()) {
    HomeScreen(
        categories = MenuCategories.names.toImmutableList(),
        selectedCategory = state.selectedCategoryIndex,
        menuItems = state.filteredMenuItems.toImmutableList(),
        basket = state.basketItems.toImmutableList(),
        orderId = state.orderId,
        total = state.totalPrice,
        onCategory = sharedViewModel::onCategorySelected,
        onMenuClick = sharedViewModel::addToBasket,
        onCouponClick = { couponDialog = true },
        onSettingsClick = onNavigateToSettings,
        onBasketClick = sharedViewModel::deleteFromBasket,
        onHistoryClick = onNavigateToOrders,
        onCheckout = sharedViewModel::onCheckoutClicked,
    )
    SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
}
```

import 확인: `Box`/`fillMaxSize`는 `layout.*`(기존), `Alignment`는 기존 import(line 17),
`SnackbarHost`/`SnackbarHostState`/`SnackbarDuration`/`SnackbarResult`는 `material3.*`
와일드카드(line 15)로 모두 커버됨 — 신규 import 불필요.

- [ ] **Step 2: printFailureMessage 헬퍼 추가**

`HomeScreen.kt` 파일 하단(다른 private 컴포저블 옆)에 추가:

```kotlin
private fun printFailureMessage(f: HomeViewModel.PrintFailure): String {
    val prefix = "${f.orderNum}번 영수증 출력 실패 — "
    return prefix + when (f.reason) {
        eloom.holybean.printer.network.PrintFailureReason.ServerUnreachable ->
            "프린터 서버에 연결되지 않았어요. Pi 전원·네트워크 확인 후 재출력하세요."
        eloom.holybean.printer.network.PrintFailureReason.PrinterOffline ->
            "프린터가 응답하지 않아요. 전원·USB 연결 확인 후 재출력하세요."
        eloom.holybean.printer.network.PrintFailureReason.PrinterError ->
            "출력에 실패했어요. 용지·덮개 상태 확인 후 재출력하세요."
        eloom.holybean.printer.network.PrintFailureReason.Unknown ->
            "다시 출력해 주세요."
    }
}
```

- [ ] **Step 3: 빌드 확인**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 커밋**

```bash
git add android/app/src/main/java/eloom/holybean/ui/home/HomeScreen.kt
git commit -m "feat(home): show indefinite snackbar with reprint on print failure"
```

---

## Task 4: 결제 로딩 버튼 (A1)

**Files:**
- Modify: `android/app/src/main/java/eloom/holybean/ui/payment/PaymentScreen.kt`

`isSubmitting`은 Task 2에서 `UiState`에 이미 추가됨. 여기서 화면에 연결한다.

- [ ] **Step 1: PaymentRoute → PaymentScreen에 isSubmitting 전달**

`PaymentRoute`의 `PaymentScreen(...)` 호출에 인자 추가:

```kotlin
PaymentScreen(
    orderId = state.orderId,
    items = state.basketItems.toImmutableList(),
    total = state.totalPrice,
    isSubmitting = state.isSubmitting,
    onCancel = onClose,
    onConfirm = { selection -> /* 기존 그대로 */
        PaymentForm.build(selection, state.basketItems, state.totalPrice, state.orderId, state.currentDate)
            .onSuccess { sharedViewModel.onOrderConfirmed(it, selection.cupOption) }
            .onFailure {
                android.widget.Toast.makeText(context, it.message, android.widget.Toast.LENGTH_SHORT).show()
            }
    },
)
```

- [ ] **Step 2: PaymentScreen 시그니처 + 버튼/취소/스크림 수정**

`PaymentScreen` 시그니처에 파라미터 추가:

```kotlin
@Composable
fun PaymentScreen(
    orderId: Int,
    items: ImmutableList<CartItem>,
    total: Int,
    isSubmitting: Boolean = false,
    onCancel: () -> Unit,
    onConfirm: (PaymentSelection) -> Unit,
) {
```

취소 버튼 `enabled = !isSubmitting` 추가:

```kotlin
OutlinedButton(
    onClick = onCancel,
    enabled = !isSubmitting,
    modifier = Modifier.heightIn(min = Dimens.minTouchTarget),
    shape = RoundedCornerShape(Dimens.radiusButton),
    colors = ButtonDefaults.outlinedButtonColors(contentColor = OnSurfaceMuted),
) { Text("✕ 취소", style = MaterialTheme.typography.bodyMedium) }
```

(C1에서 이 "✕ 취소"는 Task 6에서 아이콘으로 다시 바뀐다 — 여기서는 enabled만 추가.)

"결제 완료" 버튼을 교체:

```kotlin
Button(
    onClick = { onConfirm(PaymentSelection(cup, first, orderer, split, second, secondAmt)) },
    enabled = !isSubmitting,
    modifier = Modifier.fillMaxWidth().padding(Dimens.panePadding).height(Dimens.primaryTouchTarget),
    shape = RoundedCornerShape(Dimens.radiusButton),
    colors = ButtonDefaults.buttonColors(containerColor = Orange),
) {
    if (isSubmitting) {
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.onPrimary,
        )
        Spacer(Modifier.width(Dimens.spaceSm))
        Text("처리 중…", style = MaterialTheme.typography.titleMedium)
    } else {
        Text("결제 완료", style = MaterialTheme.typography.titleMedium)
    }
}
```

import 추가(필요 시): `import androidx.compose.material3.CircularProgressIndicator`
(이미 `material3.*` 와일드카드로 커버됨).

- [ ] **Step 3: 제출 중 스크림으로 중복 탭 차단**

`PaymentScreen`의 최상위 `ScreenContainer { ... }`를 `Box`로 감싸 스크림을 올린다.
`ScreenContainer { Column(...) { ... } }` 전체를 다음으로 감싼다:

```kotlin
Box(Modifier.fillMaxSize()) {
    ScreenContainer {
        // ...기존 Column 그대로...
    }
    if (isSubmitting) {
        Box(
            Modifier
                .matchParentSize()
                .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.08f))
                .pointerInput(Unit) { /* 소비만 — 하위로 탭 전달 차단 */ detectTapGestures {} },
        )
    }
}
```

import 추가: `import androidx.compose.foundation.background`,
`import androidx.compose.foundation.gestures.detectTapGestures`,
`import androidx.compose.ui.input.pointer.pointerInput`.
`PaymentPreview`는 `isSubmitting` 기본값(false)이라 수정 불필요.

- [ ] **Step 4: 빌드 확인**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 커밋**

```bash
git add android/app/src/main/java/eloom/holybean/ui/payment/PaymentScreen.kt
git commit -m "feat(payment): loading state on confirm button during submit"
```

---

## Task 5: 이모지 → 벡터 아이콘 (C1)

**Files:**
- Modify: `android/app/src/main/java/eloom/holybean/ui/components/MenuTile.kt`
- Modify: `android/app/src/main/java/eloom/holybean/ui/home/HomeScreen.kt`
- Modify: `android/app/src/main/java/eloom/holybean/ui/payment/PaymentScreen.kt`
- Modify: `android/app/src/main/java/eloom/holybean/ui/orders/OrdersScreen.kt`

`material-icons-extended`가 이미 의존성에 있어 추가 설정 불필요.

- [ ] **Step 1: MenuTile에 icon 슬롯 추가**

`MenuTile.kt`의 시그니처와 Column 내부 수정:

```kotlin
@Composable
fun MenuTile(
    name: String,
    price: Int?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: TileStyle = TileStyle.Menu,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
```

Column 내부, `Text(name, ...)` 바로 위에 추가:

```kotlin
if (icon != null) {
    Icon(
        icon,
        contentDescription = null, // 라벨 동반이라 장식용
        tint = labelColor,
        modifier = Modifier.size(20.dp),
    )
    Spacer(Modifier.height(Dimens.spaceXs))
}
```

import 추가: `import androidx.compose.material3.Icon`,
`import androidx.compose.foundation.layout.Spacer`,
`import androidx.compose.foundation.layout.height`,
`import androidx.compose.foundation.layout.size`.

- [ ] **Step 2: HomeScreen 설정 타일 아이콘화**

`HomeScreen.kt`의 설정 타일 호출 교체:

```kotlin
item(key = SETTINGS_TILE_ID) {
    MenuTile(
        "설정", null,
        onClick = onSettingsClick,
        style = TileStyle.Settings,
        icon = androidx.compose.material.icons.Icons.Filled.Settings,
    )
}
```

- [ ] **Step 3: Payment 취소 / Orders 닫기 아이콘화**

`PaymentScreen.kt`의 취소 버튼 내용 교체:

```kotlin
) {
    Icon(
        androidx.compose.material.icons.Icons.Filled.Close,
        contentDescription = null,
        modifier = Modifier.size(18.dp),
    )
    Spacer(Modifier.width(Dimens.spaceXs))
    Text("취소", style = MaterialTheme.typography.bodyMedium)
}
```

import 추가: `import androidx.compose.material3.Icon`,
`import androidx.compose.foundation.layout.size`,
`import androidx.compose.foundation.layout.width` (이미 `layout.*` 커버).

`OrdersScreen.kt`의 닫기 버튼 내용 교체:

```kotlin
) {
    Icon(
        androidx.compose.material.icons.Icons.Filled.Close,
        contentDescription = null,
        modifier = Modifier.size(18.dp),
    )
    Spacer(Modifier.width(Dimens.spaceXs))
    Text("닫기", style = MaterialTheme.typography.bodyMedium)
}
```

import 추가: `import androidx.compose.material3.Icon`,
`import androidx.compose.foundation.layout.size`,
`import androidx.compose.foundation.layout.Spacer`,
`import androidx.compose.foundation.layout.width` (이미 `layout.*` 커버).

- [ ] **Step 4: 빌드 확인**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 커밋**

```bash
git add android/app/src/main/java/eloom/holybean/ui/components/MenuTile.kt \
        android/app/src/main/java/eloom/holybean/ui/home/HomeScreen.kt \
        android/app/src/main/java/eloom/holybean/ui/payment/PaymentScreen.kt \
        android/app/src/main/java/eloom/holybean/ui/orders/OrdersScreen.kt
git commit -m "refactor(ui): replace emoji glyphs with Material vector icons"
```

---

## Task 6: (선택) Pi 에러 JSON에 머신 코드 (P2)

**Files:**
- Modify: `pi/src/http.rs`

HTTP 상태코드만으로 Task 1이 동작하므로 이 태스크는 독립·선택이다. 계약을 명시화해
Android 로그/향후 분기를 견고하게 한다.

- [ ] **Step 1: 실패 테스트 보강**

`pi/src/http.rs`의 `print_returns_500_when_sink_always_fails` 테스트 끝에 단언 추가:

```rust
        assert_eq!(v.get("code").and_then(|c| c.as_str()), Some("write_failed"));
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `cd pi && cargo test http::tests::print_returns_500_when_sink_always_fails`
Expected: FAIL — `code` 키 없음.

- [ ] **Step 3: print 핸들러 에러 응답에 code 추가**

`http.rs`의 `print` 함수 에러 분기 교체:

```rust
        Err(PrintError::Unavailable(msg)) => (
            StatusCode::SERVICE_UNAVAILABLE,
            Json(serde_json::json!({ "code": "device_unavailable", "error": msg })),
        ),
        Err(PrintError::Write(msg)) => (
            StatusCode::INTERNAL_SERVER_ERROR,
            Json(serde_json::json!({ "code": "write_failed", "error": msg })),
        ),
```

- [ ] **Step 4: 테스트 통과 + 회귀 확인**

Run: `cd pi && cargo test && cargo clippy --all-targets -- -D warnings`
Expected: 전체 PASS, clippy clean.

- [ ] **Step 5: 커밋**

```bash
git add pi/src/http.rs
git commit -m "feat(pi): include machine-readable code in print error responses"
```

---

## Task 7: 최종 회귀 검증

- [ ] **Step 1: 안드로이드 전체 단위 테스트 + 빌드**

Run:
```bash
cd android && JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:testDebugUnitTest :app:assembleDebug
```
Expected: 전체 PASS + BUILD SUCCESSFUL

- [ ] **Step 2: (Task 6 수행 시) Pi 검증**

Run: `cd pi && cargo test && cargo clippy --all-targets -- -D warnings`
Expected: PASS, clippy clean

- [ ] **Step 3: 에뮬레이터 육안 확인 (best-effort, 수동)**

- 결제 완료 탭 → 버튼이 "처리 중…" 스피너로 바뀌는지(느린 네트워크 모사 시 명확).
- Pi 서버 미기동 상태에서 결제 완료 → 홈에 "…프린터 서버에 연결되지 않았어요…" Snackbar +
  [재출력] 노출, 닫기 동작.
- 설정 타일·취소·닫기 버튼이 벡터 아이콘으로 표시되는지.

> 메모리 `a11y-visual-refresh`/`compose-ux-redesign`에 따라 v3는 push/merge 하지 않고
> 브랜치에 커밋만 유지한다.
