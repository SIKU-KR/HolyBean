# 앱 시작 step (스플래시 + 사전 로딩 + 헬스 체크) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 앱 시작 시 전용 스플래시 화면에서 메뉴+주문번호를 사전 로딩하고 프린트 서버 헬스를 확인한 뒤 Home으로 진입한다. 데이터 실패는 진입을 차단(재시도만), 프린터 실패는 경고만 표시하고 진입을 허용한다.

**Architecture:** NavHost의 start destination을 새 `SplashDest`로 바꾸고, `StartupViewModel`이 데이터 로딩과 프린터 헬스 체크를 병렬 실행한다. 데이터는 `MenuRepository` 인메모리 캐시를 채워 `HomeViewModel`이 이중 페치 없이 즉시 사용한다. 모두 성공 시 자동으로 `OrderFlow`(Home)로 이동하며 스플래시는 백스택에서 제거된다.

**Tech Stack:** Kotlin, Jetpack Compose, Navigation-Compose(타입세이프 라우트), Hilt, Coroutines/Flow. 테스트는 JUnit4 + mockk + kotlinx-coroutines-test.

---

## 참고 사항 (실행 전 읽기)

- 모든 Gradle 명령은 `android/` 디렉터리에서 실행한다. Java가 PATH에 없을 수 있으니 필요 시 `JAVA_HOME`을 먼저 export 한다(빌드 환경 메모리 참고).
- 단위 테스트 실행 task: `./gradlew :app:testDebugUnitTest`. 단일 클래스 필터: `--tests "<FQCN>"`.
- 기존 패턴 출처:
  - ViewModel+Route+Screen 분리, `collectAsStateWithLifecycle`, 색상 상태 점: `ui/settings/DevToolsScreen.kt`, `ui/settings/DevToolsViewModel.kt`
  - ViewModel 단위 테스트(mockk, `UnconfinedTestDispatcher`, `advanceUntilIdle`): `ui/settings/DevToolsViewModelTest.kt`
  - 타입세이프 라우트 정의/네비게이션: `ui/navigation/Routes.kt`, `ui/navigation/HolyBeanNavHost.kt`

## File Structure

- `data/repository/MenuRepository.kt` (수정): 인메모리 캐시 필드 + `getCachedMenu()` 추가, `getMenuListSync()`가 캐시를 채우도록 변경.
- `ui/home/HomeViewModel.kt` (수정): `init`에서 캐시 우선 사용.
- `ui/startup/StartupViewModel.kt` (생성): `StepStatus` enum, `UiState`, `check()`/`retry()`, 데이터/프린터 병렬 실행.
- `ui/startup/SplashScreen.kt` (생성): `SplashRoute`(VM 연결) + `SplashScreen`(stateless UI).
- `ui/navigation/Routes.kt` (수정): `SplashDest` 추가.
- `ui/navigation/HolyBeanNavHost.kt` (수정): start destination을 `SplashDest`로 변경, 스플래시 컴포저블 + Home 진입 시 popUpTo.
- `data/repository/MenuRepositoryCacheTest.kt` (생성, test): 캐시 동작.
- `ui/startup/StartupViewModelTest.kt` (생성, test): 상태 전이.

---

## Task 1: MenuRepository 인메모리 캐시

**Files:**
- Modify: `app/src/main/java/eloom/holybean/data/repository/MenuRepository.kt`
- Test: `app/src/test/kotlin/eloom/holybean/data/repository/MenuRepositoryCacheTest.kt`

- [ ] **Step 1: 실패하는 테스트 작성**

Create `app/src/test/kotlin/eloom/holybean/data/repository/MenuRepositoryCacheTest.kt`:

