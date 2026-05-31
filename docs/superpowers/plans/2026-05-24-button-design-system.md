# 버튼 디자인시스템 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 화면 전반에 흩어진 raw Compose 버튼 호출을 의미별 디자인시스템 컴포넌트(`PrimaryButton`/`SecondaryButton`/`DangerButton`/`AppTextButton`/`AppIconButton`)로 통일한다.

**Architecture:** `ui/components/buttons/AppButtons.kt` 한 파일에 공개 API 5종을 두고, filled/outlined 3종은 private 코어 컴포저블에 위임해 shape·높이(48dp)·typography(bodyMedium)·색상·loading 스피너를 단독으로 강제한다. 호출부는 `text/onClick/modifier/enabled`(+Primary `loading`)만 결정한다. 8개 화면을 화면 단위로 점진 교체하며, 기존 androidTest가 회귀 가드 역할을 한다.

**Tech Stack:** Kotlin, Jetpack Compose Material3, JUnit4 + Compose UI Test (`createComposeRule`).

**빌드/테스트 환경 (중요):** `java`는 PATH에 없다. 모든 Gradle 호출은 `android/` 디렉터리에서 `JAVA_HOME=/opt/homebrew/opt/openjdk@17` 프리픽스로 실행한다.
- 단위 테스트: `cd android && JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:testDebugUnitTest`
- 계측(Compose UI) 테스트: 에뮬레이터/기기 연결 필요. `cd android && JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:connectedDebugAndroidTest`
- 컴파일만 빠르게 확인: `cd android && JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:compileDebugKotlin`

---

## 컴포넌트 ↔ raw 버튼 매핑 (마이그레이션 기준표)

| 기존 raw 호출 | 교체 대상 | 비고 |
|---|---|---|
| `Button(containerColor=Orange)` + Text | `PrimaryButton(text, onClick)` | shape/height/colors/style 인자 전부 삭제 |
| 결제완료처럼 수동 `CircularProgressIndicator` 분기 | `PrimaryButton(text, onClick, loading = isSubmitting)` | 수동 스피너 코드 삭제 |
| `OutlinedButton(...)` (기본/Orange/muted/accent 무엇이든) + Text | `SecondaryButton(text, onClick)` | leadingIcon·border·colors·shape·height 삭제 (중립 캐논으로 통일) |
| `OutlinedButton(border=DangerRed, contentColor=DangerRed)` "삭제" | `DangerButton(text, onClick)` | |
| 다이얼로그 `confirmButton`/`dismissButton`의 `TextButton` + Text | `AppTextButton(text, onClick)` | |
| `IconButton { Icon(...) }` | `AppIconButton(icon, contentDescription, onClick, modifier)` | tint/터치타깃 통일 |

**교체 원칙:** `onClick`, `enabled`, `modifier`(레이아웃용 `fillMaxWidth`/`weight`/`padding`), 라벨 text는 보존한다. shape·colors·border·height·`style = MaterialTheme.typography.*`·leadingIcon은 삭제한다(컴포넌트가 강제). 삭제로 더 이상 쓰이지 않는 import(`ButtonDefaults`, `RoundedCornerShape`, `BorderStroke`, `Dimens.radiusButton` 등)도 함께 제거한다.

---

## Task 1: 핵심 버튼 컴포넌트 (Primary/Secondary/Danger + private 코어)

**Files:**
- Create: `android/app/src/main/java/eloom/holybean/ui/components/buttons/AppButtons.kt`
- Test: `android/app/src/androidTest/kotlin/eloom/holybean/ui/components/buttons/AppButtonsTest.kt`

- [ ] **Step 1: 실패하는 컴포넌트 테스트 작성**

