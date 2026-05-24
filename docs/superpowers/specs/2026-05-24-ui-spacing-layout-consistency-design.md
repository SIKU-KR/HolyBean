# UI 여백·배치·정렬 일관성 설계 (Spacing & Layout Consistency)

**작성일:** 2026-05-24
**브랜치:** v3
**선행 작업:** [compose-ux-redesign](2026-05-23-compose-ux-redesign-design.md), [accessibility-standards](2026-05-24-accessibility-standards-design.md)

---

## 1. 목표

Compose UI 전반의 여백·간격·정렬·배치를 **단일 8pt 스케일과 공용 레이아웃 컴포넌트** 위로 통일해, 현재의 "화면마다 제각각"인 인상을 제거하고 best-practice 수준으로 끌어올린다.

이번 작업은 **시각적 일관성 + 코드의 구조적 일관성**을 동시에 달성한다. 색·폰트·접근성 기준은 직전 작업(accessibility-standards)에서 이미 확정되었으므로 변경하지 않는다. 여기서는 **공간(spacing)과 배치(layout)**만 다룬다.

## 2. 배경 — 진단된 불일치

코드 전수 조사(`ui/` 14개 파일)에서 확인된 문제:

### 2.1 상충하는 "가장자리 패딩" 관습
| 화면군 | 화면 패딩 | 패널 내부 패딩 |
|---|---|---|
| 주문 플로우 (Home/Payment/Orders) | `Dimens.screenPadding`(12) | raw `12.dp` |
| 설정 경유 (Credits/MenuMgmt/Report/DevTools/Settings) | raw `16.dp` | raw `12~16.dp` |
| Splash | raw `32.dp` | — |

→ 같은 앱에서 화면 가장자리 패딩이 12/16/32로 갈리며 근거가 없다.

### 2.2 스케일을 깨는 매직넘버
앱 전반에서 발견된 간격값: `1, 2, 5, 6, 7, 8, 10, 12, 14, 16, 24, 32`. 이 중 `5`(카테고리·메뉴 칩 gap), `6`(결제수단 그리드/Spacer), `7`(홈 메뉴 그리드 gap, 주문 리스트 Spacer, BasketRow 세로 패딩), `10`(헤더 하단 마진·외상 gap·주문 상단 패딩)은 4/8/16 리듬과 무관하다.

### 2.3 중복 구현이 서로 다르게 작성됨
거의 모든 화면에 반복되지만 매번 다르게 구현된 패턴:

- **헤더**(제목 + 액션 버튼): Payment/Orders는 `padding(bottom = 10.dp)`, Credits/Report/DevTools/MenuMgmt는 하단 간격 없음, Home은 패널 안에 인라인.
- **2-pane 레이아웃**: pane 간 gap이 Home/Orders/Payment=`Dimens.gap`(8) vs Credits=raw `10.dp`. 좌패널 폭이 `0.38f`(Home은 `Dimens.basketWidthFraction`, Payment은 raw) vs `0.46f`(Orders/Credits 모두 raw).
- **패널(Surface)**: Home/Orders/Payment는 `radiusPane` + `paneElevation`, **Credits는 shape·elevation 없는 맨 Surface**(시각적으로 분리감 없음).
- **합계 행**(`합계 / %,d원`): Home(End 정렬, `"합계 "` 공백)·Payment(SpaceBetween)·Orders(SpaceBetween)에 3벌 복붙, 정렬 방식도 다름.
- **상태 점 행**(원형 점 + 라벨): DevTools와 Splash에 2벌, 점 색 토큰화 여부·로딩색이 서로 다름.

