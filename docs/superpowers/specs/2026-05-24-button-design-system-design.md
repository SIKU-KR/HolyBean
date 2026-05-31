# 버튼 디자인시스템 (Compose 컴포넌트화) — 설계 스펙

날짜: 2026-05-24
브랜치: v3

## 배경 & 목표

현재 화면 전반(8개 화면, 25+ call site)에서 `Button`/`OutlinedButton`/`TextButton`/`IconButton`을
직접 사용하며, 매 호출부마다 다음 보일러플레이트를 반복하고 있다:

- `shape = RoundedCornerShape(Dimens.radiusButton)`
- 터치 타깃 높이(`heightIn(min = Dimens.minTouchTarget)` 또는 `.height(Dimens.primaryTouchTarget)`)
- `Text(..., style = MaterialTheme.typography.bodyMedium)`
- `colors = ButtonDefaults.buttonColors(containerColor = Orange)` 등 색상 지정

이로 인해 동일 역할의 버튼이 화면마다 미묘하게 다르게 렌더된다. 특히 "Secondary"(outlined)는
사실상 세 갈래로 갈라져 있다: M3 기본(Orange 텍스트), Orange 2dp bold(주문기록),
muted gray + icon(결제 헤더 취소).

**1순위 목표: 일관성·수정범위 한정·재사용 편의를 균형 있게 달성.**
핵심 가치는 단순 "수정범위 한정"을 넘어 **일관성 강제(consistency by construction)** — 호출부에서
틀린 버튼을 만들 방법 자체를 없애는 것이다. 디자인 토큰/스타일 변경 시 한 곳만 고치면 된다.

## 설계 원칙

**파라미터 최소화 = 자유도 최소화 = 일관성 극대화.** 버튼 종류가 애초에 적으므로,
스타일 결정권을 호출부에서 회수해 private 코어에 잠근다. 호출부는 "무엇을(text), 어떤 의미로
(함수 이름), 언제 비활성/로딩(enabled/loading)"만 결정한다.

## 컴포넌트 구조

```
ui/components/buttons/
  AppButtons.kt      // 공개 API 5종 + private 코어
```

### 공개 API (의미별 개별 함수)

```kotlin
@Composable fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
)

@Composable fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
)

@Composable fun DangerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
)

@Composable fun AppTextButton(           // 다이얼로그 confirm/dismiss 전용
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
)

@Composable fun AppIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
)
```

노출 파라미터는 `text / onClick / modifier / enabled` (+ Primary의 `loading`)로 한정.
`size`, `leadingIcon`, `emphasis`, `shape`, `colors`, `typography` 파라미터는 **노출하지 않는다.**

### private 코어

`PrimaryButton`/`SecondaryButton`/`DangerButton`은 색상·테두리만 다른 값을 private 코어
컴포저블에 위임한다. 코어가 shape·높이·typography·내부 패딩·로딩 스피너 배치를 단독 책임진다.
→ 공개 함수는 명확, 구현 중복은 0.

`AppTextButton`, `AppIconButton`은 별도(M3 TextButton/IconButton 얇은 래퍼).

## 시각 사양 (코어가 강제)

| 항목 | 값 |
|---|---|
| 높이 | 48dp (`Dimens.minTouchTarget`) — **모든 버튼 동일** |
| 라운드 | `Dimens.radiusButton` (6dp) |
| 라벨 typography | `bodyMedium` |
| Primary | filled `Orange` + 다크 텍스트(onPrimary, a11y 기준) |
| Secondary | 1dp 중립 회색 테두리 + `OnSurface`(다크) 텍스트 |
| Danger | 1dp `DangerRed` 테두리 + `DangerRed` 텍스트 |
| AppTextButton | M3 TextButton + `bodyMedium` |
| AppIconButton | 48dp 터치타깃, `OnSurface` tint |

### 사이즈 단일화의 결과 (의도된 변화)

기존 56dp/`titleMedium`였던 **결제·결제완료·재출력** 버튼이 48dp/`bodyMedium`로 작아진다.
큰 버튼의 강조는 `Modifier.fillMaxWidth()`로만 표현한다. 이는 자유도를 없애 일관성을 얻기 위한
의도된 시각 변화다.

### loading 처리

`PrimaryButton(loading = true)`:
- 라벨 자리에 `CircularProgressIndicator`(onPrimary 색, 20dp, 2dp stroke) + 텍스트
- 자동으로 `enabled = false` 취급
- PaymentScreen의 수동 `CircularProgressIndicator` 코드를 제거하고 이걸로 대체

## 캐논 통일로 인해 바뀌는 호출부

- **주문기록**(HomeScreen): Orange 2dp bold → 중립 회색 Secondary
- **결제 헤더 취소**(PaymentScreen): 아이콘 + muted gray → 텍스트만 중립 Secondary
  (leadingIcon 미노출 결정에 따라 아이콘 제거)

두 곳 모두 일관성을 위해 캐논으로 흡수한다.

## 마이그레이션 전략

점진적, 화면 단위 교체. 각 화면을 개별 커밋으로 분리:

1. PaymentScreen (loading 포함 — 가장 까다로움, 먼저 검증)
2. HomeScreen
3. OrdersScreen
4. ReportScreen
5. CreditsScreen
6. MenuManagementScreen (IconButton 포함)
7. DevToolsScreen
8. SplashScreen

각 단계에서 raw `Button`/`OutlinedButton`/`TextButton`/`IconButton` 호출을 공개 API로 치환.

## 테스트

- **회귀 가드**: 기존 androidTest(HomeScreenTest, PaymentScreenTest, OrdersScreenTest, MenuTileTest)는
  버튼 텍스트/클릭으로 동작을 검증하므로, 교체 후에도 그대로 통과해야 한다.
- **컴포넌트 단위 androidTest 추가**: 클릭 콜백 호출, `enabled = false` 시 비클릭,
  `loading = true` 시 비클릭 + 진행 표시를 검증.

## 회귀 방지 (선택, 권장)

컴포넌트 패키지 밖에서 raw `Button`/`OutlinedButton`/`TextButton` 직접 사용을 막는 수단:
- detekt 커스텀 룰 또는 PR 체크리스트.

본 스펙에서는 "권장"으로만 기록하고, 강제 도입 여부는 별도 결정으로 남긴다.

## 범위 밖 (YAGNI)

- size enum / Prominent 변종
- leadingIcon 슬롯 (실사용 1곳뿐 → 제외)
- Secondary emphasis/accent 변종
- FilledTonalButton / ElevatedButton 등 미사용 M3 변종
- 색상/shape/typography 호출부 오버라이드