`AppButtonsTest.kt`:
```kotlin
package eloom.holybean.ui.components.buttons

import androidx.compose.material3.Text
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test

class AppButtonsTest {
    @get:Rule val rule = createComposeRule()

    @Test fun primary_click_invokesCallback() {
        var clicked = false
        rule.setContent { PrimaryButton("결제 완료", onClick = { clicked = true }) }
        rule.onNodeWithText("결제 완료").assertIsDisplayed().performClick()
        assert(clicked)
    }

    @Test fun primary_disabled_doesNotClick() {
        var clicked = false
        rule.setContent { PrimaryButton("결제 완료", onClick = { clicked = true }, enabled = false) }
        rule.onNodeWithText("결제 완료").performClick()
        assert(!clicked)
    }

    @Test fun primary_loading_doesNotClick() {
        var clicked = false
        rule.setContent { PrimaryButton("결제 완료", onClick = { clicked = true }, loading = true) }
        rule.onNodeWithText("결제 완료").performClick()
        assert(!clicked)
    }

    @Test fun secondary_click_invokesCallback() {
        var clicked = false
        rule.setContent { SecondaryButton("취소", onClick = { clicked = true }) }
        rule.onNodeWithText("취소").performClick()
        assert(clicked)
    }

    @Test fun danger_click_invokesCallback() {
        var clicked = false
        rule.setContent { DangerButton("삭제", onClick = { clicked = true }) }
        rule.onNodeWithText("삭제").performClick()
        assert(clicked)
    }
}
```

- [ ] **Step 2: 테스트가 컴파일 실패하는지 확인**

Run: `cd android && JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:compileDebugAndroidTestKotlin`
Expected: FAIL — `PrimaryButton`/`SecondaryButton`/`DangerButton` unresolved reference.

- [ ] **Step 3: AppButtons.kt 구현 (Primary/Secondary/Danger + 코어)**

`AppButtons.kt`:
```kotlin
package eloom.holybean.ui.components.buttons

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eloom.holybean.ui.theme.DangerRed
import eloom.holybean.ui.theme.Dimens
import eloom.holybean.ui.theme.OnSurface
import eloom.holybean.ui.theme.Orange

private val ButtonShape = RoundedCornerShape(Dimens.radiusButton)

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier.heightIn(min = Dimens.minTouchTarget),
        shape = ButtonShape,
        colors = ButtonDefaults.buttonColors(containerColor = Orange, contentColor = OnSurface),
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = OnSurface,
            )
            Spacer(Modifier.width(Dimens.spaceSm))
        }
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = Dimens.minTouchTarget),
        shape = ButtonShape,
        border = BorderStroke(1.dp, OnSurface),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = OnSurface),
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun DangerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = Dimens.minTouchTarget),
        shape = ButtonShape,
        border = BorderStroke(1.dp, DangerRed),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = DangerRed),
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
```

참고: `ButtonColors` import는 다음 Task에서 쓰지 않으면 불필요하므로, 위 코드에서 실제 사용되지 않은 import(`ButtonColors`)는 넣지 말 것. 위 블록에는 의도적으로 제외되어 있다.

- [ ] **Step 4: 계측 테스트 통과 확인**

Run: `cd android && JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:connectedDebugAndroidTest --tests "eloom.holybean.ui.components.buttons.AppButtonsTest"`
Expected: PASS (5 tests). 에뮬레이터/기기가 연결되어 있어야 한다.

- [ ] **Step 5: 커밋**

```bash
git add android/app/src/main/java/eloom/holybean/ui/components/buttons/AppButtons.kt \
        android/app/src/androidTest/kotlin/eloom/holybean/ui/components/buttons/AppButtonsTest.kt
git commit -m "feat(ui): add Primary/Secondary/Danger design-system buttons"
```

---

## Task 2: AppTextButton + AppIconButton

**Files:**
- Modify: `android/app/src/main/java/eloom/holybean/ui/components/buttons/AppButtons.kt`
- Modify: `android/app/src/androidTest/kotlin/eloom/holybean/ui/components/buttons/AppButtonsTest.kt`

- [ ] **Step 1: 실패하는 테스트 추가**

`AppButtonsTest.kt`의 클래스 안에 테스트 추가:
```kotlin
    @Test fun textButton_click_invokesCallback() {
        var clicked = false
        rule.setContent { AppTextButton("담기", onClick = { clicked = true }) }
        rule.onNodeWithText("담기").performClick()
        assert(clicked)
    }

    @Test fun iconButton_click_invokesCallback() {
        var clicked = false
        rule.setContent {
            AppIconButton(
                icon = androidx.compose.material.icons.Icons.Filled.Add,
                contentDescription = "추가",
                onClick = { clicked = true },
            )
        }
        rule.onNodeWithContentDescription("추가").performClick()
        assert(clicked)
    }
```
파일 상단 import에 다음을 추가:
```kotlin
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
```

- [ ] **Step 2: 컴파일 실패 확인**

Run: `cd android && JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:compileDebugAndroidTestKotlin`
Expected: FAIL — `AppTextButton`/`AppIconButton` unresolved reference.