### 2.4 실제 버그/회귀 (이번에 함께 수정)
1. **`PaymentScreen.kt:184`** — `Modifier.fillMaxWidth().height(Dimens.primaryTouchTarget).padding(12.dp)`: `padding`이 56dp 높이 버튼의 콘텐츠를 사방 12dp씩 깎아 "결제 완료" 버튼이 실질 ~32dp로 찌그러진다(최소 터치 타깃 위반).
2. **`SplashScreen.kt:126-130`** — 상태 점 색을 raw `Color(0xFF22C55E/0xFFEF4444/0xFFBBBBBB)`로 박음. 이미 `StatusOk`/`StatusError`/`StatusUnknown` 토큰이 존재하는데 미사용이고, 로딩색(`BBBBBB`)이 DevTools(`StatusUnknown`=`9E9E9E`)와 불일치.
3. **보조 버튼 터치 타깃 미달** — Report/Credits/MenuMgmt/DevTools 헤더의 `OutlinedButton`들이 높이를 지정하지 않아 Material 기본(~40dp)으로 렌더, accessibility-standards에서 정한 최소 48dp 미달.
4. **raw 리터럴 in 공용/시각 요소** — `HomeScreen.kt:154` `RoundedCornerShape(14.dp)`, `SegmentedToggle.kt:24` `Color(0xFFF0F0F0)`.

## 3. 설계 결정 (확정)

브레인스토밍에서 합의된 사항:

- **범위:** 정규화 + 배치/정렬 best-practice 개선. 최종 목업과 일부 차이가 생겨도 일관성을 우선한다.
- **스케일:** 8pt 그리드(4 half-step). 간격을 `4/8/16/24/32`로 통일하고 `12`는 제거. `5/6/7/10/12`는 모두 이 5단계로 흡수.
- **일관화 방식:** 공용 레이아웃 컴포넌트를 추출해 전 화면이 사용하도록 교체(일관성을 구조적으로 보장).
- **regression 방지:** 자동 강제(lint/test) 없이 문서 + 규약. 단, 공용 컴포넌트 자체가 "구성에 의한 강제" 역할을 한다.

### 비목표 (scope out)
- 색/폰트/타이포 스케일 변경 (직전 작업에서 확정).
- radius·elevation 스케일 재설계 (값 유지, raw만 토큰화).
- 화면 흐름/네비게이션/기능 변경.
- 설정 시트 재디자인(accessibility-standards의 후속 과제로 분리되어 있음 — 여기서는 간격만 정규화).
- 자동 lint 규칙·DimensTest 추가.

## 4. 토큰 시스템 (`ui/theme/Dimens.kt`)

최종 형태. 기존 이름 alias는 의미 기반 이름으로 정리하되, 외부 참조가 많은 것은 alias를 남긴다.

```kotlin
package eloom.holybean.ui.theme

import androidx.compose.ui.unit.dp

object Dimens {
    // ── 간격 스케일 (8pt 그리드, 4는 half-step) ─────────────────
    val spaceXs = 4.dp    // 칩/텍스트 내부, 미세 조정
    val spaceSm = 8.dp    // 기본 gap, 리스트 아이템 간격, 그리드 gap
    val spaceMd = 16.dp   // 화면 패딩, 패널 내부 패딩, 헤더 하단
    val spaceLg = 24.dp   // 섹션 간 간격
    val spaceXl = 32.dp   // 큰 블록 구분 (Splash 등)

    // ── 의미 별칭 (호출부 가독성) ──────────────────────────────
    val screenPadding = spaceMd   // 16 (was 12) — 모든 화면 가장자리
    val panePadding = spaceMd     // 16 (was raw 12) — Surface/Pane 내부
    val paneGap = spaceSm         // 8 — 2-pane 사이 간격
    val sectionGap = spaceLg      // 24 — 화면 내 섹션 구분
    val headerGap = spaceMd       // 16 — 헤더와 본문 사이
    val gridGap = spaceSm         // 8 — 메뉴/버튼 그리드 gap
    val itemGap = spaceSm         // 8 — 리스트 아이템 간 간격

    // ── 2-pane 분할 비율 ──────────────────────────────────────
    val paneSplitNarrow = 0.38f   // 장바구니/주문요약 패널 (Home/Payment)
    val paneSplitWide = 0.46f     // 목록 패널 (Orders/Credits)

    // ── 라운드 (값 유지, raw 제거) ─────────────────────────────
    val radiusButton = 6.dp
    val radiusTile = 10.dp
    val radiusPane = 14.dp
    val radiusChip = 14.dp        // FilterChip (was raw 14.dp)

    // ── elevation (유지) ──────────────────────────────────────
    val paneElevation = 2.dp
    val tileElevation = 1.dp

    // ── 터치 타깃 (accessibility-standards, 유지) ──────────────
    val minTouchTarget = 48.dp
    val primaryTouchTarget = 56.dp

    // ── 고정 치수 (스케일 예외, 명시) ──────────────────────────
    val statusDot = 10.dp         // 장식용 상태 점 (border 1·2dp처럼 스케일 무관)
    val menuTileHeight = 90.dp    // 메뉴 타일 고정 높이 (was raw 90.dp)

    // ── 폐기 예정 alias (값만 새 스케일에 정렬, 점진 제거) ──────
    @Deprecated("Use paneGap", ReplaceWith("paneGap")) val gap = paneGap
    @Deprecated("Use radiusTile", ReplaceWith("radiusTile")) val tileRadius = radiusTile
    @Deprecated("Use radiusPane", ReplaceWith("radiusPane")) val paneRadius = radiusPane
    @Deprecated("Use paneSplitNarrow", ReplaceWith("paneSplitNarrow")) val basketWidthFraction = paneSplitNarrow
}
```

