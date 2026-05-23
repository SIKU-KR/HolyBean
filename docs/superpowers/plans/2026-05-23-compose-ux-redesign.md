# HolyBean Compose UX 재설계 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** HolyBean 안드로이드 POS 앱을 드로어 없는 단일 Activity + Jetpack Compose UI로 전면 재작성하여 주문 플로우를 최적화한다.

**Architecture:** 단일 `MainActivity` + Navigation Compose(타입세이프 라우트). 화면은 상태 없는(stateless) Composable로 만들고, 상태는 기존 Hilt ViewModel의 `StateFlow`에서 `collectAsStateWithLifecycle`로 단방향 주입한다. 결제 검증·주문 빌드 같은 순수 로직은 ViewModel/Composable에서 분리해 JVM 단위 테스트로 TDD한다. 도메인 계층(Repository, Printer, DI)은 변경하지 않는다.

**Tech Stack:** Kotlin 2.2.20, Jetpack Compose (BOM 2024.12.01), Material3, Navigation Compose 2.8.x(타입세이프), Hilt + hilt-navigation-compose, kotlinx-serialization, kotlinx-collections-immutable, lifecycle-runtime-compose. 테스트: JUnit4 + mockk + coroutines-test(JVM), Compose UI Test(androidTest).

---

## Jetpack Compose 베스트 프랙티스 (이 계획 전반 규칙)

모든 화면 구현은 다음을 따른다. 각 Task는 이를 전제로 한다.

1. **상태 호이스팅 / 단방향 데이터 흐름(UDF).** `XxxScreen(state, onEvent...)` 형태의 **stateless** Composable + `XxxRoute()` 가 `hiltViewModel()` 과 `collectAsStateWithLifecycle()` 로 상태를 모아 람다로 이벤트를 위임한다. Composable에 ViewModel을 직접 넘기지 않는다.
2. **상태 수집은 `collectAsStateWithLifecycle()`** (androidx.lifecycle:lifecycle-runtime-compose). 일회성 이벤트(`UiEvent`)는 `LaunchedEffect` + `collect` 로 처리한다.
3. **안정성(Stability).** 화면에 넘기는 리스트는 `kotlinx.collections.immutable.ImmutableList` 로 변환해 불필요한 recomposition을 막는다. UI 상태 홀더 data class에는 `@Immutable` 을 붙인다.
4. **Lazy 레이아웃엔 `key` 지정.** `LazyVerticalGrid`/`LazyColumn` 의 items에 안정적 key(메뉴 id, 주문번호 등)를 준다.
5. **상태 읽기를 최대한 늦춘다(defer reads).** 자주 바뀌는 값은 람다/`Modifier` 람다 안에서 읽는다.
6. **테마는 `MaterialTheme`** 커스텀 `ColorScheme`(오렌지) + 커스텀 디자인 토큰 객체로. 하드코딩 색상 금지.
7. **모든 화면 Composable에 `@Preview`** 를 1개 이상 추가한다.
8. **순수 로직 분리.** 결제 검증/주문 빌드 등 분기 로직은 Composable 밖 순수 함수로 빼서 JVM 단위 테스트로 TDD한다.

## 테스트 전략

- **로직(ViewModel·순수 함수):** 로컬 JVM 테스트. 실행 `./gradlew :app:testDebugUnitTest --tests "<FQN>"`. 기존 mockk 패턴(`HomeViewModelTest`) 재사용.
- **Compose UI:** `androidTest` 의 `createComposeRule()` 로 stateless Composable 단위 테스트. 실행 `./gradlew :app:connectedDebugAndroidTest`(에뮬레이터/기기 필요; 에뮬레이터는 Java 21 필요 — `build-environment-quirks` 참고).
- **빌드 검증:** `./gradlew :app:assembleDebug`.
- **차단 게이트(blocking) = 로직 단위 테스트(JVM) 통과 + `assembleDebug` 성공.** Compose UI 테스트는 에뮬레이터가 있으면 실행하고, 없으면 코드만 작성·커밋(비차단). 따라서 **각 화면의 핵심 분기는 반드시 순수 로직/ViewModel 단위 테스트로 커버**한다(UI 테스트에 의존하지 않는다).

## 선행 조건 (Prerequisites)

- **백엔드 마이그레이션이 먼저 빌드 가능해야 한다.** 현재 `HomeViewModel` 은 생성자에 `firestoreRepository` 를 주입받지만 본문(`onOrderConfirmed`, `refreshOrderNumber`)이 아직 `lambdaRepository` 를 참조하는 **반쯤 깨진 상태**다. Phase 1 시작 전 이 참조를 `firestoreRepository.postOrder(...)` / `firestoreRepository.getOrderNumber()` 로 정리해 `:app:assembleDebug` 가 통과하는지 확인한다.
- `FirestoreRepository` 의 시그니처(`getOrderNumber`, `postOrder`, `getOrdersOfDay`, `getOrderDetail`, `getReport`, `getCreditsList`, `setCreditOrderPaid`, `deleteOrder`)를 변경하지 않는다 — UI 계획은 이 인터페이스 위에 올린다.

## 파일 구조 (생성/수정 맵)

```
ui/theme/Color.kt              (수정) 오렌지 팔레트
ui/theme/Theme.kt              (수정) HolyBeanTheme + ColorScheme
ui/theme/Type.kt               (수정) Pretendard Typography
ui/theme/Dimens.kt             (생성) 간격/모서리 토큰
ui/components/MenuTile.kt       (생성) 메뉴/쿠폰/설정 타일
ui/components/BasketRow.kt      (생성) 장바구니 항목 줄
ui/components/PaymentMethodTile.kt (생성) 결제수단 선택 타일
ui/components/SegmentedToggle.kt   (생성) 컵 선택 토글
ui/components/StatChip.kt          (생성) 매출 통계 칩
ui/components/PrimaryButton.kt     (생성) Primary/Secondary 버튼
ui/navigation/Routes.kt         (생성) 타입세이프 라우트(@Serializable)
ui/navigation/HolyBeanNavHost.kt(생성) NavHost + 주문 플로우 중첩 그래프
MainActivity.kt                 (수정) setContent { HolyBeanTheme { HolyBeanNavHost() } }
ui/home/HomeScreen.kt           (생성) 주문 화면(stateless) + Route
ui/home/HomeViewModel.kt        (수정) 카테고리/이벤트 보강
ui/home/MenuCategories.kt       (생성) 카테고리 상수 + id→카테고리 매핑
ui/payment/PaymentForm.kt       (생성) 순수 검증/주문 빌드 로직
ui/payment/PaymentScreen.kt     (생성) 결제 화면(stateless) + Route
ui/orders/OrdersScreen.kt       (생성) 주문기록+오늘매출(stateless) + Route
ui/orderlist/OrdersViewModel.kt (수정) 오늘 매출 요약 + 보고서 출력
ui/settings/SettingsSheet.kt    (생성) 설정 시트
ui/settings/DevToolsScreen.kt   (생성) 개발자 도구 + DevToolsViewModel
ui/menumanagement/MenuManagementScreen.kt (생성) 포팅
ui/credits/CreditsScreen.kt     (생성) 포팅
ui/report/ReportScreen.kt       (생성) 기간 리포트 포팅
```

삭제(Phase 4 마지막): 모든 `*Fragment.kt`, `res/layout/*`, `res/navigation`(없음), `res/menu/drawer_menu.xml`, `RvCustomDesign.kt`, `*Adapter.kt`, `OrderDialog.kt`, `interfaces/MainActivityListener.kt`, `interfaces/*Functions.kt`, DataBinding/ViewBinding 설정.

---

# Phase 1 — 기반 + 주문(Home) + 쿠폰

## Task 1: 의존성 & 빌드 설정

**Files:**
- Modify: `android/app/build.gradle.kts`
- Modify: `android/build.gradle.kts`

> 주의: `build.gradle.kts` 는 백엔드 마이그레이션으로 동시 편집 중일 수 있다. 아래는 **추가/변경분**이며 기존 라인과 병합한다.

- [ ] **Step 1: 루트 build.gradle.kts 에 serialization 플러그인 추가**

`android/build.gradle.kts` 의 `plugins {}` 블록에 추가:

```kotlin
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.20" apply false
```

- [ ] **Step 2: 앱 모듈 플러그인/의존성 수정**

`android/app/build.gradle.kts` 의 `plugins {}` 에 추가:

```kotlin
    id("org.jetbrains.kotlin.plugin.serialization")
```

`composeOptions { ... }` 블록은 **삭제**(컴파일러는 `org.jetbrains.kotlin.plugin.compose` 가 처리).

`dependencies {}` 에서 Compose BOM 라인을 최신으로 교체하고 라이브러리 추가:

```kotlin
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable:0.3.8")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("sh.calvin.reorderable:reorderable:2.4.0")

    androidTestImplementation(platform("androidx.compose:compose-bom:2024.12.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
```

- [ ] **Step 3: 동기화 & 빌드 검증**

Run: `cd android && ./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL (기존 Fragment 코드는 아직 존재하므로 컴파일 통과).

- [ ] **Step 4: 커밋**

```bash
git add android/app/build.gradle.kts android/build.gradle.kts
git commit -m "build: add Compose navigation, immutable, serialization deps and bump Compose BOM"
```

## Task 2: 디자인 시스템 — 컬러/타이포/토큰

**Files:**
- Modify: `android/app/src/main/java/eloom/holybean/ui/theme/Color.kt`
- Modify: `android/app/src/main/java/eloom/holybean/ui/theme/Theme.kt`
- Modify: `android/app/src/main/java/eloom/holybean/ui/theme/Type.kt`
- Create: `android/app/src/main/java/eloom/holybean/ui/theme/Dimens.kt`

- [ ] **Step 1: Color.kt 를 오렌지 팔레트로 교체**

```kotlin
package eloom.holybean.ui.theme

import androidx.compose.ui.graphics.Color

val Orange = Color(0xFFFF7F00)        // Primary
val OrangeLight = Color(0xFFFFA347)
val OrangeContainer = Color(0xFFFFF3E4)
val OrangeOnContainer = Color(0xFFC2691A)
val ScreenBg = Color(0xFFE8E9EB)      // 회색 배경
val Surface = Color(0xFFFFFFFF)       // 흰 패널
val OnSurface = Color(0xFF222222)
val OnSurfaceMuted = Color(0xFF888888)
val DangerRed = Color(0xFFD9534F)
val DividerGray = Color(0xFFEEEEEE)
```

- [ ] **Step 2: Theme.kt 를 HolyBeanTheme + ColorScheme 로 교체**

```kotlin
package eloom.holybean.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val HolyBeanColors = lightColorScheme(
    primary = Orange,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = OrangeContainer,
    onPrimaryContainer = OrangeOnContainer,
    background = ScreenBg,
    onBackground = OnSurface,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = ScreenBg,
    error = DangerRed,
)

@Composable
fun HolyBeanTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = HolyBeanColors,
        typography = HolyBeanTypography,
        content = content,
    )
}
```

상단에 `import androidx.compose.ui.graphics.Color` 추가.

- [ ] **Step 3: Type.kt 를 Pretendard Typography 로 교체**

```kotlin
package eloom.holybean.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import eloom.holybean.R

val Pretendard = FontFamily(
    Font(R.font.pretendard_medium),
    Font(R.font.pretendard_bold),
    Font(R.font.pretendard_extrabold),
)

