# POS 접근성 표준 수립 + 기존 화면 감사

날짜: 2026-05-24
브랜치: v3

## 배경 / 목적

HolyBean은 매장 고정 태블릿에서 직원이 빠르게 조작하는 카페 POS 앱이다. Google
Material Design / Android Accessibility 가이드의 정량 기준(터치 타깃, 최소 폰트,
명도 대비)을 이 프로젝트에 적용한다. 두 가지를 동시에 한다:

1. **표준 수립** — 기준을 테마 토큰(`Type`/`Color`/`Dimens`)에 박아 중앙화하고,
   접근성 표준 문서를 작성한다. 토큰 중앙화 자체가 회귀 방지의 주된 수단이다.
2. **기존 화면 감사 + 수정** — 현재 Compose 화면을 세 기준으로 점검해 위반을 고친다.

코드베이스는 이미 토큰 중심으로 잘 정리되어 있다. 화면에 하드코딩된 sp는 0건,
하드코딩 색상은 4건뿐이며 `onPrimary`/흰 글자는 테마에만 정의되어 있다. 따라서
변경 표면은 작고 집중되어 있다.

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

→ 오렌지 브랜드 색은 **유지**하되, 오렌지 배경 위 텍스트는 흰색이 아닌 진한
   색(#222222)을 쓴다. 음소거 회색은 #767676 이상으로 어둡게 한다.

## 변경 사항

### 1. 테마 토큰 (중앙화)

**`ui/theme/Type.kt`** — 14sp를 하한으로 두고 캡션·본문 위계가 무너지지 않도록
스케일을 한 단계씩 상향한다.

| 토큰 | 현재 | 변경 | 용도 |
|---|---|---|---|
| `labelSmall` | 11sp | **14sp** | 캡션·부가 라벨 (하한) |
| `bodyMedium` | 13sp | **15sp** | 본문 |
| `bodyLarge` | 15sp | **16sp** | 강조 본문 |
| `titleMedium` | 16sp | **18sp** | 소제목 |
| `titleLarge` | 22sp | 22sp | 대제목 (변경 없음) |

`labelSmall`(캡션)과 `bodyMedium`(본문)이 같은 크기로 수렴하지 않도록 14/15로
한 단계 간격을 유지한다.

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

**`ui/theme/Dimens.kt`**
- `minTouchTarget = 48.dp` 추가.
- `primaryTouchTarget = 56.dp` 추가.

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

## 범위 밖 (YAGNI)

- 시스템 글꼴 200% 가변 레이아웃 / ScrollView 재작업 (배율 고정으로 대체).
- 자동화 검증(Compose UI 테스트, lint 규칙, 대비 자동계산 테스트).
- 다크 테마, 색맹(color-blind) 팔레트, TalkBack/콘텐츠 설명 등 그 외 접근성 항목.
- 오렌지 외 브랜드 색 재설계.

## 검증 (수동)

- `assembleDebug` 빌드 성공.
- 변경 후 주요 흐름(주문 → 결제 → 주문기록, 설정/개발자도구) 육안 점검:
  오렌지 버튼 글자가 진한색으로 보이고 가독성 유지, 음소거 텍스트가 또렷,
  탭 대상이 충분히 큰지 확인.
- 시스템 글꼴 크기를 키운 상태로 앱 진입 시 레이아웃이 변하지 않는지 확인
  (배율 고정 동작 검증).

## 완료 기준

- 토큰 변경 반영: 타이포 스케일 4개(labelSmall 14 / bodyMedium 15 / bodyLarge 16
  / titleMedium 18), OnSurfaceMuted #767676, onPrimary #222222, 상태 점 3색 승격,
  minTouchTarget 48dp / primaryTouchTarget 56dp.
- fontScale 1.0 고정 적용.
- 감사 체크리스트 3항목을 전 화면에 적용, 발견된 위반 수정.
- 접근성 표준 문서 작성.
- 빌드 성공 + 육안 점검 통과.