```kotlin
package eloom.holybean.data.repository

import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MenuRepositoryCacheTest {

    private fun repoReturning(items: List<Map<String, Any>>): MenuRepository {
        val db: FirebaseFirestore = mockk()
        val collection: CollectionReference = mockk()
        val docRef: DocumentReference = mockk()
        val snap: DocumentSnapshot = mockk()
        every { db.collection(any()) } returns collection
        every { collection.document(any()) } returns docRef
        every { docRef.get() } returns Tasks.forResult(snap)
        every { snap.get("items") } returns items
        return MenuRepository(db)
    }

    @Test fun `getCachedMenu is null before any load`() {
        val repo = repoReturning(emptyList())
        assertNull(repo.getCachedMenu())
    }

    @Test fun `getMenuListSync populates cache with same list`() = runTest {
        val repo = repoReturning(
            listOf(mapOf("id" to 1001, "name" to "아메리카노", "price" to 4000, "placement" to 1, "inuse" to true))
        )
        val loaded = repo.getMenuListSync()
        assertEquals(loaded, repo.getCachedMenu())
        assertEquals(1, repo.getCachedMenu()!!.size)
        assertEquals(1001, repo.getCachedMenu()!!.first().id)
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :app:testDebugUnitTest --tests "eloom.holybean.data.repository.MenuRepositoryCacheTest"`
Expected: 컴파일 실패 — `getCachedMenu()` 미정의.

- [ ] **Step 3: 캐시 구현**

Modify `MenuRepository.kt`. 클래스 본문 상단(`menuDoc()` 위)에 캐시 필드와 접근자 추가:

```kotlin
@Volatile
private var cachedMenu: List<MenuItem>? = null

/** 스플래시에서 채운 메뉴 캐시. 없으면 null. */
fun getCachedMenu(): List<MenuItem>? = cachedMenu
```

그리고 `getMenuListSync()`가 결과를 캐시에 저장하도록 변경:

```kotlin
suspend fun getMenuListSync(): List<MenuItem> =
    parse(menuDoc().get().await().get("items")).sortedBy { it.id }
        .also { cachedMenu = it }
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew :app:testDebugUnitTest --tests "eloom.holybean.data.repository.MenuRepositoryCacheTest"`
Expected: PASS (2 tests).

- [ ] **Step 5: 커밋**

```bash
git add app/src/main/java/eloom/holybean/data/repository/MenuRepository.kt \
        app/src/test/kotlin/eloom/holybean/data/repository/MenuRepositoryCacheTest.kt
git commit -m "feat(menu): in-memory cache for prefetched menu list"
```

---

## Task 2: HomeViewModel이 캐시 우선 사용

**Files:**
- Modify: `app/src/main/java/eloom/holybean/ui/home/HomeViewModel.kt:71-81`
- Test: `app/src/test/kotlin/eloom/holybean/ui/home/HomeViewModelTest.kt` (검증만, 기존 테스트 유지)

- [ ] **Step 1: 캐시 우선 테스트 추가**

`HomeViewModelTest.kt`에 테스트 추가(클래스 내부, 헬퍼 위). 캐시가 있으면 네트워크 페치(`getMenuListSync`)를 호출하지 않아야 한다:

```kotlin
@Test
fun `init uses cached menu and skips network fetch when cache present`() = runTest(testDispatcher) {
    // Given - 캐시에 메뉴가 있다
    val cached = listOf(eloom.holybean.data.model.MenuItem(1001, "아메리카노", 4000, 1, true))
    io.mockk.every { menuRepository.getCachedMenu() } returns cached

    // When - ViewModel 재구성(init 재실행)
    homeViewModel = HomeViewModel(
        firestoreRepository,
        menuRepository,
        testDispatcher,
        CoroutineScope(SupervisorJob() + testDispatcher),
        piPrintClient,
        homePrinter,
    )
    advanceUntilIdle()

    // Then - 캐시 사용, 네트워크 페치 미호출
    assertEquals(cached, homeViewModel.uiState.value.allMenuItems)
    coVerify(exactly = 0) { menuRepository.getMenuListSync() }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :app:testDebugUnitTest --tests "eloom.holybean.ui.home.HomeViewModelTest"`
