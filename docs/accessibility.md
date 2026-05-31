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
