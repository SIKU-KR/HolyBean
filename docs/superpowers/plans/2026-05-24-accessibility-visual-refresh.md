# POS 접근성 + 시각 개선 + 목업 정합성 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** HolyBean Compose UI에 Google 접근성 기준(터치 56/48dp·폰트 ≥14sp·대비 4.5:1·배율 고정)과 시각 개선(elevation·폰트 위계·브랜드 선택색·사각 버튼)을 적용하고, 최종 목업과의 차이를 정합한다(개발자도구 확장·분할결제 분배 포함).

**Architecture:** 변경의 중심은 테마 토큰(`Type`/`Color`/`Dimens`/`Theme`) 중앙화다. 토큰을 먼저 바꿔 전 화면에 전파하고, 컴포넌트→화면 순으로 목업에 맞춘다. 로직이 있는 두 기능(분할결제 분배 계산, 개발자도구 상태 점검)은 순수 함수/주입형 인터페이스로 빼서 단위 테스트한다.

**Tech Stack:** Kotlin, Jetpack Compose (Material3), Hilt, kotlinx-coroutines, JUnit4 + MockK + coroutines-test. 빌드: Gradle (`android/` 루트, `:app` 모듈).

**Spec:** `docs/superpowers/specs/2026-05-24-accessibility-standards-design.md`
**Mockups (visual SoT):** `.superpowers/brainstorm/69341-1779542427/content/{home-final-v4,payment-v5,orders-v3,settings-v2}.html`

---

## 공통: 빌드 & 테스트 명령

모든 Gradle 호출은 `android/` 디렉터리에서, JDK 17을 지정해 실행한다.

```bash
cd /Users/benn/dev/personal/HolyBean/android
# 컴파일만(빠른 검증)
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:compileDebugKotlin
# 단위 테스트
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:testDebugUnitTest
# 단일 테스트 클래스
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:testDebugUnitTest --tests "eloom.holybean.ui.payment.PaymentFormTest"
# 전체 빌드(UI 변경 최종 검증)
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:assembleDebug
```

