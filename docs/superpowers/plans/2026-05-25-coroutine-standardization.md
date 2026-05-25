# 코루틴 표준화 (Coroutine Standardization) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `android/app` 전 영역의 코루틴 사용을 Android 표준으로 통일해, 모든 화면의 비동기 동작 기대값(에러·취소·타임아웃 처리)이 동일하도록 만든다.

**Architecture:** Repository의 suspend 함수는 `withContext(io)`로 main-safe하게 만들고 실패 시 예외를 던진다(sentinel 반환 제거). ViewModel은 `viewModelScope.launchSafely { }` 단일 진입점으로 모든 비동기 작업을 실행하며, 이 확장이 `CancellationException` 재전파·Crashlytics 1회 기록·`onError` 위임을 강제한다. 일회성 이벤트는 `tryEmit`, DI 디스패처/스코프는 타입 한정자로 통일한다.

**Tech Stack:** Kotlin Coroutines 1.10.2, Hilt, Firebase Firestore, JUnit4 + MockK + kotlinx-coroutines-test, Jetpack Compose.

**참조 스펙:** `docs/superpowers/specs/2026-05-25-coroutine-standardization-design.md` (규칙 R1–R7)

---

## 핵심 설계 결정 (모든 Task 공통 전제)

1. **ViewModel에서 `ioDispatcher` 생성자 파라미터를 제거한다.** `viewModelScope.launchSafely { ... }`는 `Dispatchers.Main`에서 돌고, Repository suspend 함수가 내부에서 IO로 전환한다. (단, `applicationScope`는 프린터 작업용이라 유지.)
2. **Repository는 sentinel 대신 예외를 던진다.** `withContext(ioDispatcher)`로 감싸 main-safe하게 만들고, Repository 내부 `e.printStackTrace()`/`recordException`은 제거한다(로깅은 `launchSafely` 단일 지점). 단, best-effort probe(`checkConnection`)는 Boolean 유지. `deleteOrder`는 "주문 없음=false"는 유지하되 실패는 throw.
3. **테스트는 공용 `MainDispatcherRule`로 통일한다.** 인라인 `Dispatchers.setMain/resetMain`을 대체하고, dispatcher 미주입 ViewModel이 테스트 디스패처 위에서 안정적으로 돌게 한다.
4. **Firestore `await()` 임포트 유지.** `kotlinx.coroutines.tasks.await`는 그대로.

**파일 맵 (생성/수정):**
- 생성: `util/CoroutineExtensions.kt`, `di/Qualifiers.kt`, `exception/DataException.kt`, `app/src/test/.../util/MainDispatcherRule.kt`, `app/src/test/.../util/CoroutineExtensionsTest.kt`, `android/docs/coroutine-conventions.md`
- 수정: `di/CoroutineModule.kt`, `data/repository/FirestoreRepository.kt`, `data/repository/MenuRepository.kt`, 7개 ViewModel + 각 테스트

---

## Task 1: 타입 안전 DI 한정자 도입

**Files:**
- Create: `app/src/main/java/eloom/holybean/di/Qualifiers.kt`
- Modify: `app/src/main/java/eloom/holybean/di/CoroutineModule.kt`
- Modify (주입 지점): `data/repository/*`, ViewModel 중 `@Named("ApplicationScope")` 사용처 (OrdersViewModel, ReportViewModel)

- [ ] **Step 1: 한정자 애노테이션 파일 생성**

`Qualifiers.kt`:
```kotlin
package eloom.holybean.di

import javax.inject.Qualifier

@Qualifier @Retention(AnnotationRetention.BINARY) annotation class IoDispatcher
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class PrinterDispatcher
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class AppScope
```

- [ ] **Step 2: CoroutineModule을 타입 한정자로 교체**

`CoroutineModule.kt` 전체를:
```kotlin
package eloom.holybean.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CoroutineModule {

    @Provides @Singleton @IoDispatcher
    fun provideIODispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides @Singleton @PrinterDispatcher
    fun providePrinterDispatcher(): CoroutineDispatcher = Dispatchers.IO.limitedParallelism(2)

    @Provides @Singleton @AppScope
    fun provideApplicationScope(
        @PrinterDispatcher printerDispatcher: CoroutineDispatcher
    ): CoroutineScope = CoroutineScope(SupervisorJob() + printerDispatcher)
}
```

- [ ] **Step 3: ApplicationScope 주입 지점 교체 (OrdersViewModel, ReportViewModel)**

두 파일에서 `import javax.inject.Named` 제거(아직 다른 용도로 안 쓰면), 생성자의 `@Named("ApplicationScope") private val applicationScope: CoroutineScope` → `@eloom.holybean.di.AppScope private val applicationScope: CoroutineScope`. (간단히 `import eloom.holybean.di.AppScope` 후 `@AppScope` 사용.)

> 주의: ViewModel의 `@Named("IO")` 디스패처는 Task 6~12에서 **제거**되므로 여기서는 `@Named("ApplicationScope")`만 교체한다. Repository는 현재 `@Named` 미사용이므로 건드릴 것 없음.

- [ ] **Step 4: 빌드 확인**

Run: `cd android && ./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL (이 시점엔 ViewModel들이 아직 `@Named("IO")`를 안 쓰므로 `@Named` import 잔존 시 경고만; OrdersViewModel/ReportViewModel에서 `@Named` 미사용 import는 제거).

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/eloom/holybean/di
git commit -m "refactor(di): replace stringly-typed @Named with typed coroutine qualifiers"
```

---

## Task 2: 경량 도메인 예외 `DataException`

**Files:**
- Create: `app/src/main/java/eloom/holybean/exception/DataException.kt`

- [ ] **Step 1: 예외 정의 파일 생성**

`DataException.kt`:
```kotlin
package eloom.holybean.exception

/**
 * Repository 계층이 던지는 데이터 예외. ViewModel/Crashlytics가 메시지 문자열이 아니라
 * 타입으로 분기할 수 있게 한다. 과도한 분류는 하지 않는다(YAGNI).
 */
sealed class DataException(message: String?, cause: Throwable?) : Exception(message, cause) {
    /** 서버 ack 등 시간 내 완료 실패(오프라인 등). */
    class Timeout(cause: Throwable? = null) : DataException("data operation timed out", cause)
    /** 그 외 데이터 계층 실패. */
    class Unknown(cause: Throwable?) : DataException(cause?.message, cause)
}
```

- [ ] **Step 2: 빌드 확인**

Run: `cd android && ./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add android/app/src/main/java/eloom/holybean/exception/DataException.kt
git commit -m "feat(exception): add lightweight DataException hierarchy"
```

---

## Task 3: `launchSafely` 확장 + 단위 테스트 (TDD)

**Files:**
- Create: `app/src/main/java/eloom/holybean/util/CoroutineExtensions.kt`
- Create: `app/src/test/kotlin/eloom/holybean/util/MainDispatcherRule.kt`
- Test: `app/src/test/kotlin/eloom/holybean/util/CoroutineExtensionsTest.kt`

- [ ] **Step 1: 공용 MainDispatcherRule 생성**

`app/src/test/kotlin/eloom/holybean/util/MainDispatcherRule.kt`:
```kotlin
package eloom.holybean.util

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/** Dispatchers.Main 을 테스트 디스패처로 바꿔주는 공용 룰. viewModelScope 가 Main 을 쓰므로 필요. */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val dispatcher: CoroutineDispatcher = UnconfinedTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) { Dispatchers.setMain(dispatcher) }
    override fun finished(description: Description) { Dispatchers.resetMain() }
}
```

- [ ] **Step 2: 실패하는 테스트 작성**