Expected: FAIL — 현재 `init`이 무조건 `getMenuListSync()`를 호출하므로 `coVerify(exactly = 0)`가 실패.

- [ ] **Step 3: init에서 캐시 우선 사용**

`HomeViewModel.kt`의 `init` 블록(현재 71-81행)을 다음으로 교체:

```kotlin
init {
    // Load initial data — 스플래시가 채운 캐시를 우선 사용하고, 없으면 네트워크 페치로 폴백
    viewModelScope.launch(ioDispatcher) {
        val menus = menuRepository.getCachedMenu() ?: menuRepository.getMenuListSync()
        _uiState.value = _uiState.value.copy(
            allMenuItems = menus,
            filteredMenuItems = menus
        )
        refreshOrderNumber()
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew :app:testDebugUnitTest --tests "eloom.holybean.ui.home.HomeViewModelTest"`
Expected: PASS (신규 + 기존 테스트 모두). 기존 테스트는 `menuRepository`가 relaxed mock이라 `getCachedMenu()`가 `null`을 반환 → `getMenuListSync()` 폴백 경로로 동작하므로 영향 없음.

- [ ] **Step 5: 커밋**

```bash
git add app/src/main/java/eloom/holybean/ui/home/HomeViewModel.kt \
        app/src/test/kotlin/eloom/holybean/ui/home/HomeViewModelTest.kt
git commit -m "feat(home): use cached menu on init, fall back to network fetch"
```

---

## Task 3: StartupViewModel (상태 + 병렬 체크)

**Files:**
- Create: `app/src/main/java/eloom/holybean/ui/startup/StartupViewModel.kt`
- Test: `app/src/test/kotlin/eloom/holybean/ui/startup/StartupViewModelTest.kt`

- [ ] **Step 1: 실패하는 테스트 작성**

Create `app/src/test/kotlin/eloom/holybean/ui/startup/StartupViewModelTest.kt`:

```kotlin
package eloom.holybean.ui.startup

import eloom.holybean.data.repository.FirestoreRepository
import eloom.holybean.data.repository.MenuRepository
import eloom.holybean.printer.PiPrintClient
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StartupViewModelTest {
    private val menu: MenuRepository = mockk(relaxed = true)
    private val firestore: FirestoreRepository = mockk(relaxed = true)
    private val pi: PiPrintClient = mockk(relaxed = true)

    private fun vm() = StartupViewModel(menu, firestore, pi, UnconfinedTestDispatcher())

    @Test fun `both succeed sets success and autoEnter`() = runTest {
        coEvery { menu.getMenuListSync() } returns emptyList()
        coEvery { firestore.getOrderNumber() } returns 1
        coEvery { pi.checkHealth() } returns true
        val sut = vm()
        advanceUntilIdle()
        val s = sut.uiState.value
        assertEquals(StepStatus.Success, s.data)
        assertEquals(StepStatus.Success, s.printer)
        assertTrue(s.autoEnter)
    }

    @Test fun `printer failure keeps data success but no autoEnter`() = runTest {
        coEvery { menu.getMenuListSync() } returns emptyList()
        coEvery { firestore.getOrderNumber() } returns 1
        coEvery { pi.checkHealth() } returns false
        val sut = vm()
        advanceUntilIdle()
        val s = sut.uiState.value
        assertEquals(StepStatus.Success, s.data)
        assertEquals(StepStatus.Failed, s.printer)
        assertTrue(s.canEnter)
        assertFalse(s.autoEnter)
    }

    @Test fun `menu fetch throwing marks data failed`() = runTest {
        coEvery { menu.getMenuListSync() } throws RuntimeException("net")
        coEvery { firestore.getOrderNumber() } returns 1
        coEvery { pi.checkHealth() } returns true
        val sut = vm()
        advanceUntilIdle()
        assertEquals(StepStatus.Failed, sut.uiState.value.data)
        assertFalse(sut.uiState.value.canEnter)
    }

    @Test fun `order number sentinel marks data failed`() = runTest {
        coEvery { menu.getMenuListSync() } returns emptyList()
        coEvery { firestore.getOrderNumber() } returns -1
        coEvery { pi.checkHealth() } returns true
        val sut = vm()
        advanceUntilIdle()
        assertEquals(StepStatus.Failed, sut.uiState.value.data)
    }

    @Test fun `retry recovers data from failed to success`() = runTest {
        coEvery { menu.getMenuListSync() } throws RuntimeException("net")
        coEvery { firestore.getOrderNumber() } returns 1
        coEvery { pi.checkHealth() } returns true
        val sut = vm()
        advanceUntilIdle()
        assertEquals(StepStatus.Failed, sut.uiState.value.data)

        // 네트워크 회복 후 재시도
        coEvery { menu.getMenuListSync() } returns emptyList()
        sut.retry()
        advanceUntilIdle()
        assertEquals(StepStatus.Success, sut.uiState.value.data)
        assertTrue(sut.uiState.value.autoEnter)
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :app:testDebugUnitTest --tests "eloom.holybean.ui.startup.StartupViewModelTest"`
Expected: 컴파일 실패 — `StartupViewModel`, `StepStatus` 미정의.