**중요(정합 규칙):** 목업은 작은 폰트(9~16px)와 흰 글자 on 오렌지를 쓰지만, **접근성이 우선**한다 — 폰트는 sp 스케일(≥14sp)을 따르고, 솔리드 오렌지 위 텍스트는 흰색이 아닌 진한 글자(#222222)를 쓴다. 솔리드 오렌지 채움/테두리/그림자/구성은 목업을 따른다.

---

## Phase 1 — 접근성 토큰 (테마 중앙화)

### Task 1: Typography 스케일 + 웨이트

**Files:**
- Modify: `android/app/src/main/java/eloom/holybean/ui/theme/Type.kt`

- [ ] **Step 1: `HolyBeanTypography`를 sp 스케일 + 웨이트로 교체**

`Type.kt`의 `HolyBeanTypography` 정의를 아래로 교체한다(`Pretendard`·import는 유지, `FontWeight`는 이미 import됨).

```kotlin
val HolyBeanTypography = Typography(
    titleLarge  = TextStyle(fontFamily = Pretendard, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp),
    titleMedium = TextStyle(fontFamily = Pretendard, fontWeight = FontWeight.Bold,      fontSize = 18.sp),
    bodyLarge   = TextStyle(fontFamily = Pretendard, fontWeight = FontWeight.Medium,    fontSize = 16.sp),
    bodyMedium  = TextStyle(fontFamily = Pretendard, fontWeight = FontWeight.Medium,    fontSize = 15.sp),
    labelSmall  = TextStyle(fontFamily = Pretendard, fontWeight = FontWeight.Medium,    fontSize = 14.sp),
)
```

- [ ] **Step 2: 컴파일 검증**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add android/app/src/main/java/eloom/holybean/ui/theme/Type.kt
git commit -m "feat(a11y): font scale floor 14sp + weight hierarchy"
```

---

### Task 2: Color 토큰 (대비 통과 색 + 상태/강조 토큰)

**Files:**
- Modify: `android/app/src/main/java/eloom/holybean/ui/theme/Color.kt`

- [ ] **Step 1: `Color.kt` 전체를 아래로 교체**

```kotlin
package eloom.holybean.ui.theme

import androidx.compose.ui.graphics.Color

val Orange = Color(0xFFFF7F00)              // Primary (브랜드)
val OrangeLight = Color(0xFFFFA347)         // 점선 테두리 등 보조
val OrangeContainer = Color(0xFFFFF3E4)     // 옅은 선택 컨테이너
val OrangeOnContainer = Color(0xFFC2691A)   // 큰/볼드 강조 (3:1)
val OrangeText = Color(0xFF9A5412)          // 작은 강조 텍스트 (흰/컨테이너 위 4.5:1)
val ScreenBg = Color(0xFFE8E9EB)            // 회색 배경
val Surface = Color(0xFFFFFFFF)             // 흰 패널
val SettingsTileBg = Color(0xFFEEEEEE)      // 설정 타일 배경
val OrderSelectedBg = Color(0xFFFFF8F0)     // 주문 목록 선택 배경 (목업)
val OnSurface = Color(0xFF222222)
val OnSurfaceMuted = Color(0xFF767676)      // 흰 배경 본문 4.5:1
val DangerRed = Color(0xFFD9534F)
val DividerGray = Color(0xFFEEEEEE)
val StatusOk = Color(0xFF22C55E)
val StatusError = Color(0xFFEF4444)
val StatusUnknown = Color(0xFF9E9E9E)       // 비텍스트 3:1 (점)
```

- [ ] **Step 2: 컴파일 검증**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL (기존 참조 토큰명 유지, 추가 토큰은 미사용 경고만)

- [ ] **Step 3: Commit**

```bash
git add android/app/src/main/java/eloom/holybean/ui/theme/Color.kt
git commit -m "feat(a11y): contrast-passing muted/accent colors + status/selection tokens"
```

---

### Task 3: `onPrimary`를 진한 글자로

**Files:**
- Modify: `android/app/src/main/java/eloom/holybean/ui/theme/Theme.kt:9`

- [ ] **Step 1: `onPrimary` 변경**

`Theme.kt`의 `HolyBeanColors` 안 `onPrimary = Color(0xFFFFFFFF),` 줄을 아래로 교체한다.

```kotlin
    onPrimary = OnSurface,   // 오렌지(primary) 위 텍스트는 진한 색 (대비 6.3:1)
```

- [ ] **Step 2: 컴파일 검증**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add android/app/src/main/java/eloom/holybean/ui/theme/Theme.kt
git commit -m "feat(a11y): dark text on primary (orange) for contrast"
```

---

### Task 4: Dimens — 터치 타깃 + 간격·라운드·elevation 스케일

**Files:**
- Modify: `android/app/src/main/java/eloom/holybean/ui/theme/Dimens.kt`

- [ ] **Step 1: `Dimens.kt` 전체를 아래로 교체**

기존 이름(`gap`/`screenPadding`/`tileRadius`/`paneRadius`/`basketWidthFraction`)은 호출부 호환을 위해 새 스케일 값으로 alias 유지한다. 빌드는 계속 green.

```kotlin
package eloom.holybean.ui.theme

import androidx.compose.ui.unit.dp

object Dimens {
    // 터치 타깃 (접근성)
    val minTouchTarget = 48.dp
    val primaryTouchTarget = 56.dp

    // 간격 스케일
    val spaceXs = 4.dp
    val spaceSm = 8.dp
    val spaceMd = 12.dp
    val spaceLg = 16.dp

    // 라운드 스케일 (버튼은 목업보다 사각 — 사용자 선호 override)
    val radiusButton = 6.dp
    val radiusTile = 10.dp
    val radiusPane = 14.dp

    // elevation
    val paneElevation = 2.dp
    val tileElevation = 1.dp

    // 기존 이름 alias (값은 새 스케일에 정렬)
    val gap = spaceSm                 // 8.dp (was 10.dp)
    val screenPadding = spaceMd       // 12.dp
    val tileRadius = radiusTile       // 10.dp (was 8.dp)
    val paneRadius = radiusPane       // 14.dp (was 10.dp)
    val basketWidthFraction = 0.38f
}
```

- [ ] **Step 2: 컴파일 검증**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add android/app/src/main/java/eloom/holybean/ui/theme/Dimens.kt
git commit -m "feat(ui): add touch-target/spacing/radius/elevation token scale"
```

---

### Task 5: 폰트 배율 1.0 고정 + 테마 Shapes

**Files:**
- Modify: `android/app/src/main/java/eloom/holybean/ui/theme/Theme.kt`

- [ ] **Step 1: `Theme.kt`를 아래로 교체**

`HolyBeanColors`는 그대로 두고(단 Task 3의 `onPrimary = OnSurface` 반영된 상태), import·Shapes·`HolyBeanTheme`를 추가/교체한다.

```kotlin
package eloom.holybean.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

private val HolyBeanColors = lightColorScheme(
    primary = Orange,
    onPrimary = OnSurface,
    primaryContainer = OrangeContainer,
    onPrimaryContainer = OrangeOnContainer,
    background = ScreenBg,
    onBackground = OnSurface,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = ScreenBg,
    error = DangerRed,
)

// 사각형에 가까운 라운드 (Card/Surface/Dialog 등 테마 기본).
// 주의: Material3 Button은 테마 shapes를 읽지 않으므로 버튼에는 호출부에서
// shape = RoundedCornerShape(Dimens.radiusButton) 를 명시한다.
private val HolyBeanShapes = Shapes(
    extraSmall = RoundedCornerShape(Dimens.radiusButton),
    small = RoundedCornerShape(Dimens.radiusButton),
    medium = RoundedCornerShape(Dimens.radiusTile),
    large = RoundedCornerShape(Dimens.radiusPane),
)

@Composable
fun HolyBeanTheme(content: @Composable () -> Unit) {
    // 매장 고정 태블릿: 시스템 글꼴 배율을 1.0으로 고정해 레이아웃 깨짐 차단.
    val density = LocalDensity.current
    val fixedDensity = Density(density = density.density, fontScale = 1f)
    CompositionLocalProvider(LocalDensity provides fixedDensity) {
        MaterialTheme(
            colorScheme = HolyBeanColors,
            typography = HolyBeanTypography,
            shapes = HolyBeanShapes,
            content = content,
        )
    }
}
```

- [ ] **Step 2: 컴파일 검증**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Phase 1 전체 빌드 검증**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add android/app/src/main/java/eloom/holybean/ui/theme/Theme.kt
git commit -m "feat(a11y): lock fontScale to 1.0 + squared theme shapes"
```

---

## Phase 2 — 컴포넌트 정합 (목업 기준)

### Task 6: MenuTile — elevation·가격 bold·쿠폰/설정 점선 테두리

**Files:**
- Modify: `android/app/src/main/java/eloom/holybean/ui/components/MenuTile.kt`
- Test(visual): `src/androidTest/.../components/MenuTileTest.kt` (기존 — 깨지지 않는지 확인)

- [ ] **Step 1: `MenuTile.kt`를 목업에 맞게 교체**

목업: 셀 그림자 `0 1px 2px`, 가격 오렌지 bold, 쿠폰 = `OrangeContainer` + 점선 `OrangeLight` 테두리 + 갈색 글자, 설정 = `SettingsTileBg` + 점선 회색 테두리 + 음소거 글자. 하드코딩 `Color(0xFFEEEEEE)` 제거.

```kotlin
package eloom.holybean.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
        TileStyle.Settings -> SettingsTileBg
    }
    val border: BorderStroke? = when (style) {
        TileStyle.Menu -> null
        TileStyle.Coupon -> BorderStroke(1.dp, OrangeLight)
        TileStyle.Settings -> BorderStroke(1.dp, OnSurfaceMuted)
    }
    val labelColor = when (style) {
        TileStyle.Coupon -> OrangeText
        TileStyle.Settings -> OnSurfaceMuted
        TileStyle.Menu -> OnSurface
    }
    Card(
        onClick = onClick,
        modifier = modifier.height(60.dp),
        shape = RoundedCornerShape(Dimens.radiusTile),
        colors = CardDefaults.cardColors(containerColor = container),
        elevation = CardDefaults.cardElevation(defaultElevation = Dimens.tileElevation),
        border = border,
    ) {
        Column(
            Modifier.fillMaxSize().padding(Dimens.spaceXs),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                name,
                style = MaterialTheme.typography.bodyMedium,
                color = labelColor,
                fontWeight = if (style == TileStyle.Menu) FontWeight.Medium else FontWeight.Bold,
            )
            if (price != null) {
                Text(
                    "%,d".format(price),
                    style = MaterialTheme.typography.labelSmall,
                    color = OrangeText,
                    fontWeight = FontWeight.Bold,
                )
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

- [ ] **Step 2: 컴파일 검증**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 가격 표시 androidTest 회귀 확인(있다면)**

`MenuTileTest.kt`가 가격 텍스트("3,500" 등)를 검사한다면 여전히 통과하는지 확인. 디바이스/에뮬레이터 없으면 컴파일만 확인하고 육안 검증으로 대체.

- [ ] **Step 4: Commit**

```bash
git add android/app/src/main/java/eloom/holybean/ui/components/MenuTile.kt
git commit -m "feat(ui): MenuTile elevation, bold price, dashed coupon/settings borders"
```

---

### Task 7: PaymentMethodTile — 강조 텍스트 대비 + 라운드 토큰

**Files:**
- Modify: `android/app/src/main/java/eloom/holybean/ui/components/PaymentMethodTile.kt`

- [ ] **Step 1: 선택 텍스트 색·라운드·높이 토큰화**

`9.dp` 하드코딩 → `Dimens.radiusButton`, 선택 텍스트 `OrangeOnContainer` → `OrangeText`(작은 텍스트 4.5:1), 높이 `46.dp` → `Dimens.minTouchTarget`(48dp).

`PaymentMethodTile.kt`에서 `Surface(...)` 블록을 아래로 교체:

```kotlin
    Surface(
        onClick = onClick, modifier = modifier.height(Dimens.minTouchTarget),
        shape = RoundedCornerShape(Dimens.radiusButton),
        color = if (selected) OrangeContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(2.dp, if (selected) Orange else DividerGray),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.bodyMedium,
                color = if (selected) OrangeText else MaterialTheme.colorScheme.onSurface)
        }
    }
