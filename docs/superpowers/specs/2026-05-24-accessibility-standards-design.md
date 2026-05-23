# POS 접근성 표준 + 시각 개선

날짜: 2026-05-24
브랜치: v3

## 배경 / 목적

HolyBean은 매장 고정 태블릿에서 직원이 빠르게 조작하는 카페 POS 앱이다. Google
Material Design / Android Accessibility 가이드의 정량 기준(터치 타깃, 최소 폰트,
명도 대비)을 적용하는 **접근성 작업**과, 현재 평면적이고 위계가 약한 UI를 다듬는
**시각 개선(visual refresh)**을 함께 한다. 세 갈래다:

1. **표준 수립** — 기준을 테마 토큰(`Type`/`Color`/`Dimens`)에 박아 중앙화하고,
   접근성 표준 문서를 작성한다. 토큰 중앙화 자체가 회귀 방지의 주된 수단이다.
2. **기존 화면 감사 + 수정** — 현재 Compose 화면을 접근성 기준으로 점검해 위반을 고친다.
3. **시각 개선** — 깊이감(elevation), 폰트 위계, 브랜드 일관 선택색, 간격·라운드
   스케일 통일 등 테마 레벨 개선과, 메뉴 타일·합계·빈 상태·구분선 등 화면 레벨
   다듬기를 한다. 접근성 변경과 같은 토큰을 만지므로 한 번에 진행한다.

코드베이스는 이미 토큰 중심으로 잘 정리되어 있다. 화면에 하드코딩된 sp는 0건,
하드코딩 색상은 4건뿐이며 `onPrimary`/흰 글자는 테마에만 정의되어 있다. 따라서
변경 표면은 작고 집중되어 있다. 현재 UI의 약점: ① 모든 패널·카드가 elevation 0의
평면, ② Pretendard `Bold`/`ExtraBold`를 로드해두고 안 써 위계가 거의 없음,
③ `CategoryChips`·`SegmentedToggle`이 Material 기본(회색) 선택 상태, ④ 간격
(5/6/7/8/10/12dp)·라운드(8/9/10dp)가 제각각.

## 표준 정의

| 항목 | 기준 | 근거 |
|---|---|---|
| 터치 타깃 (주요 액션) | **≥ 56dp** | POS 빠른 연속 탭 최적화 |
| 터치 타깃 (보조 요소) | **≥ 48dp** | Material 최소 |
| 최소 폰트 | **≥ 14sp** (가이드 최소 12sp보다 상향, 태블릿 POS 가독성) | Material |
| 본문 명도 대비 | **≥ 4.5:1** | WCAG AA |
| 큰/볼드 텍스트(18pt+) 대비 | **≥ 3:1** | WCAG AA |
| 비텍스트(아이콘·상태 점) 대비 | **≥ 3:1** | WCAG AA (non-text) |
| 폰트 배율 | 앱에서 **1.0 고정** (sp 단위는 유지) | 매장 고정 태블릿 |

"주요 액션" = 메뉴 타일, 결제 버튼, 주문 확정 등 핵심 흐름의 탭 대상.
"보조 요소" = 아이콘 버튼, 체크박스, 닫기 버튼 등.

## 명도 대비 계산 근거 (확정값)

WCAG 상대 휘도 공식으로 계산한 값. 변경의 출발점이다.