> **밀도 변화 주의:** `screenPadding`/`panePadding`이 12→16으로 +4dp씩 넓어진다. 가장 좁은 패널(Home 장바구니, `0.38f`)도 내부 가용폭이 양쪽 합 8dp 줄어든다. 태블릿 가로 화면(테스트 900dp)에서 텍스트 잘림이 없는지 에뮬레이터로 확인한다(§8).

색 토큰 추가(`ui/theme/Color.kt`):
```kotlin
val SegmentInactive = Color(0xFFF0F0F0)   // SegmentedToggle 비활성 (was raw)
```

## 5. 공용 레이아웃 컴포넌트 (신규 `ui/components/layout/`)

각 컴포넌트는 "간격을 소유"한다. 호출부는 조립만 한다. 6개를 신규 작성한다.

### 5.1 `ScreenContainer`
화면 가장자리 배경 + 패딩을 일괄 적용. 모든 풀스크린 화면의 최상위 래퍼.

```kotlin
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
- **대체 대상:** Home/Payment/Orders/Credits/Report/DevTools/MenuMgmt 의 `Modifier.fillMaxSize().background(...).padding(...)` 보일러플레이트.
- **부수효과(의도적):** Report/Credits/DevTools/MenuMgmt는 현재 배경을 지정하지 않아 투명(흰색)으로 보인다. ScreenContainer 적용 후 다른 화면과 동일한 `ScreenBg`(회색)가 깔린다 — 일관화 목적.

### 5.2 `ScreenHeader`
제목 + 우측 액션 버튼 행. 헤더–본문 간격을 통일.

```kotlin
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
- **대체 대상:** Payment/Orders/Credits/Report/DevTools/MenuMgmt 의 헤더 `Row` 및 그 안의 수동 `Spacer(width=8)`. (Home은 헤더가 장바구니 패널 내부의 "N번 주문 + 주문기록" 행이므로 §6.1에서 별도 처리.)

### 5.3 `Pane`
radius + elevation + 내부 패딩을 갖춘 흰 패널.

```kotlin
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
        content = { Column(Modifier.padding(padding), content = content) },
    )
}
```
- **대체 대상:** Home `BasketPane`, Payment 좌/우 패널, Orders 요약/목록/상세 패널, Credits 목록/상세 패널의 `Surface { Column(Modifier.padding(12.dp)) }` 패턴.
- **참고:** 내부가 `LazyColumn` 단독인 패널(Orders 좌패널 등)은 `Pane { LazyColumn(Modifier.fillMaxSize()) { ... } }`로 조립한다.

### 5.4 `SectionLabel`
섹션 제목 라벨(작은 muted 텍스트). Payment의 "컵 선택/결제 수단/주문자명" 등.