- [ ] **Step 3: AppButtons.kt에 구현 추가**

`AppButtons.kt` 하단에 추가:
```kotlin
@Composable
fun AppTextButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    TextButton(onClick = onClick, enabled = enabled) {
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun AppIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(Dimens.minTouchTarget),
        enabled = enabled,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = OnSurface)
    }
}
```
`AppButtons.kt` 상단 import에 추가:
```kotlin
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.vector.ImageVector
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd android && JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:connectedDebugAndroidTest --tests "eloom.holybean.ui.components.buttons.AppButtonsTest"`
Expected: PASS (7 tests).

- [ ] **Step 5: 커밋**

```bash
git add android/app/src/main/java/eloom/holybean/ui/components/buttons/AppButtons.kt \
        android/app/src/androidTest/kotlin/eloom/holybean/ui/components/buttons/AppButtonsTest.kt
git commit -m "feat(ui): add AppTextButton + AppIconButton to design system"
```

---

## Task 3: PaymentScreen 마이그레이션 (loading 포함)

**Files:**
- Modify: `android/app/src/main/java/eloom/holybean/ui/payment/PaymentScreen.kt`
- Test(guard): `android/app/src/androidTest/kotlin/eloom/holybean/ui/payment/PaymentScreenTest.kt`

대상 call site: 헤더 취소(`OutlinedButton`, ~line 110, 아이콘+muted), 하단 결제완료(`Button` + 수동 로딩, ~line 197).

- [ ] **Step 1: 회귀 가드 — 기존 테스트가 통과 상태인지 확인**

Run: `cd android && JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:connectedDebugAndroidTest --tests "eloom.holybean.ui.payment.PaymentScreenTest"`
Expected: PASS (변경 전 baseline).

- [ ] **Step 2: 헤더 취소 버튼 교체**

기존 `ScreenHeader(... actions = { OutlinedButton(... Icon(Close) + "취소") })` 블록의 `OutlinedButton(...) { ... }` 전체를 아래로 교체:
```kotlin
                        SecondaryButton("취소", onClick = onCancel, enabled = !isSubmitting)
```
(leadingIcon 미노출 결정에 따라 `Icon(Close)` 제거.)

- [ ] **Step 3: 하단 결제완료 버튼 교체**

기존 `Button(onClick = { onConfirm(...) }, ... ) { if (isSubmitting) {...수동 스피너...} else {...} }` 전체를 아래로 교체:
```kotlin
                        PrimaryButton(
                            text = if (isSubmitting) "처리 중…" else "결제 완료",
                            onClick = { onConfirm(PaymentSelection(cup, first, orderer, split, second, secondAmt)) },
                            modifier = Modifier.fillMaxWidth().padding(Dimens.panePadding),
                            loading = isSubmitting,
                        )
```

- [ ] **Step 4: 사용하지 않는 import 정리 + import 추가**

`import eloom.holybean.ui.components.buttons.PrimaryButton` 와 `import eloom.holybean.ui.components.buttons.SecondaryButton` 추가. 더 이상 쓰지 않으면 `Button`, `OutlinedButton`, `ButtonDefaults`, `RoundedCornerShape`, `CircularProgressIndicator`, `Icons.Filled.Close`, `OnSurfaceMuted` 관련 import 제거(다른 곳에서 여전히 쓰이면 유지).

- [ ] **Step 5: 컴파일 + 회귀 테스트 통과 확인**

Run: `cd android && JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:connectedDebugAndroidTest --tests "eloom.holybean.ui.payment.PaymentScreenTest"`
Expected: PASS (기존 테스트 그대로 통과).

- [ ] **Step 6: 커밋**

```bash
git add android/app/src/main/java/eloom/holybean/ui/payment/PaymentScreen.kt
git commit -m "refactor(ui): migrate PaymentScreen to design-system buttons"
```

---

## Task 4: HomeScreen 마이그레이션

**Files:**
- Modify: `android/app/src/main/java/eloom/holybean/ui/home/HomeScreen.kt`
- Test(guard): `android/app/src/androidTest/kotlin/eloom/holybean/ui/home/HomeScreenTest.kt`

대상 call site: 주문기록(`OutlinedButton` Orange 2dp bold, ~line 207) → `SecondaryButton`; 결제(`Button` Orange, ~line 229) → `PrimaryButton`; 수량입력 다이얼로그의 `TextButton` "담기"(~line 252)·"취소"(~line 254) → `AppTextButton`.