- [ ] **Step 3: StartupViewModel 구현**

Create `app/src/main/java/eloom/holybean/ui/startup/StartupViewModel.kt`:

```kotlin
package eloom.holybean.ui.startup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import eloom.holybean.data.repository.FirestoreRepository
import eloom.holybean.data.repository.MenuRepository
import eloom.holybean.printer.PiPrintClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Named

enum class StepStatus { Loading, Success, Failed }

@HiltViewModel
class StartupViewModel @Inject constructor(
    private val menuRepository: MenuRepository,
    private val firestoreRepository: FirestoreRepository,
    private val piPrintClient: PiPrintClient,
    @Named("IO") private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    data class UiState(
        val data: StepStatus = StepStatus.Loading,
        val printer: StepStatus = StepStatus.Loading,
    ) {
        /** 데이터가 성공이면 진입 가능(프린터는 경고일 뿐 진입을 막지 않음). */
        val canEnter: Boolean get() = data == StepStatus.Success
        /** 데이터·프린터 모두 성공이면 자동 진입. */
        val autoEnter: Boolean get() = data == StepStatus.Success && printer == StepStatus.Success
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init { check() }

    /** 두 작업을 병렬 실행. retry 시 동일 로직으로 재실행. */
    fun check() {
        _uiState.update { it.copy(data = StepStatus.Loading, printer = StepStatus.Loading) }
        viewModelScope.launch(ioDispatcher) { loadData() }
        viewModelScope.launch(ioDispatcher) { checkPrinter() }
    }

    fun retry() = check()

    private suspend fun loadData() {
        // 메뉴 페치가 예외 없이 끝나고(캐시도 채워짐) 주문번호가 유효(>0)하면 성공.
        // getOrderNumber()는 실패 시 예외 대신 -1을 반환한다.
        val ok = runCatching {
            menuRepository.getMenuListSync()
            firestoreRepository.getOrderNumber() > 0
        }.getOrDefault(false)
        _uiState.update { it.copy(data = if (ok) StepStatus.Success else StepStatus.Failed) }
    }

    private suspend fun checkPrinter() {
        val ok = piPrintClient.checkHealth()
        _uiState.update { it.copy(printer = if (ok) StepStatus.Success else StepStatus.Failed) }
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew :app:testDebugUnitTest --tests "eloom.holybean.ui.startup.StartupViewModelTest"`
Expected: PASS (5 tests).

- [ ] **Step 5: 커밋**

```bash
git add app/src/main/java/eloom/holybean/ui/startup/StartupViewModel.kt \
        app/src/test/kotlin/eloom/holybean/ui/startup/StartupViewModelTest.kt
git commit -m "feat(startup): StartupViewModel with parallel data load and printer health check"
```

---

## Task 4: SplashScreen 컴포저블 (Route + Screen)