```kotlin
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
- **대체 대상:** Payment의 반복되는 `Text(..., labelSmall, OnSurfaceMuted)` 라벨. 섹션 간 간격은 부모 `Column(verticalArrangement = Arrangement.spacedBy(Dimens.sectionGap))`로 처리(개별 `Spacer` 제거).

### 5.5 `TotalRow`
합계 행. 정렬을 `SpaceBetween`으로 통일.

```kotlin
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
- **대체 대상:** Home `BasketPane`(현재 End 정렬, `"합계 "` 공백 → SpaceBetween으로 통일), Payment·Orders 합계 행.

### 5.6 `StatusDot`
원형 상태 점. 크기·색 토큰화.

```kotlin
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
- **대체 대상:** DevTools `HealthRow`의 점(이미 `Boolean?`), Splash `StatusRow`의 점(`StepStatus` → 호출부에서 `Success→true / Failed→false / Loading→null` 매핑). Splash의 raw 색·로딩색 불일치 동시 해소.

## 6. 화면별 적용

각 화면은 위 컴포넌트로 재조립하고 raw 간격값을 토큰으로 치환한다. before→after는 핵심 간격값 기준.

### 6.1 HomeScreen
- 최상위 `Row`를 `ScreenContainer { Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(Dimens.paneGap)) { ... } }`로.
- 카테고리 칩 `LazyRow`: `spacedBy(5.dp)` → `spacedBy(Dimens.spaceSm)`(8). FilterChip `shape = RoundedCornerShape(14.dp)` → `Dimens.radiusChip`.
- 카테고리–그리드 사이 `Spacer(height(8.dp))` → `Spacer(Modifier.height(Dimens.spaceSm))`.
- 메뉴 그리드 `horizontal/verticalArrangement = spacedBy(7.dp)` → `Dimens.gridGap`(8).
- `BasketPane`: `Pane(Modifier.fillMaxHeight().fillMaxWidth(Dimens.paneSplitNarrow))`로. 내부 `padding(12.dp)` 제거(Pane이 16 적용). 헤더 행("N번 주문" + "주문기록")은 Home 고유라 유지하되 정렬·간격 토큰화. 합계 행 → `TotalRow(total)`. `Row(padding(vertical = 8.dp))` → `Dimens.spaceSm`.
- 빈 바구니 placeholder `Box`는 유지.

### 6.2 PaymentScreen
- `ScreenContainer { Column { ScreenHeader("${orderId}번 주문 · 결제", actions = { 취소 버튼 }) ... } }`. 헤더 `padding(bottom = 10.dp)` 제거(ScreenHeader가 `headerGap`=16).
- 2-pane `Row`: `spacedBy(Dimens.gap)` → `Dimens.paneGap`(8).
- 좌패널(주문 요약): `Pane(Modifier.fillMaxWidth(Dimens.paneSplitNarrow).fillMaxHeight())`. 내부 `padding(12.dp)` 제거. 라벨 → `SectionLabel`. 합계 → `TotalRow`. `padding(top = 8.dp)` 제거(divider–total 간격은 Column `spacedBy` 또는 `Dimens.spaceSm`).
- 우패널(입력): `Pane`. 스크롤 Column의 섹션 간격을 `Spacer(10/8/6)` 난립 → `Column(verticalArrangement = Arrangement.spacedBy(Dimens.sectionGap))` + `SectionLabel`. 분할결제 breakdown 행 `padding(vertical = 1.dp)` → `Dimens.spaceXs`(4)는 과하므로 `0.dp`(spacedBy로 처리) 또는 `spaceXs`. **권장: breakdown은 `Column(spacedBy(Dimens.spaceXs))`.**
- `MethodGrid`/`MethodRow`: `spacedBy(6.dp)` → `Dimens.spaceSm`(8).
- **버그 수정:** "결제 완료" 버튼 `Modifier.fillMaxWidth().height(Dimens.primaryTouchTarget).padding(12.dp)` → `.padding(12.dp)` 제거. 버튼 바깥 여백이 필요하면 부모 Column이 `panePadding`으로 이미 처리하거나 별도 외부 패딩으로 분리.

### 6.3 OrdersScreen
- `ScreenContainer { Column { ScreenHeader("주문기록", actions = { 닫기 버튼 }) ... } }`. 헤더 `padding(bottom = 10.dp)` 제거.
- 요약 바: 현재 `Surface(padding(bottom = 10.dp)) { Row(padding(12.dp), spacedBy(Dimens.spaceMd)) }` → `Pane(Modifier.fillMaxWidth())` + 하단 간격은 본문 Column `spacedBy(Dimens.spaceSm)`로. `VerticalDivider(height(32.dp))` → `Dimens.spaceXl`(32, 토큰화). StatChip 간 `spacedBy(Dimens.spaceMd)`(16) 유지.
- 2-pane `Row`: `spacedBy(Dimens.gap)` → `Dimens.paneGap`.
- 좌패널(목록): `Pane(Modifier.fillMaxWidth(Dimens.paneSplitWide).fillMaxHeight()) { LazyColumn(...) }`. 아이템 간 `Spacer(height(7.dp))` → `LazyColumn(verticalArrangement = Arrangement.spacedBy(Dimens.itemGap))`(8)로 전환(Spacer 제거).
- 우패널(상세): `Pane`. 합계 → `TotalRow`. 버튼 행 `padding(top = 10.dp), spacedBy(8.dp)` → `Dimens.spaceMd`(16) 상단, `spacedBy(Dimens.spaceSm)`.
- `OrderListItem`: `Column(padding(10.dp))` → `Dimens.spaceMd`(12 없으니 16) 또는 `spaceSm`(8). **권장: `Dimens.spaceMd`(16) — 카드 내부 가독성.** 단 목록 카드가 작으므로 `spaceSm`(8)도 후보 → 에뮬레이터 확인 후 택1, 기본은 `spaceSm`(8).

### 6.4 CreditsScreen
- `ScreenContainer { Column { ScreenHeader("외상 관리", actions = { 닫기 }) ... } }`.
- 2-pane `Row`: `padding(top = 10.dp), spacedBy(10.dp)` → 상단 간격은 ScreenHeader가 처리(제거), `spacedBy(Dimens.paneGap)`(8).
- **좌/우 패널을 `Pane`으로 승격** — 현재 shape·elevation 없는 맨 Surface라 Orders와 시각적으로 불일치. `Pane(Modifier.fillMaxWidth(Dimens.paneSplitWide).fillMaxHeight())` / `Pane(Modifier.weight(1f).fillMaxHeight())`.
- 목록 아이템 `Column(padding(vertical = 8.dp))` → `Dimens.spaceSm` 유지(토큰화). 상세 "외상 결제완료 처리" 버튼에 `.height(Dimens.primaryTouchTarget)` 부여(현재 기본 높이).

### 6.5 ReportScreen
- `ScreenContainer { Column { ScreenHeader("기간 매출 리포트", actions = { 닫기 }) ... } }`(현재 배경/헤더 간격 없음 → 통일).
- 날짜·조회 버튼 행 `padding(vertical = 10.dp), spacedBy(8.dp)` → `Dimens.spaceMd`(16) 세로, `spacedBy(Dimens.spaceSm)`. 각 버튼에 `.heightIn(min = Dimens.minTouchTarget)`.
- StatChip 행 `padding(vertical = 8.dp), spacedBy(16.dp)` → `Dimens.spaceSm` 세로, `spacedBy(Dimens.spaceMd)`(16).
- `MenuSalesRow` `padding(vertical = 6.dp)` → `Dimens.spaceSm`(8).

### 6.6 DevToolsScreen
- `ScreenContainer { Column { ScreenHeader("🛠 개발자 도구", actions = { 닫기 }) ... } }`.
- 헤더 후 `Spacer(height(12.dp))` 제거(ScreenHeader `headerGap`).
- `HealthRow`: 점을 `StatusDot(ok)`로, `Spacer(width(10.dp))` → `Dimens.spaceSm`(8), `padding(vertical = 6.dp)` → `Dimens.spaceSm`(8).
- URL 텍스트 `padding(vertical = 8.dp)` → `Dimens.spaceSm` 유지.
- 버튼 행 `spacedBy(8.dp)` → `Dimens.spaceSm`. "새로고침" `OutlinedButton`에 `.height(Dimens.primaryTouchTarget)` 부여(현재 기본).

### 6.7 SplashScreen
- 최상위 `Column(padding(32.dp))` → `Dimens.spaceXl`(32, 토큰화). 중앙 정렬 유지.
- 제목 후 `Spacer(height(24.dp))` → `Dimens.sectionGap`(24). 상태행 블록 후 `Spacer(24.dp)` → `Dimens.sectionGap`.
- 메시지 사이 `Spacer(height(8/16.dp))` → `Dimens.spaceSm`(8)/`Dimens.spaceMd`(16).
- 버튼 행 `spacedBy(12.dp)` → `Dimens.spaceMd`(16).
- `StatusRow`: 점을 `StatusDot`로(StepStatus 매핑), `Spacer(width(10.dp))` → `Dimens.spaceSm`(8), `padding(vertical = 6.dp)` → `Dimens.spaceSm`(8). raw 색 3종 제거.

### 6.8 SettingsSheet
- `Column(padding(bottom = 24.dp))` → `Dimens.sectionGap`(24, 토큰화).
- 제목 `padding(16.dp)` → `Dimens.spaceMd`(16, 토큰화).
- `SettingsRow` `padding(16.dp)` → `Dimens.spaceMd`. `heightIn(min = Dimens.minTouchTarget)` 유지.

### 6.9 MenuManagementScreen
- `ScreenContainer { Column { ScreenHeader("메뉴 관리", actions = { 추가 / 순서 저장 / 닫기 }) ... } }`. 헤더 내 `Spacer(width(8.dp))` 2개 제거(ScreenHeader가 `spacedBy(spaceSm)`).
- 카테고리 칩 `LazyRow(spacedBy(5.dp), padding(top = 8.dp))` → `spacedBy(Dimens.spaceSm)`, 상단 간격은 ScreenHeader 후 Column `spacedBy` 또는 제거.
- 메뉴 리스트 `LazyColumn(padding(top = 10.dp))` → 상단 간격 `Dimens.spaceSm`(8) 또는 Column `spacedBy`.
- `ListItem` trailing `Spacer(width(8.dp))` → `Dimens.spaceSm`. 드래그 시 `tonalElevation = 4.dp`는 elevation이므로 spacing 토큰으로 치환하지 않고 그대로 둔다(스케일 예외).
- 헤더 버튼들에 `.heightIn(min = Dimens.minTouchTarget)`.

## 7. 컴포넌트 정규화 (`ui/components/`)

- **BasketRow** (`:19`): `padding(vertical = 7.dp)` → `Dimens.spaceSm`(8).
- **MenuTile** (`:42`): `height(90.dp)` → `Dimens.menuTileHeight`. 내부 `padding(Dimens.spaceXs)` 유지.
- **PaymentMethodTile**: `BorderStroke(2.dp, ...)` 유지(테두리는 스케일 예외). 토큰 변경 없음.
- **SegmentedToggle** (`:24`): `Color(0xFFF0F0F0)` → `SegmentInactive`.
- **StatChip**: 간격 없음 — 변경 없음.

테두리 두께(`1.dp`/`2.dp`)와 `VerticalDivider`/`HorizontalDivider` 두께는 스케일 예외로 두되, `VerticalDivider(height(32.dp))`는 `Dimens.spaceXl`로 토큰화한다(§6.3).

## 8. 검증 방법

시각 작업이라 자동 단언이 제한적이다. 다음 순서로 검증한다(빌드 환경은 [build-environment-quirks] 참고 — `JAVA_HOME=/opt/homebrew/opt/openjdk@21` 프리픽스).

1. **컴파일/빌드:** `JAVA_HOME=... ./gradlew :app:assembleDebug` 성공.
2. **기존 테스트 무회귀:** `:app:testDebugUnitTest` 그대로 통과. 기존 Compose UI 테스트(`HomeScreenTest`/`OrdersScreenTest`/`PaymentScreenTest`/`MenuTileTest`)는 텍스트·동작 기반이라 간격 변경에 영향받지 않아야 한다 — 깨지면 셀렉터를 점검(텍스트/노드 구조 변경 여부)하되 의미적 동작은 유지.
3. **@Preview 육안:** 각 화면 Preview(900×500)에서 잘림·겹침·정렬 확인.
4. **에뮬레이터 육안:** `emulator-5554`에서 전 화면을 돌며 (a) 가장자리/패널 패딩이 16으로 통일됐는지, (b) 좁은 패널(Home 장바구니, `0.38f`)에서 12→16 전환으로 텍스트 잘림이 없는지, (c) 헤더–본문 간격·합계 행·상태 점이 화면 간 동일한지 확인. accessibility-standards에서 미완으로 남은 목업 정합 육안 점검도 이때 함께 수행.

> 회귀 방지는 문서·규약(이 스펙 + Dimens 주석) + 공용 컴포넌트의 "구성에 의한 강제"로 갈음한다. 별도 lint/test는 추가하지 않는다.

## 9. 위험과 완화

| 위험 | 완화 |
|---|---|
| 12→16 패딩 확대로 좁은 패널 콘텐츠 잘림 | §8.4 에뮬레이터에서 좁은 패널 우선 확인. 잘리면 해당 패널만 `Pane(padding = Dimens.spaceSm)`로 조정(8). |
| 공용 컴포넌트 추출이 기존 Compose 테스트의 노드 구조를 깨뜨림 | 컴포넌트는 기존과 동일한 시각/텍스트 출력을 내도록 작성. 테스트는 `assembleDebug` 후 즉시 실행해 조기 발견. |
| ScreenContainer 배경 통일로 Report/Credits 등 배경이 흰→회색 변경 | 의도된 일관화. 시각적으로 다른 화면과 맞춰지는지 §8.4에서 확인. |
| `@Deprecated` alias가 빌드 경고 유발 | 모든 호출부를 새 이름으로 교체한 뒤, 외부 참조가 없으면 alias 자체를 제거(작업 마지막 단계). |

## 10. 미해결 / 후속

- 설정 시트 **재디자인**(헤더바·아이콘칩·화살표·비번 배지)은 accessibility-standards의 후속 과제로 분리되어 있으며 본 작업 범위 밖(여기서는 간격만 정규화).
- 라운드/elevation 스케일의 의미적 재설계는 다루지 않음(값 유지).

---

## 부록 A — 간격값 매핑표 (raw → 토큰)

| 현재 raw | 출현 위치 | 치환 |
|---|---|---|
| `5.dp` | Home/MenuMgmt 카테고리 칩 gap | `spaceSm`(8) |
| `6.dp` | Payment MethodGrid/Row, Spacer | `spaceSm`(8) |
| `7.dp` | Home 메뉴 그리드 gap, Orders 아이템 Spacer, BasketRow 세로 | `spaceSm`(8) / `gridGap`(8) / `itemGap`(8) |
| `8.dp` | 다수 Spacer·padding | `spaceSm`(8) |
| `10.dp` | 헤더 bottom, Credits gap, Orders 상단/패딩, 상태점 Spacer | `headerGap`(16)·`paneGap`(8)·`spaceMd`(16)·`spaceSm`(8) (맥락별) |
| `12.dp` | 패널 내부 패딩 | `panePadding`(16) |
| `14.dp` | Home FilterChip shape | `radiusChip`(14) |
| `16.dp` | Credits/Report/DevTools/MenuMgmt/Settings 화면·패딩 | `screenPadding`/`spaceMd`(16) |
| `24.dp` | Splash Spacer, Settings bottom | `sectionGap`(24) |
| `32.dp` | Splash 화면 패딩, Orders VerticalDivider 높이 | `spaceXl`(32) |
| `90.dp` | MenuTile 높이 | `menuTileHeight`(90) |
| `0.38f`/`0.46f` | pane 분할 비율 | `paneSplitNarrow`/`paneSplitWide` |
| `0xFFF0F0F0` | SegmentedToggle 비활성 | `SegmentInactive` |
| raw 상태 색 3종 | Splash 점 | `StatusOk`/`StatusError`/`StatusUnknown` |

> `맥락별`로 표시된 `10.dp`는 단일 치환이 불가하므로 §6의 화면별 지침을 따른다.