- [ ] **Step 1: 회귀 가드 baseline 확인**

Run: `cd android && JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:connectedDebugAndroidTest --tests "eloom.holybean.ui.home.HomeScreenTest"`
Expected: PASS.

- [ ] **Step 2: 주문기록 버튼 교체**

기존 `OutlinedButton(onClick = onHistory, shape=..., border = BorderStroke(2.dp, Orange), colors=...) { Text("주문기록", ...) }` 전체를:
```kotlin
            SecondaryButton("주문기록", onClick = onHistory)
```

- [ ] **Step 3: 결제 버튼 교체**

기존 `Button(onClick = onCheckout, enabled = basket.isNotEmpty(), modifier = Modifier.fillMaxWidth().height(...), shape=..., colors=...) { Text("결제", ...) }` 전체를:
```kotlin
        PrimaryButton(
            "결제",
            onClick = onCheckout,
            modifier = Modifier.fillMaxWidth(),
            enabled = basket.isNotEmpty(),
        )
```

- [ ] **Step 4: 다이얼로그 TextButton 2개 교체**

`confirmButton = { TextButton(onClick = { text.toIntOrNull()?.let(onConfirm) }) { Text("담기", ...) } }` →
```kotlin
        confirmButton = { AppTextButton("담기", onClick = { text.toIntOrNull()?.let(onConfirm) }) },
```
`dismissButton = { TextButton(onClick = onDismiss) { Text("취소", ...) } }` →
```kotlin
        dismissButton = { AppTextButton("취소", onClick = onDismiss) },
```

- [ ] **Step 5: import 정리**

`PrimaryButton`, `SecondaryButton`, `AppTextButton` import 추가. 미사용된 `Button`, `OutlinedButton`, `TextButton`, `ButtonDefaults`, `BorderStroke`, `RoundedCornerShape` import 제거(잔존 사용처 확인 후).

- [ ] **Step 6: 컴파일 + 회귀 테스트**

Run: `cd android && JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:connectedDebugAndroidTest --tests "eloom.holybean.ui.home.HomeScreenTest"`
Expected: PASS.

- [ ] **Step 7: 커밋**

```bash
git add android/app/src/main/java/eloom/holybean/ui/home/HomeScreen.kt
git commit -m "refactor(ui): migrate HomeScreen to design-system buttons"
```

---

## Task 5: OrdersScreen 마이그레이션

**Files:**
- Modify: `android/app/src/main/java/eloom/holybean/ui/orders/OrdersScreen.kt`
- Test(guard): `android/app/src/androidTest/kotlin/eloom/holybean/ui/orders/OrdersScreenTest.kt`

대상 call site: 삭제확인 다이얼로그 `TextButton` "삭제"(~line 52)·"취소"(~line 53) → `AppTextButton`; 목록/필터의 `OutlinedButton`(~line 88, ~line 115) → `SecondaryButton`; 재출력 `Button`(~line 146) → `PrimaryButton`; 삭제 `OutlinedButton(border=DangerRed)`(~line 151) → `DangerButton`.

- [ ] **Step 1: 회귀 가드 baseline**

Run: `cd android && JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:connectedDebugAndroidTest --tests "eloom.holybean.ui.orders.OrdersScreenTest"`
Expected: PASS.

- [ ] **Step 2: 다이얼로그 버튼 교체**

`confirmButton = { TextButton(onClick = { viewModel.deleteOrder(); confirmDelete = false }) { Text("삭제", ...) } }` →
```kotlin
        confirmButton = { AppTextButton("삭제", onClick = { viewModel.deleteOrder(); confirmDelete = false }) },
```
`dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("취소", ...) } }` →
```kotlin
        dismissButton = { AppTextButton("취소", onClick = { confirmDelete = false }) },
```

- [ ] **Step 3: 재출력/삭제 버튼 교체 (~line 146~151 Row)**

기존:
```kotlin
                        Button(onClick = onReprint, modifier = Modifier.weight(1f).height(Dimens.primaryTouchTarget),
                            shape = RoundedCornerShape(Dimens.radiusButton),
                            colors = ButtonDefaults.buttonColors(containerColor = Orange)) {
                            Text("재출력", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(onClick = onDelete, modifier = Modifier.height(Dimens.primaryTouchTarget),
                            shape = RoundedCornerShape(Dimens.radiusButton),
                            border = BorderStroke(1.dp, DangerRed),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = DangerRed)) { Text("삭제", style = MaterialTheme.typography.bodyMedium) }
```
교체:
```kotlin
                        PrimaryButton("재출력", onClick = onReprint, modifier = Modifier.weight(1f))
                        DangerButton("삭제", onClick = onDelete)
```

