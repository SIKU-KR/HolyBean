# UI 여백·배치·정렬 일관성 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Compose UI 전반의 여백·간격·정렬을 8pt 토큰 스케일 + 공용 레이아웃 컴포넌트 위로 통일하고 4건의 레이아웃 버그를 고친다.

**Architecture:** ① `Dimens.kt`를 8pt 스케일로 재정의(기존 이름은 `@Deprecated` alias로 남겨 무중단 이행) → ② 6개 공용 컴포넌트 신규(`ScreenContainer`/`ScreenHeader`/`Pane`/`SectionLabel`/`TotalRow`/`StatusDot`) → ③ 공용 컴포넌트 정규화 → ④ 화면 9개를 컴포넌트+토큰으로 재조립 → ⑤ deprecated alias 제거 + 전체 검증. 각 단계는 컴파일·기존 테스트 통과를 게이트로 둔다.

**Tech Stack:** Kotlin, Jetpack Compose, Material3, Gradle(JDK 17 toolchain via foojay).

**스펙:** `docs/superpowers/specs/2026-05-24-ui-spacing-layout-consistency-design.md`

---

## 빌드/검증 공통 규약

- **모든 gradle 명령은 리포 루트(`/Users/benn/dev/personal/HolyBean`)에서** 다음 프리픽스로 실행한다(이 환경은 `java`가 PATH에 없음):
  ```bash
  JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./android/gradlew -p android <task>
  ```
- **컴파일 게이트:** `:app:assembleDebug` — 모든 코드 태스크 후 실행, 성공이어야 한다.
- **단위 테스트 게이트:** `:app:testDebugUnitTest` — JVM에서 빠르게 도는 신뢰 가능한 게이트. 본 작업은 ViewModel/순수 로직을 건드리지 않으므로 항상 통과해야 한다.
- **Compose UI 테스트**(`:app:connectedDebugAndroidTest`)는 에뮬레이터(`emulator-5554`)가 필요하므로 **마지막 Task 13에서 일괄 실행**한다. 참고: `HomeScreenTest`는 `"결제 →"`를 찾는데 현재 코드는 `"결제"`라 **이 작업과 무관하게 이미 실패**할 수 있다 — 본 plan은 어떤 버튼 라벨도 바꾸지 않으므로 이 불일치를 새로 만들거나 고치지 않는다.
- **라벨/텍스트 불변 원칙:** 모든 화면의 사용자 표시 텍스트·버튼 라벨·접근성 텍스트는 그대로 유지한다(테스트 셀렉터 보호).

---

## File Structure

**신규 생성:**
- `android/app/src/main/java/eloom/holybean/ui/components/layout/ScreenContainer.kt`
- `.../ui/components/layout/ScreenHeader.kt`
- `.../ui/components/layout/Pane.kt`
- `.../ui/components/layout/SectionLabel.kt`
- `.../ui/components/layout/TotalRow.kt`
- `.../ui/components/layout/StatusDot.kt`

**수정:**
- `.../ui/theme/Dimens.kt` (토큰 재정의 → 마지막에 alias 제거)
- `.../ui/theme/Color.kt` (`SegmentInactive` 추가)
- `.../ui/components/{BasketRow,MenuTile,SegmentedToggle}.kt`
- `.../ui/home/HomeScreen.kt`, `.../ui/payment/PaymentScreen.kt`, `.../ui/orders/OrdersScreen.kt`
- `.../ui/credits/CreditsScreen.kt`, `.../ui/report/ReportScreen.kt`, `.../ui/settings/DevToolsScreen.kt`
- `.../ui/startup/SplashScreen.kt`, `.../ui/settings/SettingsSheet.kt`, `.../ui/menumanagement/MenuManagementScreen.kt`

경로 접두사는 이하 `app/.../ui/`로 축약(`android/app/src/main/java/eloom/holybean/ui/`).

---

## Task 1: 토큰 시스템 재정의 (Dimens + Color)

기존 이름을 `@Deprecated` alias로 보존하므로 이 태스크만으로 빌드가 깨지지 않는다(값만 8pt로 재정렬).

**Files:**
- Modify: `app/.../ui/theme/Dimens.kt` (전체 교체)
- Modify: `app/.../ui/theme/Color.kt` (1줄 추가)

- [ ] **Step 1: `Dimens.kt` 전체를 새 토큰 체계로 교체**

`app/src/main/java/eloom/holybean/ui/theme/Dimens.kt` 전체를 다음으로 대체:

```kotlin
package eloom.holybean.ui.theme

import androidx.compose.ui.unit.dp

object Dimens {
    // ── 간격 스케일 (8pt 그리드, 4는 half-step) ─────────────────
    val spaceXs = 4.dp    // 칩/텍스트 내부, 미세 조정
    val spaceSm = 8.dp    // 기본 gap, 리스트 아이템 간격, 그리드 gap
    val spaceMd = 16.dp   // 화면 패딩, 패널 내부 패딩, 헤더 하단
    val spaceLg = 24.dp   // 섹션 간 간격
    val spaceXl = 32.dp   // 큰 블록 구분

    // ── 의미 별칭 ──────────────────────────────────────────────
    val screenPadding = spaceMd   // 16
    val panePadding = spaceMd     // 16
    val paneGap = spaceSm         // 8
    val sectionGap = spaceLg      // 24
    val headerGap = spaceMd       // 16
    val gridGap = spaceSm         // 8
    val itemGap = spaceSm         // 8

    // ── 2-pane 분할 비율 ──────────────────────────────────────
    val paneSplitNarrow = 0.38f
    val paneSplitWide = 0.46f

    // ── 라운드 (값 유지) ──────────────────────────────────────
    val radiusButton = 6.dp
    val radiusTile = 10.dp
    val radiusPane = 14.dp
    val radiusChip = 14.dp

    // ── elevation ─────────────────────────────────────────────
    val paneElevation = 2.dp
    val tileElevation = 1.dp

    // ── 터치 타깃 ─────────────────────────────────────────────
    val minTouchTarget = 48.dp
    val primaryTouchTarget = 56.dp

    // ── 고정 치수 (스케일 예외) ───────────────────────────────
    val statusDot = 10.dp
    val menuTileHeight = 90.dp

    // ── 폐기 예정 alias (Task 13에서 제거) ─────────────────────
    @Deprecated("Use paneGap", ReplaceWith("paneGap")) val gap = paneGap
    @Deprecated("Use radiusTile", ReplaceWith("radiusTile")) val tileRadius = radiusTile
    @Deprecated("Use radiusPane", ReplaceWith("radiusPane")) val paneRadius = radiusPane
    @Deprecated("Use paneSplitNarrow", ReplaceWith("paneSplitNarrow")) val basketWidthFraction = paneSplitNarrow
}
```