```

- [ ] **Step 2: 컴파일 검증**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add android/app/src/main/java/eloom/holybean/ui/components/PaymentMethodTile.kt
git commit -m "feat(ui): PaymentMethodTile contrast text + radius/touch tokens"
```

---

### Task 8: BasketRow — 쿠폰 행 강조색

**Files:**
- Modify: `android/app/src/main/java/eloom/holybean/ui/components/BasketRow.kt`

- [ ] **Step 1: 쿠폰 행을 강조색으로**

목업 `.bitem.cp`는 쿠폰 행을 갈색(`OrangeText`)으로 표시. `isCoupon` 파라미터 추가(기본 false), 이름/금액 색에 반영.

`BasketRow` 시그니처와 본문을 아래로 교체:

```kotlin
@Composable
fun BasketRow(name: String, count: Int, amount: Int, isCoupon: Boolean = false, onClick: () -> Unit) {
    val nameColor = if (isCoupon) OrangeText else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "$name ", style = MaterialTheme.typography.bodyMedium, color = nameColor,
            modifier = Modifier.weight(1f),
        )
        Text("${count}개 ", style = MaterialTheme.typography.labelSmall, color = OnSurfaceMuted)
        Text("%,d".format(amount), style = MaterialTheme.typography.bodyMedium, color = nameColor)
    }
}
```

`import eloom.holybean.ui.theme.OrangeText` 추가, `MaterialTheme` import 확인.

- [ ] **Step 2: 컴파일 검증**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL (기존 호출부는 `isCoupon` 기본값으로 그대로 컴파일)

- [ ] **Step 3: Commit**

```bash
git add android/app/src/main/java/eloom/holybean/ui/components/BasketRow.kt
git commit -m "feat(ui): BasketRow coupon-row accent color"
```

---

### Task 9: SegmentedToggle — 회색 컨테이너 + 솔리드 오렌지 선택(진한 글자)

**Files:**
- Modify: `android/app/src/main/java/eloom/holybean/ui/components/SegmentedToggle.kt`

- [ ] **Step 1: 색·모양 커스텀**

선택 = 솔리드 `Orange` + 진한 글자(`OnSurface`), 비선택 = 회색 컨테이너. `SegmentedButtonDefaults.colors`로 지정.

`SegmentedToggle.kt` 전체를 아래로 교체:

```kotlin
package eloom.holybean.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import eloom.holybean.ui.theme.Orange
import eloom.holybean.ui.theme.OnSurface
import eloom.holybean.ui.theme.OnSurfaceMuted
import kotlinx.collections.immutable.ImmutableList

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SegmentedToggle(options: ImmutableList<String>, selected: String, onSelect: (String) -> Unit) {
    SingleChoiceSegmentedButtonRow {
        options.forEachIndexed { i, opt ->
            SegmentedButton(
                selected = opt == selected,
                onClick = { onSelect(opt) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = Orange,
                    activeContentColor = OnSurface,
                    inactiveContainerColor = Color(0xFFF0F0F0),
                    inactiveContentColor = OnSurfaceMuted,
                ),
            ) { Text(opt) }
        }
    }
}
```

- [ ] **Step 2: 컴파일 검증**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add android/app/src/main/java/eloom/holybean/ui/components/SegmentedToggle.kt
git commit -m "feat(ui): SegmentedToggle solid-orange selection with dark text"
```

---

## Phase 3 — 화면 정합

### Task 10: HomeScreen — 칩·주문기록 버튼·합계·빈 상태·elevation·쿠폰행

**Files:**
- Modify: `android/app/src/main/java/eloom/holybean/ui/home/HomeScreen.kt`

- [ ] **Step 1: `CategoryChips`를 솔리드 오렌지 선택 스타일로**

`CategoryChips`의 `FilterChip(...)`을 아래로 교체(선택 = 솔리드 오렌지 + 진한 글자 bold):

```kotlin
            FilterChip(
                selected = index == selected,
                onClick = { onSelect(index) },
                label = { Text(name) },
                shape = RoundedCornerShape(14.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Orange,
                    selectedLabelColor = OnSurface,
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = index == selected,
                    selectedBorderColor = Orange,
                ),
            )
```

`import eloom.holybean.ui.theme.OnSurface` 추가.

- [ ] **Step 2: 주문기록 버튼 = 오렌지 외곽선**

`BasketPane`의 `OutlinedButton(onClick = onHistory) { Text("주문기록") }`을 아래로 교체:

```kotlin
                OutlinedButton(
                    onClick = onHistory,
                    shape = RoundedCornerShape(Dimens.radiusButton),
                    border = BorderStroke(2.dp, Orange),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Orange),
                ) { Text("주문기록", fontWeight = FontWeight.Bold) }