**Files:**
- Create: `app/src/main/java/eloom/holybean/ui/startup/SplashScreen.kt`

이 Task는 UI 컴포저블이며 단위 테스트 대상이 아니다(상태 로직은 Task 3에서 검증). 컴파일과 Task 6의 수동 검증으로 확인한다.

- [ ] **Step 1: SplashScreen.kt 작성**

Create `app/src/main/java/eloom/holybean/ui/startup/SplashScreen.kt`:

```kotlin
package eloom.holybean.ui.startup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay

@Composable
fun SplashRoute(
    onNavigateToHome: () -> Unit,
    vm: StartupViewModel = hiltViewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    // 데이터·프린터 모두 성공이면 잠깐 노출 후 자동 진입
    LaunchedEffect(state.autoEnter) {
        if (state.autoEnter) {
            delay(500)
            onNavigateToHome()
        }
    }
    SplashScreen(
        state = state,
        onRetry = vm::retry,
        onEnterAnyway = onNavigateToHome,
    )
}

@Composable
fun SplashScreen(
    state: StartupViewModel.UiState,
    onRetry: () -> Unit,
    onEnterAnyway: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("HolyBean", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(24.dp))

        StatusRow("데이터", state.data, loadingText = "데이터 불러오는 중…", successText = "데이터 준비 완료")
        StatusRow("프린터", state.printer, loadingText = "프린터 연결 확인 중…", successText = "프린터 연결됨")

        Spacer(Modifier.height(24.dp))

        when {
            // 데이터 실패 → 진입 차단, 재시도만
            state.data == StepStatus.Failed -> {
                Text(
                    "데이터를 불러오지 못했습니다",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "인터넷 연결 상태를 확인한 뒤 다시 시도해 주세요.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = onRetry) { Text("다시 시도") }
            }
            // 데이터 성공 + 프린터 실패 → 경고 후 진입 허용
            state.canEnter && state.printer == StepStatus.Failed -> {
                Text(
                    "프린터에 연결할 수 없습니다",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "영수증이 출력되지 않을 수 있습니다. 프린터 전원과 와이파이 연결을 확인해 주세요. 이대로도 주문은 가능합니다.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onRetry) { Text("다시 시도") }
                    Button(onClick = onEnterAnyway) { Text("그대로 진입") }
                }
            }
            // 그 외(진행 중 또는 모두 성공 직전): 스피너
            else -> CircularProgressIndicator()
        }
    }
}

@Composable
private fun StatusRow(
    label: String,
    status: StepStatus,
    loadingText: String,
    successText: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 6.dp),
    ) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(
                    when (status) {
                        StepStatus.Success -> Color(0xFF22C55E)
                        StepStatus.Failed -> Color(0xFFEF4444)
                        StepStatus.Loading -> Color(0xFFBBBBBB)
                    },
                ),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = when (status) {
                StepStatus.Loading -> loadingText
                StepStatus.Success -> successText
                StepStatus.Failed -> "$label 실패"
            },
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
```

- [ ] **Step 2: 컴파일 확인**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: 커밋**

```bash
git add app/src/main/java/eloom/holybean/ui/startup/SplashScreen.kt
git commit -m "feat(startup): splash screen UI with per-step status and retry/enter actions"
```

---

## Task 5: 네비게이션 배선 (start destination = Splash)

**Files:**
- Modify: `app/src/main/java/eloom/holybean/ui/navigation/Routes.kt`
- Modify: `app/src/main/java/eloom/holybean/ui/navigation/HolyBeanNavHost.kt:30`

- [ ] **Step 1: SplashDest 라우트 추가**

`Routes.kt`에 추가(다른 `*Dest` 옆):

```kotlin
@Serializable object SplashDest
```

- [ ] **Step 2: NavHost에 스플래시 배선**

`HolyBeanNavHost.kt`에서 `import`에 다음 추가:

```kotlin
import eloom.holybean.ui.startup.SplashRoute
```