val HolyBeanTypography = Typography(
    titleLarge = TextStyle(fontFamily = Pretendard, fontSize = 22.sp),
    titleMedium = TextStyle(fontFamily = Pretendard, fontSize = 16.sp),
    bodyLarge = TextStyle(fontFamily = Pretendard, fontSize = 15.sp),
    bodyMedium = TextStyle(fontFamily = Pretendard, fontSize = 13.sp),
    labelSmall = TextStyle(fontFamily = Pretendard, fontSize = 11.sp),
)
```

> 폰트 파일명이 `res/font/` 의 실제 파일과 다르면 맞춘다(`ls android/app/src/main/res/font`).

- [ ] **Step 4: Dimens.kt 생성**

```kotlin
package eloom.holybean.ui.theme

import androidx.compose.ui.unit.dp

object Dimens {
    val gap = 10.dp
    val paneRadius = 10.dp
    val tileRadius = 8.dp
    val screenPadding = 12.dp
    val basketWidthFraction = 0.38f
}
```

- [ ] **Step 5: 빌드 검증 & 커밋**

Run: `cd android && ./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

```bash
git add android/app/src/main/java/eloom/holybean/ui/theme/
git commit -m "feat(theme): orange Material3 color scheme, Pretendard typography, dimens"
```

## Task 3: 카테고리 매핑 (순수 로직, TDD)

**Files:**
- Create: `android/app/src/main/java/eloom/holybean/ui/home/MenuCategories.kt`
- Test: `android/app/src/test/kotlin/eloom/holybean/ui/home/MenuCategoriesTest.kt`

- [ ] **Step 1: 실패 테스트 작성**

```kotlin
package eloom.holybean.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

class MenuCategoriesTest {
    @Test fun `index 0 keeps all items`() {
        val items = listOf(1001, 2002, 3003)
        assertEquals(items, MenuCategories.filterIds(items, 0))
    }
    @Test fun `index filters by id div 1000`() {
        val items = listOf(1001, 1002, 2001, 3001)
        assertEquals(listOf(1001, 1002), MenuCategories.filterIds(items, 1))
    }
    @Test fun `category names has 6 entries starting with 전체`() {
        assertEquals(6, MenuCategories.names.size)
        assertEquals("전체", MenuCategories.names.first())
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "eloom.holybean.ui.home.MenuCategoriesTest"`
Expected: FAIL (MenuCategories 미정의).

- [ ] **Step 3: 구현**

```kotlin
package eloom.holybean.ui.home

object MenuCategories {
    val names = listOf("전체", "ICE커피", "HOT커피", "에이드/스무디", "티/음료", "베이커리")

    /** index 0 = 전체, 그 외 = id / 1000 == index. */
    fun filterIds(ids: List<Int>, index: Int): List<Int> =
        if (index == 0) ids else ids.filter { it / 1000 == index }
}
```

- [ ] **Step 4: 통과 확인**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "eloom.holybean.ui.home.MenuCategoriesTest"`
Expected: PASS

- [ ] **Step 5: 커밋**

```bash
git add android/app/src/main/java/eloom/holybean/ui/home/MenuCategories.kt android/app/src/test/kotlin/eloom/holybean/ui/home/MenuCategoriesTest.kt
git commit -m "feat(home): category id mapping with unit tests"
```

## Task 4: 재사용 컴포넌트 — MenuTile / BasketRow

**Files:**
- Create: `android/app/src/main/java/eloom/holybean/ui/components/MenuTile.kt`
- Create: `android/app/src/main/java/eloom/holybean/ui/components/BasketRow.kt`
- Test: `android/app/src/androidTest/java/eloom/holybean/ui/components/MenuTileTest.kt`

- [ ] **Step 1: MenuTile.kt 구현**

```kotlin
package eloom.holybean.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import eloom.holybean.ui.theme.*

enum class TileStyle { Menu, Coupon, Settings }

@Composable
fun MenuTile(
    name: String,
    price: Int?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: TileStyle = TileStyle.Menu,
) {
    val container = when (style) {
        TileStyle.Menu -> Surface
        TileStyle.Coupon -> OrangeContainer
        TileStyle.Settings -> Color(0xFFEEEEEE)
    }
    Card(
        onClick = onClick,
        modifier = modifier.height(60.dp),
        shape = RoundedCornerShape(Dimens.tileRadius),
        colors = CardDefaults.cardColors(containerColor = container),
    ) {
        Column(
            Modifier.fillMaxSize().padding(4.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(name, style = MaterialTheme.typography.bodyMedium)
            if (price != null) {
                Text("%,d".format(price), style = MaterialTheme.typography.labelSmall, color = Orange)
            }
        }
    }
}

@Preview
@Composable
private fun MenuTilePreview() = HolyBeanTheme {
    MenuTile(name = "아메리카노", price = 3500, onClick = {})
}
```

- [ ] **Step 2: BasketRow.kt 구현**

```kotlin
package eloom.holybean.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import eloom.holybean.ui.theme.HolyBeanTheme
import eloom.holybean.ui.theme.OnSurfaceMuted

@Composable
fun BasketRow(name: String, count: Int, amount: Int, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "$name ", style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Text("${count}개 ", style = MaterialTheme.typography.labelSmall, color = OnSurfaceMuted)
        Text("%,d".format(amount), style = MaterialTheme.typography.bodyMedium)
    }
}

@Preview
@Composable
private fun BasketRowPreview() = HolyBeanTheme {
    BasketRow("아메리카노", 2, 7000, onClick = {})
}
```

- [ ] **Step 3: MenuTile UI 테스트 작성**

```kotlin
package eloom.holybean.ui.components

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test

class MenuTileTest {
    @get:Rule val rule = createComposeRule()

    @Test fun showsNameAndPrice_andClicks() {
        var clicked = false
        rule.setContent { MenuTile(name = "라떼", price = 4500, onClick = { clicked = true }) }
        rule.onNodeWithText("라떼").assertIsDisplayed()
        rule.onNodeWithText("4,500").assertIsDisplayed()
        rule.onNodeWithText("라떼").performClick()
        assert(clicked)
    }
}
```

- [ ] **Step 4: 테스트 실행(에뮬레이터)**

Run: `cd android && ./gradlew :app:connectedDebugAndroidTest --tests "eloom.holybean.ui.components.MenuTileTest"`
Expected: PASS. (에뮬레이터 없으면 코드만 검증하고 다음 단계로.)

- [ ] **Step 5: 커밋**

```bash
git add android/app/src/main/java/eloom/holybean/ui/components/ android/app/src/androidTest/java/eloom/holybean/ui/components/
git commit -m "feat(components): MenuTile and BasketRow with preview and UI test"
```

## Task 5: HomeViewModel 보강 (이벤트 NavigateToPayment)

**Files:**
- Modify: `android/app/src/main/java/eloom/holybean/ui/home/HomeViewModel.kt`
- Test: `android/app/src/test/kotlin/eloom/holybean/ui/home/HomeViewModelTest.kt`

> 기존 `HomeViewModel` 은 `UiState`(allMenuItems, filteredMenuItems, selectedCategoryIndex, basketItems, orderId, totalPrice, currentDate), `UiEvent`(ShowToast, NavigateHome), `addToBasket/deleteFromBasket/addCoupon/onCategorySelected/onOrderConfirmed/refreshOrderNumber` 를 가진다. 결제 화면 분리를 위해 이벤트만 보강한다.

- [ ] **Step 1: 실패 테스트 추가** (`HomeViewModelTest.kt` 에 메서드 추가)

```kotlin
    @Test
    fun `addCoupon adds a positive cart line and increases total`() = runTest {
        coEvery { menuRepository.getMenuListSync() } returns emptyList()
        homeViewModel.addCoupon(3000)
        advanceUntilIdle()
        val state = homeViewModel.uiState.value
        assertEquals(1, state.basketItems.size)
        assertEquals("쿠폰", state.basketItems.first().name)
        assertEquals(3000, state.totalPrice)
    }
```

- [ ] **Step 2: 실패/통과 확인**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "eloom.holybean.ui.home.HomeViewModelTest"`
Expected: 기존 `addCoupon` 구현이 이미 양수 한 줄 추가이므로 PASS. (회귀 가드.) 실패하면 `addCoupon` 의 양수/합계 로직을 점검.