- [ ] **Step 4: 나머지 OutlinedButton 2곳 교체**

~line 88, ~line 115의 `OutlinedButton(onClick = X, ...) { Text("<라벨>", ...) }` 를 각각 `SecondaryButton("<라벨>", onClick = X, modifier = <기존 레이아웃 modifier가 있으면 유지>)` 로 교체. 라벨/onClick/레이아웃 modifier는 그대로 보존하고 shape·colors·border·height·style 인자는 삭제한다.

- [ ] **Step 5: import 정리**

`PrimaryButton`, `SecondaryButton`, `DangerButton`, `AppTextButton` import 추가. 미사용 `Button`, `OutlinedButton`, `TextButton`, `ButtonDefaults`, `BorderStroke`, `RoundedCornerShape`, `FontWeight`(잔존 확인) 제거.

- [ ] **Step 6: 컴파일 + 회귀 테스트**

Run: `cd android && JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:connectedDebugAndroidTest --tests "eloom.holybean.ui.orders.OrdersScreenTest"`
Expected: PASS.

- [ ] **Step 7: 커밋**

```bash
git add android/app/src/main/java/eloom/holybean/ui/orders/OrdersScreen.kt
git commit -m "refactor(ui): migrate OrdersScreen to design-system buttons"
```

---

## Task 6: ReportScreen 마이그레이션

**Files:**
- Modify: `android/app/src/main/java/eloom/holybean/ui/report/ReportScreen.kt`

대상 call site: ~line 52, 59, 60, 62 `OutlinedButton`(날짜 시작/끝/출력 등) → `SecondaryButton`; ~line 61 조회 `Button` → `PrimaryButton`. ReportScreen 전용 androidTest는 없으므로 컴파일 + 전체 단위 테스트로 검증한다.

- [ ] **Step 1: 조회 버튼 교체 (~line 61)**

`Button(onClick = { vm.loadReportData(start, end) }, modifier = Modifier.heightIn(min = Dimens.minTouchTarget)) { Text("조회", ...) }` →
```kotlin
                Button(...)  // 아래로 교체
                PrimaryButton("조회", onClick = { vm.loadReportData(start, end) })
```
(최종 코드는 `PrimaryButton("조회", onClick = { vm.loadReportData(start, end) })` 한 줄.)

- [ ] **Step 2: OutlinedButton 4곳 교체**

~line 52, 59, 60, 62의 `OutlinedButton(onClick = X, modifier = Modifier.heightIn(min = Dimens.minTouchTarget)) { Text("<라벨>", ...) }` 를 각각 `SecondaryButton("<라벨>", onClick = X)` 로 교체. 날짜 버튼의 라벨은 동적 변수(`start`/`end`)이므로 `SecondaryButton(start, onClick = { pick { start = it } })` 형태로 변수를 그대로 넘긴다.

- [ ] **Step 3: import 정리**

`PrimaryButton`, `SecondaryButton` import 추가. 미사용 `Button`, `OutlinedButton`, `RoundedCornerShape`, `ButtonDefaults` 제거.

- [ ] **Step 4: 컴파일 확인**

Run: `cd android && JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: 커밋**

```bash
git add android/app/src/main/java/eloom/holybean/ui/report/ReportScreen.kt
git commit -m "refactor(ui): migrate ReportScreen to design-system buttons"
```

---

## Task 7: CreditsScreen 마이그레이션

**Files:**
- Modify: `android/app/src/main/java/eloom/holybean/ui/credits/CreditsScreen.kt`

대상 call site: ~line 31 `TextButton` "처리"·~line 32 "취소" → `AppTextButton`; ~line 49 `OutlinedButton` → `SecondaryButton`; ~line 78 `Button` → `PrimaryButton`. 라벨/onClick은 읽어서 그대로 보존.

- [ ] **Step 1: 다이얼로그 TextButton 2곳 교체**

`confirmButton = { TextButton(onClick = { vm.handleDeleteButton(); confirmPaid = false }) { Text("처리", ...) } }` →
```kotlin
        confirmButton = { AppTextButton("처리", onClick = { vm.handleDeleteButton(); confirmPaid = false }) },