```

import 추가: `androidx.compose.foundation.BorderStroke`, `androidx.compose.ui.text.font.FontWeight`.

- [ ] **Step 3: BasketPane elevation + 빈 상태 + 쿠폰행 + 합계 강조 + CTA 사각**

`BasketPane`의 `Surface(...)`에 `shadowElevation = Dimens.paneElevation` 추가, `LazyColumn` 비었을 때 안내, 쿠폰 항목 `isCoupon` 전달, 합계 금액 `OrangeOnContainer`(큰 텍스트), CTA `shape` 사각.

`BasketPane` 함수 본문을 아래로 교체:

```kotlin
@Composable
private fun BasketPane(
    orderId: Int, basket: ImmutableList<CartItem>, total: Int,
    onItemClick: (Int) -> Unit, onHistory: () -> Unit, onCheckout: () -> Unit, modifier: Modifier,
) {
    Surface(modifier, shape = RoundedCornerShape(Dimens.paneRadius),
        color = MaterialTheme.colorScheme.surface, shadowElevation = Dimens.paneElevation) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${orderId}번 주문", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                OutlinedButton(
                    onClick = onHistory,
                    shape = RoundedCornerShape(Dimens.radiusButton),
                    border = BorderStroke(2.dp, Orange),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Orange),
                ) { Text("주문기록", fontWeight = FontWeight.Bold) }
            }
            if (basket.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("담긴 상품이 없습니다", style = MaterialTheme.typography.bodyMedium, color = OnSurfaceMuted)
                }
            } else {
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Dimens.spaceXs)) {
                    // 쿠폰은 항상 id=999 라 key=it.id 면 중복 → 인덱스 키 사용.
                    itemsIndexed(basket, key = { index, _ -> index }) { _, item ->
                        BasketRow(item.name, item.count, item.count * item.price, isCoupon = item.id == 999) {
                            onItemClick(item.id)
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.End) {
                Text("합계 ", style = MaterialTheme.typography.titleMedium)
                Text("%,d원".format(total), style = MaterialTheme.typography.titleMedium, color = OrangeOnContainer)
            }
            Button(
                onClick = onCheckout, enabled = basket.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(Dimens.primaryTouchTarget),
                shape = RoundedCornerShape(Dimens.radiusButton),
                colors = ButtonDefaults.buttonColors(containerColor = Orange),
            ) { Text("결제 →", fontWeight = FontWeight.Bold) }
        }
    }
}
```

import 추가/확인: `androidx.compose.foundation.layout.Box`, `OrangeOnContainer`, `OnSurfaceMuted`. (구분선 제거로 `HorizontalDivider`/`DividerGray` 미사용 시 import 정리.)

- [ ] **Step 4: 컴파일 검증**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/eloom/holybean/ui/home/HomeScreen.kt
git commit -m "feat(ui): Home chips/history-btn/total/empty-state/elevation parity"
```

---

### Task 11: PaymentScreen — 패널 elevation·합계 강조·요약 빈 상태·취소 버튼

**Files:**
- Modify: `android/app/src/main/java/eloom/holybean/ui/payment/PaymentScreen.kt`

(분할 분배 표시는 Task 16에서 추가. 여기서는 스타일 정합만.)

- [ ] **Step 1: 두 패널 `Surface`에 elevation 추가**

요약 패널·옵션 패널의 두 `Surface(...)` 모두에 `shadowElevation = Dimens.paneElevation` 인자를 추가한다(기존 `shape`/`color` 유지).

- [ ] **Step 2: 요약 합계 강조 + top divider**

요약 패널의 합계 `Row`를 아래로 교체(목업 `.stotal`: 위 구분선 + 금액 강조):

```kotlin
                    HorizontalDivider(color = DividerGray)
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("합계", style = MaterialTheme.typography.titleMedium)
                        Text("%,d원".format(total), style = MaterialTheme.typography.titleMedium, color = OrangeOnContainer)
                    }
```

import 확인: `androidx.compose.material3.HorizontalDivider`, `OrangeOnContainer`.

- [ ] **Step 3: 요약 비었을 때 안내**

요약 패널 `LazyColumn`을 `if (items.isEmpty())` 분기로 감싸 빈 상태 문구를 표시:

```kotlin
                    if (items.isEmpty()) {
                        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text("담긴 상품이 없습니다", style = MaterialTheme.typography.bodyMedium, color = OnSurfaceMuted)
                        }
                    } else {
                        LazyColumn(Modifier.weight(1f)) {
                            itemsIndexed(items, key = { index, _ -> index }) { _, it ->
                                BasketRow(it.name, it.count, it.count * it.price, isCoupon = it.id == 999) {}
                            }
                        }
                    }
```

import 추가: `androidx.compose.foundation.layout.Box`.

- [ ] **Step 4: 취소 버튼·결제완료 버튼 정합**

상단 `OutlinedButton(onClick = onCancel) { Text("✕ 취소") }`을 음소거 외곽선으로:

```kotlin
            OutlinedButton(
                onClick = onCancel,
                shape = RoundedCornerShape(Dimens.radiusButton),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = OnSurfaceMuted),
            ) { Text("✕ 취소") }
```

"결제 완료" `Button`에 `shape = RoundedCornerShape(Dimens.radiusButton)` 및 `Modifier...height(Dimens.primaryTouchTarget)` 추가, 라벨 `fontWeight = FontWeight.Bold`.

import 추가: `androidx.compose.ui.text.font.FontWeight`, `OnSurfaceMuted`, `ButtonDefaults`.

- [ ] **Step 5: 컴파일 검증**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add android/app/src/main/java/eloom/holybean/ui/payment/PaymentScreen.kt
git commit -m "feat(ui): Payment panel elevation/total/empty-state/button parity"
```

---

### Task 12: OrdersScreen — 보고서 버튼·목록 항목 bold·선택 톤·매출 구분선·합계

**Files:**
- Modify: `android/app/src/main/java/eloom/holybean/ui/orders/OrdersScreen.kt`

- [ ] **Step 1: 패널 elevation + 보고서/닫기 버튼 + 매출 스트립 세로 구분선**

매출 스트립 `Surface`에 `shadowElevation = Dimens.paneElevation` 추가. `StatChip` 사이에 세로 구분선(`VerticalDivider`) 삽입. "보고서 출력" 버튼을 오렌지 외곽선으로. "✕ 닫기"는 음소거 외곽선.

매출 스트립 `Surface { Row { ... } }` 내부를 아래로 교체:

```kotlin
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.spaceMd)) {
                StatChip("오늘 총 판매", "%,d원".format(summary.totalSales), OrangeOnContainer)
                VerticalDivider(Modifier.height(32.dp), color = DividerGray)
                StatChip("총 건수", "${summary.orderCount}건")
                VerticalDivider(Modifier.height(32.dp), color = DividerGray)
                StatChip("총 잔수", "${summary.drinkCount}잔")
                Spacer(Modifier.weight(1f))
                OutlinedButton(
                    onClick = onPrintReport,
                    shape = RoundedCornerShape(Dimens.radiusButton),
                    border = BorderStroke(2.dp, Orange),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Orange),
                ) { Text("보고서 출력", fontWeight = FontWeight.Bold) }
            }