- [ ] **Step 3: 내비게이션 이벤트 재전송 방지 — `replay=0` (리뷰 #3)**

`_uiEvent` 선언의 `replay = 1` 을 `replay = 0` 으로 변경한다. 이유: 공유 ViewModel이라 Home 재구성 시 `LaunchedEffect` 가 재수집하는데 `replay=1` 이면 마지막 `NavigateToPayment`/`NavigateHome` 이 재전송되어 결제로 다시 튕기거나 중복 pop이 발생한다. `extraBufferCapacity = 16, DROP_OLDEST` 가 있어 수집 전 발행 이벤트는 버퍼로 보존되므로 안전하다.

`UiEvent` 에 추가:

```kotlin
        object NavigateToPayment : UiEvent()
```

`onCheckoutClicked()` 함수 추가(장바구니 비어있지 않을 때만 결제로 이동):

```kotlin
    fun onCheckoutClicked() {
        if (_uiState.value.basketItems.isEmpty()) return
        _uiEvent.tryEmit(UiEvent.NavigateToPayment)
    }
```

- [ ] **Step 4: 실패 테스트 — 주문 성공 시 장바구니/주문번호 리셋 (리뷰 #1)**

```kotlin
    @Test
    fun `successful order resets basket and refreshes order number`() = runTest {
        coEvery { firestoreRepository.getOrderNumber() } returnsMany listOf(10, 11)
        coEvery { firestoreRepository.postOrder(any()) } returns Unit
        homeViewModel.addCoupon(3000)
        advanceUntilIdle()
        val order = Order("2026-05-23", 10, 0, "", listOf(CartItem(999, "쿠폰", 3000, 1, 3000)),
            listOf(PaymentMethod("현금", 3000)), 3000)
        homeViewModel.onOrderConfirmed(order, "일회용컵")
        advanceUntilIdle()
        val state = homeViewModel.uiState.value
        assertTrue(state.basketItems.isEmpty())
        assertEquals(0, state.totalPrice)
        assertEquals(11, state.orderId) // 다음 주문번호로 갱신
    }
```

> `postOrder` 가 `suspend` 가 아니면(현재 동기 `fun`) `coEvery` 대신 `every { ... } returns Unit` 로 맞춘다. 구현 시 `FirestoreRepository.postOrder` 시그니처를 확인.

- [ ] **Step 5: 실패 확인 → 리셋 구현**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "eloom.holybean.ui.home.HomeViewModelTest"`
Expected: FAIL (리셋 없음).

`onOrderConfirmed` 의 네트워크 성공 분기를 수정 — `NavigateHome` 직전에 상태를 리셋하고 주문번호를 다시 채번한다:

```kotlin
        viewModelScope.launch(ioDispatcher) {
            try {
                firestoreRepository.postOrder(data)
                _uiState.update { it.copy(basketItems = emptyList(), totalPrice = 0) }
                refreshOrderNumber()           // 다음 주문번호 채번
                _uiEvent.emit(UiEvent.NavigateHome)
            } catch (e: Exception) {
                e.printStackTrace()
                _uiEvent.emit(UiEvent.ShowToast("주문 처리 중 오류가 발생했습니다."))
            }
        }
```

(이미 `import kotlinx.coroutines.flow.update` 가 없다면 추가.)

- [ ] **Step 6: 통과 확인 & 빌드**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "eloom.holybean.ui.home.HomeViewModelTest"`
Expected: PASS (신규 2건 포함)

- [ ] **Step 7: 커밋**

```bash
git add android/app/src/main/java/eloom/holybean/ui/home/HomeViewModel.kt android/app/src/test/kotlin/eloom/holybean/ui/home/HomeViewModelTest.kt
git commit -m "feat(home): NavigateToPayment, reset basket+order# on success, one-shot events"
```

## Task 6: 타입세이프 라우트 & NavHost 셸

**Files:**
- Create: `android/app/src/main/java/eloom/holybean/ui/navigation/Routes.kt`
- Create: `android/app/src/main/java/eloom/holybean/ui/navigation/HolyBeanNavHost.kt`

- [ ] **Step 1: Routes.kt 생성**

```kotlin
package eloom.holybean.ui.navigation

import kotlinx.serialization.Serializable

// 라우트(목적지) object는 `*Dest`, Composable 진입점은 `*Route` 로 명명 → 이름 충돌/alias 방지.
@Serializable object OrderFlow      // 주문+결제 공유 그래프
@Serializable object HomeDest
@Serializable object PaymentDest
@Serializable object OrdersDest
@Serializable object MenuMgmtDest
@Serializable object CreditsDest
@Serializable object ReportDest
@Serializable object DevToolsDest
```

- [ ] **Step 2: HolyBeanNavHost.kt 생성 (Home 자리표시 포함, 이후 Task에서 화면 연결)**

```kotlin
package eloom.holybean.ui.navigation

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import eloom.holybean.ui.home.HomeRoute

@Composable
fun HolyBeanNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = OrderFlow) {
        navigation<OrderFlow>(startDestination = HomeDest) {
            composable<HomeDest> { entry ->
                HomeRoute(
                    sharedViewModel = entry.sharedOrderViewModel(navController),
                    onNavigateToPayment = { navController.navigate(PaymentDest) },
                    onNavigateToOrders = { navController.navigate(OrdersDest) },
                    onNavigateToSettings = { /* Task 15: 설정 시트 */ },
                )
            }
            composable<PaymentDest> { /* Task 11 에서 연결 */ }
        }
        composable<OrdersDest> { /* Task 14 */ }
        composable<MenuMgmtDest> { /* Task 19 */ }
        composable<CreditsDest> { /* Task 18 */ }
        composable<ReportDest> { /* Task 17 */ }
        composable<DevToolsDest> { /* Task 16 */ }
    }
}

/** 주문 플로우(Home+Payment)가 동일 HomeViewModel 인스턴스를 공유하도록 부모 그래프 스코프로 가져온다. */
@Composable
fun NavBackStackEntry.sharedOrderViewModel(nav: NavHostController): eloom.holybean.ui.home.HomeViewModel {
    val parentEntry = androidx.compose.runtime.remember(this) {
        nav.getBackStackEntry(OrderFlow)
    }
    return hiltViewModel(parentEntry)
}
```

> 베스트 프랙티스: 주문→결제는 장바구니 상태를 공유해야 하므로 `OrderFlow` 중첩 그래프 백스택 엔트리에 스코프된 단일 ViewModel을 쓴다.
>
> ⚠️ **공유 ViewModel 함정(리뷰 #1·#3 반영):** 결제 후 같은 `HomeViewModel` 인스턴스로 Home에 돌아오므로 **(a)** 주문 성공 시 장바구니/주문번호를 반드시 리셋해야 하고(Task 5에서 처리), **(b)** 내비게이션 이벤트가 `replay` 로 재전송되면 결제로 다시 튕기므로 `_uiEvent` 의 `replay=0` 을 보장한다(Task 5에서 처리).

- [ ] **Step 3: 빌드 (`HomeRoute` Composable 이 아직 없으면 Task 7과 함께 컴파일)**

이 Task는 Task 7과 함께 컴파일된다. 커밋은 Task 7 Step 5에서 함께 한다.

## Task 7: HomeScreen (stateless) + HomeRoute

**Files:**
- Create: `android/app/src/main/java/eloom/holybean/ui/home/HomeScreen.kt`
- Test: `android/app/src/androidTest/java/eloom/holybean/ui/home/HomeScreenTest.kt`

- [ ] **Step 1: HomeScreen.kt 구현**

```kotlin
package eloom.holybean.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import eloom.holybean.data.model.CartItem
import eloom.holybean.data.model.MenuItem
import eloom.holybean.ui.components.*
import eloom.holybean.ui.theme.*
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

private const val COUPON_TILE_ID = -1
private const val SETTINGS_TILE_ID = -2

@Composable
fun HomeRoute(
    sharedViewModel: HomeViewModel,
    onNavigateToPayment: () -> Unit,
    onNavigateToOrders: () -> Unit,
    onNavigateToSettings: () -> Unit,
) {
    val state by sharedViewModel.uiState.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(Unit) {
        sharedViewModel.uiEvent.collect { event ->
            when (event) {
                is HomeViewModel.UiEvent.ShowToast ->
                    android.widget.Toast.makeText(context, event.message, android.widget.Toast.LENGTH_SHORT).show()
                HomeViewModel.UiEvent.NavigateToPayment -> onNavigateToPayment()
                HomeViewModel.UiEvent.NavigateHome -> Unit
            }
        }
    }

    var couponDialog by remember { mutableStateOf(false) }
    if (couponDialog) {
        CouponAmountDialog(
            onConfirm = { amount -> sharedViewModel.addCoupon(amount); couponDialog = false },
            onDismiss = { couponDialog = false },
        )
    }

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
}

@Composable
fun HomeScreen(
    categories: ImmutableList<String>,
    selectedCategory: Int,
    menuItems: ImmutableList<MenuItem>,
    basket: ImmutableList<CartItem>,
    orderId: Int,
    total: Int,
    onCategory: (Int) -> Unit,
    onMenuClick: (Int) -> Unit,
    onCouponClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onBasketClick: (Int) -> Unit,
    onHistoryClick: () -> Unit,
    onCheckout: () -> Unit,
) {
    Row(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(Dimens.screenPadding),
        horizontalArrangement = Arrangement.spacedBy(Dimens.gap),
    ) {
        // 좌: 카테고리 + 메뉴 그리드
        Column(Modifier.weight(1f)) {
            CategoryChips(categories, selectedCategory, onCategory)
            Spacer(Modifier.height(8.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(menuItems, key = { it.id }) { item ->
                    MenuTile(item.name, item.price, onClick = { onMenuClick(item.id) })
                }
                item(key = COUPON_TILE_ID) {
                    MenuTile("쿠폰", null, onClick = onCouponClick, style = TileStyle.Coupon)
                }
                item(key = SETTINGS_TILE_ID) {
                    MenuTile("⚙ 설정", null, onClick = onSettingsClick, style = TileStyle.Settings)
                }
            }
        }
        // 우: 장바구니
        BasketPane(orderId, basket, total, onBasketClick, onHistoryClick, onCheckout,
            Modifier.fillMaxHeight().fillMaxWidth(Dimens.basketWidthFraction))
    }
}
```

(아래 보조 Composable을 같은 파일에 추가)

```kotlin
@Composable
private fun CategoryChips(categories: ImmutableList<String>, selected: Int, onSelect: (Int) -> Unit) {
    androidx.compose.foundation.lazy.LazyRow(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        itemsIndexed(categories) { index, name ->
            FilterChip(selected = index == selected, onClick = { onSelect(index) }, label = { Text(name) })
        }
    }
}

@Composable
private fun BasketPane(
    orderId: Int, basket: ImmutableList<CartItem>, total: Int,
    onItemClick: (Int) -> Unit, onHistory: () -> Unit, onCheckout: () -> Unit, modifier: Modifier,
) {
    Surface(modifier, shape = RoundedCornerShape(Dimens.paneRadius), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${orderId}번 주문", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = onHistory) { Text("주문기록") }
            }
            LazyColumn(Modifier.weight(1f)) {
                // 리뷰 #2: 쿠폰은 항상 id=999 라 key=it.id 면 중복 → 인덱스 키 사용.
                itemsIndexed(basket, key = { index, _ -> index }) { _, item ->
                    BasketRow(item.name, item.count, item.count * item.price) { onItemClick(item.id) }
                    HorizontalDivider(color = DividerGray)
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.End) {
                Text("합계 ", style = MaterialTheme.typography.titleMedium)
                Text("%,d원".format(total), style = MaterialTheme.typography.titleMedium, color = Orange)
            }
            Button(
                onClick = onCheckout, enabled = basket.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Orange),
            ) { Text("결제 →") }
        }
    }
}
```

import 보강: `import androidx.compose.foundation.background`, `import androidx.compose.foundation.lazy.grid.itemsIndexed` 대신 `androidx.compose.foundation.lazy.itemsIndexed`(LazyRow 용).

- [ ] **Step 2: CouponAmountDialog (시스템 키보드) 구현** — 같은 파일에 추가

```kotlin
@Composable
private fun CouponAmountDialog(onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("쿠폰 금액 입력") },
        text = {
            OutlinedTextField(
                value = text, onValueChange = { text = it.filter(Char::isDigit) },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { text.toIntOrNull()?.let(onConfirm) }) { Text("담기") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

@androidx.compose.ui.tooling.preview.Preview(widthDp = 900, heightDp = 500)
@Composable
private fun HomeScreenPreview() = HolyBeanTheme {
    HomeScreen(
        categories = MenuCategories.names.toImmutableList(), selectedCategory = 0,
        menuItems = persistentListOf(MenuItem(1001, "아메리카노", 3500, 1, true)),
        basket = persistentListOf(CartItem(1001, "아메리카노", 3500, 2, 7000)),
        orderId = 128, total = 7000,
        onCategory = {}, onMenuClick = {}, onCouponClick = {}, onSettingsClick = {},
        onBasketClick = {}, onHistoryClick = {}, onCheckout = {},
    )
}
```

- [ ] **Step 3: HomeScreen UI 테스트**

```kotlin
package eloom.holybean.ui.home

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import eloom.holybean.data.model.CartItem
import eloom.holybean.data.model.MenuItem
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.junit.Rule
import org.junit.Test

class HomeScreenTest {
    @get:Rule val rule = createComposeRule()

    @Test fun checkoutDisabledWhenBasketEmpty() {
        rule.setContent {
            HomeScreen(
                categories = MenuCategories.names.toImmutableList(), selectedCategory = 0,
                menuItems = persistentListOf(MenuItem(1001, "아메리카노", 3500, 1, true)),
                basket = persistentListOf(), orderId = 1, total = 0,
                onCategory = {}, onMenuClick = {}, onCouponClick = {}, onSettingsClick = {},
                onBasketClick = {}, onHistoryClick = {}, onCheckout = {},
            )
        }
        rule.onNodeWithText("결제 →").assertIsNotEnabled()
    }

    @Test fun menuClickEmitsId() {
        var clickedId = -99
        rule.setContent {
            HomeScreen(
                categories = MenuCategories.names.toImmutableList(), selectedCategory = 0,
                menuItems = persistentListOf(MenuItem(1001, "아메리카노", 3500, 1, true)),
                basket = persistentListOf(CartItem(1001, "아메리카노", 3500, 1, 3500)),
                orderId = 1, total = 3500,
                onCategory = {}, onMenuClick = { clickedId = it }, onCouponClick = {}, onSettingsClick = {},
                onBasketClick = {}, onHistoryClick = {}, onCheckout = {},
            )
        }
        rule.onNodeWithText("아메리카노").performClick()
        assert(clickedId == 1001)
    }
}
```

- [ ] **Step 4: HolyBeanNavHost 의 Home 연결 확인** (Task 6의 자리표시를 실제 `HomeRoute` 호출로 교체했는지 확인)

- [ ] **Step 5: 빌드/테스트/커밋**

Run: `cd android && ./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

```bash
git add android/app/src/main/java/eloom/holybean/ui/home/HomeScreen.kt android/app/src/main/java/eloom/holybean/ui/navigation/ android/app/src/androidTest/java/eloom/holybean/ui/home/HomeScreenTest.kt
git commit -m "feat(home): Compose order screen with menu grid, basket, coupon dialog"
```

## Task 8: MainActivity 를 Compose 단일 진입으로 (임시 공존)

**Files:**
- Modify: `android/app/src/main/java/eloom/holybean/MainActivity.kt`

> Phase 1 끝에서 주문 화면을 실제 구동한다. 기존 Fragment/레이아웃은 Phase 4에서 제거하므로 여기서는 MainActivity만 Compose로 전환한다.

- [ ] **Step 1: MainActivity 교체**

```kotlin
package eloom.holybean

import android.os.Bundle
import android.view.WindowInsets
import android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dagger.hilt.android.AndroidEntryPoint
import eloom.holybean.ui.navigation.HolyBeanNavHost
import eloom.holybean.ui.theme.HolyBeanTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureSystemBars()
        setContent { HolyBeanTheme { HolyBeanNavHost() } }
    }

    private fun configureSystemBars() {
        window.insetsController?.let {
            it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
            it.systemBarsBehavior = BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}
```

- [ ] **Step 2: 실행 검증**

Run: `cd android && ./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL. 기기/에뮬레이터에서 앱 실행 → 주문 화면이 뜨고 메뉴 담기/장바구니/카테고리 동작 확인.

- [ ] **Step 3: 커밋**

```bash
git add android/app/src/main/java/eloom/holybean/MainActivity.kt
git commit -m "feat: single-activity Compose entry point (order screen live)"
```

---

# Phase 2 — 결제(Payment)

## Task 9: PaymentForm 순수 로직 (TDD 핵심)

**Files:**
- Create: `android/app/src/main/java/eloom/holybean/ui/payment/PaymentForm.kt`
- Test: `android/app/src/test/kotlin/eloom/holybean/ui/payment/PaymentFormTest.kt`

- [ ] **Step 1: 실패 테스트 작성**

```kotlin
package eloom.holybean.ui.payment

import eloom.holybean.data.model.CartItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentFormTest {
    private val cart = listOf(CartItem(1001, "아메리카노", 3500, 2, 7000))
    private fun base() = PaymentSelection(
        cupOption = "일회용컵", firstMethod = "현금", ordererName = "",
        splitEnabled = false, secondMethod = null, secondAmountText = "",
    )

    @Test fun `single cash builds one payment method`() {
        val r = PaymentForm.build(base(), cart, total = 7000, orderId = 5, date = "2026-05-23")
        assertTrue(r.isSuccess)
        val order = r.getOrThrow()
        assertEquals(1, order.paymentMethods.size)
        assertEquals("현금", order.paymentMethods[0].type)
        assertEquals(7000, order.paymentMethods[0].amount)
        assertEquals(0, order.creditStatus)
    }

    @Test fun `account transfer requires orderer name`() {
        val r = PaymentForm.build(base().copy(firstMethod = "계좌이체"), cart, 7000, 5, "2026-05-23")
        assertTrue(r.isFailure)
    }

    @Test fun `credit sets creditStatus 1`() {
        val r = PaymentForm.build(base().copy(firstMethod = "외상", ordererName = "홍길동"), cart, 7000, 5, "2026-05-23")
        assertEquals(1, r.getOrThrow().creditStatus)
    }

    @Test fun `split divides remainder to first method`() {
        val sel = base().copy(splitEnabled = true, secondMethod = "계좌이체", secondAmountText = "2000", ordererName = "홍길동")
        val order = PaymentForm.build(sel, cart, 7000, 5, "2026-05-23").getOrThrow()
        assertEquals(2, order.paymentMethods.size)
        assertEquals(5000, order.paymentMethods[0].amount) // 현금 잔액
        assertEquals(2000, order.paymentMethods[1].amount) // 계좌이체
    }

    @Test fun `split remainder must be positive`() {
        val sel = base().copy(splitEnabled = true, secondMethod = "쿠폰", secondAmountText = "7000")
        assertTrue(PaymentForm.build(sel, cart, 7000, 5, "2026-05-23").isFailure)
    }

    @Test fun `second method candidates exclude chosen first`() {
        assertEquals(listOf("계좌이체", "쿠폰", "무료쿠폰"), PaymentForm.secondCandidates("현금"))
        assertEquals(listOf("현금", "쿠폰", "무료쿠폰"), PaymentForm.secondCandidates("계좌이체"))
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "eloom.holybean.ui.payment.PaymentFormTest"`
Expected: FAIL (PaymentForm 미정의)

- [ ] **Step 3: 구현**

```kotlin
package eloom.holybean.ui.payment

import eloom.holybean.data.model.CartItem
import eloom.holybean.data.model.Order
import eloom.holybean.data.model.PaymentMethod

data class PaymentSelection(
    val cupOption: String,
    val firstMethod: String,
    val ordererName: String,
    val splitEnabled: Boolean,
    val secondMethod: String?,
    val secondAmountText: String,
)

object PaymentForm {
    val methods = listOf("현금", "쿠폰", "계좌이체", "외상", "무료쿠폰", "무료제공")
    private val secondPool = listOf("계좌이체", "현금", "쿠폰", "무료쿠폰")
    private fun needsOrderer(m: String?) = m == "계좌이체" || m == "외상"

    fun secondCandidates(first: String): List<String> = secondPool.filter { it != first }

    fun build(sel: PaymentSelection, cart: List<CartItem>, total: Int, orderId: Int, date: String): Result<Order> {
        val first = sel.firstMethod
        if (!sel.splitEnabled) {
            if (needsOrderer(first) && sel.ordererName.isBlank())
                return Result.failure(IllegalStateException("주문자를 입력하세요"))
            return Result.success(order(date, orderId, credit(first), sel.ordererName, cart,
                listOf(PaymentMethod(first, total)), total))
        }
        val second = sel.secondMethod ?: return Result.failure(IllegalStateException("2번째 수단을 선택하세요"))
        val secondAmount = sel.secondAmountText.toIntOrNull()
            ?: return Result.failure(IllegalStateException("올바른 금액이 아닙니다"))
        val remainder = total - secondAmount
        if (remainder <= 0) return Result.failure(IllegalStateException("분할 금액을 확인하세요"))
        if ((needsOrderer(first) || needsOrderer(second)) && sel.ordererName.isBlank())
            return Result.failure(IllegalStateException("주문자를 입력하세요"))
        return Result.success(order(date, orderId, credit(first, second), sel.ordererName, cart,
            listOf(PaymentMethod(first, remainder), PaymentMethod(second, secondAmount)), total))
    }

    private fun credit(vararg m: String) = if (m.any { it == "외상" }) 1 else 0
    private fun order(date: String, num: Int, credit: Int, name: String, items: List<CartItem>,
                      methods: List<PaymentMethod>, total: Int) =
        Order(date, num, credit, name, items, methods, total)
}
```

- [ ] **Step 4: 통과 확인**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "eloom.holybean.ui.payment.PaymentFormTest"`
Expected: PASS

- [ ] **Step 5: 커밋**

```bash
git add android/app/src/main/java/eloom/holybean/ui/payment/PaymentForm.kt android/app/src/test/kotlin/eloom/holybean/ui/payment/PaymentFormTest.kt
git commit -m "feat(payment): pure PaymentForm validation/order-build logic with tests"
```

## Task 10: PaymentMethodTile / SegmentedToggle 컴포넌트

**Files:**
- Create: `android/app/src/main/java/eloom/holybean/ui/components/PaymentMethodTile.kt`
- Create: `android/app/src/main/java/eloom/holybean/ui/components/SegmentedToggle.kt`

- [ ] **Step 1: PaymentMethodTile.kt**

```kotlin
package eloom.holybean.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eloom.holybean.ui.theme.*

@Composable
fun PaymentMethodTile(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick, modifier = modifier.height(46.dp),
        shape = RoundedCornerShape(9.dp),
        color = if (selected) OrangeContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(2.dp, if (selected) Orange else DividerGray),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.bodyMedium,
                color = if (selected) OrangeOnContainer else MaterialTheme.colorScheme.onSurface)
        }
    }
}
```

- [ ] **Step 2: SegmentedToggle.kt**

```kotlin
package eloom.holybean.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.collections.immutable.ImmutableList