- [ ] **Step 2: `Color.kt`에 `SegmentInactive` 추가**

`app/.../ui/theme/Color.kt`의 마지막 색 정의 뒤(`val StatusUnknown = ...` 다음 줄)에 추가:

```kotlin
val SegmentInactive = Color(0xFFF0F0F0)     // SegmentedToggle 비활성 (was raw)
```

- [ ] **Step 3: 컴파일 검증**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./android/gradlew -p android :app:assembleDebug`
Expected: BUILD SUCCESSFUL. 기존 호출부(`Dimens.gap`, `Dimens.paneRadius`, `Dimens.basketWidthFraction`)는 deprecated 경고만 나고 컴파일된다. `Theme.kt`가 쓰는 `Dimens.radiusButton/radiusTile/radiusPane`는 그대로 존재.

- [ ] **Step 4: 단위 테스트 검증**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./android/gradlew -p android :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL (기존 통과 테스트 그대로).

- [ ] **Step 5: 커밋**

```bash
git add android/app/src/main/java/eloom/holybean/ui/theme/Dimens.kt \
        android/app/src/main/java/eloom/holybean/ui/theme/Color.kt
git commit -m "refactor(ui): redefine Dimens on 8pt scale + SegmentInactive token"
```

---

## Task 2: 공용 레이아웃 컴포넌트 6종 신규

새 패키지 `ui/components/layout/`. 각 파일은 함수 하나. 이 태스크 후 컴포넌트는 아직 미사용이라 빌드만 검증한다.

**Files:**
- Create: `app/.../ui/components/layout/ScreenContainer.kt`
- Create: `app/.../ui/components/layout/ScreenHeader.kt`
- Create: `app/.../ui/components/layout/Pane.kt`
- Create: `app/.../ui/components/layout/SectionLabel.kt`
- Create: `app/.../ui/components/layout/TotalRow.kt`
- Create: `app/.../ui/components/layout/StatusDot.kt`

- [ ] **Step 1: `ScreenContainer.kt` 생성**

```kotlin
package eloom.holybean.ui.components.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import eloom.holybean.ui.theme.Dimens

/** 풀스크린 화면의 최상위 래퍼: 배경 + 화면 가장자리 패딩을 통일한다. */
@Composable
fun ScreenContainer(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(Dimens.screenPadding),
        content = content,
    )
}
```

- [ ] **Step 2: `ScreenHeader.kt` 생성**

```kotlin
package eloom.holybean.ui.components.layout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import eloom.holybean.ui.theme.Dimens

/** 제목 + 우측 액션 버튼 행. 헤더-본문 간격을 통일한다. */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(bottom = Dimens.headerGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
            verticalAlignment = Alignment.CenterVertically,
            content = actions,
        )
    }
}
```

- [ ] **Step 3: `Pane.kt` 생성**

```kotlin
package eloom.holybean.ui.components.layout

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import eloom.holybean.ui.theme.Dimens

/** radius + elevation + 내부 패딩을 갖춘 흰 패널. */
@Composable
fun Pane(
    modifier: Modifier = Modifier,
    padding: Dp = Dimens.panePadding,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier,
        shape = RoundedCornerShape(Dimens.radiusPane),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = Dimens.paneElevation,
    ) {
        Column(Modifier.padding(padding), content = content)
    }
}
```

- [ ] **Step 4: `SectionLabel.kt` 생성**

```kotlin
package eloom.holybean.ui.components.layout

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import eloom.holybean.ui.theme.OnSurfaceMuted

/** 섹션 제목 라벨(작은 muted 텍스트). */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = OnSurfaceMuted,
        modifier = modifier,
    )
}
```

- [ ] **Step 5: `TotalRow.kt` 생성**

```kotlin
package eloom.holybean.ui.components.layout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import eloom.holybean.ui.theme.OrangeOnContainer

/** "합계 / N원" 행. 정렬을 SpaceBetween으로 통일. */
@Composable
fun TotalRow(total: Int, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("합계", style = MaterialTheme.typography.titleMedium)
        Text(
            "%,d원".format(total),
            style = MaterialTheme.typography.titleMedium,
            color = OrangeOnContainer,
        )
    }
}
```

- [ ] **Step 6: `StatusDot.kt` 생성**

```kotlin
package eloom.holybean.ui.components.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import eloom.holybean.ui.theme.Dimens
import eloom.holybean.ui.theme.StatusError
import eloom.holybean.ui.theme.StatusOk
import eloom.holybean.ui.theme.StatusUnknown

/** 원형 상태 점. true=정상/false=오류/null=미상. */
@Composable
fun StatusDot(ok: Boolean?, modifier: Modifier = Modifier) {
    val color = when (ok) {
        true -> StatusOk
        false -> StatusError
        null -> StatusUnknown
    }
    Box(
        modifier
            .size(Dimens.statusDot)
            .clip(CircleShape)
            .background(color),
    )
}
```

- [ ] **Step 7: 컴파일 검증**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./android/gradlew -p android :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: 커밋**

```bash
git add android/app/src/main/java/eloom/holybean/ui/components/layout/
git commit -m "feat(ui): add shared layout components (ScreenContainer/Header/Pane/SectionLabel/TotalRow/StatusDot)"
```

---

## Task 3: 공용 컴포넌트 정규화 (BasketRow / MenuTile / SegmentedToggle)

**Files:**
- Modify: `app/.../ui/components/BasketRow.kt`
- Modify: `app/.../ui/components/MenuTile.kt`
- Modify: `app/.../ui/components/SegmentedToggle.kt`

- [ ] **Step 1: `BasketRow.kt` — 세로 패딩 토큰화**

`Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 7.dp)` 를:
```kotlin
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = Dimens.spaceSm),
```
파일 상단 import에 추가:
```kotlin
import eloom.holybean.ui.theme.Dimens
```
(기존 `import androidx.compose.ui.unit.dp` 는 다른 dp 사용이 없으면 제거 — 컴파일 경고 정리.)

- [ ] **Step 2: `MenuTile.kt` — 고정 높이 토큰화**

`modifier = modifier.height(90.dp)` 를:
```kotlin
        modifier = modifier.height(Dimens.menuTileHeight),
```
`Dimens`는 이미 `import eloom.holybean.ui.theme.*`로 임포트됨. `import androidx.compose.ui.unit.dp`는 `BorderStroke(1.dp, ...)`에서 계속 쓰이므로 유지.

- [ ] **Step 3: `SegmentedToggle.kt` — 비활성 색 토큰화**

`inactiveContainerColor = Color(0xFFF0F0F0),` 를:
```kotlin
                    inactiveContainerColor = SegmentInactive,
