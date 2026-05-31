# 대시보드 — 결제수단 집계 + 거래 단위 엑셀 (설계)

- 날짜: 2026-05-27
- 대상: `firebase/public/` 웹 매출 대시보드(v3 브랜치, https://holybean.web.app 배포됨)
- 선행 스펙: `docs/superpowers/specs/2026-05-27-firebase-dashboard-design.md`

## 1. 배경 / 목적

현재 대시보드는 하루를 선택해 **총매출·전체 잔 수·메뉴별 판매내역(수량 내림차순)**을 보여주고, 메뉴별 집계를 엑셀로 내려받는다. 두 가지를 추가한다.

- **A. 결제수단 집계(웹 화면):** 그날 결제수단별 매출을 화면에서 바로 확인.
- **B. 거래 단위 엑셀:** 엑셀에 "누가·무엇을·어떻게 샀는지"를 주문 단위 행으로 담는다. 기존 메뉴 집계 시트는 유지.

## 2. 데이터 모델 (기존, 변경 없음)

- `reportRollups/{YYYY-MM-DD}` = `{ total, menuSales:{name:{quantity,sales}}, paymentSales:{method:amount} }` — 정산분(creditStatus=0)만 집계. **A는 이 `paymentSales`만 사용**(추가 조회 없음).
- `orders/{date}_{num}` = `{ orderDate, orderNum, totalAmount, customerName, creditStatus(0=정산/1=외상), items:[{name,quantity,subtotal,unitPrice}], payments:[{method,amount}] }` — 정산·외상 모두 포함. **B는 이 컬렉션을 날짜로 조회**.
- 결제수단 종류: 현금, 카드, 쿠폰, 외상, 계좌이체, 무료쿠폰, 무료제공.
- 보안 규칙: `orders` `allow read: if signedIn()` 이미 허용됨(로그인 사용자 읽기 가능). 규칙 변경 없음.

## 3. A. 결제수단 집계 (웹 화면)

### 표시
- 위치: 총매출/전체 잔 수 stat 카드 **아래**, "엑셀로 내려받기" 버튼 **위**.
- 스타일: 기존 **판매 내역 리스트와 동일한 마크업/스타일**(좌측 이름 · 우측 값) 재사용. 헤더 텍스트 "결제수단".
- 행: 결제수단별로 `현금 ⋯ 126,500원` 형태. **금액 내림차순** 정렬. 비율(%) 없음(금액만).
- 데이터 소스: 현재 선택된 날짜의 `days[idx].data.paymentSales`. 이미 메모리에 있으므로 `render()`에서 동기 렌더.
- `paymentSales`가 없거나 비어 있으면 결제수단 섹션 전체를 숨김.

### 순수함수 (transform.js)
```js
// paymentSales 객체 → [{method, amount}] 금액 내림차순. null/빈 객체는 [] 반환, 금액은 Number 강제.
export function paymentRows(rollup) { ... }
```

## 4. B. 거래 단위 엑셀

### 조회
- 엑셀 버튼 클릭 시 선택 날짜의 주문을 조회: `query(collection(db,"orders"), where("orderDate","==",date))`.
  - 단일 필드 등식 필터 → 자동 단일 필드 인덱스. **복합 인덱스 추가 불필요.**
- 결과를 메모리에서 `orderNum` 오름차순 정렬.
- 조회 중 버튼 비활성(중복 클릭 방지), 실패 시 화면에 에러 메시지(`showMessage` 또는 인라인). orders가 0건이면 거래내역 시트는 헤더만 출력하고 메뉴집계 시트는 정상 출력.

### 시트 구성 (워크북 1개, 시트 2개)
1. **`거래내역`** — 주문 한 건 = 한 행.

   | 주문번호 | 고객명 | 주문내역 | 총액 | 결제수단 |
   |---|---|---|---|---|
   | 3 | 김철수 | 아메리카노 1개 | 5,000 | 외상 |
   | 5 | - | 아메리카노 2개, 카페라떼 1개 | 9,000 | 현금 |

   - 주문번호: `orderNum`(오름차순).
   - 고객명: `customerName`. 비어 있으면(`""`) `-` 표기.
   - 주문내역: `items[]` → `"{name} {quantity}개"`를 쉼표로 결합.
   - 총액: `totalAmount`(숫자 셀).
   - 결제수단: `payments[]`의 `method`를 `+`로 결합(`현금`, `현금+카드`, `외상`). 기존 `daySummaries.orderMethod` 규칙과 동일. payments가 비면 빈 문자열.
   - **외상 전부 포함.** 결제수단 칸의 `외상` 표기로 구분(별도 상태 컬럼 없음).
   - 맨 아래 **합계 행**: 라벨 "합계" + 총액 컬럼에 전체 합.
2. **`메뉴집계`** — 기존 `exportAOA`가 만들던 메뉴별 집계 시트를 그대로 유지(날짜·메뉴·수량·매출·총매출).

- 파일명: 기존과 동일 `holybean-{date}.xlsx`.

### 순수함수 (transform.js)
```js
// orders 문서 배열 → 거래내역 시트 AOA(헤더+행+합계). 빈 배열이면 헤더만(+합계 0 또는 합계행 생략).
// 입력은 orderNum 오름차순으로 정렬된 배열을 가정(정렬은 호출측 app.js 책임).
// customerName 빈 값은 "-", items/payments 결합, 숫자 강제.
export function transactionAOA(orders) { ... }
```
- 기존 `exportAOA(date, rows, total)`는 메뉴집계 시트용으로 유지.

## 5. 변경 파일

- `firebase/public/transform.js` — `paymentRows`, `transactionAOA` 추가.
- `firebase/test/transform.test.js` — 신규 함수 단위테스트 추가.
- `firebase/public/index.html` — 결제수단 섹션 마크업 추가(판매 내역 리스트 구조 재사용, `id="payments"` 등).
- `firebase/public/styles.css` — 필요 시 결제수단 섹션 미세 조정(기존 list 스타일 최대한 재사용).
- `firebase/public/app.js` — `render()`에서 결제수단 렌더, 엑셀 핸들러에서 `orders` 조회 + 2시트 워크북 생성. `where` import 추가.
- `firebase/scripts/seedDashboard.mjs` — 에뮬레이터 e2e용 `orders` 시드 추가(정산+외상 혼합).

## 6. 검증

- **단위테스트:** `paymentRows`(정렬/빈값/숫자강제), `transactionAOA`(주문내역·결제수단 결합, 익명 `-`, 합계 행, 빈 배열).
- **에뮬레이터 e2e(puppeteer):** orders 시드 후 로그인 → 결제수단 섹션 렌더 확인 → 엑셀 다운로드 후 `거래내역` 시트에 주문 행/익명 `-`/외상 행 존재 확인.
- **프로덕션 스모크(puppeteer):** 실데이터로 결제수단 표시 + 엑셀 다운로드, 콘솔 에러 0.

## 7. 범위 외

- 결제수단 비율(%)·추이 그래프, 거래 행의 메뉴별 분해(주문 단위 유지), 외상 현황 별도 화면, 기간 선택. (기존 범위 외 유지.)