@Composable
fun SegmentedToggle(options: ImmutableList<String>, selected: String, onSelect: (String) -> Unit) {
    SingleChoiceSegmentedButtonRow {
        options.forEachIndexed { i, opt ->
            SegmentedButton(
                selected = opt == selected, onClick = { onSelect(opt) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
            ) { Text(opt) }
        }
    }
}
```

- [ ] **Step 3: 빌드/커밋**

Run: `cd android && ./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

```bash
git add android/app/src/main/java/eloom/holybean/ui/components/PaymentMethodTile.kt android/app/src/main/java/eloom/holybean/ui/components/SegmentedToggle.kt
git commit -m "feat(components): payment method tile and segmented toggle"
```

## Task 11: PaymentScreen + Route

**Files:**
- Create: `android/app/src/main/java/eloom/holybean/ui/payment/PaymentScreen.kt`
- Modify: `android/app/src/main/java/eloom/holybean/ui/navigation/HolyBeanNavHost.kt`
- Test: `android/app/src/androidTest/java/eloom/holybean/ui/payment/PaymentScreenTest.kt`

- [ ] **Step 1: PaymentScreen.kt 구현 (stateless + Route)**

```kotlin
package eloom.holybean.ui.payment

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eloom.holybean.data.model.CartItem
import eloom.holybean.ui.components.*
import eloom.holybean.ui.home.HomeViewModel
import eloom.holybean.ui.theme.*
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

@Composable
fun PaymentRoute(sharedViewModel: HomeViewModel, onClose: () -> Unit, onPaid: () -> Unit) {
    val state by sharedViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        sharedViewModel.uiEvent.collect { e ->
            when (e) {
                is HomeViewModel.UiEvent.ShowToast ->
                    android.widget.Toast.makeText(context, e.message, android.widget.Toast.LENGTH_SHORT).show()
                HomeViewModel.UiEvent.NavigateHome -> onPaid()
                HomeViewModel.UiEvent.NavigateToPayment -> Unit
            }
        }
    }
    PaymentScreen(
        orderId = state.orderId,
        items = state.basketItems.toImmutableList(),
        total = state.totalPrice,
        onCancel = onClose,
        onConfirm = { selection ->
            PaymentForm.build(selection, state.basketItems, state.totalPrice, state.orderId, state.currentDate)
                .onSuccess { sharedViewModel.onOrderConfirmed(it, selection.cupOption) }
                .onFailure { android.widget.Toast.makeText(context, it.message, android.widget.Toast.LENGTH_SHORT).show() }
        },
    )
}

@Composable
fun PaymentScreen(
    orderId: Int,
    items: ImmutableList<CartItem>,
    total: Int,
    onCancel: () -> Unit,
    onConfirm: (PaymentSelection) -> Unit,
) {
    var cup by rememberSaveable { mutableStateOf("일회용컵") }
    var first by rememberSaveable { mutableStateOf("현금") }
    var orderer by rememberSaveable { mutableStateOf("") }
    var split by rememberSaveable { mutableStateOf(false) }
    var second by rememberSaveable { mutableStateOf<String?>(null) }
    var secondAmt by rememberSaveable { mutableStateOf("") }

    // 무료제공 선택 시 분할 비활성화
    LaunchedEffect(first) { if (first == "무료제공") { split = false } }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(Dimens.screenPadding)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${orderId}번 주문 · 결제", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = onCancel) { Text("✕ 취소") }
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(Dimens.gap)) {
            // 좌: 요약
            Surface(Modifier.fillMaxWidth(0.38f).fillMaxHeight(), shape = RoundedCornerShape(Dimens.paneRadius),
                color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(12.dp)) {
                    Text("주문 요약", style = MaterialTheme.typography.labelSmall, color = OnSurfaceMuted)
                    LazyColumn(Modifier.weight(1f)) {
                        // 리뷰 #2: 쿠폰 중복 id 대비 인덱스 키
                        itemsIndexed(items, key = { index, _ -> index }) { _, it ->
                            BasketRow(it.name, it.count, it.count * it.price) {}
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("합계", style = MaterialTheme.typography.titleMedium)
                        Text("%,d원".format(total), style = MaterialTheme.typography.titleMedium, color = Orange)
                    }
                }
            }
            // 우: 옵션 (스크롤 영역 + 고정 푸터)
            Surface(Modifier.weight(1f).fillMaxHeight(), shape = RoundedCornerShape(Dimens.paneRadius),
                color = MaterialTheme.colorScheme.surface) {
                Column {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(12.dp)) {
                        Text("컵 선택", style = MaterialTheme.typography.labelSmall, color = OnSurfaceMuted)
                        SegmentedToggle(persistentListOf("일회용컵", "머그컵"), cup) { cup = it }
                        Spacer(Modifier.height(10.dp))
                        Text("결제 수단", style = MaterialTheme.typography.labelSmall, color = OnSurfaceMuted)
                        MethodGrid(PaymentForm.methods.toImmutableList(), first) { first = it }
                        Spacer(Modifier.height(8.dp))
                        if (first != "무료제공") {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Switch(checked = split, onCheckedChange = { split = it })
                                Text("  결제수단 추가 (분할결제)", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        if (split) {
                            val candidates = PaymentForm.secondCandidates(first).toImmutableList()
                            LaunchedEffect(first) { if (second !in candidates) second = candidates.firstOrNull() }
                            Spacer(Modifier.height(6.dp))
                            Text("2번째 결제 수단", style = MaterialTheme.typography.labelSmall, color = OnSurfaceMuted)
                            MethodRow(candidates, second) { second = it }
                            OutlinedTextField(
                                value = secondAmt, onValueChange = { secondAmt = it.filter(Char::isDigit) },
                                label = { Text("${second ?: ""} 금액") }, singleLine = true,
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Text("주문자명", style = MaterialTheme.typography.labelSmall, color = OnSurfaceMuted)
                        OutlinedTextField(orderer, { orderer = it }, singleLine = true,
                            modifier = Modifier.fillMaxWidth())
                    }
                    HorizontalDivider(color = DividerGray)
                    Button(
                        onClick = { onConfirm(PaymentSelection(cup, first, orderer, split, second, secondAmt)) },
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Orange),
                    ) { Text("결제 완료") }
                }
            }
        }
    }
}
```

(보조 그리드 — 같은 파일)

```kotlin
@Composable
private fun MethodGrid(methods: ImmutableList<String>, selected: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        methods.chunked(3).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                rowItems.forEach { m ->
                    PaymentMethodTile(m, m == selected, { onSelect(m) }, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun MethodRow(methods: ImmutableList<String>, selected: String?, onSelect: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        methods.forEach { m -> PaymentMethodTile(m, m == selected, { onSelect(m) }, Modifier.weight(1f)) }
    }
}

@androidx.compose.ui.tooling.preview.Preview(widthDp = 900, heightDp = 500)
@Composable
private fun PaymentPreview() = HolyBeanTheme {
    PaymentScreen(128, persistentListOf(CartItem(1001, "아메리카노", 3500, 2, 7000)), 7000, {}, {})
}
```

import 보강: `import androidx.compose.foundation.background`.

- [ ] **Step 2: NavHost 의 PaymentRoute 연결**

`HolyBeanNavHost.kt` 의 `composable<PaymentDest>` 자리표시를 교체:

```kotlin
            composable<PaymentDest> { entry ->
                eloom.holybean.ui.payment.PaymentRoute(
                    sharedViewModel = entry.sharedOrderViewModel(navController),
                    onClose = { navController.popBackStack() },
                    onPaid = { navController.popBackStack(HomeDest, inclusive = false) },
                )
            }
```

(`popBackStack` 은 NavController 멤버라 별도 import 불필요.)

- [ ] **Step 3: UI 테스트**

```kotlin
package eloom.holybean.ui.payment

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import eloom.holybean.data.model.CartItem
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test

class PaymentScreenTest {
    @get:Rule val rule = createComposeRule()

    @Test fun confirmEmitsCashSelectionByDefault() {
        var sel: PaymentSelection? = null
        rule.setContent {
            PaymentScreen(128, persistentListOf(CartItem(1001, "아메리카노", 3500, 2, 7000)), 7000, {}, { sel = it })
        }
        rule.onNodeWithText("결제 완료").performClick()
        assert(sel?.firstMethod == "현금")
        assert(sel?.cupOption == "일회용컵")
    }
}
```

- [ ] **Step 4: 빌드/검증/커밋**

Run: `cd android && ./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL. 기기에서 주문→결제→확정→홈 복귀 + 영수증 출력 확인.

```bash
git add android/app/src/main/java/eloom/holybean/ui/payment/PaymentScreen.kt android/app/src/main/java/eloom/holybean/ui/navigation/HolyBeanNavHost.kt android/app/src/androidTest/java/eloom/holybean/ui/payment/PaymentScreenTest.kt
git commit -m "feat(payment): full-screen Compose payment with split and orderer"
```

---

# Phase 3 — 주문기록 + 오늘 매출

## Task 12: OrdersViewModel 에 오늘 매출 요약 + 보고서 출력

**Files:**
- Modify: `android/app/src/main/java/eloom/holybean/ui/orderlist/OrdersViewModel.kt`
- Test: `android/app/src/test/kotlin/eloom/holybean/ui/orderlist/OrdersViewModelTest.kt`

> `OrdersViewModel` 은 `OrdersUiState`(ordersList, selectedOrderNumber, selectedOrderTotal, orderDetails, isLoading, deleteStatus) 와 `loadOrdersOfDay/selectOrder/reprint/fetchOrderDetail/deleteOrder` 를 가진다. `FirestoreRepository.getReport(today,today)` 로 매출 요약을 만들고, `ReportPrinter` 로 보고서를 출력한다.

- [ ] **Step 1: 실패 테스트 작성**

```kotlin
    @Test
    fun `loadTodaySummary populates totals`() = runTest {
        coEvery { firestoreRepository.getReport(any(), any()) } returns
            eloom.holybean.data.model.SalesReport(
                menuSales = listOf(eloom.holybean.data.model.ReportDetailItem("아메리카노", 5, 17500)),
                paymentSales = mapOf("총합" to 100000),
            )
        coEvery { firestoreRepository.getOrdersOfDay() } returns arrayListOf(
            eloom.holybean.data.model.OrderItem(1, 5000, "현금", "")
        )
        viewModel.loadTodaySummary()
        advanceUntilIdle()
        val s = viewModel.uiState.value.todaySummary
        assertEquals(100000, s.totalSales)
        assertEquals(1, s.orderCount)
        assertEquals(5, s.drinkCount)
    }
```

- [ ] **Step 2: 상태/메서드 추가**

`OrdersUiState` 에 필드 추가:

```kotlin
        val todaySummary: TodaySummary = TodaySummary(),
```

새 데이터 클래스 + 메서드:

```kotlin
    data class TodaySummary(val totalSales: Int = 0, val orderCount: Int = 0, val drinkCount: Int = 0)

    fun loadTodaySummary() {
        viewModelScope.launch(ioDispatcher) {
            runCatching {
                val report = firestoreRepository.getReport(getCurrentDate(), getCurrentDate())
                val orders = firestoreRepository.getOrdersOfDay()
                TodaySummary(
                    totalSales = report.paymentSales["총합"] ?: 0,
                    orderCount = orders.size,
                    drinkCount = report.menuSales.filter { it.name != "쿠폰" }.sumOf { it.quantity },
                )
            }.onSuccess { sum -> _uiState.update { it.copy(todaySummary = sum) } }
        }
    }
```

> 총 잔수 = 음료 항목 수량 합계(쿠폰 제외). 스펙 정의 반영.

생성자에 `ReportPrinter` 주입 + 보고서 출력 메서드:

```kotlin
    // 생성자 파라미터에 추가:
    //   private val reportPrinter: eloom.holybean.printer.polymorphism.ReportPrinter,

    fun printTodayReport() {
        applicationScope.launch {
            runCatching {
                val today = getCurrentDate()
                val report = firestoreRepository.getReport(today, today)
                val dto = eloom.holybean.data.model.PrinterDTO(today, today, report.paymentSales, report.menuSales)
                piPrintClient.print(reportPrinter.makeCommands(dto))
            }.onFailure { _uiEvent.tryEmit(OrdersUiEvent.ShowToast("보고서 출력 실패: ${it.message}")) }
             .onSuccess { _uiEvent.tryEmit(OrdersUiEvent.ShowToast("보고서 출력 완료")) }
        }
    }
```

`init { loadOrdersOfDay() }` 에 `loadTodaySummary()` 추가.

- [ ] **Step 3: 테스트 통과 확인**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "eloom.holybean.ui.orderlist.OrdersViewModelTest"`
Expected: PASS.

> 기존 테스트 수정 필수: (1) 생성자에 `reportPrinter = mockk(relaxed = true)` 추가. (2) `init` 이 이제 `loadTodaySummary()` 도 호출하므로 `@Before` 에서 `coEvery { firestoreRepository.getReport(any(), any()) } returns SalesReport(emptyList(), mapOf("총합" to 0))` 와 `coEvery { firestoreRepository.getOrdersOfDay() } returns arrayListOf()` 를 스텁(미스텁 시 relaxed mock의 `SalesReport` 접근에서 깨질 수 있음).

- [ ] **Step 4: 커밋**

```bash
git add android/app/src/main/java/eloom/holybean/ui/orderlist/OrdersViewModel.kt android/app/src/test/kotlin/eloom/holybean/ui/orderlist/OrdersViewModelTest.kt
git commit -m "feat(orders): today sales summary and report print in OrdersViewModel"
```

## Task 13: StatChip 컴포넌트

**Files:**
- Create: `android/app/src/main/java/eloom/holybean/ui/components/StatChip.kt`

- [ ] **Step 1: 구현**

```kotlin
package eloom.holybean.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import eloom.holybean.ui.theme.OnSurfaceMuted

@Composable
fun StatChip(label: String, value: String, valueColor: Color = MaterialTheme.colorScheme.onSurface, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = OnSurfaceMuted)
        Text(value, style = MaterialTheme.typography.titleMedium, color = valueColor)
    }
}
```

- [ ] **Step 2: 빌드/커밋**

Run: `cd android && ./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

```bash
git add android/app/src/main/java/eloom/holybean/ui/components/StatChip.kt
git commit -m "feat(components): StatChip for sales summary"
```

## Task 14: OrdersScreen + Route

**Files:**
- Create: `android/app/src/main/java/eloom/holybean/ui/orders/OrdersScreen.kt`
- Modify: `android/app/src/main/java/eloom/holybean/ui/navigation/HolyBeanNavHost.kt`
- Test: `android/app/src/androidTest/java/eloom/holybean/ui/orders/OrdersScreenTest.kt`

- [ ] **Step 1: OrdersScreen.kt 구현 (stateless + Route)**

```kotlin
package eloom.holybean.ui.orders

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eloom.holybean.data.model.OrderItem
import eloom.holybean.data.model.OrdersDetailItem
import eloom.holybean.ui.components.*
import eloom.holybean.ui.orderlist.OrdersViewModel
import eloom.holybean.ui.theme.*
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

@Composable
fun OrdersRoute(onClose: () -> Unit, viewModel: OrdersViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.uiEvent.collect { e ->
            when (e) {
                is OrdersViewModel.OrdersUiEvent.ShowToast ->
                    android.widget.Toast.makeText(context, e.message, android.widget.Toast.LENGTH_SHORT).show()
                OrdersViewModel.OrdersUiEvent.RefreshOrders -> viewModel.loadOrdersOfDay()
            }
        }
    }
    var confirmDelete by remember { mutableStateOf(false) }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("주문 삭제") },
            text = { Text("${state.selectedOrderNumber}번 주문을 삭제하시겠습니까? 복구할 수 없습니다.") },
            confirmButton = { TextButton(onClick = { viewModel.deleteOrder(); confirmDelete = false }) { Text("삭제") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("취소") } },
        )
    }
    OrdersScreen(
        summary = state.todaySummary,
        orders = state.ordersList.toImmutableList(),
        selectedOrderNumber = state.selectedOrderNumber,
        details = state.orderDetails.toImmutableList(),
        selectedTotal = state.selectedOrderTotal,
        onClose = onClose,
        onPrintReport = viewModel::printTodayReport,
        onSelect = { viewModel.selectOrder(it.orderId, it.totalAmount) },
        onReprint = viewModel::reprint,
        onDelete = { confirmDelete = true },
    )
}