```
import 정리: `import androidx.compose.ui.graphics.Color` 제거(다른 Color 사용 없음), 추가:
```kotlin
import eloom.holybean.ui.theme.SegmentInactive
```

- [ ] **Step 4: 컴파일 검증**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./android/gradlew -p android :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: 단위 테스트 검증**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./android/gradlew -p android :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: 커밋**

```bash
git add android/app/src/main/java/eloom/holybean/ui/components/BasketRow.kt \
        android/app/src/main/java/eloom/holybean/ui/components/MenuTile.kt \
        android/app/src/main/java/eloom/holybean/ui/components/SegmentedToggle.kt
git commit -m "refactor(ui): tokenize shared component spacing & colors"
```

---

## Task 4: HomeScreen 마이그레이션

`HomeScreen` 컴포저블(`:115-142`)과 `CategoryChips`(`:145-167`), `BasketPane`(`:170-212`)을 토큰+`ScreenContainer`+`Pane`+`TotalRow`로 재조립. **버튼 라벨 "결제"·"주문기록"은 변경 금지.**

**Files:**
- Modify: `app/.../ui/home/HomeScreen.kt`

- [ ] **Step 1: import 추가**

`import eloom.holybean.ui.theme.Dimens` 아래에 추가:
```kotlin
import eloom.holybean.ui.components.layout.Pane
import eloom.holybean.ui.components.layout.ScreenContainer
import eloom.holybean.ui.components.layout.TotalRow
```

- [ ] **Step 2: `HomeScreen` 본문(`:115-142`)을 ScreenContainer 기반으로 교체**

`fun HomeScreen(...) {` 의 본문 `Row(...) { ... }` 전체(`:115`의 `Row` ~ `:141`의 닫는 `}`)를 다음으로 교체:

```kotlin
    ScreenContainer {
        Row(
            Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.paneGap),
        ) {
            Column(Modifier.weight(1f)) {
                CategoryChips(categories, selectedCategory, onCategory)
                Spacer(Modifier.height(Dimens.spaceSm))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.gridGap),
                    verticalArrangement = Arrangement.spacedBy(Dimens.gridGap),
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
            BasketPane(
                orderId, basket, total, onBasketClick, onHistoryClick, onCheckout,
                Modifier.fillMaxHeight().fillMaxWidth(Dimens.paneSplitNarrow),
            )
        }
    }
```

- [ ] **Step 3: `CategoryChips`(`:145-167`) 간격·radius 토큰화**

`LazyRow(horizontalArrangement = Arrangement.spacedBy(5.dp))` 를:
```kotlin
    LazyRow(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm)) {
```
FilterChip의 `shape = RoundedCornerShape(14.dp),` 를:
```kotlin
                shape = RoundedCornerShape(Dimens.radiusChip),
```

- [ ] **Step 4: `BasketPane`(`:170-212`)를 Pane + TotalRow로 교체**

`private fun BasketPane(...) {` 의 본문(`:174`의 `Surface` ~ `:211`의 닫는 `}`)을 다음으로 교체:

```kotlin
    Pane(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${orderId}번 주문", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            OutlinedButton(
                onClick = onHistory,
                shape = RoundedCornerShape(Dimens.radiusButton),
                border = BorderStroke(2.dp, Orange),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Orange),
            ) { Text("주문기록", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold) }
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
        TotalRow(total, Modifier.padding(vertical = Dimens.spaceSm))
        Button(
            onClick = onCheckout, enabled = basket.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().height(Dimens.primaryTouchTarget),
            shape = RoundedCornerShape(Dimens.radiusButton),
            colors = ButtonDefaults.buttonColors(containerColor = Orange),
        ) { Text("결제", style = MaterialTheme.typography.titleMedium) }
    }
```

> 참고: `BasketPane`의 `modifier: Modifier` 파라미터는 그대로 유지(호출부에서 폭·높이 전달). `Pane`이 Surface+Column+panePadding을 담당하므로 기존 `Surface{Column(padding(12.dp))}` 보일러플레이트가 사라진다. `OrangeOnContainer` import는 `TotalRow`가 내부에서 쓰므로 HomeScreen에서 더 이상 직접 안 쓰면 미사용 import 경고가 날 수 있다 — 빌드 경고 시 제거.

- [ ] **Step 5: 컴파일 + 단위 테스트**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./android/gradlew -p android :app:assembleDebug :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL. ("결제"·"주문기록" 라벨, MenuTile 텍스트 불변이므로 셀렉터 영향 없음.)

- [ ] **Step 6: 커밋**

```bash
git add android/app/src/main/java/eloom/holybean/ui/home/HomeScreen.kt
git commit -m "refactor(ui): migrate HomeScreen to shared layout + tokens"
```

---

## Task 5: PaymentScreen 마이그레이션 (+ 결제완료 버튼 버그)

**Files:**
- Modify: `app/.../ui/payment/PaymentScreen.kt`

- [ ] **Step 1: import 추가**

`import eloom.holybean.ui.theme.Dimens` 부근에 추가:
```kotlin
import eloom.holybean.ui.components.layout.Pane
import eloom.holybean.ui.components.layout.ScreenContainer
import eloom.holybean.ui.components.layout.ScreenHeader
import eloom.holybean.ui.components.layout.SectionLabel
import eloom.holybean.ui.components.layout.TotalRow
```

- [ ] **Step 2: 최상위 Column → ScreenContainer + ScreenHeader, 헤더 교체**

`Column(Modifier.fillMaxSize()...padding(Dimens.screenPadding))` 블록의 시작(`:94`)부터 헤더 `Row`(`:97-104`)까지를 다음으로 교체. 즉 `Column(...) {` 와 그 안 첫 `Row(... padding(bottom = 10.dp) ...) { ... }`를:

```kotlin
    ScreenContainer {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader(
                "${orderId}번 주문 · 결제",
                actions = {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.heightIn(min = Dimens.minTouchTarget),
                        shape = RoundedCornerShape(Dimens.radiusButton),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = OnSurfaceMuted),
                    ) { Text("✕ 취소", style = MaterialTheme.typography.bodyMedium) }
                },
            )
