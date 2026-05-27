# DynamoDB → Firestore 데이터 마이그레이션 설계

작성일: 2026-05-27

## 목적

구버전 백엔드의 AWS DynamoDB에 남아있는 주문/메뉴 데이터를 현재 앱이 사용하는
Firebase Firestore(`holybean-e4201`)로 일회성 이관한다. 앱 코드는 100% Firestore
기반이며 DynamoDB 참조가 없으므로, 이관은 앱 외부의 독립 스크립트로 수행한다.

## 전제

- **일회성** 마이그레이션. 반복 동기화 불필요.
- 대상 Firestore의 `orders`/집계 컬렉션은 **비어있음** → 충돌/병합 처리 불필요.
- AWS CLI 로그인 완료(region `ap-northeast-2`). Firebase는 service account JSON 필요.

## 원본 (DynamoDB, ap-northeast-2)

### `holybean` — 주문, 약 5,241건
- 키: `orderDate`(S, HASH) + `orderNum`(N, RANGE)
- 필드:
  - `orderDate`(S), `orderNum`(N), `totalAmount`(N), `creditStatus`(N: 0/1)
  - `customerName`(S, **NULL 가능**)
  - `orderItems`(L of M: `itemName`(S), `unitPrice`(N), `quantity`(N), `subtotal`(N))
  - `paymentMethods`(L of M: `method`(S), `amount`(N))

### `holybean-menu` — 메뉴, 6개 버전(이력)
- 키: `pk`(S, HASH, "default") + `timestamp`(S, RANGE)
- `menu_items`(L of M: `id`(N), `name`(S), `price`(N), `order`(N), `inuse`(BOOL))

## 대상 (Firestore, holybean-e4201)

소스 오브 트루스는 앱 코드의 `FirestoreSchema`, `OrderAggregation`,
`FirestoreRepository.postOrder`. 마이그레이션 산출물은 이들이 생성하는 형태와
정확히 일치해야 한다.

### `orders/{orderDate}_{orderNum}`
```
orderDate: string
orderNum: int
totalAmount: int
customerName: string        // NULL → ""
creditStatus: int           // 0 또는 1
items: [ { name, quantity, subtotal, unitPrice } ]
payments: [ { method, amount } ]
createdAt: timestamp        // 합성: orderDate 자정(KST, Asia/Seoul)
```

### `daySummaries/{orderDate}` — 모든 주문 포함
```
lastOrderNum: int           // 해당 날짜 최대 orderNum
orders: {
  "{orderNum}": {
    customerName: string,
    totalAmount: int,
    orderMethod: string,    // 결제수단 type을 "+"로 조인 (예: "카드+현금"). 비면 "Unknown"
    creditStatus: int
  }
}
```

### `reportRollups/{orderDate}` — **creditStatus==0(정산 완료)만** 집계
```
menuSales: { "{itemName}": { quantity: int, sales: int } }
paymentSales: { "{method}": int }
total: int                  // 결제 amount 총합
```

### `aggregates/openCredits` — **creditStatus==1(미수금)만**, 단일 문서
```
items: {
  "{orderDate}_{orderNum}": {
    customerName: string,
    totalAmount: int,
    orderNum: int,
    orderDate: string
  }
}
```

### `menu/current` — 최신 버전 1개만
```
items: [ { id, name, price, placement, inuse } ]   // order → placement
updatedAt: timestamp        // 마이그레이션 실행 시각
```

## 필드 변환 요약

| DynamoDB | Firestore | 변환 |
|---|---|---|
| `orderItems[].itemName` | `items[].name` | 키 이름 변경 |
| `orderItems[].{unitPrice,quantity,subtotal}` | `items[].{unitPrice,quantity,subtotal}` | 동일 |
| `paymentMethods` | `payments` | 키 이름 변경(원소 `method`/`amount`는 동일) |
| `customerName` = NULL | `customerName` = `""` | 앱의 `?: ""` 폴백과 일치 |
| (없음) | `createdAt` | orderDate 자정(Asia/Seoul) 타임스탬프 합성 |
| `menu_items[].order` | `items[].placement` | 키 이름 변경 |

## 집계 규칙 (앱 로직 복제)

`OrderAggregation` / `FirestoreRepository.postOrder` 기준:

- **daySummaries**: creditStatus 무관 모든 주문 포함.
- **reportRollups**: `postOrder`에서 `creditStatus == CREDIT_SETTLED(0)`일 때만
  rollup 가산 → 마이그레이션도 **creditStatus==0 주문만** 메뉴/결제 집계에 포함.
- **openCredits**: `creditStatus != 0`일 때만 등록 → **creditStatus==1 주문만** 포함.
- `orderMethod` 라벨: `payments`가 비면 `"Unknown"`, 아니면 type을 `"+"`로 조인.

## 접근 방식

**Python 단일 스크립트.** 5,241건은 메모리에서 충분히 처리 가능.

- 대안 B(raw/집계 2단계): 이 규모에서 복잡도만 증가 → 기각.
- 대안 C(앱이 집계 재생성): 앱에 재생성 로직 없음(증분 생성) → 기각.

## 구현

### 위치
- `android/scripts/migrate_dynamo_to_firestore.py`
- `android/scripts/requirements.txt` (`boto3`, `firebase-admin`)
- `android/scripts/README.md` (사용법, 자격증명 안내)

### 자격증명
- AWS: 기본 자격증명 체인(CLI 로그인 그대로 사용).
- Firebase: service account JSON 경로를 `--service-account` 인자 또는
  `GOOGLE_APPLICATION_CREDENTIALS` 환경변수로 전달. 키 파일은 커밋하지 않음(.gitignore).

### 처리 흐름
1. `holybean` 페이지네이션 스캔 → 전체 주문 로드(boto3 paginator).
2. 각 주문을 `orders/{date}_{num}` 문서로 변환.
3. 스캔하며 메모리에 집계 누적(daySummaries / reportRollups / openCredits).
4. orders 문서를 500개 단위 배치로 기록.
5. 집계 문서 기록(daySummaries 날짜별, reportRollups 날짜별, openCredits 단일).
6. `holybean-menu` 스캔 → 최신 `timestamp` 버전 선택 → `menu/current` 기록.

### 안전장치
- `--dry-run`: 기록 없이 변환 결과(주문 수, 날짜 범위, 집계 샘플) 출력.
- **멱등성**: 모든 쓰기는 `set`(덮어쓰기)이라 재실행 안전. orders는 500개씩 청크.
- **검증 패스**: 기록 후
  - Firestore `orders` 문서 수 == DynamoDB 항목 수
  - 날짜별 `daySummaries.lastOrderNum` == 해당 날짜 최대 orderNum
  - `reportRollups` total 합 == creditStatus==0 주문의 결제 총합
  - `openCredits.items` 수 == creditStatus==1 주문 수
- 모든 단계 로그 출력(진행 카운트, 경고: NULL customerName, 결제수단 누락 등).

## 범위 밖

- 반복/양방향 동기화.
- DynamoDB 테이블 삭제/정리(이관 검증 후 사용자가 별도 판단).
- 앱 코드 변경(스크립트는 외부 도구).