@Composable
fun OrdersScreen(
    summary: OrdersViewModel.TodaySummary,
    orders: ImmutableList<OrderItem>,
    selectedOrderNumber: Int,
    details: ImmutableList<OrdersDetailItem>,
    selectedTotal: Int,
    onClose: () -> Unit,
    onPrintReport: () -> Unit,
    onSelect: (OrderItem) -> Unit,
    onReprint: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(Dimens.screenPadding)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("주문기록", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = onClose) { Text("✕ 닫기") }
        }
        // 매출 스트립
        Surface(Modifier.fillMaxWidth().padding(bottom = 10.dp), shape = RoundedCornerShape(Dimens.paneRadius),
            color = MaterialTheme.colorScheme.surface) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                StatChip("오늘 총 판매", "%,d원".format(summary.totalSales), Orange)
                StatChip("총 건수", "${summary.orderCount}건")
                StatChip("총 잔수", "${summary.drinkCount}잔")
                Spacer(Modifier.weight(1f))
                OutlinedButton(onClick = onPrintReport) { Text("보고서 출력") }
            }
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(Dimens.gap)) {
            // 좌: 목록
            Surface(Modifier.fillMaxWidth(0.46f).fillMaxHeight(), shape = RoundedCornerShape(Dimens.paneRadius),
                color = MaterialTheme.colorScheme.surface) {
                LazyColumn(Modifier.padding(12.dp)) {
                    items(orders, key = { it.orderId }) { o ->
                        OrderListItem(o, o.orderId == selectedOrderNumber) { onSelect(o) }
                        Spacer(Modifier.height(7.dp))
                    }
                }
            }
            // 우: 상세
            Surface(Modifier.weight(1f).fillMaxHeight(), shape = RoundedCornerShape(Dimens.paneRadius),
                color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(12.dp)) {
                    Text("${selectedOrderNumber}번 주문", style = MaterialTheme.typography.titleMedium)
                    LazyColumn(Modifier.weight(1f)) {
                        // 리뷰 #2: 상세 항목명 중복 대비 인덱스 키
                        itemsIndexed(details, key = { index, _ -> index }) { _, d -> BasketRow(d.name, d.count, d.subtotal) {} }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("합계", style = MaterialTheme.typography.titleMedium)
                        Text("%,d원".format(selectedTotal), style = MaterialTheme.typography.titleMedium, color = Orange)
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onReprint, modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Orange)) { Text("재출력") }
                        OutlinedButton(onClick = onDelete,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = DangerRed)) { Text("삭제") }
                    }
                }
            }
        }
    }
}