```
> 이 변경으로 `Column`이 한 단계 더 중첩되므로, 함수 끝의 닫는 중괄호를 하나 더 추가해야 한다(Step 6에서 확인). 배경/패딩은 `ScreenContainer`가 담당하므로 기존 `.background(...).padding(...)`는 제거됨.

- [ ] **Step 3: 2-pane gap + 좌패널(주문 요약)을 Pane으로**

`Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(Dimens.gap))`(`:105`)를:
```kotlin
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(Dimens.paneGap)) {
```
좌패널 `Surface(Modifier.fillMaxWidth(0.38f)...) { Column(Modifier.padding(12.dp)) { ... } }`(`:106-129`)를:
```kotlin
                Pane(Modifier.fillMaxWidth(Dimens.paneSplitNarrow).fillMaxHeight()) {
                    SectionLabel("주문 요약")
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
                    HorizontalDivider(color = DividerGray, modifier = Modifier.padding(vertical = Dimens.spaceSm))
                    TotalRow(total)
                }
```

- [ ] **Step 4: 우패널(입력)을 Pane + SectionLabel + 섹션 간격으로**

우패널 `Surface(Modifier.weight(1f)...) { Column { Column(...verticalScroll...padding(12.dp)) { ... } HorizontalDivider ... Button ... } }`(`:130-189`)를 다음으로 교체:

```kotlin
                Pane(Modifier.weight(1f).fillMaxHeight(), padding = 0.dp) {
                    Column(
                        Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(Dimens.panePadding),
                        verticalArrangement = Arrangement.spacedBy(Dimens.sectionGap),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceSm)) {
                            SectionLabel("컵 선택")
                            SegmentedToggle(persistentListOf("일회용컵", "머그컵"), cup) { cup = it }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceSm)) {
                            SectionLabel("결제 수단")
                            MethodGrid(PaymentForm.methods.toImmutableList(), first) { first = it }
                            if (first != "무료제공") {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Switch(checked = split, onCheckedChange = { split = it })
                                    Text("  결제수단 추가 (분할결제)", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                        if (split) {
                            val candidates = PaymentForm.secondCandidates(first).toImmutableList()
                            LaunchedEffect(first) {
                                if (second !in candidates) {
                                    second = candidates.firstOrNull()
                                    secondAmt = ""
                                }
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceSm)) {
                                SectionLabel("2번째 결제 수단 (${first} 제외)")
                                MethodRow(candidates, second) { second = it }
                                OutlinedTextField(
                                    value = secondAmt, onValueChange = { secondAmt = it.filter(Char::isDigit) },
                                    label = { Text("${second ?: ""} 금액", style = MaterialTheme.typography.labelSmall) }, singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                val breakdown = PaymentForm.splitBreakdown(first, second, total, secondAmt)
                                Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceXs)) {
                                    breakdown.forEach { line ->
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text(line.label, style = MaterialTheme.typography.labelSmall, color = OrangeText)
                                            Text("%,d원".format(line.amount), style = MaterialTheme.typography.labelSmall, color = OrangeText)
                                        }
                                    }
                                }
                            }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceSm)) {
                            SectionLabel("주문자명")
                            OutlinedTextField(orderer, { orderer = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        }
                    }
                    HorizontalDivider(color = DividerGray)
                    Button(
                        onClick = { onConfirm(PaymentSelection(cup, first, orderer, split, second, secondAmt)) },
                        modifier = Modifier.fillMaxWidth().padding(Dimens.panePadding).height(Dimens.primaryTouchTarget),
                        shape = RoundedCornerShape(Dimens.radiusButton),
                        colors = ButtonDefaults.buttonColors(containerColor = Orange),
                    ) { Text("결제 완료", style = MaterialTheme.typography.titleMedium) }
                }