`CoroutineExtensionsTest.kt`:
```kotlin
package eloom.holybean.util

import com.google.firebase.crashlytics.FirebaseCrashlytics
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@ExperimentalCoroutinesApi
class CoroutineExtensionsTest {

    @Before fun setUp() {
        mockkStatic(FirebaseCrashlytics::class)
        every { FirebaseCrashlytics.getInstance() } returns mockk(relaxed = true)
    }

    @After fun tearDown() { unmockkAll() }

    @Test fun `runs block on success and does not call onError`() = runTest {
        var ran = false
        var err: Throwable? = null
        launchSafely(onError = { err = it }) { ran = true }
        advanceUntilIdle()
        assertTrue(ran)
        assertNull(err)
    }

    @Test fun `routes ordinary exception to onError`() = runTest {
        var err: Throwable? = null
        launchSafely(onError = { err = it }) { throw IllegalStateException("boom") }
        advanceUntilIdle()
        assertTrue(err is IllegalStateException)
    }

    @Test fun `rethrows CancellationException and does not call onError`() = runTest {
        var onErrorCalled = false
        val started = CompletableDeferred<Unit>()
        val job = launch {
            launchSafely(onError = { onErrorCalled = true }) {
                started.complete(Unit)
                delay(10_000)
            }
        }
        started.await()
        job.cancel()
        advanceUntilIdle()
        assertEquals(false, onErrorCalled)
    }

    @Test fun `TimeoutCancellationException is rethrown not routed to onError`() = runTest {
        var onErrorCalled = false
        launchSafely(onError = { onErrorCalled = true }) {
            withTimeout(1) { delay(100) }
        }
        advanceUntilIdle()
        assertEquals(false, onErrorCalled)
    }
}
```

- [ ] **Step 3: 테스트가 컴파일 실패(미정의)하는지 확인**

Run: `cd android && ./gradlew testDebugUnitTest --tests "eloom.holybean.util.CoroutineExtensionsTest"`
Expected: FAIL — `launchSafely` unresolved reference.

- [ ] **Step 4: `launchSafely` 구현**

`CoroutineExtensions.kt`:
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
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `cd android && ./gradlew testDebugUnitTest --tests "eloom.holybean.util.CoroutineExtensionsTest"`
Expected: PASS (4 tests)

- [ ] **Step 6: Commit**

```bash
git add android/app/src/main/java/eloom/holybean/util/CoroutineExtensions.kt \
        android/app/src/test/kotlin/eloom/holybean/util/MainDispatcherRule.kt \
        android/app/src/test/kotlin/eloom/holybean/util/CoroutineExtensionsTest.kt
git commit -m "feat(util): add launchSafely extension and shared MainDispatcherRule"
```

---

## Task 4: `FirestoreRepository` — 예외 전파 + main-safe

**Files:**
- Modify: `app/src/main/java/eloom/holybean/data/repository/FirestoreRepository.kt`

- [ ] **Step 1: 생성자에 IO 디스패처 주입 + 임포트 정리**

상단 임포트에서 `import com.google.firebase.crashlytics.FirebaseCrashlytics` 제거, 다음 추가:
```kotlin
import eloom.holybean.di.IoDispatcher
import eloom.holybean.exception.DataException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
```
생성자 변경:
```kotlin
class FirestoreRepository @Inject constructor(
    private val db: FirebaseFirestore,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
```

- [ ] **Step 2: 조회 메서드들을 `withContext(io)` + throw 로 전환**

각 메서드의 `return try { ... } catch (e: Exception) { e.printStackTrace(); FirebaseCrashlytics...; <sentinel> }` 패턴을 `withContext(ioDispatcher) { ... }`(try-catch 제거)로 바꾼다. 예:

`getOrderNumber()`:
```kotlin
suspend fun getOrderNumber(): Int = withContext(ioDispatcher) {
    val snap = db.collection(FirestoreSchema.DAY_SUMMARIES).document(today()).get().await()
    (snap.getLong("lastOrderNum") ?: 0L).toInt() + 1
}
```

`getOrdersOfDay()`:
```kotlin
suspend fun getOrdersOfDay(): ArrayList<OrderItem> = withContext(ioDispatcher) {
    val snap = db.collection(FirestoreSchema.DAY_SUMMARIES).document(today()).get().await()
    @Suppress("UNCHECKED_CAST")
    val orders = (snap.get("orders") as? Map<String, Map<String, Any>>) ?: emptyMap()
    val list = orders.entries
        .sortedBy { it.key.toIntOrNull() ?: 0 }
        .map { (num, m) ->
            OrderItem(
                orderId = num.toIntOrNull() ?: 0,
                totalAmount = (m["totalAmount"] as? Number)?.toInt() ?: 0,
                method = m["orderMethod"] as? String ?: "Unknown",
                orderer = m["customerName"] as? String ?: ""
            )
        }
    ArrayList(list)
}
```

`getOrderDetail()`:
```kotlin
suspend fun getOrderDetail(date: String, num: Int): ArrayList<OrdersDetailItem> = withContext(ioDispatcher) {
    val snap = db.collection(FirestoreSchema.ORDERS)
        .document(FirestoreSchema.orderId(date, num)).get().await()
    @Suppress("UNCHECKED_CAST")
    val items = (snap.get("items") as? List<Map<String, Any>>) ?: emptyList()
    ArrayList(items.map {
        OrdersDetailItem(
            name = it["name"] as? String ?: "",
            count = (it["quantity"] as? Number)?.toInt() ?: 0,
            subtotal = (it["subtotal"] as? Number)?.toInt() ?: 0
        )
    })
}
```

`getCreditsList()`:
```kotlin
suspend fun getCreditsList(): ArrayList<CreditItem> = withContext(ioDispatcher) {
    val snap = db.collection(FirestoreSchema.AGGREGATES)
        .document(FirestoreSchema.OPEN_CREDITS_DOC).get().await()
    @Suppress("UNCHECKED_CAST")
    val items = (snap.get("items") as? Map<String, Map<String, Any>>) ?: emptyMap()
    ArrayList(items.values.map {
        CreditItem(
            orderId = (it["orderNum"] as? Number)?.toInt() ?: 0,
            totalAmount = (it["totalAmount"] as? Number)?.toInt() ?: 0,
            date = it["orderDate"] as? String ?: "",
            orderer = it["customerName"] as? String ?: ""
        )
    }.sortedWith(compareBy({ it.date }, { it.orderId })))
}
```

`getReport()`: 본문을 `withContext(ioDispatcher) { ... }`로 감싸고 try-catch 제거, 마지막 줄 `ReportAggregation.combine(rollups)`가 블록 반환값이 되게 한다.

- [ ] **Step 3: `postOrder()` 타임아웃을 DataException.Timeout 으로 변환**

마지막 줄을 교체:
```kotlin
        try {
            withTimeout(POST_ORDER_ACK_TIMEOUT_MS) { batch.commit().await() }  // 서버 ack 대기
        } catch (e: TimeoutCancellationException) {
            throw DataException.Timeout(e)   // 정상 취소가 아니라 저장 실패로 다룬다(launchSafely 가 재전파하지 않도록)
        }
```
`postOrder` 자체는 디스패처 전환이 필요하면 `withContext(ioDispatcher)`로 감쌀 수 있으나, batch 구성은 CPU 작업이고 commit은 비동기이므로 전체를 `withContext(ioDispatcher)`로 감싼다.

- [ ] **Step 4: `setCreditOrderPaid()` / `deleteOrder()` 전환**