@Composable
private fun OrderListItem(o: OrderItem, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick, shape = RoundedCornerShape(9.dp),
        color = if (selected) OrangeContainer else MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) Orange else DividerGray),
    ) {
        Column(Modifier.fillMaxWidth().padding(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${o.orderId}번", style = MaterialTheme.typography.bodyMedium)
                Text("%,d원".format(o.totalAmount), style = MaterialTheme.typography.bodyMedium)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(o.orderer.ifBlank { "—" }, style = MaterialTheme.typography.labelSmall, color = OnSurfaceMuted)
                Text(o.method, style = MaterialTheme.typography.labelSmall, color = OnSurfaceMuted)
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(widthDp = 900, heightDp = 500)
@Composable
private fun OrdersPreview() = HolyBeanTheme {
    OrdersScreen(
        summary = OrdersViewModel.TodaySummary(1240000, 86, 152),
        orders = persistentListOf(OrderItem(128, 15000, "현금", "홍길동")),
        selectedOrderNumber = 128,
        details = persistentListOf(OrdersDetailItem("아메리카노", 2, 7000)),
        selectedTotal = 15000, onClose = {}, onPrintReport = {}, onSelect = {}, onReprint = {}, onDelete = {},
    )
}
```

- [ ] **Step 2: NavHost 의 OrdersRoute 연결**

```kotlin
        composable<OrdersDest> {
            eloom.holybean.ui.orders.OrdersRoute(onClose = { navController.popBackStack() })
        }
```

- [ ] **Step 3: UI 테스트**

```kotlin
package eloom.holybean.ui.orders

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import eloom.holybean.data.model.OrderItem
import eloom.holybean.data.model.OrdersDetailItem
import eloom.holybean.ui.orderlist.OrdersViewModel
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test

class OrdersScreenTest {
    @get:Rule val rule = createComposeRule()

    @Test fun showsSummaryAndSelectedDetail() {
        rule.setContent {
            OrdersScreen(
                summary = OrdersViewModel.TodaySummary(1240000, 86, 152),
                orders = persistentListOf(OrderItem(128, 15000, "현금", "홍길동")),
                selectedOrderNumber = 128,
                details = persistentListOf(OrdersDetailItem("아메리카노", 2, 7000)),
                selectedTotal = 15000, onClose = {}, onPrintReport = {}, onSelect = {}, onReprint = {}, onDelete = {},
            )
        }
        rule.onNodeWithText("총 잔수").assertIsDisplayed()
        rule.onNodeWithText("152잔").assertIsDisplayed()
        rule.onNodeWithText("재출력").assertIsDisplayed()
    }
}
```

- [ ] **Step 4: 빌드/검증/커밋**

Run: `cd android && ./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL. 기기에서 주문기록 진입/선택/재출력/보고서출력/삭제 확인.

```bash
git add android/app/src/main/java/eloom/holybean/ui/orders/ android/app/src/main/java/eloom/holybean/ui/navigation/HolyBeanNavHost.kt android/app/src/androidTest/java/eloom/holybean/ui/orders/OrdersScreenTest.kt
git commit -m "feat(orders): Compose order history + today sales screen"
```

---

# Phase 4 — 설정 + 개발자 도구 + 희귀화면 포팅 + 정리

## Task 15: SettingsSheet

**Files:**
- Create: `android/app/src/main/java/eloom/holybean/ui/settings/SettingsSheet.kt`
- Modify: `android/app/src/main/java/eloom/holybean/ui/navigation/HolyBeanNavHost.kt`
- Modify: `android/app/src/main/java/eloom/holybean/ui/home/HomeScreen.kt` (설정 콜백 연결 확인)

- [ ] **Step 1: SettingsSheet.kt 구현** (ModalBottomSheet)

```kotlin
package eloom.holybean.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    onDismiss: () -> Unit,
    onMenuMgmt: () -> Unit,
    onCredits: () -> Unit,
    onReport: () -> Unit,
    onDevTools: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Text("설정", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
            SettingsRow("메뉴 관리 (비밀번호)", onMenuMgmt)
            SettingsRow("외상 관리", onCredits)
            SettingsRow("기간 매출 리포트", onReport)
            SettingsRow("개발자 도구", onDevTools)
        }
    }
}

@Composable
private fun SettingsRow(label: String, onClick: () -> Unit) {
    Text(label, style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp))
}
```

- [ ] **Step 2: NavHost 에서 설정 시트 상태 관리**

`HolyBeanNavHost` 내부에 시트 상태 추가 후 Home 의 `onNavigateToSettings` 에 연결:

```kotlin
    var showSettings by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    if (showSettings) {
        eloom.holybean.ui.settings.SettingsSheet(
            onDismiss = { showSettings = false },
            onMenuMgmt = { showSettings = false; navController.navigate(MenuMgmtDest) },
            onCredits = { showSettings = false; navController.navigate(CreditsDest) },
            onReport = { showSettings = false; navController.navigate(ReportDest) },
            onDevTools = { showSettings = false; navController.navigate(DevToolsDest) },
        )
    }
```

Home composable 의 `onNavigateToSettings = { showSettings = true }` 로 연결.

- [ ] **Step 3: 빌드/커밋**

Run: `cd android && ./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

```bash
git add android/app/src/main/java/eloom/holybean/ui/settings/SettingsSheet.kt android/app/src/main/java/eloom/holybean/ui/navigation/HolyBeanNavHost.kt
git commit -m "feat(settings): settings bottom sheet entered from menu tile"
```

## Task 16: DevToolsViewModel (TDD) + DevToolsScreen

**Files:**
- Create: `android/app/src/main/java/eloom/holybean/ui/settings/DevToolsViewModel.kt`
- Create: `android/app/src/main/java/eloom/holybean/ui/settings/DevToolsScreen.kt`
- Test: `android/app/src/test/kotlin/eloom/holybean/ui/settings/DevToolsViewModelTest.kt`
- Modify: `android/app/src/main/java/eloom/holybean/ui/navigation/HolyBeanNavHost.kt`

> 확인된 사실: `PiPrintClient` 에는 `suspend fun print(commands)` 만 있다. `PrintServerApi` 에는 `@GET("health") suspend fun health(): Response<Unit>` 가 이미 존재한다. 따라서 `PiPrintClient` 에 `checkHealth()`/`printTestReceipt()` 래퍼를 먼저 추가한다.

- [ ] **Step 1: PiPrintClient 에 checkHealth / printTestReceipt 추가**

`android/app/src/main/java/eloom/holybean/printer/PiPrintClient.kt` 에 메서드 추가:

```kotlin
    /** /health 핑. 예외/실패는 false. */
    suspend fun checkHealth(): Boolean = withContext(printerDispatcher) {
        runCatching { api.health().isSuccessful }.getOrDefault(false)
    }

    /** 진단용 테스트 영수증 1장 출력. */
    suspend fun printTestReceipt() = print(
        listOf(
            PrintCommandDto(type = "text", content = "HolyBean 테스트 출력", align = "center", bold = true),
            PrintCommandDto(type = "text", content = java.time.LocalDateTime.now().toString(), align = "center"),
            PrintCommandDto(type = "cut"),
        )
    )
```

- [ ] **Step 2: 실패 테스트 작성**

```kotlin
package eloom.holybean.ui.settings

import eloom.holybean.printer.PiPrintClient
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class DevToolsViewModelTest {
    private val pi: PiPrintClient = mockk(relaxed = true)

    @Test fun `health success sets printer ok`() = runTest {
        coEvery { pi.checkHealth() } returns true
        val vm = DevToolsViewModel(pi, UnconfinedTestDispatcher())
        vm.refresh()
        advanceUntilIdle()
        assertEquals(true, vm.uiState.value.printerOk)
    }
}
```

- [ ] **Step 3: DevToolsViewModel 구현**

```kotlin
package eloom.holybean.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import eloom.holybean.BuildConfig
import eloom.holybean.printer.PiPrintClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DevToolsViewModel @Inject constructor(
    private val piPrintClient: PiPrintClient,
    @javax.inject.Named("IO") private val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher,
) : ViewModel() {
    data class State(val printerOk: Boolean? = null, val printerUrl: String = BuildConfig.PRINT_SERVER_URL)
    private val _uiState = MutableStateFlow(State())
    val uiState: StateFlow<State> = _uiState.asStateFlow()

    fun refresh() {
        viewModelScope.launch(ioDispatcher) {
            val ok = runCatching { piPrintClient.checkHealth() }.getOrDefault(false)
            _uiState.update { it.copy(printerOk = ok) }
        }
    }

    fun testPrint() {
        viewModelScope.launch(ioDispatcher) { runCatching { piPrintClient.printTestReceipt() } }
    }
}
```

> `viewModelScope.launch` 를 그냥 쓰면 `Dispatchers.Main` 으로 떨어져 JVM 단위 테스트에서 깨진다. 주입된 `ioDispatcher` 로 launch 해야 `UnconfinedTestDispatcher` 로 테스트 가능.

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "eloom.holybean.ui.settings.DevToolsViewModelTest"`
Expected: PASS

- [ ] **Step 5: DevToolsScreen 구현**

```kotlin
package eloom.holybean.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eloom.holybean.ui.theme.HolyBeanTheme

@Composable
fun DevToolsRoute(onClose: () -> Unit, vm: DevToolsViewModel = hiltViewModel()) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.refresh() }
    DevToolsScreen(state, onClose, vm::refresh, vm::testPrint)
}

@Composable
fun DevToolsScreen(state: DevToolsViewModel.State, onClose: () -> Unit, onRefresh: () -> Unit, onTestPrint: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("개발자 도구", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = onClose) { Text("닫기") }
        }
        Spacer(Modifier.height(12.dp))
        HealthRow("Pi 프린터 (/health)", state.printerOk)
        Text("프린터 서버 URL: ${state.printerUrl}", style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(vertical = 8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onRefresh) { Text("새로고침") }
            Button(onClick = onTestPrint) { Text("테스트 영수증 출력") }
        }
    }
}

@Composable
private fun HealthRow(label: String, ok: Boolean?) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(
            when (ok) { true -> Color(0xFF22C55E); false -> Color(0xFFEF4444); null -> Color(0xFFBBBBBB) }))
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}
```

import: `import androidx.compose.foundation.background`.

- [ ] **Step 6: NavHost 연결 + 커밋**

```kotlin
        composable<DevToolsDest> {
            eloom.holybean.ui.settings.DevToolsRoute(onClose = { navController.popBackStack() })
        }
```

Run: `cd android && ./gradlew :app:assembleDebug` → BUILD SUCCESSFUL

```bash
git add android/app/src/main/java/eloom/holybean/ui/settings/ android/app/src/test/kotlin/eloom/holybean/ui/settings/ android/app/src/main/java/eloom/holybean/ui/navigation/HolyBeanNavHost.kt
git commit -m "feat(devtools): Pi health check, URL, test print"
```

## Task 17: 기간 매출 리포트 포팅 (ReportScreen)

**Files:**
- Create: `android/app/src/main/java/eloom/holybean/ui/report/ReportScreen.kt`
- Modify: `android/app/src/main/java/eloom/holybean/ui/navigation/HolyBeanNavHost.kt`

> 기존 `ReportViewModel`(`loadReportData(start,end)`, `printReport()`, `reportData: Map<String,Int>`, `reportDetailData: List<ReportDetailItem>`, `reportTitle`) 재사용. 날짜 선택은 Compose `DatePickerDialog` 대신 안드로이드 `android.app.DatePickerDialog` 를 `LocalContext` 로 띄운다(기능 동일).

- [ ] **Step 1: ReportScreen.kt 구현**

```kotlin
package eloom.holybean.ui.report

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eloom.holybean.data.model.ReportDetailItem
import eloom.holybean.ui.components.StatChip
import eloom.holybean.ui.theme.Orange
import java.util.Calendar

@Composable
fun ReportRoute(onClose: () -> Unit, vm: ReportViewModel = hiltViewModel()) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val today = remember { java.time.LocalDate.now().toString() }
    var start by rememberSaveable { mutableStateOf(today) }
    var end by rememberSaveable { mutableStateOf(today) }
    val context = LocalContext.current

    fun pick(onPicked: (String) -> Unit) {
        val c = Calendar.getInstance()
        DatePickerDialog(context, { _, y, m, d -> onPicked(vm.formatDate(y, m, d)) },
            c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("기간 매출 리포트", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = onClose) { Text("닫기") }
        }
        Row(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { pick { start = it } }) { Text(start) }
            OutlinedButton(onClick = { pick { end = it } }) { Text(end) }
            Button(onClick = { vm.loadReportData(start, end) }) { Text("조회") }
            OutlinedButton(onClick = { vm.printReport() }) { Text("출력") }
        }
        Text(state.reportTitle, style = MaterialTheme.typography.bodyMedium)
        Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            StatChip("총합", "%,d".format(state.reportData["총합"] ?: 0), Orange)
            StatChip("현금", "%,d".format(state.reportData["현금"] ?: 0))
            StatChip("계좌이체", "%,d".format(state.reportData["계좌이체"] ?: 0))
            StatChip("쿠폰", "%,d".format(state.reportData["쿠폰"] ?: 0))
        }
        LazyColumn(Modifier.weight(1f)) {
            items(state.reportDetailData, key = { it.name }) { d -> MenuSalesRow(d) }
        }
    }
}

@Composable
private fun MenuSalesRow(d: ReportDetailItem) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(d.name, style = MaterialTheme.typography.bodyMedium)
        Text("${d.quantity}개", style = MaterialTheme.typography.bodyMedium)
        Text("%,d".format(d.subtotal), style = MaterialTheme.typography.bodyMedium)
    }
}
```

- [ ] **Step 2: NavHost 연결**

```kotlin
        composable<ReportDest> {
            eloom.holybean.ui.report.ReportRoute(onClose = { navController.popBackStack() })
        }
```

- [ ] **Step 3: 빌드/커밋**

Run: `cd android && ./gradlew :app:assembleDebug` → BUILD SUCCESSFUL

```bash
git add android/app/src/main/java/eloom/holybean/ui/report/ReportScreen.kt android/app/src/main/java/eloom/holybean/ui/navigation/HolyBeanNavHost.kt
git commit -m "feat(report): Compose period sales report (ported)"
```

## Task 18: 외상 관리 포팅 (CreditsScreen)

**Files:**
- Create: `android/app/src/main/java/eloom/holybean/ui/credits/CreditsScreen.kt`
- Modify: `android/app/src/main/java/eloom/holybean/ui/navigation/HolyBeanNavHost.kt`

> 확인된 `CreditsUiState` 필드: `creditsList: List<CreditItem>`, `selectedOrderNumber`, `selectedOrderTotal`, `selectedOrderDate`, `orderDetails: List<OrdersDetailItem>`. `CreditItem(orderId, totalAmount, date, orderer)`. 메서드: `loadCredits()`, `selectOrder(num, total, date)`(상세는 자동조회 안 함), `fetchOrderDetail()`(선택 후 호출), `handleDeleteButton()`(=외상 결제완료 처리). 이벤트: `ShowToast`, `RefreshCredits`. **목록 항목 클릭 시 `selectOrder(...)` → `fetchOrderDetail()` 를 연달아 호출**해야 우측 상세가 채워진다.

- [ ] **Step 1: CreditsScreen.kt 구현** (OrdersScreen 패턴 재사용, 외상 목록 + 상세 + "결제완료 처리" 버튼)

```kotlin
package eloom.holybean.ui.credits

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eloom.holybean.ui.components.BasketRow
import eloom.holybean.ui.components.StatChip
import kotlinx.collections.immutable.toImmutableList

@Composable
fun CreditsRoute(onClose: () -> Unit, vm: CreditsViewModel = hiltViewModel()) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) {
        vm.uiEvent.collect { e ->
            when (e) {
                is CreditsViewModel.CreditsUiEvent.ShowToast ->
                    android.widget.Toast.makeText(context, e.message, android.widget.Toast.LENGTH_SHORT).show()
                CreditsViewModel.CreditsUiEvent.RefreshCredits -> vm.loadCredits()
            }
        }
    }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("외상 관리", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = onClose) { Text("닫기") }
        }
        Row(Modifier.weight(1f).padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(Modifier.fillMaxWidth(0.46f).fillMaxHeight(), color = MaterialTheme.colorScheme.surface) {
                LazyColumn(Modifier.padding(12.dp)) {
                    items(state.creditsList.toImmutableList(), key = { it.orderId }) { c ->
                        Column(
                            Modifier.fillMaxWidth()
                                .clickable { vm.selectOrder(c.orderId, c.totalAmount, c.date); vm.fetchOrderDetail() }
                                .padding(vertical = 8.dp)
                        ) {
                            Text("${c.orderId}번 · ${c.orderer}", style = MaterialTheme.typography.bodyMedium)
                            Text("%,d원 · ${c.date}".format(c.totalAmount), style = MaterialTheme.typography.labelSmall)
                        }
                        HorizontalDivider()
                    }
                }
            }
            Surface(Modifier.weight(1f).fillMaxHeight(), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(12.dp)) {
                    Text("상세", style = MaterialTheme.typography.titleMedium)
                    LazyColumn(Modifier.weight(1f)) {
                        items(state.orderDetails.toImmutableList(), key = { it.name }) {
                            BasketRow(it.name, it.count, it.subtotal) {}
                        }
                    }
                    Button(onClick = { vm.handleDeleteButton() }, modifier = Modifier.fillMaxWidth()) {
                        Text("외상 결제완료 처리")
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 2: NavHost 연결**

```kotlin
        composable<CreditsDest> {
            eloom.holybean.ui.credits.CreditsRoute(onClose = { navController.popBackStack() })
        }
```

- [ ] **Step 3: 빌드/커밋**

Run: `cd android && ./gradlew :app:assembleDebug` → BUILD SUCCESSFUL

```bash
git add android/app/src/main/java/eloom/holybean/ui/credits/CreditsScreen.kt android/app/src/main/java/eloom/holybean/ui/navigation/HolyBeanNavHost.kt
git commit -m "feat(credits): Compose credits management (ported)"
```

## Task 19: 메뉴 관리 포팅 (MenuManagementScreen, 비밀번호 + 드래그 정렬)

**Files:**
- Create: `android/app/src/main/java/eloom/holybean/ui/menumanagement/MenuManagementScreen.kt`
- Modify: `android/app/src/main/java/eloom/holybean/ui/navigation/HolyBeanNavHost.kt`

> 기존 `MenuManagementViewModel`(`UiState`, `isPasswordSessionVerified()`, `markPasswordSessionAsVerified()`, `onCategorySelected(index)`, `moveItem(from,to)`, `saveMenuOrder()`, `addMenu(...)`, `updateMenu(...)`, `toggleMenuInUse(item)`, `getNextAvailableId()`, `getNextAvailablePlacement()`) 재사용. 드래그 정렬은 `sh.calvin.reorderable` 라이브러리(Task 1에서 추가)로 구현.

- [ ] **Step 1: 비밀번호 게이트 + 목록 화면 구현**

```kotlin
package eloom.holybean.ui.menumanagement

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import kotlinx.collections.immutable.toImmutableList
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

private const val MENU_PASSWORD = "1031" // PasswordDialog.correctPassword 와 동일 (확인됨)

@Composable
fun MenuManagementRoute(onClose: () -> Unit, vm: MenuManagementViewModel = hiltViewModel()) {
    var unlocked by remember { mutableStateOf(vm.isPasswordSessionVerified()) }
    if (!unlocked) {
        PasswordGate(onPass = { vm.markPasswordSessionAsVerified(); unlocked = true }, onCancel = onClose)
        return
    }
    val state by vm.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        vm.moveItem(from.index, to.index)
    }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("메뉴 관리", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = { vm.saveMenuOrder() }) { Text("순서 저장") }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = onClose) { Text("닫기") }
        }
        // 카테고리 칩(기본 선택 = ICE커피, index 1). 전체(0)는 정렬 의미가 없어 1~5만 노출.
        androidx.compose.foundation.lazy.LazyRow(
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(5.dp),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            items(eloom.holybean.ui.home.MenuCategories.names.drop(1)) { name ->
                val idx = eloom.holybean.ui.home.MenuCategories.names.indexOf(name)
                FilterChip(selected = idx == state.selectedCategoryIndex, onClick = { vm.onCategorySelected(idx) },
                    label = { Text(name) })
            }
        }
        LazyColumn(state = listState, modifier = Modifier.weight(1f).padding(top = 10.dp)) {
            items(state.filteredMenuItems.toImmutableList(), key = { it.id }) { item ->
                ReorderableItem(reorderState, key = item.id) {
                    ListItem(
                        headlineContent = { Text(item.name) },
                        trailingContent = { Text("%,d".format(item.price)) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun PasswordGate(onPass: () -> Unit, onCancel: () -> Unit) {
    var pw by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("비밀번호 입력") },
        text = { OutlinedTextField(pw, { pw = it }, visualTransformation = PasswordVisualTransformation(), singleLine = true) },
        confirmButton = { TextButton(onClick = { if (pw == MENU_PASSWORD) onPass() }) { Text("확인") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("취소") } },
    )
}
```

> 확인된 사실: `UiState(allMenuItems, filteredMenuItems, selectedCategoryIndex=1 기본 ICE커피, isLoading)`, 비밀번호 `1031`, `UiEvent`(`ShowToast`, `RefreshMenu`). `moveItem(from, to)` 는 **현재 필터된 목록의 위치 인덱스**를 받는다. 추가/수정 다이얼로그(`addMenu`/`updateMenu`/`toggleMenuInUse`)는 기존 `MenuAddDialog`/`MenuEditDialog` 의 입력 항목(이름·가격)을 그대로 Compose `AlertDialog`(Step 2) 로 옮긴다.

- [ ] **Step 2: 추가/수정 다이얼로그 구현** (기존 MenuAddDialog/MenuEditDialog 의 필드 그대로: 이름, 가격)

```kotlin
@Composable
fun MenuEditDialog(initialName: String, initialPrice: String, onConfirm: (String, Int) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initialName) }
    var price by remember { mutableStateOf(initialPrice) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("메뉴") },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text("이름") }, singleLine = true)
                OutlinedTextField(price, { price = it.filter(Char::isDigit) }, label = { Text("가격") }, singleLine = true)
            }
        },
        confirmButton = { TextButton(onClick = { price.toIntOrNull()?.let { onConfirm(name, it) } }) { Text("저장") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}
```

- [ ] **Step 3: NavHost 연결**

```kotlin
        composable<MenuMgmtDest> {
            eloom.holybean.ui.menumanagement.MenuManagementRoute(onClose = { navController.popBackStack() })
        }
```

- [ ] **Step 4: 빌드/검증/커밋**

Run: `cd android && ./gradlew :app:assembleDebug` → BUILD SUCCESSFUL. 기기에서 비밀번호→정렬→저장→추가/수정 확인.

```bash
git add android/app/src/main/java/eloom/holybean/ui/menumanagement/MenuManagementScreen.kt android/app/src/main/java/eloom/holybean/ui/navigation/HolyBeanNavHost.kt
git commit -m "feat(menumgmt): Compose menu management with password and reorder (ported)"
```

## Task 20: 레거시 제거 & 최종 검증

**Files:**
- Delete: 모든 `ui/**/​*Fragment.kt`, `ui/**/*Adapter.kt`, `ui/home/OrderDialog.kt`, `ui/RvCustomDesign.kt`, `interfaces/MainActivityListener.kt`, `interfaces/HomeFunctions.kt`, `interfaces/OrdersFragmentFunction.kt`, `interfaces/OrderDialogListener.kt`(HomeViewModel 의 `: OrderDialogListener` 도 제거), `ui/menumanagement/MenuAddDialog.kt`/`MenuEditDialog.kt`/`PasswordDialog.kt`
- Delete: `res/layout/*.xml`, `res/menu/drawer_menu.xml`, `res/layout/nav_header.xml`
- Modify: `android/app/build.gradle.kts` (dataBinding/viewBinding 비활성화)

- [ ] **Step 1: HomeViewModel 에서 OrderDialogListener 의존 제거**

`class HomeViewModel(...) : ViewModel(), OrderDialogListener` → `: ViewModel()`. `override fun onOrderConfirmed(...)` 의 `override` 키워드 제거(일반 public 함수로 유지 — PaymentRoute 가 호출).

- [ ] **Step 2: Fragment/Adapter/Dialog/레이아웃 삭제**

```bash
cd android/app/src/main
rm java/eloom/holybean/ui/home/HomeFragment.kt \
   java/eloom/holybean/ui/home/MenuAdapter.kt java/eloom/holybean/ui/home/CartAdapter.kt \
   java/eloom/holybean/ui/home/OrderDialog.kt \
   java/eloom/holybean/ui/orderlist/OrdersFragment.kt java/eloom/holybean/ui/orderlist/OrdersAdapter.kt java/eloom/holybean/ui/orderlist/OrdersDetailAdapter.kt \
   java/eloom/holybean/ui/report/ReportFragment.kt java/eloom/holybean/ui/report/ReportDetailAdapter.kt \
   java/eloom/holybean/ui/credits/CreditsFragment.kt java/eloom/holybean/ui/credits/CreditsAdapter.kt \
   java/eloom/holybean/ui/menumanagement/MenuManagementFragment.kt java/eloom/holybean/ui/menumanagement/MenuAdapter.kt \
   java/eloom/holybean/ui/menumanagement/MenuAddDialog.kt java/eloom/holybean/ui/menumanagement/MenuEditDialog.kt java/eloom/holybean/ui/menumanagement/PasswordDialog.kt \
   java/eloom/holybean/ui/RvCustomDesign.kt \
   java/eloom/holybean/interfaces/MainActivityListener.kt java/eloom/holybean/interfaces/HomeFunctions.kt \
   java/eloom/holybean/interfaces/OrdersFragmentFunction.kt java/eloom/holybean/interfaces/OrderDialogListener.kt
rm -f res/layout/*.xml res/menu/drawer_menu.xml
```

- [ ] **Step 3: build.gradle.kts 에서 ViewBinding/DataBinding 비활성화**

`buildFeatures {}` 에서 `dataBinding = true`, `viewBinding = true` 제거(또는 false). 파일 끝의 `viewBinding { enable = true }` 블록 삭제.

- [ ] **Step 4: 전체 빌드 & 테스트**

Run: `cd android && ./gradlew :app:assembleDebug :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, 모든 단위 테스트 PASS. 컴파일 에러(삭제된 심볼 참조)가 나면 해당 참조를 Compose 경로로 교체.

- [ ] **Step 5: 기기 스모크 테스트**

기기/에뮬레이터에서: 주문 담기→쿠폰→결제(단일/분할)→영수증, 주문기록 진입→재출력→보고서출력→삭제, 설정→메뉴관리(비번)/외상/기간리포트/개발자도구. 모두 동작 확인.

- [ ] **Step 6: 커밋**

```bash
git add -A
git commit -m "refactor: remove Fragments/XML/ViewBinding after Compose migration"
```

---

## 자체 검토 메모 (스펙 대비)

- 주문 화면(헤더 없음, 메뉴 그리드+가격, 쿠폰/설정 타일, 장바구니 'N번 주문'/'메뉴명 N개', 합계 오렌지, 결제 버튼, 주문기록 버튼) → Task 4,7,8 ✅
- 쿠폰(메뉴 타일+시스템 키보드, 항상 +) → Task 7 ✅
- 결제(전체화면, 좌 요약/우 옵션, 6수단, 주문자명 상시, 분할 토글·OFF 무스크롤·2번째 남은 3개 1줄, 무료제공 분할 비활성, 검증) → Task 9,10,11 ✅
- 주문기록+오늘매출(2-pane, 검색 없음, 우측 번호+항목+합계+재출력/삭제, 매출 스트립 총판매/총건수/총잔수+보고서출력) → Task 12,13,14 ✅
- 설정(마지막 메뉴 타일→시트) + 개발자도구(Pi health/URL/테스트출력) → Task 15,16 ✅
- 희귀화면(기간리포트/외상/메뉴관리) 기능 포팅+테마 → Task 17,18,19 ✅
- 드로어/Fragment/XML 제거, 단일 Activity Compose → Task 8,20 ✅
- 아키텍처(상태 호이스팅, collectAsStateWithLifecycle, 타입세이프 Nav, ImmutableList, 순수로직 TDD) → 전 Task 규칙 적용 ✅

## 비판적 리뷰 보정 (반영 완료)

- **#1 주문 후 상태 리셋:** 공유 ViewModel이라 결제 후 장바구니/주문번호가 안 지워지는 버그 → Task 5 Step 4·5 에서 `onOrderConfirmed` 성공 시 `basketItems` 비우고 `refreshOrderNumber()` 호출 + 회귀 테스트.
- **#2 LazyColumn 키 중복:** 쿠폰 id=999 중복으로 인한 크래시 → Task 7·11·14 의 장바구니/요약/상세 리스트를 인덱스 키(`itemsIndexed`)로 변경.
- **#3 내비 이벤트 재전송:** `_uiEvent` `replay=1` → `0` 으로(Task 5 Step 3). Home 재구성 시 결제로 재진입/중복 pop 방지.
- **#4 플레이스홀더 제거:** 비밀번호 `1031`(확정), `CreditsUiState`/`MenuManagement UiState` 실제 필드명 반영, 외상 `selectOrder→fetchOrderDetail` 배선, `PiPrintClient.checkHealth()/printTestReceipt()` 구체 구현(Task 16 Step 1) + DevToolsViewModel 디스패처 주입으로 테스트 가능화.
- **#5 네이밍 충돌:** 라우트 object를 `*Dest`, Composable 진입점을 `*Route` 로 분리(alias 해킹 제거).
- **#6 테스트 게이트:** 차단 게이트 = 로직 단위 테스트(JVM) + `assembleDebug`. Compose UI 테스트는 비차단.
- **#7 선행 조건:** Firestore 마이그레이션의 `lambdaRepository` 잔여 참조 정리를 Phase 1 선행 조건으로 명시.