```

> **버그 수정:** 기존 `.height(56).padding(12.dp)`(순서상 패딩이 높이 안쪽을 깎음)를 `.padding(16).height(56)` 순서로 바꿔, 버튼 바깥 16dp 여백 + 56dp 실제 높이를 보장한다. `Pane(padding = 0.dp)`로 패널 자체 패딩을 끄고, 스크롤 영역과 버튼이 각자 `panePadding`을 적용하도록 했다(버튼 아래/divider가 패널 가장자리에 붙게 하기 위함).

- [ ] **Step 5: 라인 정리 — 제거된 import / Spacer**

위 교체로 `Spacer`(height 10/8/6) 호출이 모두 사라진다. `import androidx.compose.ui.unit.dp`는 `padding(0.dp)`에서 계속 쓰이므로 유지. `MethodGrid`/`MethodRow`(`:194-212`)의 `spacedBy(6.dp)`를 각각 `Arrangement.spacedBy(Dimens.spaceSm)`로 변경:
```kotlin
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceSm)) {
```
```kotlin
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm)) {
```
```kotlin
    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm)) {
```

- [ ] **Step 6: 중괄호 균형 + 컴파일 검증**

`PaymentScreen` 함수가 `ScreenContainer { Column { ... } }`로 한 단계 더 감싸졌으므로, 함수 끝(`MethodGrid` 정의 직전)에 닫는 `}`가 올바른지 확인한다.
Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./android/gradlew -p android :app:assembleDebug :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL. ("결제 완료"·"현금"·"일회용컵" 텍스트 불변이라 `PaymentScreenTest` 영향 없음.)

- [ ] **Step 7: 커밋**

```bash
git add android/app/src/main/java/eloom/holybean/ui/payment/PaymentScreen.kt
git commit -m "refactor(ui): migrate PaymentScreen to shared layout + fix shrunken confirm button"
```

---

## Task 6: OrdersScreen 마이그레이션

**Files:**
- Modify: `app/.../ui/orders/OrdersScreen.kt`

- [ ] **Step 1: import 추가**

`import eloom.holybean.ui.theme.*` 아래에 추가:
```kotlin
import eloom.holybean.ui.components.layout.Pane
import eloom.holybean.ui.components.layout.ScreenContainer
import eloom.holybean.ui.components.layout.ScreenHeader
import eloom.holybean.ui.components.layout.TotalRow
```
(`eloom.holybean.ui.components.*`는 이미 import됨 — `StatChip`/`BasketRow` 등. layout 하위 패키지는 별도 import 필요.)

- [ ] **Step 2: 최상위 Column → ScreenContainer + 헤더/요약/2-pane 재조립**

`fun OrdersScreen(...) {` 본문 전체(`:78`의 `Column` ~ `:144`의 닫는 `}`)를 다음으로 교체:

```kotlin
    ScreenContainer {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader(
                "주문기록",
                actions = {
                    OutlinedButton(
                        onClick = onClose,
                        modifier = Modifier.heightIn(min = Dimens.minTouchTarget),
                        shape = RoundedCornerShape(Dimens.radiusButton),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = OnSurfaceMuted),
                    ) { Text("✕ 닫기", style = MaterialTheme.typography.bodyMedium) }
                },
            )
            Pane(Modifier.fillMaxWidth().padding(bottom = Dimens.spaceSm)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimens.spaceMd),
                ) {
                    StatChip("오늘 총 판매", "%,d원".format(summary.totalSales), OrangeOnContainer)
                    VerticalDivider(Modifier.height(Dimens.spaceXl), color = DividerGray)
                    StatChip("총 건수", "${summary.orderCount}건")
                    VerticalDivider(Modifier.height(Dimens.spaceXl), color = DividerGray)
                    StatChip("총 잔수", "${summary.drinkCount}잔")
                    Spacer(Modifier.weight(1f))
                    OutlinedButton(
                        onClick = onPrintReport,
                        modifier = Modifier.heightIn(min = Dimens.minTouchTarget),
                        shape = RoundedCornerShape(Dimens.radiusButton),
                        border = BorderStroke(2.dp, Orange),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Orange),
                    ) { Text("보고서 출력", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold) }
                }
            }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(Dimens.paneGap)) {
                Pane(Modifier.fillMaxWidth(Dimens.paneSplitWide).fillMaxHeight()) {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(Dimens.itemGap)) {
                        items(orders, key = { it.orderId }) { o ->
                            OrderListItem(o, o.orderId == selectedOrderNumber) { onSelect(o) }
                        }
                    }
                }
                Pane(Modifier.weight(1f).fillMaxHeight()) {
                    Text(
                        if (selectedOrderNumber == 0) "주문을 선택하세요" else "${selectedOrderNumber}번 주문",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    LazyColumn(Modifier.weight(1f)) {
                        itemsIndexed(details, key = { index, _ -> index }) { _, d -> BasketRow(d.name, d.count, d.subtotal) {} }
                    }
                    HorizontalDivider(color = DividerGray, modifier = Modifier.padding(vertical = Dimens.spaceSm))
                    TotalRow(selectedTotal)
                    Row(
                        Modifier.fillMaxWidth().padding(top = Dimens.spaceMd),
                        horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
                    ) {
                        Button(onClick = onReprint, modifier = Modifier.weight(1f).height(Dimens.primaryTouchTarget),
                            shape = RoundedCornerShape(Dimens.radiusButton),
                            colors = ButtonDefaults.buttonColors(containerColor = Orange)) {
                            Text("재출력", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(onClick = onDelete, modifier = Modifier.height(Dimens.primaryTouchTarget),
                            shape = RoundedCornerShape(Dimens.radiusButton),
                            border = BorderStroke(1.dp, DangerRed),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = DangerRed)) { Text("삭제", style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            }
        }
    }
```

> 변경 요약: 헤더 `bottom=10`→`ScreenHeader headerGap`; 요약바 `Surface{Row(padding12)}`→`Pane{Row}`, 하단 `10`→`spaceSm`; divider 높이 `32.dp`→`spaceXl`; pane gap `gap`→`paneGap`; 좌패널 `0.46f`→`paneSplitWide`, 아이템 `Spacer(7)`→`LazyColumn spacedBy(itemGap)`; 합계→`TotalRow`; 버튼행 `top=10`→`spaceMd`, `spacedBy(8)`→`spaceSm`. (삭제 버튼 테두리 `BorderStroke(1.dp, DangerRed)` 유지 — 스케일 예외.)

- [ ] **Step 3: `OrderListItem`(`:149-166`) 내부 패딩 토큰화**

`Column(Modifier.fillMaxWidth().padding(10.dp))` 를:
```kotlin
        Column(Modifier.fillMaxWidth().padding(Dimens.spaceSm)) {
```

- [ ] **Step 4: 컴파일 + 단위 테스트**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./android/gradlew -p android :app:assembleDebug :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL. ("총 잔수"·"152잔"·"재출력" 텍스트 불변 → `OrdersScreenTest` 영향 없음.)

- [ ] **Step 5: 커밋**

```bash
git add android/app/src/main/java/eloom/holybean/ui/orders/OrdersScreen.kt
git commit -m "refactor(ui): migrate OrdersScreen to shared layout + tokens"
```

---

## Task 7: CreditsScreen 마이그레이션 (+ 패널 승격, 버튼 터치 타깃)

**Files:**
- Modify: `app/.../ui/credits/CreditsScreen.kt`

- [ ] **Step 1: import 추가**

`import eloom.holybean.ui.components.BasketRow` 아래에 추가:
```kotlin
import eloom.holybean.ui.components.layout.Pane
import eloom.holybean.ui.components.layout.ScreenContainer
import eloom.holybean.ui.components.layout.ScreenHeader
import eloom.holybean.ui.theme.Dimens
```

- [ ] **Step 2: 본문(`:41-80`)을 ScreenContainer + ScreenHeader + Pane으로 교체**

`Column(Modifier.fillMaxSize().padding(16.dp)) { ... }` 전체(`:41`~`:80`)를 다음으로 교체:

```kotlin
    ScreenContainer {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader(
                "외상 관리",
                actions = {
                    OutlinedButton(
                        onClick = onClose,
                        modifier = Modifier.heightIn(min = Dimens.minTouchTarget),
                    ) { Text("닫기", style = MaterialTheme.typography.bodyMedium) }
                },
            )
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(Dimens.paneGap)) {
                Pane(Modifier.fillMaxWidth(Dimens.paneSplitWide).fillMaxHeight()) {
                    LazyColumn {
                        items(state.creditsList.toImmutableList(), key = { it.orderId }) { c ->
                            Column(
                                Modifier.fillMaxWidth()
                                    .clickable { vm.selectOrder(c.orderId, c.totalAmount, c.date); vm.fetchOrderDetail() }
                                    .padding(vertical = Dimens.spaceSm)
                            ) {
                                Text("${c.orderId}번 · ${c.orderer}", style = MaterialTheme.typography.bodyMedium)
                                Text("%,d원 · ${c.date}".format(c.totalAmount), style = MaterialTheme.typography.labelSmall)
                            }
                            HorizontalDivider()
                        }
                    }
                }
                Pane(Modifier.weight(1f).fillMaxHeight()) {
                    Text("상세", style = MaterialTheme.typography.titleMedium)
                    LazyColumn(Modifier.weight(1f)) {
                        itemsIndexed(state.orderDetails.toImmutableList(), key = { index, _ -> index }) { _, it ->
                            BasketRow(it.name, it.count, it.subtotal) {}
                        }
                    }
                    Button(
                        onClick = { confirmPaid = true },
                        enabled = state.selectedOrderNumber != 0,
                        modifier = Modifier.fillMaxWidth().height(Dimens.primaryTouchTarget),
                        shape = RoundedCornerShape(Dimens.radiusButton),
                    ) {
                        Text("외상 결제완료 처리", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
```
> 변경: 화면/패널을 다른 화면과 동일한 `ScreenContainer`/`Pane`(radius+elevation)으로 통일, gap `10`→`paneGap`, 좌패널 `0.46f`→`paneSplitWide`, 결제완료 버튼에 56dp 높이+radius 부여. `RoundedCornerShape`/`Dimens` 사용을 위해 import 필요(아래 Step 3).

- [ ] **Step 3: 추가 import**

상단 import에 추가:
```kotlin
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
```
(`androidx.compose.foundation.layout.*`가 이미 와일드카드로 import되어 있으면 `height`는 생략 가능 — 현재 `import androidx.compose.foundation.layout.*` 존재하므로 `height` 추가 불필요. `RoundedCornerShape`만 추가.)

- [ ] **Step 4: 컴파일 + 단위 테스트**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./android/gradlew -p android :app:assembleDebug :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: 커밋**

```bash
git add android/app/src/main/java/eloom/holybean/ui/credits/CreditsScreen.kt
git commit -m "refactor(ui): migrate CreditsScreen to shared layout, promote panes, fix touch targets"
```

---

## Task 8: ReportScreen 마이그레이션

**Files:**
- Modify: `app/.../ui/report/ReportScreen.kt`

- [ ] **Step 1: import 추가**

`import eloom.holybean.ui.components.StatChip` 아래에 추가:
```kotlin
import eloom.holybean.ui.components.layout.ScreenContainer
import eloom.holybean.ui.components.layout.ScreenHeader
import eloom.holybean.ui.theme.Dimens
```

- [ ] **Step 2: 본문(`:46-67`)을 ScreenContainer + ScreenHeader로 교체**

`Column(Modifier.fillMaxSize().padding(16.dp)) {` 의 시작과 헤더 `Row`(`:46-50`)를 다음으로 교체(이후 날짜행~LazyColumn은 Step 3에서 토큰화):

```kotlin
    ScreenContainer {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader(
                "기간 매출 리포트",
                actions = {
                    OutlinedButton(
                        onClick = onClose,
                        modifier = Modifier.heightIn(min = Dimens.minTouchTarget),
                    ) { Text("닫기", style = MaterialTheme.typography.bodyMedium) }
                },
            )
```
> `Column`이 `ScreenContainer` 안으로 한 단계 더 들어가므로 함수 끝에 닫는 `}` 하나 추가 필요(Step 4에서 확인).

- [ ] **Step 3: 날짜행·StatChip행·MenuSalesRow 토큰화 + 버튼 터치 타깃**

날짜/조회 행(`:51-56`)을:
```kotlin
            Row(Modifier.padding(vertical = Dimens.spaceMd), horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm)) {
                OutlinedButton(onClick = { pick { start = it } }, modifier = Modifier.heightIn(min = Dimens.minTouchTarget)) { Text(start, style = MaterialTheme.typography.bodyMedium) }
                OutlinedButton(onClick = { pick { end = it } }, modifier = Modifier.heightIn(min = Dimens.minTouchTarget)) { Text(end, style = MaterialTheme.typography.bodyMedium) }
                Button(onClick = { vm.loadReportData(start, end) }, modifier = Modifier.heightIn(min = Dimens.minTouchTarget)) { Text("조회", style = MaterialTheme.typography.bodyMedium) }
                OutlinedButton(onClick = { vm.printReport() }, modifier = Modifier.heightIn(min = Dimens.minTouchTarget)) { Text("출력", style = MaterialTheme.typography.bodyMedium) }
            }
```
StatChip 행(`:58`)을:
```kotlin
            Row(Modifier.padding(vertical = Dimens.spaceSm), horizontalArrangement = Arrangement.spacedBy(Dimens.spaceMd)) {
```
`MenuSalesRow`(`:72`)의 `padding(vertical = 6.dp)`를:
```kotlin
    Row(Modifier.fillMaxWidth().padding(vertical = Dimens.spaceSm), horizontalArrangement = Arrangement.SpaceBetween) {
```

- [ ] **Step 4: 중괄호 균형 + 컴파일 + 단위 테스트**

`ReportRoute` 함수 끝에 `ScreenContainer`/`Column` 추가 중첩분 닫는 `}`가 맞는지 확인. `import androidx.compose.ui.unit.dp`는 다른 dp 사용이 없으면 미사용 경고 → 제거.
Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./android/gradlew -p android :app:assembleDebug :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: 커밋**

```bash
git add android/app/src/main/java/eloom/holybean/ui/report/ReportScreen.kt
git commit -m "refactor(ui): migrate ReportScreen to shared layout + tokens"
```

---

## Task 9: DevToolsScreen 마이그레이션

**Files:**
- Modify: `app/.../ui/settings/DevToolsScreen.kt`

- [ ] **Step 1: import 추가, 미사용 import 정리**

추가:
```kotlin
import eloom.holybean.ui.components.layout.ScreenContainer
import eloom.holybean.ui.components.layout.ScreenHeader
import eloom.holybean.ui.components.layout.StatusDot
```
제거(StatusDot로 대체되며 직접 사용 사라짐): `androidx.compose.foundation.background`, `androidx.compose.foundation.layout.size`, `androidx.compose.foundation.shape.CircleShape`, `androidx.compose.ui.draw.clip`, `eloom.holybean.ui.theme.StatusError`, `eloom.holybean.ui.theme.StatusOk`, `eloom.holybean.ui.theme.StatusUnknown`. (빌드 경고를 보고 최종 정리.)

- [ ] **Step 2: `DevToolsScreen`(`:64-108`)을 ScreenContainer + ScreenHeader로 교체**

`Column(Modifier.fillMaxSize().padding(16.dp)) { ... }` 전체를 다음으로 교체:

```kotlin
    ScreenContainer {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader(
                "🛠 개발자 도구",
                actions = {
                    OutlinedButton(
                        onClick = onClose,
                        modifier = Modifier.heightIn(min = Dimens.minTouchTarget),
                        shape = RoundedCornerShape(Dimens.radiusButton),
                    ) { Text("닫기", style = MaterialTheme.typography.bodyMedium) }
                },
            )
            HealthRow("Pi 프린터 (/health)", state.printerOk,
                when (state.printerOk) {
                    true -> state.printerLatencyMs?.let { "정상 · ${it}ms" } ?: "정상"
                    false -> "응답 실패"
                    null -> "—"
                })
            HealthRow(
                label = "네트워크 연결",
                ok = state.networkOk,
                value = state.networkInfo,
            )
            HealthRow("Firestore", state.firestoreOk,
                when (state.firestoreOk) { true -> "정상"; false -> "응답 없음"; null -> "—" })
            Text(
                "프린터 서버 URL: ${state.printerUrl}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = Dimens.spaceSm),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm)) {
                OutlinedButton(onClick = onRefresh, modifier = Modifier.heightIn(min = Dimens.minTouchTarget), shape = RoundedCornerShape(Dimens.radiusButton)) {
                    Text("새로고침", style = MaterialTheme.typography.bodyMedium)
                }
                Button(
                    onClick = onTestPrint,
                    modifier = Modifier.height(Dimens.primaryTouchTarget),
                    shape = RoundedCornerShape(Dimens.radiusButton),
                    colors = ButtonDefaults.buttonColors(containerColor = Orange),
                ) {
                    Text("테스트 영수증 출력", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
```
> 헤더 후 `Spacer(height(12.dp))` 제거(ScreenHeader headerGap), 버튼 간격 `8`→`spaceSm`, "새로고침"에 최소 터치 타깃 부여.

- [ ] **Step 3: `HealthRow`(`:110-132`)의 점을 StatusDot으로**

`Box(Modifier.size(10.dp).clip(CircleShape).background(when(ok){...}))` 블록(`:116-127`)을:
```kotlin
        StatusDot(ok)
```
`Spacer(Modifier.width(10.dp))`를:
```kotlin
        Spacer(Modifier.width(Dimens.spaceSm))
```
`Modifier.padding(vertical = 6.dp)`를:
```kotlin
        modifier = Modifier.padding(vertical = Dimens.spaceSm),
```

- [ ] **Step 4: 컴파일 + 단위 테스트**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./android/gradlew -p android :app:assembleDebug :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL. (`DevToolsViewModelTest`는 ViewModel 단위 테스트라 UI 변경 무관.)

- [ ] **Step 5: 커밋**

```bash
git add android/app/src/main/java/eloom/holybean/ui/settings/DevToolsScreen.kt
git commit -m "refactor(ui): migrate DevToolsScreen to shared layout + StatusDot"
```

---

## Task 10: SplashScreen 마이그레이션 (+ 상태색 토큰화)

**Files:**
- Modify: `app/.../ui/startup/SplashScreen.kt`

- [ ] **Step 1: import 추가, 미사용 정리**

추가:
```kotlin
import eloom.holybean.ui.components.layout.StatusDot
import eloom.holybean.ui.theme.Dimens
```
제거 후보(StatusDot 대체로 점 직접 그리기 사라짐): `androidx.compose.foundation.background`, `androidx.compose.foundation.layout.size`, `androidx.compose.foundation.shape.CircleShape`, `androidx.compose.ui.draw.clip`, `androidx.compose.ui.graphics.Color`. (빌드 경고 확인 후 정리.)

- [ ] **Step 2: 최상위 Column 패딩·Spacer 토큰화 (`:58-101`)**

`Modifier.fillMaxSize().padding(32.dp)`를:
```kotlin
        modifier = Modifier.fillMaxSize().padding(Dimens.spaceXl),
```
이어지는 Spacer/버튼행을 다음 규칙으로 치환:
- `Spacer(Modifier.height(24.dp))` (제목 뒤, 상태행 뒤 2곳) → `Spacer(Modifier.height(Dimens.sectionGap))`
- `Spacer(Modifier.height(8.dp))` (메시지 사이 2곳) → `Spacer(Modifier.height(Dimens.spaceSm))`
- `Spacer(Modifier.height(16.dp))` (버튼 위 2곳) → `Spacer(Modifier.height(Dimens.spaceMd))`
- `Row(horizontalArrangement = Arrangement.spacedBy(12.dp))` (재시도/진입 버튼행) → `Arrangement.spacedBy(Dimens.spaceMd)`

- [ ] **Step 3: `StatusRow`(`:109-142`)의 점을 StatusDot으로 + 간격 토큰화**

`Box(Modifier.size(10.dp).clip(CircleShape).background(when(status){...}))` 블록(`:120-131`)을:
```kotlin
        StatusDot(
            when (status) {
                StepStatus.Success -> true
                StepStatus.Failed -> false
                StepStatus.Loading -> null
            },
        )
```
`Spacer(Modifier.width(10.dp))`를:
```kotlin
        Spacer(Modifier.width(Dimens.spaceSm))
```
`Modifier.padding(vertical = 6.dp)`를:
```kotlin
        modifier = Modifier.padding(vertical = Dimens.spaceSm),
```

- [ ] **Step 4: 컴파일 + 단위 테스트**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./android/gradlew -p android :app:assembleDebug :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL. (`StartupViewModelTest` 무관.)

- [ ] **Step 5: 커밋**

```bash
git add android/app/src/main/java/eloom/holybean/ui/startup/SplashScreen.kt
git commit -m "refactor(ui): migrate SplashScreen to StatusDot + status color tokens"
```

---

## Task 11: SettingsSheet 토큰화

**Files:**
- Modify: `app/.../ui/settings/SettingsSheet.kt`

- [ ] **Step 1: 패딩 토큰화**

`Column(Modifier.padding(bottom = 24.dp))` 를:
```kotlin
        Column(Modifier.padding(bottom = Dimens.sectionGap)) {
```
제목 `modifier = Modifier.padding(16.dp)` 를:
```kotlin
            modifier = Modifier.padding(Dimens.spaceMd))
```
`SettingsRow`의 `.padding(16.dp)` 를:
```kotlin
            .clickable(onClick = onClick).padding(Dimens.spaceMd))
```
`import androidx.compose.ui.unit.dp`는 다른 dp 사용이 없으면 제거(`Dimens`는 이미 import됨).

- [ ] **Step 2: 컴파일 검증**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./android/gradlew -p android :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: 커밋**

```bash
git add android/app/src/main/java/eloom/holybean/ui/settings/SettingsSheet.kt
git commit -m "refactor(ui): tokenize SettingsSheet spacing"
```

---

## Task 12: MenuManagementScreen 마이그레이션

**Files:**
- Modify: `app/.../ui/menumanagement/MenuManagementScreen.kt`

- [ ] **Step 1: import 추가**

추가:
```kotlin
import eloom.holybean.ui.components.layout.ScreenContainer
import eloom.holybean.ui.components.layout.ScreenHeader
import eloom.holybean.ui.theme.Dimens
```

- [ ] **Step 2: 본문 컨테이너 + 헤더 교체 (`:93-126`)**

`Column(Modifier.fillMaxSize().padding(16.dp)) {` 시작과 헤더 `Row`(`:98-109`), 카테고리 `LazyRow`(`:114-126`)를 다음으로 교체:

```kotlin
    ScreenContainer {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader(
                "메뉴 관리",
                actions = {
                    OutlinedButton(onClick = { editingItem = null; dialogOpen = true }, modifier = Modifier.heightIn(min = Dimens.minTouchTarget)) { Text("추가", style = MaterialTheme.typography.bodyMedium) }
                    OutlinedButton(onClick = { vm.saveMenuOrder() }, modifier = Modifier.heightIn(min = Dimens.minTouchTarget)) { Text("순서 저장", style = MaterialTheme.typography.bodyMedium) }
                    OutlinedButton(onClick = onClose, modifier = Modifier.heightIn(min = Dimens.minTouchTarget)) { Text("닫기", style = MaterialTheme.typography.bodyMedium) }
                },
            )

            // 카테고리 칩(전체(0)는 정렬 의미가 없어 1~5만 노출). names 인덱스 i == 카테고리 번호.
            // ViewModel.onCategorySelected(idx) 는 내부에서 idx + 1 을 카테고리로 사용하므로 (i - 1) 을 넘긴다.
            // state.selectedCategoryIndex 에는 카테고리 번호(1..5)가 들어 있다.
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm)) {
                val categoryNames = MenuCategories.names
                items((1..categoryNames.lastIndex).toList()) { i ->
                    FilterChip(
                        selected = i == state.selectedCategoryIndex,
                        onClick = { vm.onCategorySelected(i - 1) },
                        label = { Text(categoryNames[i], style = MaterialTheme.typography.bodyMedium) },
                    )
                }
            }
```
> 헤더 내 `Spacer(width(8.dp))` 2개 제거(ScreenHeader가 `spacedBy(spaceSm)`), 칩 gap `5`→`spaceSm`, 칩 `padding(top=8)` 제거(헤더 headerGap이 간격 제공).

- [ ] **Step 3: 메뉴 LazyColumn 상단 패딩·trailing Spacer 토큰화**

`LazyColumn(state = listState, modifier = Modifier.weight(1f).padding(top = 10.dp))`(`:128-133`)를:
```kotlin
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).padding(top = Dimens.spaceSm),
            ) {
```
trailing의 `Spacer(Modifier.width(8.dp))`(`:156`)를:
```kotlin
                                    Spacer(Modifier.width(Dimens.spaceSm))
```
(드래그 `tonalElevation = if (isDragging) 4.dp else 0.dp` 는 elevation이므로 변경하지 않음.)

- [ ] **Step 4: 함수 끝 중괄호 균형 확인 + 컴파일 + 단위 테스트**

`MenuManagementRoute`의 `Column` 본문이 `ScreenContainer`로 한 단계 더 감싸졌으므로, `if (dialogOpen)` 블록 직전에 닫는 `}` 하나 추가 필요. `LazyColumn`의 `items { ... }` 블록은 그대로 유지.
Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./android/gradlew -p android :app:assembleDebug :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL. (`MenuManagementViewModelTest` 무관.)

- [ ] **Step 5: 커밋**

```bash
git add android/app/src/main/java/eloom/holybean/ui/menumanagement/MenuManagementScreen.kt
git commit -m "refactor(ui): migrate MenuManagementScreen to shared layout + tokens"
```

---

## Task 13: deprecated alias 제거 + 전체 검증

모든 호출부가 새 이름으로 옮겨졌으므로 alias를 제거하고 전체를 검증한다.

**Files:**
- Modify: `app/.../ui/theme/Dimens.kt`

- [ ] **Step 1: 잔존 alias 사용처 확인**

Run: `grep -rn "Dimens.gap\b\|Dimens.tileRadius\|Dimens.paneRadius\|Dimens.basketWidthFraction" android/app/src/main`
Expected: 출력 없음(모두 새 이름으로 교체됨). 만약 남아 있으면 해당 파일을 새 이름(`paneGap`/`radiusTile`/`radiusPane`/`paneSplitNarrow`)으로 고친 뒤 진행.

- [ ] **Step 2: `Dimens.kt`에서 deprecated 블록 제거**

`Dimens.kt` 끝의 `// ── 폐기 예정 alias ...` 주석과 그 아래 4개 `@Deprecated val` 줄을 모두 삭제.

- [ ] **Step 3: 컴파일 + 단위 테스트**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./android/gradlew -p android :app:assembleDebug :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, deprecated 경고 사라짐.

- [ ] **Step 4: Compose UI 테스트 (에뮬레이터)**

에뮬레이터(`emulator-5554`) 부팅 상태에서:
Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./android/gradlew -p android :app:connectedDebugAndroidTest`
Expected: `MenuTileTest`/`OrdersScreenTest`/`PaymentScreenTest` 통과. `HomeScreenTest.checkoutDisabledWhenBasketEmpty`는 `"결제 →"`/`"결제"` 선행 불일치로 실패할 수 있음 — **본 작업이 원인이 아님**을 확인(이전 커밋에서도 동일하게 실패하는지 `git stash` 없이 코드 텍스트 `"결제"` 그대로임을 근거로 판단). 이 실패는 별도 이슈로 기록하고 본 plan 범위 밖으로 둔다.

- [ ] **Step 5: 육안 검증 체크리스트 (에뮬레이터)**

각 화면을 돌며 확인:
- [ ] 모든 화면 가장자리 패딩이 16dp로 동일한가
- [ ] 좁은 장바구니 패널(Home, 0.38f)에서 텍스트 잘림이 없는가 (12→16 패딩 확대 영향)
- [ ] 헤더–본문 간격, 합계 행, 상태 점이 화면 간 동일하게 보이는가
- [ ] Report/Credits 배경이 회색(`ScreenBg`)으로 통일됐는가
- [ ] Payment "결제 완료" 버튼이 56dp 높이로 정상 렌더되는가(찌그러짐 해소)
- [ ] accessibility-standards 후속의 목업 정합 육안 점검도 함께 수행

> 잘림이 발견되면 해당 패널만 `Pane(padding = Dimens.spaceSm)`로 조정(스펙 §9).

- [ ] **Step 6: 커밋**

```bash
git add android/app/src/main/java/eloom/holybean/ui/theme/Dimens.kt
git commit -m "refactor(ui): drop deprecated Dimens aliases"
```

---

## Self-Review 결과 (작성자 점검)

**스펙 커버리지:** §4 토큰→Task 1, §5 컴포넌트→Task 2, §7 컴포넌트 정규화→Task 3, §6.1~6.9 화면→Task 4~12, §3 alias 제거→Task 13, §8 검증→각 태스크 게이트 + Task 13. 버그 4건: 결제버튼→Task 5, Splash 색→Task 10, 보조버튼 터치타깃→Task 5~9·12, raw radius/color→Task 3·4. 누락 없음.

**플레이스홀더:** 없음. 모든 코드 변경 스텝에 실제 코드 제시.

**타입/이름 일관성:** 컴포넌트 시그니처(`ScreenContainer`/`ScreenHeader`/`Pane(padding)`/`SectionLabel`/`TotalRow(total)`/`StatusDot(ok)`)가 정의(Task 2)와 사용(Task 4~12)에서 일치. 토큰 이름(`paneGap`/`paneSplitNarrow`/`paneSplitWide`/`radiusChip`/`statusDot`/`menuTileHeight`/`SegmentInactive`)이 정의(Task 1)와 사용 전반에서 일치.

**주의 사항(실행자):** 중괄호 균형은 Payment(Task 5)·Report(Task 8)·MenuMgmt(Task 12)에서 `ScreenContainer`/`Column` 중첩이 한 단계 늘어나므로 각 함수 끝 닫는 `}`를 반드시 확인할 것. 미사용 import는 빌드 경고로 드러나면 정리.