```

상단 닫기 버튼:

```kotlin
            OutlinedButton(
                onClick = onClose,
                shape = RoundedCornerShape(Dimens.radiusButton),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = OnSurfaceMuted),
            ) { Text("✕ 닫기") }
```

imports: `androidx.compose.material3.VerticalDivider`, `androidx.compose.foundation.BorderStroke`, `androidx.compose.ui.text.font.FontWeight`, `OrangeOnContainer`, `OnSurfaceMuted`. (`StatChip`의 `valueColor`를 `Orange`→`OrangeOnContainer`로 바꿔 큰 숫자 대비 보정.)

- [ ] **Step 2: 상세 패널 elevation + 합계 강조 + 재출력/삭제 버튼 사각**

상세 패널 `Surface`에 `shadowElevation = Dimens.paneElevation`. 합계 `Row` 위에 `HorizontalDivider`, 금액 색 `OrangeOnContainer`. 재출력 `Button`에 `shape`/`height(primaryTouchTarget)`, 삭제 `OutlinedButton`에 빨강 테두리·`shape`.

상세 패널 합계 + 액션 `Row`를 아래로 교체:

```kotlin
                    HorizontalDivider(color = DividerGray)
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("합계", style = MaterialTheme.typography.titleMedium)
                        Text("%,d원".format(selectedTotal), style = MaterialTheme.typography.titleMedium, color = OrangeOnContainer)
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onReprint, modifier = Modifier.weight(1f).height(Dimens.primaryTouchTarget),
                            shape = RoundedCornerShape(Dimens.radiusButton),
                            colors = ButtonDefaults.buttonColors(containerColor = Orange)) {
                            Text("재출력", fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(onClick = onDelete, modifier = Modifier.height(Dimens.primaryTouchTarget),
                            shape = RoundedCornerShape(Dimens.radiusButton),
                            border = BorderStroke(2.dp, DangerRed),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = DangerRed)) { Text("삭제") }
                    }
```

- [ ] **Step 3: `OrderListItem` — 번호·금액 bold + 선택 배경 #FFF8F0**

`OrderListItem`의 `Surface` 색을 선택 시 `OrderSelectedBg`로, 1행 텍스트를 `titleMedium`(Bold), 라운드 토큰화:

```kotlin
    Surface(
        onClick = onClick, shape = RoundedCornerShape(Dimens.radiusButton),
        color = if (selected) OrderSelectedBg else MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) Orange else DividerGray),
    ) {
        Column(Modifier.fillMaxWidth().padding(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${o.orderId}번", style = MaterialTheme.typography.titleMedium)
                Text("%,d원".format(o.totalAmount), style = MaterialTheme.typography.titleMedium)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(o.orderer.ifBlank { "—" }, style = MaterialTheme.typography.labelSmall, color = OnSurfaceMuted)
                Text(o.method, style = MaterialTheme.typography.labelSmall, color = OnSurfaceMuted)
            }
        }
    }
```

`import eloom.holybean.ui.theme.OrderSelectedBg` 추가.

- [ ] **Step 4: 컴파일 검증**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/eloom/holybean/ui/orders/OrdersScreen.kt
git commit -m "feat(ui): Orders report-btn/list-bold/selected-tone/dividers/total parity"
```

---

### Task 13: 터치 타깃 감사 + 설정 시트 토큰 적용

**Files:**
- Modify: `android/app/src/main/java/eloom/holybean/ui/home/OrderDialog.kt`
- Modify: `android/app/src/main/java/eloom/holybean/ui/menumanagement/MenuManagementScreen.kt`
- Modify: `android/app/src/main/java/eloom/holybean/ui/settings/SettingsSheet.kt`

- [ ] **Step 1: 탭 요소 48dp 보장 감사**

`OrderDialog.kt`·`MenuManagementScreen.kt`에서 `IconButton`/`Checkbox`/`Switch`/작은 버튼을 찾아 시각 크기가 48dp 미만이면 `Modifier.sizeIn(minWidth = Dimens.minTouchTarget, minHeight = Dimens.minTouchTarget)`(또는 `IconButton`은 기본 48dp이므로 확인만)을 적용한다. 각 파일에서 탭 요소를 확인하고 미달분만 보정.

- [ ] **Step 2: SettingsSheet — 행 높이 48dp 보장(구조 재디자인 아님)**

`SettingsSheet.kt`의 `SettingsRow`가 충분한 탭 높이를 갖도록 `padding(16.dp)`를 유지하되 `Modifier.fillMaxWidth().heightIn(min = Dimens.minTouchTarget).clickable(...)`로 보강. (헤더바·아이콘칩·화살표 재디자인은 범위 밖.)

```kotlin
@Composable
private fun SettingsRow(label: String, onClick: () -> Unit) {
    Text(label, style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.minTouchTarget)
            .clickable(onClick = onClick).padding(16.dp))
}
```

import 추가: `androidx.compose.foundation.layout.heightIn`, `eloom.holybean.ui.theme.Dimens`.