| 조합 | 대비 | 판정 |
|---|---|---|
| 흰 글자(#FFF) on 오렌지(#FF7F00) | 2.53:1 | ❌ 본문·큰글자 모두 미달 |
| 진한 글자(#222222) on 오렌지(#FF7F00) | 6.29:1 | ✅ 통과 |
| `OnSurfaceMuted` #888888 on 흰 배경 | 3.55:1 | ❌ 본문 미달 (큰글자는 통과) |
| #767676 on 흰 배경 | 4.54:1 | ✅ 본문 통과 |
| `OrangeOnContainer` #C2691A on `OrangeContainer` #FFF3E4 | 3.61:1 | ❌ 본문 미달 (큰글자는 통과) |
| #9A5412 on `OrangeContainer` #FFF3E4 | 4.6:1 | ✅ 본문 통과 |

→ 오렌지 브랜드 색은 **유지**하되, 오렌지 배경 위 텍스트는 흰색이 아닌 진한
   색(#222222)을 쓴다. 음소거 회색은 #767676 이상으로 어둡게 한다. 선택 상태
   칩·세그먼트처럼 `OrangeContainer` 위에 **작은 텍스트**를 올릴 때는 `OrangeOnContainer`를
   #9A5412 수준으로 darken해 본문 4.5:1을 만족시킨다(큰/볼드 텍스트는 현행 #C2691A로 3:1 통과).

## 변경 사항

### 1. 테마 토큰 (중앙화)

**`ui/theme/Type.kt`** — 14sp를 하한으로 두고 캡션·본문 위계가 무너지지 않도록
스케일을 한 단계씩 상향한다. 동시에 폰트 **웨이트**를 부여해 위계를 강화한다(B).
현재는 모든 스타일에 웨이트 미지정이라 사실상 Medium 한 종류만 쓰인다.

| 토큰 | 현재 sp | 변경 sp | 웨이트 | 용도 |
|---|---|---|---|---|
| `labelSmall` | 11 | **14** | Medium | 캡션·부가 라벨 (하한) |
| `bodyMedium` | 13 | **15** | Medium | 본문 |
| `bodyLarge` | 15 | **16** | Medium | 강조 본문 |
| `titleMedium` | 16 | **18** | **Bold** | 소제목·합계·헤더 |
| `titleLarge` | 22 | 22 | **ExtraBold** | 대제목 |

`labelSmall`(캡션)과 `bodyMedium`(본문)이 같은 크기로 수렴하지 않도록 14/15로
한 단계 간격을 유지한다. Pretendard `Bold`/`ExtraBold`는 이미 `Type.kt`에 로드되어
있어 추가 폰트 리소스 없이 웨이트만 지정하면 된다.

**`ui/theme/Color.kt`**
- `OnSurfaceMuted` `#888888 → #767676` (흰 배경 본문 4.5:1 통과).
- 상태 점 색을 토큰으로 승격(현재 `DevToolsScreen`에 하드코딩):
  - `StatusOk = #22C55E`, `StatusError = #EF4444`, `StatusUnknown` —
    현재 `#BBBBBB`(흰 배경 1.92:1)는 비텍스트 3:1 미달이므로 **#9E9E9E 이상으로
    darken**하거나 점에 테두리를 추가해 식별성 확보.

**`ui/theme/Theme.kt`**
- `onPrimary` `#FFFFFF → #222222`. 모든 오렌지(primary) 버튼 글자가 일괄 진한색이
  되어 6.3:1 통과. (`onPrimary`가 테마에만 정의되어 있어 한 곳 변경으로 전파됨 —
  사용자 동의 완료.)

**`ui/theme/Dimens.kt`** — 터치 타깃 토큰 + 간격·라운드 스케일 통일(D).
- 터치: `minTouchTarget = 48.dp`, `primaryTouchTarget = 56.dp`.
- 간격 스케일(흩어진 5/6/7/8/10/12dp를 대체): `spaceXs = 4.dp`, `spaceSm = 8.dp`,
  `spaceMd = 12.dp`, `spaceLg = 16.dp`. 기존 `gap = 10.dp`·`screenPadding = 12.dp`는
  스케일 값으로 정렬(`gap → spaceSm`, `screenPadding → spaceMd`).
- 라운드 스케일(기존 8/9/10dp 통일): `radiusButton = 6.dp`(사각형에 가깝게),
  `radiusTile = 10.dp`, `radiusPane = 14.dp`. 기존 `tileRadius`/`paneRadius` 및
  `PaymentMethodTile`의 하드코딩 `9.dp`를 이 토큰으로 교체.
- elevation(A): `paneElevation = 2.dp`, `tileElevation = 1.dp`.

**`ui/theme/Theme.kt`** — `MaterialTheme(shapes = …)`에 사각형에 가까운 `Shapes`를
지정해 `Button`·`SegmentedButton` 등 Material 컴포넌트가 기본 알약형 대신 작은
라운드(`radiusButton`)를 따르도록 한다. 테마에서 shape를 못 읽는 컴포넌트는
호출부에서 `shape = RoundedCornerShape(Dimens.radiusButton)`로 명시.

### 2. 폰트 배율 고정

- 시스템 글꼴 배율을 앱 내부에서 1.0으로 고정한다. `HolyBeanTheme` 내부(또는
  `MainActivity`의 `setContent` 직후)에서 `CompositionLocalProvider`로 `LocalDensity`를
  현재 density 기반 + `fontScale = 1f`로 덮어쓴다. sp 단위는 그대로 두되 시스템
  배율 변동이 레이아웃에 반영되지 않도록 한다.
- 200% 가변 레이아웃 대응은 범위 밖(아래 참조).

### 3. 기존 화면 감사 (수동 체크리스트)

각 Compose 화면을 다음 세 항목으로 점검하고 위반 시 토큰으로 교체한다.

1. **터치 타깃**: 탭 가능 요소가 48/56dp를 충족하는가. `IconButton`/`Checkbox`/
   `Switch`/`RadioButton`을 쓰는 화면 우선 점검: `home/OrderDialog.kt`,
   `payment/PaymentScreen.kt`, `menumanagement/MenuManagementScreen.kt`.
   미달 시 `Modifier.sizeIn(minWidth/minHeight = Dimens.minTouchTarget)` 등으로 보정.
2. **오렌지를 텍스트 색으로 쓴 곳**: 흰/밝은 배경 위 오렌지 텍스트(2.53:1)는
   위반. 발견 시 진한 색 또는 `OrangeOnContainer`로 교체.
3. **하드코딩 색 위 텍스트**: `components/MenuTile.kt`의 `#EEEEEE`(설정 타일 배경)
   위 텍스트 대비를 점검하고, 색을 `DividerGray` 등 토큰으로 통일.

감사 대상 화면(현 ui 트리): `home`, `payment`, `orders`, `report`, `credits`,
`menumanagement`, `settings`(SettingsSheet/DevTools), `components/*`.

### 4. 문서

- 접근성 표준 문서를 작성한다. 위치: `docs/accessibility.md` (혹은 `ui/theme/`
  옆 README). 내용: 위 "표준 정의" 표 + 명도 대비 근거 + **"색/폰트/터치 치수는
  하드코딩 금지, 반드시 `Color`/`Type`/`Dimens` 토큰 사용"** 규칙 + 새 화면 추가
  시 점검 체크리스트.

## 시각 개선 (Visual Refresh)

테마 토큰 변경(폰트 위계 B, 간격·라운드·elevation 스케일 D)은 위 "변경 사항"에
정의돼 있다. 여기서는 그 토큰을 화면에 **적용**하는 작업과 화면별 손질을 정리한다.

### A. 깊이감 (elevation)

- 흰 패널·카드를 회색 배경(#E8E9EB) 위에 떠 보이게 미묘한 그림자를 준다.
- `HomeScreen`의 `BasketPane`, `PaymentScreen`의 두 패널 `Surface` → `shadowElevation
  = Dimens.paneElevation(2dp)`.
- `MenuTile`의 `Card` → `CardDefaults.cardElevation(defaultElevation = tileElevation(1dp))`.
- 과하지 않게: 그림자는 은은한 수준, 색은 기본 그림자색 유지.

### C. 브랜드 일관 선택색

- `CategoryChips`(`FilterChip`): 선택 시 `selectedContainerColor = OrangeContainer`,
  `selectedLabelColor = #9A5412`(대비 4.5:1), 선택 테두리 `Orange`. 비선택은 현행 유지.
- `SegmentedToggle`(`SegmentedButton`): `SegmentedButtonDefaults.colors(activeContainerColor
  = OrangeContainer, activeContentColor = #9A5412)`. 모양은 테마 `Shapes`의 사각형에
  가까운 라운드를 따름(버튼 디자인과 통일).
- `PaymentMethodTile`은 이미 오렌지 선택 상태라 기준 컴포넌트로 두되, 텍스트 색만
  `OrangeOnContainer`→#9A5412로 맞춰 대비 통과.

### 버튼 모양 (사각형化)

- 주요 버튼(`결제 →`, `결제 완료` 등)과 세그먼트 버튼을 기본 알약형이 아닌 작은
  라운드(`radiusButton = 6.dp`)로. 테마 `Shapes` 지정으로 일괄 적용하고, 필요 시
  호출부 `shape` 명시로 보강.
- 주요 버튼 높이는 `primaryTouchTarget(56dp)` 이상 확보.

### E. 메뉴 타일 다듬기

- 이름은 `bodyMedium`(Medium), 가격은 한 단계 작게 + 음소거가 아닌 `Orange` 강조 유지.
- 내부 패딩을 `spaceXs`/`spaceSm` 스케일로, 이름·가격 사이 간격 정리.
- 타일 높이는 터치 기준(≥56dp) 충족하도록 조정(현 60dp는 유지 가능).
- `Card(onClick)`의 기본 ripple로 press 피드백은 이미 있음 — 유지.

### F. 합계 강조

- `HomeScreen` `BasketPane`와 `PaymentScreen` 요약의 "합계" 행을 옅은 `OrangeContainer`
  배경의 둥근 박스로 감싸고, 금액을 `titleMedium`(Bold)로 강조. 텍스트 대비는 위
  명도 규칙(#9A5412 또는 큰/볼드 #C2691A) 준수.

### G. 빈 상태

- 장바구니(Home `BasketPane`, Payment 요약)가 비었을 때 가운데 정렬 음소거 문구
  (예: "담긴 상품이 없습니다") 표시. 메뉴 그리드가 비었을 때도 동일 패턴 적용 검토.

### H. 구분선 정리

- `BasketRow` 목록의 매 행 `HorizontalDivider`를 제거하고 `LazyColumn`의
  `verticalArrangement = Arrangement.spacedBy(spaceXs)` 여백으로 대체하거나, 꼭
  필요한 곳만 인셋된 옅은 구분선을 남긴다. 시각적 잡음을 줄인다.

## 범위 밖 (YAGNI)

- 시스템 글꼴 200% 가변 레이아웃 / ScrollView 재작업 (배율 고정으로 대체).
- 자동화 검증(Compose UI 테스트, lint 규칙, 대비 자동계산 테스트).
- 다크 테마, 색맹(color-blind) 팔레트, TalkBack/콘텐츠 설명 등 그 외 접근성 항목.
- 오렌지 외 브랜드 색 재설계.

## 검증 (수동)

- `assembleDebug` 빌드 성공.
- 변경 후 주요 흐름(주문 → 결제 → 주문기록, 설정/개발자도구) 육안 점검:
  - 접근성: 오렌지 버튼 글자가 진한색으로 가독, 음소거 텍스트 또렷, 탭 대상이 충분히 큼.
  - 시각: 패널이 배경 위에 떠 보임(그림자), 제목이 Bold로 도드라짐, 선택된 칩·세그먼트가
    오렌지로 보임, 버튼이 사각형에 가까움, 합계가 강조되고 빈 장바구니에 안내 문구가 뜸.
- 시스템 글꼴 크기를 키운 상태로 앱 진입 시 레이아웃이 변하지 않는지 확인(배율 고정).
- Compose `@Preview`로 주요 컴포넌트(`MenuTile`, `PaymentMethodTile`, `BasketRow`,
  `HomeScreen`, `PaymentScreen`) 렌더 확인.

## 완료 기준

**접근성**
- 토큰 변경 반영: 타이포 스케일 4개(labelSmall 14 / bodyMedium 15 / bodyLarge 16
  / titleMedium 18), OnSurfaceMuted #767676, onPrimary #222222, 상태 점 3색 승격,
  minTouchTarget 48dp / primaryTouchTarget 56dp.
- fontScale 1.0 고정 적용.
- 감사 체크리스트 3항목을 전 화면에 적용, 발견된 위반 수정.
- 접근성 표준 문서 작성.

**시각 개선**
- 폰트 웨이트(titleMedium Bold / titleLarge ExtraBold) 적용.
- 간격·라운드·elevation 스케일 토큰 추가 및 화면 적용(흩어진 dp/라운드 교체).
- 패널·카드 elevation 적용.
- CategoryChips·SegmentedToggle 오렌지 선택색 + 선택 텍스트 대비(#9A5412) 통과.
- 버튼·세그먼트 사각형(작은 라운드) 적용.
- 메뉴 타일 다듬기, 합계 강조, 빈 상태 문구, 구분선 정리 반영.

**공통**
- 빌드 성공 + 육안/Preview 점검 통과.