```
`dismissButton = { TextButton(onClick = { confirmPaid = false }) { Text("취소", ...) } }` →
```kotlin
        dismissButton = { AppTextButton("취소", onClick = { confirmPaid = false }) },
```

- [ ] **Step 2: OutlinedButton(~line 49) 교체**

해당 `OutlinedButton(onClick = X, ...) { Text("<라벨>", ...) }` 을 `SecondaryButton("<라벨>", onClick = X, modifier = <기존 레이아웃 modifier 유지>)` 로 교체.

- [ ] **Step 3: Button(~line 78) 교체**

해당 `Button(onClick = X, ...) { Text("<라벨>", ...) }` 을 `PrimaryButton("<라벨>", onClick = X, modifier = <기존 레이아웃 modifier 유지>)` 로 교체.

- [ ] **Step 4: import 정리**

`PrimaryButton`, `SecondaryButton`, `AppTextButton` import 추가. 미사용 raw 버튼/`ButtonDefaults`/`RoundedCornerShape`/`BorderStroke` import 제거.

- [ ] **Step 5: 컴파일 확인**

Run: `cd android && JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: 커밋**

```bash
git add android/app/src/main/java/eloom/holybean/ui/credits/CreditsScreen.kt
git commit -m "refactor(ui): migrate CreditsScreen to design-system buttons"
```

---

## Task 8: MenuManagementScreen 마이그레이션 (IconButton 포함)

**Files:**
- Modify: `android/app/src/main/java/eloom/holybean/ui/menumanagement/MenuManagementScreen.kt`

대상 call site: ~line 101~103 `OutlinedButton` 3개("추가"/"순서 저장"/"닫기") → `SecondaryButton`; ~line 222·248 다이얼로그 confirm `TextButton`, ~line 227·256 dismiss `TextButton` "취소" → `AppTextButton`; ~line 139 드래그 핸들 `IconButton` → `AppIconButton`.

- [ ] **Step 1: 상단 OutlinedButton 3개 교체**

```kotlin
                    SecondaryButton("추가", onClick = { editingItem = null; dialogOpen = true })
                    SecondaryButton("순서 저장", onClick = { vm.saveMenuOrder() })
                    SecondaryButton("닫기", onClick = onClose)
```

- [ ] **Step 2: 다이얼로그 TextButton 교체**

~line 222·248의 `TextButton(onClick = { ... }) { Text("<라벨>", ...) }` confirm 버튼은 `AppTextButton("<라벨>", onClick = { ... })`, ~line 227·256의 dismiss는 `AppTextButton("취소", onClick = onDismiss/onCancel)` 로 교체(기존 onClick 람다 그대로 보존).

- [ ] **Step 3: 드래그 핸들 IconButton 교체 (~line 139)**

기존:
```kotlin
                                IconButton(
                                    onClick = {},
                                    modifier = Modifier.longPressDraggableHandle(),
                                ) {
                                    Icon(Icons.Filled.DragHandle, contentDescription = "정렬 핸들")
                                }
```
교체:
```kotlin
                                AppIconButton(
                                    icon = Icons.Filled.DragHandle,
                                    contentDescription = "정렬 핸들",
                                    onClick = {},
                                    modifier = Modifier.longPressDraggableHandle(),
                                )
```

- [ ] **Step 4: import 정리**

`SecondaryButton`, `AppTextButton`, `AppIconButton` import 추가. 미사용 `OutlinedButton`, `TextButton`, `IconButton`, `ButtonDefaults`, `RoundedCornerShape` 제거. `Icon`/`Icons.Filled.DragHandle`는 다른 곳(예: 다른 Icon) 사용 여부 확인 후 정리.

- [ ] **Step 5: 컴파일 확인**