- [ ] **Step 3: 컴파일 검증**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Phase 3 전체 빌드 검증**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/eloom/holybean/ui/home/OrderDialog.kt android/app/src/main/java/eloom/holybean/ui/menumanagement/MenuManagementScreen.kt android/app/src/main/java/eloom/holybean/ui/settings/SettingsSheet.kt
git commit -m "feat(a11y): touch-target audit fixes + settings row min height"
```

---

## Phase 4 — 분할결제 분배 표시

### Task 14: `PaymentForm.splitBreakdown` (TDD)

**Files:**
- Modify: `android/app/src/main/java/eloom/holybean/ui/payment/PaymentForm.kt`
- Test: `android/app/src/test/kotlin/eloom/holybean/ui/payment/PaymentFormTest.kt`

- [ ] **Step 1: 실패하는 테스트 작성**

`PaymentFormTest.kt`에 추가:

```kotlin
    @Test fun `split breakdown returns first-remainder and second-amount lines`() {
        val lines = PaymentForm.splitBreakdown(first = "현금", second = "계좌이체", total = 15000, secondAmountText = "5000")
        assertEquals(2, lines.size)
        assertEquals("현금 (잔액)", lines[0].label)
        assertEquals(10000, lines[0].amount)
        assertEquals("계좌이체", lines[1].label)
        assertEquals(5000, lines[1].amount)
    }

    @Test fun `split breakdown empty when amount invalid or non-positive remainder`() {
        assertTrue(PaymentForm.splitBreakdown("현금", "계좌이체", 15000, "").isEmpty())
        assertTrue(PaymentForm.splitBreakdown("현금", "계좌이체", 15000, "0").isEmpty())
        assertTrue(PaymentForm.splitBreakdown("현금", "계좌이체", 15000, "15000").isEmpty())
        assertTrue(PaymentForm.splitBreakdown("현금", null, 15000, "5000").isEmpty())
    }
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:testDebugUnitTest --tests "eloom.holybean.ui.payment.PaymentFormTest"`
Expected: 컴파일 실패 또는 FAIL ("splitBreakdown" 미정의)

- [ ] **Step 3: `PaymentForm`에 `SplitLine` + `splitBreakdown` 구현**

`PaymentForm.kt`의 `object PaymentForm {` 안(예: `secondCandidates` 아래)에 추가, 파일 상단에 `data class SplitLine` 추가:

```kotlin
data class SplitLine(val label: String, val amount: Int)
```

```kotlin
    /** 분할결제 ON 시 1번째(잔액)·2번째 수단의 금액 분배 라인. 입력이 유효하지 않으면 빈 리스트. */
    fun splitBreakdown(first: String, second: String?, total: Int, secondAmountText: String): List<SplitLine> {
        if (second == null) return emptyList()
        val secondAmount = secondAmountText.toIntOrNull() ?: return emptyList()
        val remainder = total - secondAmount
        if (secondAmount <= 0 || remainder <= 0) return emptyList()
        return listOf(
            SplitLine("$first (잔액)", remainder),
            SplitLine(second, secondAmount),
        )
    }
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:testDebugUnitTest --tests "eloom.holybean.ui.payment.PaymentFormTest"`
Expected: PASS (기존 + 신규 테스트 모두)

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/eloom/holybean/ui/payment/PaymentForm.kt android/app/src/test/kotlin/eloom/holybean/ui/payment/PaymentFormTest.kt
git commit -m "feat(payment): splitBreakdown pure helper with tests"
```

---

### Task 15: PaymentScreen — 분할 분배 라인 표시

**Files:**
- Modify: `android/app/src/main/java/eloom/holybean/ui/payment/PaymentScreen.kt`

- [ ] **Step 1: 분할 ON 영역에 분배 라인 추가**

`if (split) { ... }` 블록 안, 2번째 금액 `OutlinedTextField` 아래에 분배 라인을 추가한다(목업 `.splitline`, 색 `OrangeText`):

```kotlin
                            val breakdown = PaymentForm.splitBreakdown(first, second, total, secondAmt)
                            breakdown.forEach { line ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 1.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(line.label, style = MaterialTheme.typography.labelSmall, color = OrangeText)
                                    Text("%,d원".format(line.amount), style = MaterialTheme.typography.labelSmall, color = OrangeText)
                                }
                            }
```

- [ ] **Step 2: 라벨 정합(선택)**

분할 ON일 때 "결제 수단" 라벨을 "결제 수단 (1번째)"로, "2번째 결제 수단"을 "2번째 결제 수단 (${first} 제외)"로 표시하도록 해당 `Text(...)` 라벨 문자열을 조건부로 바꾼다.

import 추가: `eloom.holybean.ui.theme.OrangeText`.

- [ ] **Step 3: 컴파일 검증**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add android/app/src/main/java/eloom/holybean/ui/payment/PaymentScreen.kt
git commit -m "feat(payment): show split-payment distribution lines"
```

---

## Phase 5 — 개발자도구 확장

### Task 16: NetworkStatusProvider (인터페이스 + 구현 + Hilt 모듈)

**Files:**
- Create: `android/app/src/main/java/eloom/holybean/diag/NetworkStatusProvider.kt`
- Create: `android/app/src/main/java/eloom/holybean/di/NetworkModule.kt`
- Modify: `android/app/src/main/AndroidManifest.xml` (권한)

- [ ] **Step 1: 인터페이스 + Android 구현 작성**

`diag/NetworkStatusProvider.kt`:

```kotlin
package eloom.holybean.diag

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

data class NetworkStatus(val connected: Boolean, val info: String)

interface NetworkStatusProvider {
    fun current(): NetworkStatus
}

class AndroidNetworkStatusProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) : NetworkStatusProvider {
    override fun current(): NetworkStatus {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return NetworkStatus(false, "알 수 없음")
        val network = cm.activeNetwork ?: return NetworkStatus(false, "연결 없음")
        val caps = cm.getNetworkCapabilities(network) ?: return NetworkStatus(false, "연결 없음")
        val connected = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val type = when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "셀룰러"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "이더넷"
            else -> "기타"
        }
        return NetworkStatus(connected, if (connected) type else "연결 없음")
    }
}
```

- [ ] **Step 2: Hilt 바인딩 모듈 작성**

`di/NetworkModule.kt`:

```kotlin
package eloom.holybean.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import eloom.holybean.diag.AndroidNetworkStatusProvider
import eloom.holybean.diag.NetworkStatusProvider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkModule {
    @Binds @Singleton
    abstract fun bindNetworkStatusProvider(impl: AndroidNetworkStatusProvider): NetworkStatusProvider
}
```

- [ ] **Step 3: 매니페스트에 권한 추가(없으면)**

`AndroidManifest.xml`의 `<manifest>` 하위에 아래가 없으면 추가:

```xml
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