`NavHost(...)` 호출의 `startDestination`을 `OrderFlow`에서 `SplashDest`로 변경하고, `navigation<OrderFlow>` 블록 **앞에** 스플래시 컴포저블을 추가한다. 변경 후 `NavHost` 시작 부분은 다음과 같아야 한다:

```kotlin
    NavHost(navController = navController, startDestination = SplashDest) {
        composable<SplashDest> {
            SplashRoute(
                onNavigateToHome = {
                    navController.navigate(OrderFlow) {
                        popUpTo(SplashDest) { inclusive = true }
                    }
                },
            )
        }
        navigation<OrderFlow>(startDestination = HomeDest) {
```

(나머지 `navigation<OrderFlow>` 내부와 이후 컴포저블은 그대로 둔다.)

- [ ] **Step 3: 컴파일 확인**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: 전체 단위 테스트 실행**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS (신규 포함 전체 테스트 통과).

- [ ] **Step 5: 커밋**

```bash
git add app/src/main/java/eloom/holybean/ui/navigation/Routes.kt \
        app/src/main/java/eloom/holybean/ui/navigation/HolyBeanNavHost.kt
git commit -m "feat(startup): make splash the start destination, enter Home via popUpTo"
```

---

## Task 6: 빌드 및 수동 검증

**Files:** 없음(검증 전용).

- [ ] **Step 1: 디버그 APK 빌드**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: 수동 시나리오 점검(에뮬레이터 또는 기기)**

다음을 확인한다(에뮬레이터 실행에는 Java 21 필요 — 빌드 환경 메모리 참고):

1. **정상 경로**: 인터넷+프린터 정상 → 스플래시에서 두 항목이 초록색으로 바뀌고 약 0.5초 후 자동으로 Home 진입. Home의 메뉴가 즉시 표시(이중 로딩 없음).
2. **프린터만 장애**: 프린터 서버 끄고 실행 → 데이터는 초록, 프린터는 빨강 + 프린터 경고 메시지, `다시 시도`/`그대로 진입` 버튼. `그대로 진입` 시 Home 진입.
3. **데이터 장애**: 와이파이 끄고 실행 → 데이터 빨강 + "데이터를 불러오지 못했습니다" + `다시 시도`만(진입 차단). 와이파이 켜고 `다시 시도` → 두 항목 초록 후 자동 진입.
4. **뒤로가기**: Home에서 뒤로가기 시 스플래시로 돌아가지 않고 앱 종료(popUpTo inclusive 확인).

- [ ] **Step 3: 결과 기록**

각 시나리오의 실제 동작을 기록한다. 어긋나는 항목이 있으면 systematic-debugging으로 원인을 좁힌다.

---

## Self-Review 결과

**Spec coverage:**
- 진입 정책(데이터 blocking / 프린터 non-blocking) → Task 3 `canEnter`/`autoEnter`, Task 4 분기.
- 전용 스플래시 UI → Task 4.
- 인터넷 체크=데이터 로딩 성공 → Task 3 `loadData`(getOrderNumber>0).
- 로딩 범위 메뉴+주문번호 → Task 3 `loadData`.
- 실패 시 수동 재시도만 → Task 3 `retry`, Task 4 버튼.
- 데이터 공유=MenuRepository 인메모리 캐시 → Task 1, Task 2.
- 화면 문구(상태 라벨/실패 메시지) → Task 4 문자열.
- 테스트(VM 4+1 케이스, 캐시 동작) → Task 3, Task 1.

**Placeholder scan:** 없음(모든 스텝에 실제 코드/명령 포함).

**Type consistency:** `StepStatus{Loading,Success,Failed}`, `UiState.{data,printer,canEnter,autoEnter}`, `check()/retry()`, `getCachedMenu()`가 모든 Task에서 동일하게 사용됨. `SplashRoute(onNavigateToHome)`/`SplashScreen(state,onRetry,onEnterAnyway)` 시그니처가 Task 4↔5 간 일치.