Run: `cd android && JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: 커밋**

```bash
git add android/app/src/main/java/eloom/holybean/ui/menumanagement/MenuManagementScreen.kt
git commit -m "refactor(ui): migrate MenuManagementScreen to design-system buttons"
```

---

## Task 9: DevToolsScreen 마이그레이션

**Files:**
- Modify: `android/app/src/main/java/eloom/holybean/ui/settings/DevToolsScreen.kt`

대상 call site: ~line 63·89 `OutlinedButton` → `SecondaryButton`; ~line 92 `Button` → `PrimaryButton`. 라벨/onClick/레이아웃 modifier 보존.

- [ ] **Step 1: OutlinedButton 2곳 교체**

~line 63, 89의 `OutlinedButton(onClick = X, modifier = ..., shape = ...) { Text("<라벨>", ...) }` 을 `SecondaryButton("<라벨>", onClick = X, modifier = <기존 레이아웃 modifier 유지>)` 로 교체. (예: ~line 89는 `SecondaryButton("새로고침"/실제라벨, onClick = onRefresh)`.)

- [ ] **Step 2: Button(~line 92) 교체**

`Button(onClick = X, ...) { Text("<라벨>", ...) }` 을 `PrimaryButton("<라벨>", onClick = X, modifier = <기존 레이아웃 modifier 유지>)` 로 교체.

- [ ] **Step 3: import 정리**

`PrimaryButton`, `SecondaryButton` import 추가. 미사용 `Button`, `OutlinedButton`, `ButtonDefaults`, `RoundedCornerShape` 제거.

- [ ] **Step 4: 컴파일 확인**

Run: `cd android && JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: 커밋**

```bash
git add android/app/src/main/java/eloom/holybean/ui/settings/DevToolsScreen.kt
git commit -m "refactor(ui): migrate DevToolsScreen to design-system buttons"
```

---

## Task 10: SplashScreen 마이그레이션

**Files:**
- Modify: `android/app/src/main/java/eloom/holybean/ui/startup/SplashScreen.kt`
- Test(guard): `android/app/src/test/kotlin/eloom/holybean/ui/startup/StartupViewModelTest.kt` (VM 단위 — UI 직접 검증 아님; 컴파일로 가드)

대상 call site: ~line 79 `Button` "다시 시도" → `PrimaryButton`; ~line 94 `OutlinedButton` "다시 시도" → `SecondaryButton`; ~line 95 `Button` "그대로 진입" → `PrimaryButton`.

- [ ] **Step 1: 버튼 3개 교체**

```kotlin
                Button(onClick = onRetry) { Text("다시 시도", ...) }   // ~line 79
```
→ `PrimaryButton("다시 시도", onClick = onRetry)`

~line 94 `OutlinedButton(onClick = onRetry) { Text("다시 시도", ...) }` → `SecondaryButton("다시 시도", onClick = onRetry)`
~line 95 `Button(onClick = onEnterAnyway) { Text("그대로 진입", ...) }` → `PrimaryButton("그대로 진입", onClick = onEnterAnyway)`

- [ ] **Step 2: import 정리**

`PrimaryButton`, `SecondaryButton` import 추가. 미사용 `Button`, `OutlinedButton` 제거.

- [ ] **Step 3: 컴파일 확인**

Run: `cd android && JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: 커밋**

```bash
git add android/app/src/main/java/eloom/holybean/ui/startup/SplashScreen.kt
git commit -m "refactor(ui): migrate SplashScreen to design-system buttons"
```

---

## Task 11: 최종 검증 — 잔존 raw 버튼 0 확인 + 전체 테스트

- [ ] **Step 1: 컴포넌트 패키지 밖에 raw 버튼이 남았는지 확인**

Run:
```bash
cd /Users/benn/dev/personal/HolyBean/android/app/src/main/java/eloom/holybean/ui
grep -rnE "\b(Button|OutlinedButton|TextButton|IconButton)\(" . | grep -v "/components/buttons/" | grep -vE "(Primary|Secondary|Danger|AppText|AppIcon)Button|IconButton\b.*//"
```
Expected: 결과 없음(공용 컴포넌트 외 raw 버튼 0). 만약 남으면 해당 화면을 매핑표대로 교체.

- [ ] **Step 2: 전체 단위 테스트**

Run: `cd android && JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: 전체 계측 테스트 (에뮬레이터 연결)**

Run: `cd android && JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:connectedDebugAndroidTest`
Expected: 모든 테스트 PASS.

- [ ] **Step 4: (있다면) 잔여 정리 커밋**

```bash
git add -A && git commit -m "refactor(ui): finalize button design-system migration"
```

---

## 회귀 방지 (선택, 본 플랜 범위 밖)

컴포넌트 패키지 밖 raw 버튼 사용을 막는 detekt 커스텀 룰 또는 PR 체크리스트는 별도 결정으로 남긴다. Task 11 Step 1의 grep을 CI 스크립트로 승격하는 것이 가장 저비용 방안.