- [ ] **Step 4: 컴파일 검증**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/eloom/holybean/diag/NetworkStatusProvider.kt android/app/src/main/java/eloom/holybean/di/NetworkModule.kt android/app/src/main/AndroidManifest.xml
git commit -m "feat(devtools): NetworkStatusProvider + Hilt binding + permission"
```

---

### Task 17: FirestoreRepository.checkConnection

**Files:**
- Modify: `android/app/src/main/java/eloom/holybean/data/repository/FirestoreRepository.kt`

- [ ] **Step 1: best-effort 연결 점검 메서드 추가**

`FirestoreRepository`의 `getOrderNumber()` 아래에 추가(타임아웃 3초 내 작은 읽기 성공 여부):

```kotlin
    /** 개발자도구용 best-effort 연결 점검. 3초 내 작은 읽기 성공 시 true. */
    suspend fun checkConnection(): Boolean {
        return try {
            withTimeout(3000) {
                db.collection(FirestoreSchema.DAY_SUMMARIES).document(today()).get().await()
            }
            true
        } catch (e: Exception) {
            false
        }
    }
```

파일 상단 import에 `import kotlinx.coroutines.withTimeout` 추가.

- [ ] **Step 2: 컴파일 검증**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add android/app/src/main/java/eloom/holybean/data/repository/FirestoreRepository.kt
git commit -m "feat(devtools): FirestoreRepository.checkConnection (best-effort)"
```

---

### Task 18: DevToolsViewModel 확장 (TDD)

**Files:**
- Modify: `android/app/src/main/java/eloom/holybean/ui/settings/DevToolsViewModel.kt`
- Modify: `android/app/src/test/kotlin/eloom/holybean/ui/settings/DevToolsViewModelTest.kt`

- [ ] **Step 1: 기존 테스트 생성자 수정 + 신규 실패 테스트 작성**

`DevToolsViewModelTest.kt`를 아래로 교체(신규 의존성 mock 추가, 상태 확장 검증):

```kotlin
package eloom.holybean.ui.settings

import eloom.holybean.data.repository.FirestoreRepository
import eloom.holybean.diag.NetworkStatus
import eloom.holybean.diag.NetworkStatusProvider
import eloom.holybean.printer.PiPrintClient
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DevToolsViewModelTest {
    private val pi: PiPrintClient = mockk(relaxed = true)
    private val firestore: FirestoreRepository = mockk(relaxed = true)
    private val network: NetworkStatusProvider = mockk(relaxed = true)

    private fun vm() = DevToolsViewModel(pi, firestore, network, UnconfinedTestDispatcher())

    @Test fun `refresh populates printer network and firestore status`() = runTest {
        coEvery { pi.checkHealth() } returns true
        coEvery { firestore.checkConnection() } returns true
        every { network.current() } returns NetworkStatus(true, "Wi-Fi")
        val vm = vm()
        vm.refresh()
        advanceUntilIdle()
        val s = vm.uiState.value
        assertEquals(true, s.printerOk)
        assertEquals(true, s.networkOk)
        assertEquals("Wi-Fi", s.networkInfo)
        assertEquals(true, s.firestoreOk)
        assertTrue(s.printerLatencyMs != null)
    }

    @Test fun `refresh reflects failures`() = runTest {
        coEvery { pi.checkHealth() } returns false
        coEvery { firestore.checkConnection() } returns false
        every { network.current() } returns NetworkStatus(false, "연결 없음")
        val vm = vm()
        vm.refresh()
        advanceUntilIdle()
        val s = vm.uiState.value
        assertEquals(false, s.printerOk)
        assertEquals(false, s.networkOk)
        assertEquals(false, s.firestoreOk)
    }

    @Test fun `test print delegates to client`() = runTest {
        val vm = vm()
        vm.testPrint()
        advanceUntilIdle()
        coVerify { pi.printTestReceipt() }
    }

    @Test fun `test print failure emits show toast`() = runTest {
        coEvery { pi.printTestReceipt() } throws RuntimeException("boom")
        val vm = vm()
        val events = mutableListOf<DevToolsViewModel.DevToolsUiEvent>()
        val collector = launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiEvent.collect { events.add(it) }
        }
        vm.testPrint()
        advanceUntilIdle()
        assertTrue(events.any { it is DevToolsViewModel.DevToolsUiEvent.ShowToast })
        collector.cancel()
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:testDebugUnitTest --tests "eloom.holybean.ui.settings.DevToolsViewModelTest"`
Expected: 컴파일 실패(생성자 인자 불일치) — 다음 스텝에서 ViewModel 확장

- [ ] **Step 3: `DevToolsViewModel` 확장**

`DevToolsViewModel.kt`의 생성자·`State`·`refresh()`를 아래로 교체(import에 `FirestoreRepository`, `NetworkStatusProvider`, `kotlin.system.measureTimeMillis` 추가):

```kotlin
@HiltViewModel
class DevToolsViewModel @Inject constructor(
    private val piPrintClient: PiPrintClient,
    private val firestoreRepository: FirestoreRepository,
    private val networkStatusProvider: NetworkStatusProvider,
    @Named("IO") private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {
    data class State(
        val printerOk: Boolean? = null,
        val printerLatencyMs: Long? = null,
        val networkOk: Boolean? = null,
        val networkInfo: String = "",
        val firestoreOk: Boolean? = null,
        val printerUrl: String = BuildConfig.PRINT_SERVER_URL,
    )
```

```kotlin
    fun refresh() {
        viewModelScope.launch(ioDispatcher) {
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
```

`testPrint()`는 기존 그대로 유지. import 추가:

```kotlin
import eloom.holybean.data.repository.FirestoreRepository
import eloom.holybean.diag.NetworkStatusProvider
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:testDebugUnitTest --tests "eloom.holybean.ui.settings.DevToolsViewModelTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/eloom/holybean/ui/settings/DevToolsViewModel.kt android/app/src/test/kotlin/eloom/holybean/ui/settings/DevToolsViewModelTest.kt
git commit -m "feat(devtools): expand VM with printer latency, network, firestore status"
```

---

### Task 19: DevToolsScreen — 상태 행 3종 + 헤더/버튼 정합

**Files:**
- Modify: `android/app/src/main/java/eloom/holybean/ui/settings/DevToolsScreen.kt`

- [ ] **Step 1: 상태 점 색을 토큰화하고 3종 행 렌더링**

`DevToolsScreen`의 `Column` 본문을 아래로 교체(헤더 + Pi/네트워크/Firestore 행 + URL + 테스트 버튼). `HealthRow`는 `Boolean?`와 값 문자열을 받도록 일반화하고 상태 토큰 사용.