`setCreditOrderPaid()`: 본문을 `withContext(ioDispatcher) { ... }`로 감싸고 try-catch + Crashlytics 제거(예외 전파). `return` early(`if (!snap.exists()) return`)는 `return@withContext`로.

`deleteOrder()`: `withContext(ioDispatcher) { ... }`로 감싸고 try-catch 제거. "주문 없음"은 `false` 유지(`if (!snap.exists()) return@withContext false`), 성공은 `true`. 실패(예외)는 전파.

- [ ] **Step 5: `checkConnection()` 은 best-effort 유지(주석 추가)**

변경 없음. 단 주석 보강:
```kotlin
/** 개발자도구용 best-effort 연결 점검. 실패도 결과이므로 예외를 던지지 않고 false 반환. [R6] */
suspend fun checkConnection(): Boolean = withContext(ioDispatcher) {
    try {
        withTimeout(3000) {
            db.collection(FirestoreSchema.DAY_SUMMARIES).document(today()).get().await()
        }
        true
    } catch (e: Exception) {
        false
    }
}
```

- [ ] **Step 6: 빌드 확인**

Run: `cd android && ./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: 기존 firestore 관련 단위 테스트 회귀 확인**

Run: `cd android && ./gradlew testDebugUnitTest --tests "eloom.holybean.data.*"`
Expected: PASS (OrderAggregationTest, ReportAggregationTest, MenuRepositoryCacheTest — Repository 직접 인스턴스화 테스트는 없으므로 영향 없음). 실패 시 생성자 시그니처 변경 영향 파일 확인.

- [ ] **Step 8: Commit**

```bash
git add android/app/src/main/java/eloom/holybean/data/repository/FirestoreRepository.kt
git commit -m "refactor(repo): FirestoreRepository throws on failure and is main-safe via withContext"
```

---

## Task 5: `MenuRepository` — main-safe (`withContext`)

**Files:**
- Modify: `app/src/main/java/eloom/holybean/data/repository/MenuRepository.kt`
- Test 회귀: `app/src/test/kotlin/eloom/holybean/data/repository/MenuRepositoryCacheTest.kt`

> MenuRepository 는 이미 예외를 삼키지 않는다(throw). main-safety(`withContext`)만 추가한다.

- [ ] **Step 1: IO 디스패처 주입 + 임포트**

상단에 추가:
```kotlin
import eloom.holybean.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
```
생성자:
```kotlin
class MenuRepository @Inject constructor(
    private val db: FirebaseFirestore,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
```

- [ ] **Step 2: suspend 함수 본문을 `withContext(ioDispatcher)`로 감싸기**

`getMenuListSync`, `writeAll`, `saveMenuOrders`, `updateSpecificMenu`, `addMenu`, `isValidMenuName`, `getNextAvailableIdForCategory`, `getNextAvailablePlacementForCategory`를 각각 `withContext(ioDispatcher) { ... }`로 감싼다. 예:
```kotlin
suspend fun getMenuListSync(): List<MenuItem> = withContext(ioDispatcher) {
    parse(menuDoc().get().await().get("items")).sortedBy { it.id }
        .also { cachedMenu = it }
}

private suspend fun writeAll(items: List<MenuItem>) = withContext(ioDispatcher) {
    menuDoc().set(mapOf("items" to serialize(items), "updatedAt" to FieldValue.serverTimestamp())).await()
    Unit
}
```
(다른 함수들도 동일 패턴. `getMenuList()` callbackFlow 는 변경 없음 — 이미 `close(err)`로 에러 전파.)

- [ ] **Step 3: MenuRepositoryCacheTest 생성자 호출 갱신**

`MenuRepositoryCacheTest.kt`에서 `MenuRepository(db)` 형태의 생성자 호출에 테스트 디스패처를 추가한다. 파일 상단에 `import kotlinx.coroutines.test.UnconfinedTestDispatcher` 추가 후, 생성자 호출을 `MenuRepository(db, UnconfinedTestDispatcher())`로 변경. (테스트가 `runTest { }` 내부면 `UnconfinedTestDispatcher(testScheduler)` 사용; 단순 캐시 테스트면 `UnconfinedTestDispatcher()`로 충분.)

- [ ] **Step 4: 빌드 + 테스트**

Run: `cd android && ./gradlew testDebugUnitTest --tests "eloom.holybean.data.repository.MenuRepositoryCacheTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/eloom/holybean/data/repository/MenuRepository.kt \
        android/app/src/test/kotlin/eloom/holybean/data/repository/MenuRepositoryCacheTest.kt
git commit -m "refactor(repo): make MenuRepository suspend functions main-safe via withContext"
```

---

## Task 6: `CreditsViewModel` — launchSafely 전환

**Files:**
- Modify: `app/src/main/java/eloom/holybean/ui/credits/CreditsViewModel.kt`
- Modify: `app/src/test/kotlin/eloom/holybean/ui/credits/CreditsViewModelTest.kt`

- [ ] **Step 1: 생성자에서 dispatcher 제거 + 임포트 정리**

`import kotlinx.coroutines.CoroutineDispatcher`, `import javax.inject.Named` 제거. `import eloom.holybean.util.launchSafely` 추가. 생성자:
```kotlin
class CreditsViewModel @Inject constructor(
    private val firestoreRepository: FirestoreRepository,
) : ViewModel() {
```

- [ ] **Step 2: 메서드들을 launchSafely 로 전환**

`loadCredits()`:
```kotlin
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

`fetchOrderDetail()` 의 가드 분기(`selectedOrderNumber == 0`)는 `viewModelScope.launch(dispatcher)` → `viewModelScope.launchSafely(onError = {}) { ... }` 로 바꾸되, 단순 토스트는 `viewModelScope.launchSafely(onError = {}) { _uiEvent.tryEmit(...) }` 보다 직접 `_uiEvent.tryEmit(...)` 호출로 단순화 가능(launch 불필요). 가드 토스트는 다음으로 통일:
```kotlin
fun fetchOrderDetail() {
    val currentState = _uiState.value
    if (currentState.selectedOrderNumber == 0) {
        _uiEvent.tryEmit(CreditsUiEvent.ShowToast("주문을 선택해주세요"))
        return
    }
    viewModelScope.launchSafely(onError = { e ->
        _uiEvent.tryEmit(CreditsUiEvent.ShowToast("주문 조회 중 오류가 발생했습니다: ${e.message}"))
    }) {
        val fetched = firestoreRepository.getOrderDetail(currentState.selectedOrderDate, currentState.selectedOrderNumber)
        if (fetched.isEmpty()) {
            _uiEvent.tryEmit(CreditsUiEvent.ShowToast("주문 내역이 없습니다."))
        } else {
            _uiState.update { it.copy(orderDetails = fetched) }
        }
    }
}
```

`handleDeleteButton()`: 가드 토스트 동일하게 직접 `tryEmit`, 본문은 launchSafely:
```kotlin
fun handleDeleteButton() {
    val currentState = _uiState.value
    if (currentState.selectedOrderNumber == 0) {
        _uiEvent.tryEmit(CreditsUiEvent.ShowToast("주문을 선택해주세요"))
        return
    }
    viewModelScope.launchSafely(onError = { e ->
        _uiEvent.tryEmit(CreditsUiEvent.ShowToast("외상 처리 중 오류가 발생했습니다: ${e.message}"))
    }) {
        firestoreRepository.setCreditOrderPaid(currentState.selectedOrderDate, currentState.selectedOrderNumber)
        _uiEvent.tryEmit(CreditsUiEvent.ShowToast("외상이 성공적으로 처리되었습니다."))
        _uiEvent.tryEmit(CreditsUiEvent.RefreshCredits)
    }
}
```

> 가드 분기를 `launch` 없이 직접 `tryEmit` 으로 바꾸므로, 기존 테스트의 "no order selected" 케이스도 통과한다(tryEmit 은 동기, 버퍼 발행).

- [ ] **Step 3: 테스트 갱신 — MainDispatcherRule + 생성자 인자 제거**

`CreditsViewModelTest.kt`:
- 임포트 추가: `import eloom.holybean.util.MainDispatcherRule`, `import com.google.firebase.crashlytics.FirebaseCrashlytics`, `import io.mockk.mockkStatic`.
- 인라인 `Dispatchers.setMain/resetMain` 제거하고 룰 추가:
```kotlin
@get:Rule val mainDispatcherRule = MainDispatcherRule()
```
- `setUp()`에서 `Dispatchers.setMain(testDispatcher)` 줄 삭제, 대신 Crashlytics 정적 모킹 추가:
```kotlin
mockkStatic(FirebaseCrashlytics::class)
every { FirebaseCrashlytics.getInstance() } returns mockk(relaxed = true)
```
- 모든 `CreditsViewModel(firestoreRepository, testDispatcher)` / `CreditsViewModel(firestoreRepository, dispatcher)` 호출에서 dispatcher 인자 제거 → `CreditsViewModel(firestoreRepository)`.
- `tearDown()`의 `Dispatchers.resetMain()` 제거(룰이 처리), `clearAllMocks()` 뒤 `unmockkAll()` 추가.

> 별도 `dispatcher = UnconfinedTestDispatcher(testScheduler)`로 만들던 테스트들은 이제 `runTest` 의 스케줄러가 Main(룰의 UnconfinedTestDispatcher)과 다를 수 있다. 안전하게: 이벤트 수집 `launch(dispatcher)` 를 `launch` (기본 = runTest 컨텍스트)로 바꾸고 `advanceUntilIdle()` 유지. UnconfinedTestDispatcher 기반이라 즉시 실행되어 기존 단언 유지됨.

- [ ] **Step 4: 테스트 실행**

Run: `cd android && ./gradlew testDebugUnitTest --tests "eloom.holybean.ui.credits.CreditsViewModelTest"`
Expected: PASS (모든 케이스). 실패 시 이벤트 수집 타이밍(`launch` 디스패처) 조정.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/eloom/holybean/ui/credits/CreditsViewModel.kt \
        android/app/src/test/kotlin/eloom/holybean/ui/credits/CreditsViewModelTest.kt
git commit -m "refactor(credits): adopt launchSafely, drop injected dispatcher"
```

---

## Task 7: `ReportViewModel` — launchSafely 전환

**Files:**
- Modify: `app/src/main/java/eloom/holybean/ui/report/ReportViewModel.kt`
- Modify: `app/src/test/kotlin/eloom/holybean/ui/report/ReportViewModelTest.kt`

- [ ] **Step 1: 생성자에서 IO dispatcher 제거(applicationScope 는 유지)**

`import kotlinx.coroutines.CoroutineDispatcher` 제거(여전히 `CoroutineScope`는 필요). `import eloom.holybean.util.launchSafely`, `import eloom.holybean.di.AppScope` 추가. 생성자:
```kotlin
class ReportViewModel @Inject constructor(
    private val firestoreRepository: FirestoreRepository,
    @AppScope private val applicationScope: CoroutineScope,
    private val piPrintClient: PiPrintClient,
    private val reportPrinter: ReportPrinter,
) : ViewModel() {
```

- [ ] **Step 2: `loadReportData()` 전환**

```kotlin
fun loadReportData(startDate: String, endDate: String) {
    if (!isValidDateRange(startDate, endDate)) {
        _uiEvent.tryEmit(ReportUiEvent.ShowError("잘못된 날짜 범위입니다"))
        return
    }
    viewModelScope.launchSafely(onError = { e ->
        _uiState.update { it.copy(isLoading = false) }
        _uiEvent.tryEmit(ReportUiEvent.ShowError("리포트를 불러오는데 실패했습니다: ${e.localizedMessage}"))
    }) {
        _uiState.update { it.copy(isLoading = true, reportTitle = "$startDate ~ $endDate") }
        val report = firestoreRepository.getReport(startDate, endDate)
        _uiState.update { it.copy(reportDetailData = report.menuSales, reportData = report.paymentSales, isLoading = false) }
    }
}
```

- [ ] **Step 3: `printReport()` 의 applicationScope 작업을 launchSafely 로**

가드(`summary.isEmpty()...`)는 직접 `_uiEvent.tryEmit(ReportUiEvent.ShowError("인쇄할 데이터가 없습니다"))` + return 으로 단순화. 인쇄 본문:
```kotlin
applicationScope.launchSafely(onError = { e ->
    _uiEvent.tryEmit(ReportUiEvent.ShowError("인쇄 실패 : ${e.localizedMessage}"))
}) {
    val dateParts = title.split(" ~ ")
    val printerDTO = PrinterDTO(dateParts[0], dateParts[1], summary, details)
    piPrintClient.print(reportPrinter.makeCommands(printerDTO))
    _uiEvent.tryEmit(ReportUiEvent.ShowToast("리포트 인쇄가 완료되었습니다"))
}
```

- [ ] **Step 4: 테스트 갱신**

`ReportViewModelTest.kt`: HomeViewModel/Credits 와 동일 패턴 — 인라인 setMain 제거 → `MainDispatcherRule`, Crashlytics 정적 모킹 추가, `ReportViewModel(...)` 생성자 호출에서 `testDispatcher` 인자 제거(applicationScope 인자는 유지: `CoroutineScope(SupervisorJob() + testDispatcher)`). 기존 단언 유지.

- [ ] **Step 5: 테스트 실행**

Run: `cd android && ./gradlew testDebugUnitTest --tests "eloom.holybean.ui.report.ReportViewModelTest"`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add android/app/src/main/java/eloom/holybean/ui/report/ReportViewModel.kt \
        android/app/src/test/kotlin/eloom/holybean/ui/report/ReportViewModelTest.kt
git commit -m "refactor(report): adopt launchSafely, drop injected io dispatcher"
```

---

## Task 8: `OrdersViewModel` — launchSafely 전환

**Files:**
- Modify: `app/src/main/java/eloom/holybean/ui/orderlist/OrdersViewModel.kt`
- Modify: `app/src/test/kotlin/eloom/holybean/ui/orderlist/OrdersViewModelTest.kt`

- [ ] **Step 1: 생성자에서 IO dispatcher 제거(applicationScope 유지)**

`import kotlinx.coroutines.CoroutineDispatcher` 제거, `import javax.inject.Named` 제거, `import eloom.holybean.util.launchSafely` + `import eloom.holybean.di.AppScope` 추가. 생성자:
```kotlin
class OrdersViewModel @Inject constructor(
    private val firestoreRepository: FirestoreRepository,
    @AppScope private val applicationScope: CoroutineScope,
    private val piPrintClient: PiPrintClient,
    private val ordersPrinter: OrdersPrinter,
    private val reportPrinter: ReportPrinter,
) : ViewModel() {
```

- [ ] **Step 2: `loadOrdersOfDay()` 전환**

```kotlin
fun loadOrdersOfDay() {
    viewModelScope.launchSafely(onError = { e ->
        _uiState.update { it.copy(isLoading = false) }
        _uiEvent.tryEmit(OrdersUiEvent.ShowToast("주문 목록을 불러오는 중 오류가 발생했습니다: ${e.message}"))
    }) {
        _uiState.update { it.copy(isLoading = true) }
        val ordersList = firestoreRepository.getOrdersOfDay()
        _uiState.update { it.copy(ordersList = ordersList, isLoading = false) }
        if (ordersList.isNotEmpty()) {
            val first = ordersList.first()
            selectOrder(first.orderId, first.totalAmount)
        } else {
            _uiState.update { it.copy(selectedOrderNumber = 0, selectedOrderTotal = 0, orderDetails = emptyList()) }
        }
    }
}
```

- [ ] **Step 3: `fetchOrderDetail()` 전환**

```kotlin
fun fetchOrderDetail(orderNumber: Int) {
    viewModelScope.launchSafely(onError = { e ->
        _uiEvent.tryEmit(OrdersUiEvent.ShowToast("주문 조회 중 오류가 발생했습니다: ${e.message}"))
    }) {
        val fetched = firestoreRepository.getOrderDetail(getCurrentDate(), orderNumber)
        if (fetched.isEmpty()) {
            _uiEvent.tryEmit(OrdersUiEvent.ShowToast("주문 내역이 없습니다."))
        } else {
            _uiState.update { it.copy(orderDetails = fetched) }
        }
    }
}
```

- [ ] **Step 4: `reprint()` — 가드 직접 tryEmit, 인쇄는 applicationScope.launchSafely**

```kotlin
fun reprint() {
    val currentState = _uiState.value
    if (currentState.orderDetails.isEmpty()) {
        _uiEvent.tryEmit(OrdersUiEvent.ShowToast("주문 조회 후 클릭해주세요"))
        return
    }
    val commands = ordersPrinter.makeCommands(currentState.selectedOrderNumber, currentState.orderDetails.toList())
    // Printer I/O - ViewModel 생명주기와 독립(사용자가 화면 떠나도 인쇄 완료)
    applicationScope.launchSafely(onError = { e ->
        _uiEvent.tryEmit(OrdersUiEvent.ShowToast("Printer error: ${e.message}"))
    }) {
        piPrintClient.print(commands)
    }
}
```

- [ ] **Step 5: `deleteOrder()` 전환 ("주문 없음"=false 유지)**

가드 직접 tryEmit + return. 본문:
```kotlin
viewModelScope.launchSafely(onError = { e ->
    _uiState.update { it.copy(deleteStatus = DeleteStatus.Error("오류가 발생했습니다. 다시 시도해주세요.")) }
}) {
    _uiState.update { it.copy(deleteStatus = DeleteStatus.Loading) }
    val deleted = firestoreRepository.deleteOrder(getCurrentDate(), currentState.selectedOrderNumber)
    if (deleted) {
        _uiState.update { it.copy(deleteStatus = DeleteStatus.Success) }
        _uiEvent.tryEmit(OrdersUiEvent.ShowToast("주문이 성공적으로 삭제되었습니다."))
        _uiEvent.tryEmit(OrdersUiEvent.RefreshOrders)
        loadTodaySummary()
    } else {
        _uiState.update { it.copy(deleteStatus = DeleteStatus.Error("주문 삭제에 실패했습니다.")) }
    }
}
```

- [ ] **Step 6: `loadTodaySummary()` 전환 (runCatching → launchSafely)**

```kotlin
fun loadTodaySummary() {
    viewModelScope.launchSafely(onError = { e ->
        _uiEvent.tryEmit(OrdersUiEvent.ShowToast("매출 요약을 불러오지 못했습니다: ${e.message}"))
    }) {
        val today = getCurrentDate()
        val report = firestoreRepository.getReport(today, today)
        val orders = firestoreRepository.getOrdersOfDay()
        _uiState.update { it.copy(todaySummary = TodaySummary(
            totalSales = report.paymentSales["총합"] ?: 0,
            orderCount = orders.size,
            drinkCount = report.menuSales.filter { it.name != "쿠폰" }.sumOf { it.quantity },
        )) }
    }
}
```

- [ ] **Step 7: `printTodayReport()` 전환 (applicationScope.launchSafely)**

```kotlin
fun printTodayReport() {
    applicationScope.launchSafely(onError = { e ->
        _uiEvent.tryEmit(OrdersUiEvent.ShowToast("보고서 출력 실패: ${e.message}"))
    }) {
        val today = getCurrentDate()
        val report = firestoreRepository.getReport(today, today)
        val dto = PrinterDTO(today, today, report.paymentSales, report.menuSales)
        piPrintClient.print(reportPrinter.makeCommands(dto))
        _uiEvent.tryEmit(OrdersUiEvent.ShowToast("보고서 출력 완료"))
    }
}
```

- [ ] **Step 8: 테스트 갱신**

`OrdersViewModelTest.kt`:
- 인라인 setMain 제거 → `@get:Rule val mainDispatcherRule = MainDispatcherRule()`; Crashlytics 정적 모킹 추가.
- `createViewModelWithPrinter(...)`와 `setUp()`의 `OrdersViewModel(firestoreRepository, testDispatcher, CoroutineScope(...), ...)` 에서 `testDispatcher`(두 번째 인자) 제거 → `OrdersViewModel(firestoreRepository, CoroutineScope(SupervisorJob() + testDispatcher), client, printer, report)`.
- `tearDown()`의 `Dispatchers.resetMain()` 제거, `unmockkAll()` 추가.
- 기존 단언은 모두 유지 가능(동작 동일). `reprint`/`printTodayReport`가 applicationScope(=`CoroutineScope(SupervisorJob()+testDispatcher)`)에서 도므로 `advanceUntilIdle()` 후 검증 그대로 동작.

- [ ] **Step 9: 테스트 실행**

Run: `cd android && ./gradlew testDebugUnitTest --tests "eloom.holybean.ui.orderlist.OrdersViewModelTest"`
Expected: PASS (전 케이스)

- [ ] **Step 10: Commit**

```bash
git add android/app/src/main/java/eloom/holybean/ui/orderlist/OrdersViewModel.kt \
        android/app/src/test/kotlin/eloom/holybean/ui/orderlist/OrdersViewModelTest.kt
git commit -m "refactor(orders): adopt launchSafely across loads/delete/print, drop io dispatcher"
```

---

## Task 9: `MenuManagementViewModel` — launchSafely 전환

**Files:**
- Modify: `app/src/main/java/eloom/holybean/ui/menumanagement/MenuManagementViewModel.kt`
- Modify: `app/src/test/kotlin/eloom/holybean/ui/menumanagement/MenuManagementViewModelTest.kt`

- [ ] **Step 1: 생성자에서 dispatcher 제거 + 임포트**

`import kotlinx.coroutines.CoroutineDispatcher`, `import javax.inject.Named` 제거, `import eloom.holybean.util.launchSafely` 추가. 생성자:
```kotlin
class MenuManagementViewModel @Inject constructor(
    private val menuRepository: MenuRepository,
) : ViewModel() {
```

- [ ] **Step 2: `loadMenuList()` — Flow 수집을 launchSafely 로 감싸기**

```kotlin
private fun loadMenuList() {
    viewModelScope.launchSafely(onError = { e ->
        _uiState.update { it.copy(isLoading = false) }
        _uiEvent.tryEmit(UiEvent.ShowToast("Error loading menu: ${e.message}"))
    }) {
        _uiState.update { it.copy(isLoading = true) }
        menuRepository.getMenuList()
            .map { list -> list.sortedBy { it.order } }
            .collect { menuList ->
                _uiState.update { it.copy(allMenuItems = menuList, isLoading = false) }
                filterMenuByCategory(_uiState.value.selectedCategoryIndex)
            }
    }
}
```
(Flow 의 `.catch{}`는 제거하고 launchSafely 의 onError 로 일원화. 단, 기존 테스트가 ".catch 후 emit(emptyList())" 동작에 의존하면 onError 로 동일 토스트가 나오는지 확인.)

- [ ] **Step 3: `saveMenuOrder()`, `addMenu()`, `updateMenu()`, `toggleMenuInUse()` 전환**

각 `viewModelScope.launch(dispatcher) { ... }` → `viewModelScope.launchSafely(onError = { e -> _uiEvent.tryEmit(UiEvent.ShowToast("작업 중 오류: ${e.message}")) }) { ... }`. 본문은 동일. 예 `saveMenuOrder()`:
```kotlin
fun saveMenuOrder() {
    viewModelScope.launchSafely(onError = { e ->
        _uiEvent.tryEmit(UiEvent.ShowToast("저장 중 오류: ${e.message}"))
    }) {
        val category = _uiState.value.selectedCategoryIndex
        val itemsToSave = _uiState.value.allMenuItems.filter { it.id / 1000 == category }
        menuRepository.saveMenuOrders(itemsToSave)
        _uiEvent.tryEmit(UiEvent.ShowToast("저장되었습니다."))
        _uiEvent.tryEmit(UiEvent.RefreshMenu)
    }
}
```
`getNextAvailableId()/getNextAvailablePlacement()`는 suspend 함수로 유지(호출부가 launchSafely/LaunchedEffect 내부).

- [ ] **Step 4: 테스트 갱신**

`MenuManagementViewModelTest.kt`: 인라인 setMain → `MainDispatcherRule`, Crashlytics 정적 모킹, `MenuManagementViewModel(menuRepository, testDispatcher)` → `MenuManagementViewModel(menuRepository)`. 기존 단언 유지. (단, 메뉴 로드 에러 테스트가 있으면 onError 토스트 메시지 일치 확인.)

- [ ] **Step 5: 테스트 실행**

Run: `cd android && ./gradlew testDebugUnitTest --tests "eloom.holybean.ui.menumanagement.MenuManagementViewModelTest"`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add android/app/src/main/java/eloom/holybean/ui/menumanagement/MenuManagementViewModel.kt \
        android/app/src/test/kotlin/eloom/holybean/ui/menumanagement/MenuManagementViewModelTest.kt
git commit -m "refactor(menu): adopt launchSafely, drop injected dispatcher"
```

---

## Task 10: `StartupViewModel` — launchSafely 전환 + sentinel 제거 반영

**Files:**
- Modify: `app/src/main/java/eloom/holybean/ui/startup/StartupViewModel.kt`
- Modify: `app/src/test/kotlin/eloom/holybean/ui/startup/StartupViewModelTest.kt`

- [ ] **Step 1: 생성자에서 dispatcher 제거 + 임포트**

`import kotlinx.coroutines.CoroutineDispatcher`, `import javax.inject.Named` 제거, `import eloom.holybean.util.launchSafely` 추가. 생성자:
```kotlin
class StartupViewModel @Inject constructor(
    private val menuRepository: MenuRepository,
    private val firestoreRepository: FirestoreRepository,
    private val piPrintClient: PiPrintClient,
) : ViewModel() {
```

- [ ] **Step 2: `check()` / `loadData()` / `checkPrinter()` 전환**

`getOrderNumber()`가 이제 실패 시 throw 하므로 `> 0` 의미가 단순해진다(성공이면 항상 양수). `loadData()`는 예외→Failed 로 onError 에서 매핑:
```kotlin
fun check() {
    _uiState.update { it.copy(data = StepStatus.Loading, printer = StepStatus.Loading) }
    viewModelScope.launchSafely(onError = {
        _uiState.update { it.copy(data = StepStatus.Failed) }
    }) {
        menuRepository.getMenuListSync()                 // 실패 시 throw → onError → Failed
        val ok = firestoreRepository.getOrderNumber() > 0
        _uiState.update { it.copy(data = if (ok) StepStatus.Success else StepStatus.Failed) }
    }
    viewModelScope.launchSafely(onError = {
        _uiState.update { it.copy(printer = StepStatus.Failed) }
    }) {
        val ok = piPrintClient.checkHealth()             // checkHealth 는 throw 안 함(Boolean)
        _uiState.update { it.copy(printer = if (ok) StepStatus.Success else StepStatus.Failed) }
    }
}
```
(`loadData()`/`checkPrinter()` private suspend 메서드는 인라인하거나 그대로 두고 launchSafely 본문에서 호출. 위는 인라인 버전.)

- [ ] **Step 3: 테스트 갱신 — sentinel 테스트를 throw 로 변경**

`StartupViewModelTest.kt`:
- 상단 임포트: `import eloom.holybean.util.MainDispatcherRule`, `import com.google.firebase.crashlytics.FirebaseCrashlytics`, `import io.mockk.every`, `import io.mockk.mockkStatic`, `import io.mockk.unmockkAll`, `import org.junit.After`, `import org.junit.Before`, `import org.junit.Rule`.
- 룰/모킹 추가:
```kotlin
@get:Rule val mainDispatcherRule = MainDispatcherRule()
@Before fun setUp() {
    mockkStatic(FirebaseCrashlytics::class)
    every { FirebaseCrashlytics.getInstance() } returns mockk(relaxed = true)
}
@After fun tearDown() { unmockkAll() }
```
- `vm()`에서 dispatcher 인자 제거: `private fun vm() = StartupViewModel(menu, firestore, pi)`.
- 테스트 `order number sentinel marks data failed`를 변경:
```kotlin
@Test fun `order number failure marks data failed`() = runTest {
    coEvery { menu.getMenuListSync() } returns emptyList()
    coEvery { firestore.getOrderNumber() } throws RuntimeException("net")
    coEvery { pi.checkHealth() } returns true
    val sut = vm()
    advanceUntilIdle()
    assertEquals(StepStatus.Failed, sut.uiState.value.data)
}
```
- 나머지 테스트는 그대로(menu throw 케이스 등) 통과.

- [ ] **Step 4: 테스트 실행**

Run: `cd android && ./gradlew testDebugUnitTest --tests "eloom.holybean.ui.startup.StartupViewModelTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/eloom/holybean/ui/startup/StartupViewModel.kt \
        android/app/src/test/kotlin/eloom/holybean/ui/startup/StartupViewModelTest.kt
git commit -m "refactor(startup): adopt launchSafely, handle repository throw instead of sentinel"
```

---

## Task 11: `DevToolsViewModel` — launchSafely 전환

**Files:**
- Modify: `app/src/main/java/eloom/holybean/ui/settings/DevToolsViewModel.kt`
- Modify: `app/src/test/kotlin/eloom/holybean/ui/settings/DevToolsViewModelTest.kt`

- [ ] **Step 1: 생성자에서 dispatcher 제거 + 임포트**

`import kotlinx.coroutines.CoroutineDispatcher`, `import javax.inject.Named` 제거, `import eloom.holybean.util.launchSafely` 추가. 생성자:
```kotlin
class DevToolsViewModel @Inject constructor(
    private val piPrintClient: PiPrintClient,
    private val firestoreRepository: FirestoreRepository,
    private val networkStatusProvider: NetworkStatusProvider,
) : ViewModel() {
```

- [ ] **Step 2: `refresh()` / `testPrint()` 전환**

`refresh()`는 각 단계가 best-effort(checkHealth/checkConnection 은 throw 안 함, current()는 동기)지만, 예기치 못한 예외 대비 launchSafely 로 감싼다:
```kotlin
fun refresh() {
    viewModelScope.launchSafely(onError = {
        _uiEvent.tryEmit(DevToolsUiEvent.ShowToast("진단 중 오류가 발생했습니다"))
    }) {
        val net = networkStatusProvider.current()
        _uiState.update { it.copy(networkOk = net.connected, networkInfo = net.info) }
        val start = System.currentTimeMillis()
        val printerHealthy = piPrintClient.checkHealth()
        val latency = System.currentTimeMillis() - start
        _uiState.update { it.copy(printerOk = printerHealthy, printerLatencyMs = latency) }
        val fs = firestoreRepository.checkConnection()
        _uiState.update { it.copy(firestoreOk = fs) }
    }
}

fun testPrint() {
    viewModelScope.launchSafely(onError = { e ->
        _uiEvent.tryEmit(DevToolsUiEvent.ShowToast("테스트 출력 실패: ${e.message}"))
    }) {
        piPrintClient.printTestReceipt()
        _uiEvent.tryEmit(DevToolsUiEvent.ShowToast("테스트 영수증을 출력했습니다"))
    }
}
```

- [ ] **Step 3: 테스트 갱신**

`DevToolsViewModelTest.kt`: 기존에 setMain 을 쓰는지 확인 후, `MainDispatcherRule` + Crashlytics 모킹으로 통일. `DevToolsViewModel(...)` 생성자 호출에서 `ioDispatcher` 인자 제거. 기존 단언 유지(성공/실패 토스트 메시지 동일).

- [ ] **Step 4: 테스트 실행**

Run: `cd android && ./gradlew testDebugUnitTest --tests "eloom.holybean.ui.settings.DevToolsViewModelTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/eloom/holybean/ui/settings/DevToolsViewModel.kt \
        android/app/src/test/kotlin/eloom/holybean/ui/settings/DevToolsViewModelTest.kt
git commit -m "refactor(devtools): adopt launchSafely, drop injected dispatcher"
```

---

## Task 12: `HomeViewModel` — launchSafely 전환 (가장 복잡, 마지막)

**Files:**
- Modify: `app/src/main/java/eloom/holybean/ui/home/HomeViewModel.kt`
- Modify: `app/src/test/kotlin/eloom/holybean/ui/home/HomeViewModelTest.kt`

- [ ] **Step 1: 생성자에서 dispatcher 제거 + 임포트 정리**

`import kotlinx.coroutines.CoroutineDispatcher`, `import javax.inject.Named`, `import com.google.firebase.crashlytics.FirebaseCrashlytics`(아래서 custom key 때문에 일부 유지 필요 → 유지), `import kotlinx.coroutines.CancellationException`(launchSafely 가 처리하므로 제거 가능) 정리. `import eloom.holybean.util.launchSafely` 추가. 생성자:
```kotlin
class HomeViewModel @Inject constructor(
    private val firestoreRepository: FirestoreRepository,
    private val menuRepository: MenuRepository,
    private val piPrintClient: PiPrintClient,
    private val homePrinter: HomePrinter,
) : ViewModel() {
```

- [ ] **Step 2: 단순 메서드들 전환 (`init`, `refreshOrderNumber`, `onCategorySelected`, `addToBasket`, `deleteFromBasket`, `addCoupon`)**

이들 본문은 Repository 호출이 거의 없거나(`getMenuListSync`/`getOrderNumber`) 순수 상태 변경이다. `viewModelScope.launch(ioDispatcher) { ... }` → `viewModelScope.launchSafely(onError = {}) { ... }` (onError 비움 — 메뉴/장바구니 조작 실패는 무시 가능하나 Crashlytics 는 기록됨). `addCoupon`/`onOrderConfirmed`의 가드 토스트(`_uiEvent.emit(...)`)는 직접 `_uiEvent.tryEmit(...)`로 바꿔 launch 제거:
```kotlin
fun addCoupon(amount: Int) {
    if (amount <= 0) {
        _uiEvent.tryEmit(UiEvent.ShowToast("올바른 금액이 아닙니다"))
        return
    }
    viewModelScope.launchSafely(onError = {}) {
        val currentBasket = _uiState.value.basketItems.toMutableList()
        currentBasket.add(CartItem(999, "쿠폰", amount, 1, amount))
        val total = currentBasket.sumOf { it.count * it.price }
        _uiState.update { it.copy(basketItems = currentBasket, totalPrice = total) }
    }
}
```
`init`/`refreshOrderNumber`/`onCategorySelected`/`addToBasket`/`deleteFromBasket` 동일 패턴(`launchSafely(onError = {}) { ... }`).

- [ ] **Step 3: `onOrderConfirmed` 가드 토스트 tryEmit 화**

```kotlin
fun onOrderConfirmed(data: Order, takeOption: String) {
    if (data.orderNum <= 0) {
        _uiEvent.tryEmit(UiEvent.ShowToast("주문번호를 불러오지 못했습니다. 다시 시도해 주세요."))
        return
    }
    if (_uiState.value.isSubmitting) return
    lastOrder = data to takeOption
    orderSaved = false
    printDone = false
    _uiState.update { it.copy(isSubmitting = true, submitError = null) }
    runSubmission(data, takeOption)
}
```

- [ ] **Step 4: `completeAndNavigate` 의 emit → tryEmit**

```kotlin
private suspend fun completeAndNavigate() {
    val nextOrderId = firestoreRepository.getOrderNumber()
    _uiState.update { it.copy(basketItems = emptyList(), totalPrice = 0, orderId = nextOrderId, submitError = null) }
    _uiEvent.tryEmit(UiEvent.NavigateHome)
}
```

- [ ] **Step 5: `runSubmission` 을 launchSafely + 차등 onError 로 전환 (finally 보존)**

```kotlin
private fun runSubmission(data: Order, takeOption: String) {
    viewModelScope.launchSafely(onError = { e ->
        when (e) {
            is PrintServerException -> {
                _uiState.update { it.copy(submitError = SubmitError.PrintFailed(e.reason, ++submitSeq)) }
                FirebaseCrashlytics.getInstance().apply {
                    setCustomKey("orderNum", data.orderNum)
                    setCustomKey("print_reason", e.reason.name)
                }
            }
            else -> _uiState.update { it.copy(submitError = SubmitError.SaveFailed(++submitSeq)) }
            // DataException.Timeout 등 저장 실패는 모두 SaveFailed 로 귀결
        }
    }) {
        try {
            coroutineScope {
                val printDeferred = async { if (!printDone) { printReceipt(data, takeOption); printDone = true } }
                if (!orderSaved) {
                    firestoreRepository.postOrder(data)
                    orderSaved = true
                }
                printDeferred.await()
            }
            completeAndNavigate()
        } finally {
            _uiState.update { it.copy(isSubmitting = false) }
        }
    }
}
```
> 주의: `launchSafely` 의 catch 가 Crashlytics `recordException(e)` 를 이미 호출하므로 onError 에서는 중복 호출하지 않는다(custom key 만 설정). 기존의 `catch (TimeoutCancellationException)` 는 더 이상 필요 없다 — `postOrder` 가 `DataException.Timeout` 으로 던지므로 `else` 분기에서 SaveFailed 가 된다.

- [ ] **Step 6: 테스트 갱신**

`HomeViewModelTest.kt`:
- `import eloom.holybean.util.MainDispatcherRule` 추가; `@get:Rule val mainDispatcherRule = MainDispatcherRule(testDispatcher)` 추가(기존 `testDispatcher` 재사용, 단 `StandardTestDispatcher` 사용 테스트는 자체 dispatcher 로 vm 생성하므로 Main 룰과 별개 — 그 테스트들은 `MainDispatcherRule(StandardTestDispatcher(...))`가 아니라 vm 이 viewModelScope=Main 을 쓰므로, 해당 두 테스트(`isSubmitting...`, `second concurrent...`)는 Main 을 StandardTestDispatcher 로 바꿔야 함 → 아래 Step 7 참조).
- 모든 `HomeViewModel(firestoreRepository, menuRepository, testDispatcher, piPrintClient, homePrinter)` 호출에서 `testDispatcher`(세 번째 인자) 제거 → `HomeViewModel(firestoreRepository, menuRepository, piPrintClient, homePrinter)`.
- `postOrder timeout is reported as SaveFailed` 테스트의 목을 `DataException.Timeout` 으로 변경:
```kotlin
coEvery { firestoreRepository.postOrder(any()) } throws
    eloom.holybean.exception.DataException.Timeout()
```
(가상 시계 withTimeout 트릭 제거 — 이제 repository 가 DataException.Timeout 을 던지는 계약이므로 그대로 모킹.)

- [ ] **Step 7: StandardTestDispatcher 사용 테스트 2건 조정**

`isSubmitting is true after confirm and false after completion` 과 `second concurrent onOrderConfirmed is blocked while submitting` 은 `StandardTestDispatcher(testScheduler)`를 생성자에 넘겼다. 이제 dispatcher 인자가 없으므로, 이 두 테스트는 `MainDispatcherRule`의 디스패처를 통해 동작해야 한다. 두 테스트를 다음으로 조정: 클래스 룰을 `MainDispatcherRule(UnconfinedTestDispatcher())` 대신, 이 두 테스트만 별도 스케줄링이 필요하므로 `runTest { val vm = HomeViewModel(...); ... }` 에서 `Dispatchers.setMain(StandardTestDispatcher(testScheduler))` 를 테스트 본문에서 호출하거나(룰보다 우선), 더 단순하게 — `isSubmitting` 단언을 위해 `advanceUntilIdle()` 전후를 검사하도록 유지하되, Main 이 Unconfined 면 `onOrderConfirmed` 직후 본문이 즉시 실행되어 `isSubmitting` 이 곧바로 false 가 될 수 있다. 따라서 이 두 테스트는 클래스 룰을 무시하고 본문에서 `Dispatchers.setMain(StandardTestDispatcher(testScheduler))` 를 설정한다:
```kotlin
@Test
fun `isSubmitting is true after confirm and false after completion`() = runTest {
    val std = StandardTestDispatcher(testScheduler)
    Dispatchers.setMain(std)
    val vm = HomeViewModel(firestoreRepository, menuRepository, piPrintClient, homePrinter)
    advanceUntilIdle()
    coEvery { firestoreRepository.postOrder(any()) } returns Unit
    vm.onOrderConfirmed(createTestOrder(), "포장")
    assertTrue(vm.uiState.value.isSubmitting) // 런치 예약만 됨, 본문 미실행
    advanceUntilIdle()
    assertEquals(false, vm.uiState.value.isSubmitting)
}
```
(MainDispatcherRule 의 finished 가 resetMain 하므로 테스트 본문의 setMain 은 안전. `second concurrent...` 도 동일하게 본문 setMain(std) 적용.)
임포트 `import kotlinx.coroutines.Dispatchers` 추가.

- [ ] **Step 8: 테스트 실행**

Run: `cd android && ./gradlew testDebugUnitTest --tests "eloom.holybean.ui.home.HomeViewModelTest"`
Expected: PASS (전 케이스 — print failure, save failure, timeout=SaveFailed, retry 흐름, precedence 포함)

- [ ] **Step 9: Commit**

```bash
git add android/app/src/main/java/eloom/holybean/ui/home/HomeViewModel.kt \
        android/app/src/test/kotlin/eloom/holybean/ui/home/HomeViewModelTest.kt
git commit -m "refactor(home): adopt launchSafely with differentiated onError, drop io dispatcher"
```

---

## Task 13: 컨벤션 문서 작성

**Files:**
- Create: `android/docs/coroutine-conventions.md`

- [ ] **Step 1: 문서 작성**

`android/docs/coroutine-conventions.md` 에 스펙 §2 의 R1–R7 을 근거·예시와 함께 정리한다. 포함 섹션: (1) launchSafely 사용법 + 차등 onError 예시, (2) Repository는 throw + withContext(best-effort probe 예외), (3) tryEmit 규칙, (4) 타입 한정자(@IoDispatcher/@PrinterDispatcher/@AppScope), (5) applicationScope 수명(화면 이탈 후 인쇄 완료), (6) 테스트 표준(MainDispatcherRule, 실패/취소 케이스, runTest/advanceUntilIdle), (7) DataException 사용처. 각 항목에 본 리팩토링에서 바뀐 실제 파일을 1개씩 예로 링크.

- [ ] **Step 2: Commit**

```bash
git add android/docs/coroutine-conventions.md
git commit -m "docs: add coroutine conventions reference (R1-R7)"
```

---

## Task 14: 전체 검증

- [ ] **Step 1: 전체 단위 테스트**

Run: `cd android && ./gradlew testDebugUnitTest`
Expected: BUILD SUCCESSFUL, 전 테스트 PASS.

- [ ] **Step 2: 컴파일/Lint (디버그)**

Run: `cd android && ./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL. (남은 `@Named`/`CoroutineDispatcher`/`CancellationException` 미사용 import 경고 정리.)

- [ ] **Step 3: 잔존 안티패턴 그렙 확인**

Run:
```bash
cd android && grep -rn "@Named(\"IO\"\|@Named(\"Printer\"\|@Named(\"ApplicationScope\"" app/src/main
grep -rn "_uiEvent.emit(" app/src/main/java/eloom/holybean/ui
grep -rn "runCatching" app/src/main/java/eloom/holybean/ui
```
Expected: 첫·둘째 그렙 결과 없음(전부 타입 한정자/ tryEmit). `runCatching` 도 ViewModel 에서 사라짐(launchSafely 로 대체). 남아 있으면 해당 위치 정리.

- [ ] **Step 4: 최종 커밋(정리분이 있으면)**

```bash
git add -A && git commit -m "chore(coroutines): cleanup leftover imports and patterns"
```

---

## Self-Review (작성자 체크)

- **스펙 커버리지:** R1(launchSafely 재전파)=Task3, R2(withContext)=Task4·5, R3(throw+단일로깅)=Task4·12, R4(launchSafely 강제)=Task6–12, R5(tryEmit)=Task12·전반, R6(probe Boolean)=Task4 checkConnection, R7(타입 한정자)=Task1. 컨벤션 문서=Task13. 테스트 표준(MainDispatcherRule)=Task3 + 각 VM. ✅
- **타입 일관성:** 한정자 `@IoDispatcher`/`@PrinterDispatcher`/`@AppScope`(Task1) — 모든 후속 Task 동일 사용. `launchSafely(context, onError, block)` 시그니처 — 전 Task 동일. `DataException.Timeout`(Task2) — postOrder(Task4)·Home onError(Task12) 일관. ✅
- **플레이스홀더:** 없음(코드/명령 명시). 단, 일부 테스트 파일(Report/DevTools)은 현 내용 미확인이라 "동일 패턴 적용 + 단언 유지"로 기술 — 실행 단계에서 실제 시그니처에 맞춰 dispatcher 인자만 제거하면 됨. ✅
- **위험 지점:** Home 의 StandardTestDispatcher 테스트 2건(Task12 Step7)이 가장 미묘 — 본문 setMain 으로 해결. Menu 의 Flow `.catch` 제거가 기존 테스트 기대(emit emptyList)와 충돌 가능 — Task9 Step4 에서 메시지 일치 확인 필수.