```kotlin
@Composable
fun DevToolsScreen(
    state: DevToolsViewModel.State,
    onClose: () -> Unit,
    onRefresh: () -> Unit,
    onTestPrint: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("🛠 개발자 도구", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = onClose, shape = RoundedCornerShape(Dimens.radiusButton)) { Text("닫기") }
        }
        Spacer(Modifier.height(12.dp))
        HealthRow("Pi 프린터 (/health)", state.printerOk,
            state.printerLatencyMs?.let { "정상 · ${it}ms" } ?: "—")
        HealthRow("네트워크 연결", state.networkOk, state.networkInfo)
        HealthRow("Firestore", state.firestoreOk, if (state.firestoreOk == true) "정상" else "응답 없음")
        Text("프린터 서버 URL: ${state.printerUrl}",
            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onRefresh, shape = RoundedCornerShape(Dimens.radiusButton)) { Text("새로고침") }
            Button(onClick = onTestPrint, modifier = Modifier.height(Dimens.primaryTouchTarget),
                shape = RoundedCornerShape(Dimens.radiusButton),
                colors = ButtonDefaults.buttonColors(containerColor = Orange)) {
                Text("테스트 영수증 출력", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun HealthRow(label: String, ok: Boolean?, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(
            when (ok) { true -> StatusOk; false -> StatusError; null -> StatusUnknown }))
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.labelSmall, color = OnSurfaceMuted)
    }
}
```

import 정리: `androidx.compose.foundation.shape.RoundedCornerShape`, `androidx.compose.material3.ButtonDefaults`, `androidx.compose.ui.text.font.FontWeight`, `eloom.holybean.ui.theme.*`(Orange, StatusOk/Error/Unknown, OnSurfaceMuted, Dimens). 하드코딩 `Color(0xFF...)` 제거.

- [ ] **Step 2: 컴파일 검증 + 단위 테스트**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, 모든 테스트 PASS

- [ ] **Step 3: Commit**

```bash
git add android/app/src/main/java/eloom/holybean/ui/settings/DevToolsScreen.kt
git commit -m "feat(devtools): printer/network/firestore status rows + styled actions"
```

---

## Phase 6 — 문서 & 최종 검증

### Task 20: 접근성 표준 문서

**Files:**
- Create: `docs/accessibility.md`

- [ ] **Step 1: 문서 작성**

```markdown
# HolyBean 접근성 & 디자인 토큰 표준

매장 고정 태블릿 POS 기준. 색·폰트·치수는 **하드코딩 금지**, 반드시
`ui/theme/`의 `Color`/`HolyBeanTypography`/`Dimens` 토큰을 사용한다.

## 정량 기준
| 항목 | 기준 |
|---|---|
| 터치 타깃 (주요 액션) | ≥ 56dp (`Dimens.primaryTouchTarget`) |
| 터치 타깃 (보조 요소) | ≥ 48dp (`Dimens.minTouchTarget`) |
| 최소 폰트 | ≥ 14sp (`labelSmall`) |
| 본문 대비 | ≥ 4.5:1 |
| 큰/볼드 텍스트 대비 | ≥ 3:1 |
| 폰트 배율 | 앱에서 1.0 고정 (`HolyBeanTheme`) |

## 색 사용 규칙
- 오렌지(`Orange`) 배경 위 텍스트는 진한 글자(`OnSurface`), 흰색 금지.
- 작은 강조 텍스트(메뉴 가격 등) = `OrangeText`(#9A5412, 4.5:1).
- 큰/볼드 강조(합계 등) = `OrangeOnContainer`(#C2691A, 3:1).
- 음소거 텍스트 = `OnSurfaceMuted`(#767676).
- 선택 상태: 칩·세그먼트 = 솔리드 `Orange` + 진한 글자 / 결제수단·쿠폰·주문선택 = `OrangeContainer` + `OrangeText`.

## 새 화면 체크리스트
1. 모든 sp는 Typography 토큰 경유(직접 sp 금지).
2. 탭 요소는 48/56dp 충족.
3. 텍스트/배경 대비 4.5:1(큰 텍스트 3:1) 확인.
4. 색은 `Color.kt` 토큰만 사용.
```

- [ ] **Step 2: Commit**

```bash
git add docs/accessibility.md
git commit -m "docs(a11y): accessibility & design token standards"
```

---

### Task 21: 최종 전체 빌드 + 테스트 + 정합 점검

**Files:** (없음 — 검증 전용)

- [ ] **Step 1: 전체 단위 테스트**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests PASS

- [ ] **Step 2: 전체 빌드**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 목업 정합 육안 점검(에뮬레이터/디바이스)**

스펙의 "목업 정합성" 체크리스트와 "검증(수동)" 절을 따라 홈/결제/주문기록/개발자도구를 최종 목업과 나란히 비교:
- 칩·세그먼트 선택 = 솔리드 오렌지 + 진한 글자
- 패널이 배경 위에 떠 보임(그림자), 제목 Bold, 버튼 사각
- 메뉴 가격 bold, 쿠폰/설정 타일 점선 테두리
- 합계 강조, 빈 장바구니 안내 문구
- 주문기록 항목 bold·선택 톤·매출 구분선
- 개발자도구 Pi/네트워크/Firestore 상태 점·값 표시
- 분할결제 ON 시 분배 라인 표시
- 시스템 글꼴 크기를 키워도 레이아웃 불변(배율 고정)

- [ ] **Step 4: 최종 커밋(필요 시)**

육안 점검에서 발견된 미세 조정만 별도 커밋.

---

## Self-Review 메모 (작성자 확인 완료)

- **Spec coverage**: 접근성 토큰(Task 1–5), 시각 개선/elevation/위계/사각버튼(Task 1·4·5·6–13), 목업 정합(Task 6–13), 분할결제(Task 14–15), 개발자도구 확장(Task 16–19), 문서(Task 20), 검증(Task 21) — 스펙 각 절 대응됨. 설정 시트 재디자인은 스펙대로 범위 밖.
- **타입 일관성**: `SplitLine(label, amount)`·`NetworkStatus(connected, info)`·`DevToolsViewModel.State` 필드명이 테스트/구현/화면에서 일치. `splitBreakdown`·`checkConnection`·`current()` 시그니처 통일.
- **토큰명 일관성**: `OrangeText`/`OrangeOnContainer`/`OnSurfaceMuted`/`StatusOk|Error|Unknown`/`OrderSelectedBg`/`SettingsTileBg`/`Dimens.radiusButton|radiusTile|radiusPane|primaryTouchTarget|minTouchTarget|paneElevation|tileElevation` 전 태스크 동일 사용.
