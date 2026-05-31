# PRD - 백엔드 제거 및 Firebase 이전 (AWS Lambda/DynamoDB → Firestore 직접 접근)

> 이 문서는 [`v3_prd.md`](./v3_prd.md)(영수증 프린트 서버)와 **독립된 작업**이다.
> 프린트 서버는 Android↔Pi 인쇄 경로를, 본 문서는 Android↔데이터 저장소 경로를 다룬다.
> 두 작업은 서로의 선행 조건이 아니며 병렬로 진행할 수 있다.

## 1. 배경 및 문제 정의

### 현재 상황
데이터 백엔드는 **AWS API Gateway + Go Lambda 10개 + DynamoDB 2테이블**(`holybean`, `holybean-menu`)로 구성된다.
Android 앱은 Retrofit으로 API Gateway에 호출하며 `apikey` 헤더로 인증한다.

### 핵심 관찰 — 백엔드가 신뢰 경계가 아니다
Lambda 10개를 분석하면 **서버가 실질적으로 하는 일이 거의 없다.**

| 분류 | 함수 | 비고 |
|------|------|------|
| 순수 CRUD 패스스루 (8개) | post_order, get_order_day, get_order_item_specific, delete_order, get_credits_list, update_credit_status, get_last_menulist, save_menulist | **서버측 검증 없음.** `post_order`는 클라이언트가 보낸 `totalAmount`를 그대로 저장 |
| 실제 로직 (2개) | get_current_order_num (오늘 MAX+1 채번), get_report (기간 집계) | 클라이언트/DB로 이전 가능 |

→ 서버가 인가/검증/계산의 권위(authority) 역할을 하지 않으므로, **"백엔드를 어디로 옮길까"가 아니라 "백엔드가 필요한가"** 가 올바른 질문이다.

### 문제점
- **분산된 운영 표면** — Lambda 10개가 각각 별도 배포 단위. 빌드는 함수마다 수작업(`how_to_build.md`), 중앙 파이프라인 없음.
- **로그/크래시 가시성 부족** — 함수별 CloudWatch 로그가 흩어져 있음.
- **DynamoDB의 부적합** — `get_report`가 전체 Scan으로 집계. 단일 매장에 비해 운영 모델이 무겁고 로컬 개발이 번거롭다.
- **유지보수 대상인 서비스가 존재** — 단일 매장 앱치고 관리할 클라우드 표면이 넓다.

### 동기 (왜 Firestore 직접 접근인가)
| 기대 효과 | 근거 |
|----------|------|
| **서버 코드 제거** | 백엔드가 순수 CRUD라 삭제해도 무결성 손실 없음. 유지보수·배포 대상 자체가 사라진다. |
| **데이터 형태 일치** | 기존 데이터가 문서형(`orderItems[]`/`paymentMethods[]` 임베드). Firestore에 1:1 매핑, ETL이 문서→문서로 단순. (D1은 정규화 분해 필요) |
| **단일 콘솔 통합** | 크래시(Crashlytics) + DB(Firestore) + 인증이 한 Firebase 콘솔로. (크래시 결정으로 "중앙 대시보드" 목표가 이미 Firebase로 이동) |
| **오프라인 내장** | Firestore 오프라인 영속성으로 인터넷 단절에도 읽기/쓰기·자동 동기화. |
| 비용/운영 | 단일 매장 트래픽은 Firebase 무료(Spark) 티어 내. 서버 운영비 0. |

---

## 2. 목표

### 핵심 목표
**클라우드 백엔드(API GW + Lambda)를 제거**하고, Android 앱이 **Firestore를 직접 읽고 쓰도록** 재설계한다.
인가는 **Firebase Auth + App Check + 보안 규칙**으로 대체하고, 기존 운영 데이터를 Firestore로 이관한다.

### 성공 기준
| 지표 | 현재 (AWS) | 목표 (Firebase) |
|------|-----------|----------------|
| 유지보수할 서버 | Lambda 10 + API GW | **없음** |
| 배포 | 함수별 zip 수작업 | 앱 배포만 (서버 배포 소멸) |
| report | 전체 Scan | Firestore 인덱스 쿼리 + 클라이언트 집계 |
| 관측(크래시/DB) | CloudWatch 분산 | Firebase 콘솔 단일화 |
| 오프라인 | 불가 | Firestore 캐시로 가능 |
| 인가 | `apikey` 헤더 | Firebase Auth + App Check + 보안 규칙 |
| 기존 데이터 | DynamoDB | Firestore로 1회 이관, 매출 리포트 연속성 유지 |

### 비목표 (Out of Scope)
- **서버측 권위 검증 도입** — 현재도 없으며 본 작업의 목표가 아니다. 무결성 모델은 현행과 동일(클라이언트 신뢰).
- **데이터 스키마 재설계** — 필드 의미는 보존. 신규 기능/필드는 별도 안건.
- **멀티 매장/멀티 테넌시.**
- **프린트 서버(Pi)와의 결합** — 무관한 경로(§v3_prd).

> **트레이드오프 인지** — 백엔드 제거의 대가는 ① **보안 규칙이 인가 계층이 됨**(apikey 헤더보다 설계 신중 필요), ② 리포트가 클라이언트 집계로(읽기 과금), ③ 로직이 앱에 박힘(향후 클라이언트 추가 시 중복). 단일 Android 앱·단일 매장(기기 1대) 전제에서 이 비용은 수용 가능하다고 판단한다. **채번 동시성은 기기 1대 전제로 비고려.**

---

## 3. 현재 동작 인벤토리 (대체 대상)

Android `ApiService.kt`의 10개 호출을 Firestore 연산으로 대체한다.

| # | 기존 호출 | 동작 | Firestore 대체 (상세는 §4) |
|---|-----------|------|----------------|
| 1 | GET `ordernum` | 오늘 다음 주문번호 | `daySummaries/{today}.lastOrderNum` +1 (로컬, 서버 왕복 0) |
| 2 | POST `order` | 주문 저장 | 쓰기 팬아웃 (orders + daySummaries + 롤업/외상) |
| 3 | GET `order/{date}` | 당일 주문 목록 | `daySummaries/{date}` 점읽기 1건 |
| 4 | GET `order?date&num` | 특정 주문 상세 | `orders/{date}_{num}` 점읽기 1건 |
| 5 | DELETE `order?date&num` | 주문 삭제 | 삭제 팬아웃 |
| 6 | GET `report?start&end` | 기간 매출 집계 | `reportRollups/{날짜}` 일수만큼 읽어 합산 |
| 7 | GET `/credit` | 외상 목록 | `aggregates/openCredits` 점읽기 1건 |
| 8 | PUT `/credit/{date}/{num}` | 외상 정산 | 정산 팬아웃 |
| 9 | GET `/menu` | 최신 메뉴 | `menu/current` 점읽기 1건 |
| 10 | POST `/menu` | 메뉴 저장 | `menu/current` set (+선택 history) |

> 도메인 필드는 보존: `creditStatus` `1`=외상 미수 / `0`=정산. 금액은 원(KRW) 정수.

---

## 4. 솔루션 — Firestore 데이터 모델 (액세스 패턴 최적화)

> **스키마 제약 없음** — 데이터는 추후 백필하므로 DynamoDB 모양을 답습하지 않고 **읽기 패턴에 맞춰 재설계**한다.

### 설계 원칙
1. **읽기는 전부 point read(1 문서, 고정 경로)로** — 쿼리/인덱스를 사실상 없앤다. point read는 쿼리보다 빠르고 싸며 오프라인 캐시에서 즉시 응답된다.
2. **쓰기 시 사전 집계(pre-aggregation)** — 화면이 필요로 하는 집계를 쓰기 시점에 미리 갱신해, 읽기 시 재집계하지 않는다. 쓰기는 로컬 우선이라 지연에 영향 없음.
3. **source of truth ↔ 파생 캐시 분리** — `orders`가 원본, 나머지는 **언제든 `orders`로부터 재생성 가능한 파생 문서**. 역정규화 정합성 부담을 "재생성으로 복구 가능"으로 봉인한다.

### 컬렉션
```
# ── source of truth ──
orders/{YYYY-MM-DD}_{num}                 // 주문 원본 (상세/쓰기). 점읽기 1회
  ├─ orderDate, orderNum, totalAmount, customerName, creditStatus
  ├─ items:    array<map{ name, quantity, subtotal, unitPrice }>
  ├─ payments: array<map{ method, amount }>
  └─ createdAt: timestamp

# ── 파생(쓰기 시 갱신, orders로 재생성 가능) ──
daySummaries/{YYYY-MM-DD}                 // 당일 주문 목록 화면 + 채번. 점읽기 1회
  ├─ lastOrderNum: number                 // 채번 소스 (기기 1대)
  └─ orders: map{ "{num}": { customerName, totalAmount, orderMethod, creditStatus } }

reportRollups/{YYYY-MM-DD}                // 일별 매출 집계(정산분만). 리포트는 일수만큼 읽기
  ├─ menuSales:    map{ "{itemName}": { quantity, sales } }
  ├─ paymentSales: map{ "{method}": amount }
  └─ total: number

aggregates/openCredits                    // 미수 외상 목록(단일 문서). 점읽기 1회
  └─ items: map{ "{date}_{num}": { customerName, totalAmount, orderNum, orderDate } }

menu/current                              // 현재 메뉴(고정 경로). 점읽기 1회
  ├─ items: array<map{ id, name, price, placement, inuse }>
  └─ updatedAt: timestamp
menu/history_{timestamp}                  // (선택) 메뉴 변경 이력/감사
```

### 읽기 비용 (작업당 문서 수)
| 작업 | 읽는 문서 | 인덱스 |
|------|-----------|--------|
| 다음 주문번호 | `daySummaries/{today}.lastOrderNum` (로컬 캐시) | — |
| 당일 주문 목록 | `daySummaries/{date}` **1건** | — |
| 주문 상세 | `orders/{date}_{num}` **1건** | — |
| 외상 목록 | `aggregates/openCredits` **1건** | — |
| 메뉴 | `menu/current` **1건** | — |
| 리포트(기간) | `reportRollups/{각 날짜}` = **기간 일수**만큼 | — |

→ 리포트를 제외한 **모든 읽기가 점읽기 1회**, **추가 인덱스 0개**. 리포트도 주문 수가 아닌 **일수**에 비례(월 리포트≈30읽기).

### 쓰기 팬아웃 (로컬 우선, WriteBatch로 원자 적용)
| 트리거 | 갱신 |
|--------|------|
| 주문 생성 | `orders` set · `daySummaries.orders.{num}` set + `lastOrderNum` · 정산(0)이면 `reportRollups/{date}` 증분 · 미수(1)면 `openCredits.items` 추가 |
| 외상 정산 (1→0) | `orders.creditStatus=0` · `daySummaries` 항목 갱신 · `openCredits.items` 제거 · **원 주문일 `reportRollups` 증분** |
| 주문 삭제 | `orders` 삭제 · `daySummaries` 항목 제거 · 정산분이면 `reportRollups` 감산 · 미수면 `openCredits` 제거 |

> 외상은 정산 전까지 리포트에서 제외(구 `creditStatus==0` 필터와 동일). 정산 시점에 **원래 주문일**의 롤업에 가산하는 것이 핵심.

### 지연(latency) 설계 — 핫패스 서버 왕복 0회
| 연산 | 기존(AWS) | Firestore |
|------|-----------|-----------|
| 주문 채번 | `ordernum` 블로킹 왕복 (+Lambda 콜드스타트) | `daySummaries` 로컬값 +1, 왕복 0 |
| 주문 저장 | `post_order` 블로킹 왕복 | 로컬 우선 배치 쓰기, 체감 0 (백그라운드 동기화) |
| 목록/상세/외상/메뉴 | 매번 왕복 | 점읽기 1건, 캐시 우선 |
| 리포트 | 전체 Scan 왕복 | 일별 롤업 N건 (핫패스 밖) |

원칙: **핫패스(주문 받기)는 로컬에서 완결**, 네트워크는 백그라운드 동기화만. 쓰기 UI는 서버 ack를 await하지 않는다. 체감 지연은 현 AWS 구조보다 **개선**된다.

---

## 5. 인증 / 인가 (apikey 대체) — 최우선 설계 항목

백엔드 제거의 유일한 실질 비용. 다층으로 막는다.

| 계층 | 역할 |
|------|------|
| **Firebase App Check** | 요청이 **정품 앱 바이너리**에서 왔음을 증명(Play Integrity). 기존 "우리 앱만 아는 apikey"의 의도를 대체. |
| **Firebase Auth** | 매장 기기 신원. 익명 인증 또는 단일 운영 계정. 보안 규칙에서 `request.auth != null` 게이트. |
| **보안 규칙(Security Rules)** | 컬렉션별 read/write 허용 범위, 필드 타입/형태 검증, 삭제 제한 등. 인가의 최종 경계. |

설계 원칙:
- 인증되지 않은 요청은 전부 거부. App Check 미통과 요청 거부.
- 규칙에서 문서 형태 최소 검증(필드 존재/타입). 단, 무결성 권위는 아님(현행과 동일).
- 규칙은 **테스트(에뮬레이터)로 회귀 검증** — 보안 규칙 오류가 본 아키텍처의 최대 리스크이므로.

---

## 6. 관측성 & 크래시 로그 — Firebase

| 레이어 | 도구 | 내용 |
|--------|------|------|
| **Android 크래시/ANR** | **Firebase Crashlytics** | JVM 예외 + ANR + 네이티브 크래시 자동 포착. Gradle 플러그인이 **ProGuard/R8 매핑 자동 업로드** → 난독화 복원. 오프라인 큐 내장, 무료. |
| **DB 사용/오류** | Firestore 사용량·규칙 거부 로그 | Firebase 콘솔에서 읽기/쓰기량, 규칙 거부, 인덱스 경고 확인. |
| 부가(선택) | `recordException()`, 브레드크럼/커스텀 키 | 비치명 오류·사용자 흐름 추적. |

설정: `com.google.gms.google-services` + `firebase-crashlytics` + `firebase-firestore` + `firebase-appcheck` Gradle 플러그인, `google-services.json` 추가.

---

## 7. Android 앱 변경

| 항목 | 변경 |
|------|------|
| 제거 | `RetrofitClient`, `ApiService`, `BASE_URL`, `apikey`(BuildConfig) — 네트워크 계층 삭제 |
| 추가 | Firebase BoM + Firestore/Auth/App Check/Crashlytics SDK, `google-services.json` |
| 데이터 계층 | `LambdaRepository` → `FirestoreRepository`로 교체. 10개 호출을 Firestore 연산으로 재구현 |
| 메뉴 캐시 | **Room 메뉴 캐시는 재검토** — Firestore 오프라인 영속성이 동일 역할. 유지(점진 이전) 또는 제거 결정 필요(§10) |
| 모델 | 응답 DTO는 Firestore 문서 ↔ 도메인 모델 매핑으로 흡수. 도메인 모델 자체는 최대한 보존 |

---

## 8. 데이터 이전 (백필 + 파생 재생성)

스키마가 바뀌었으므로 단순 복사가 아니라 **원본 적재 → 파생 재생성** 2단계로 한다. 데이터는 컷오버 후 백필해도 무방하다.

1. **Export** — DynamoDB `holybean`, `holybean-menu` 전량 export(JSON).
2. **원본 적재(orders/menu)** — Admin SDK로 `holybean` 항목 → `orders/{date}_{num}`(필드 매핑·`createdAt` 보강), `holybean-menu` 최신 → `menu/current`.
3. **파생 재생성** — 적재된 `orders`를 전수 순회해 `daySummaries`·`reportRollups`·`aggregates/openCredits`를 **재구성**. (이 재생성 잡은 운영 중에도 정합성 복구용으로 재사용 가능 — §11 참조)
4. **검증** — 주문 건수·동일 구간 매출 리포트를 구/신에서 비교해 수치 일치 확인. 롤업 합계 = orders 직접 집계와 대조.
5. **컷오버** — Firestore 버전 앱 배포. AWS는 일정 기간 읽기 보존 후 폐기.

---

## 9. 요구사항

### 기능 요구사항
| ID | 요구사항 | 우선순위 |
|----|---------|---------|
| FR-1 | 기존 10개 동작(주문 채번/저장/조회/삭제, 리포트, 외상, 메뉴)을 Firestore 연산으로 동등하게 제공한다 | 필수 |
| FR-2 | 일일 주문번호 채번이 로컬에서 서버 왕복 없이 즉시 할당된다 (기기 1대) | 필수 |
| FR-3 | 리포트 결과가 기존과 동일하다(메뉴별 판매·결제수단 합계·총합), 일별 롤업 합산으로 산출 | 필수 |
| FR-4 | 최신 메뉴를 `menu/current` 점읽기로 반환한다 | 필수 |
| FR-5 | 기존 DynamoDB 데이터를 원본 적재 후 파생 문서로 재생성해 이관한다 | 필수 |
| FR-6 | 파생 문서(daySummaries/reportRollups/openCredits)를 orders로부터 재생성하는 잡을 제공한다 | 필수 |

### 비기능 요구사항
| ID | 요구사항 | 우선순위 |
|----|---------|---------|
| NFR-1 | 클라우드 백엔드(API GW/Lambda)가 완전히 제거된다 | 필수 |
| NFR-2 | App Check + Firebase Auth + 보안 규칙으로 무인증/비정품 접근을 차단한다 | 필수 |
| NFR-3 | 보안 규칙을 에뮬레이터 테스트로 회귀 검증한다 | 필수 |
| NFR-4 | 인터넷 단절 시 Firestore 오프라인 캐시로 읽기·쓰기·자동 동기화된다 | 필수 |
| NFR-7 | 주문 접수 핫패스는 로컬에서 완결되어 서버 왕복으로 인한 체감 지연이 없다 | 필수 |
| NFR-5 | Android 앱 크래시/ANR이 Crashlytics에 난독화 복원되어 수집된다 | 필수 |
| NFR-6 | `google-services.json` 등 설정값을 비공개로 관리한다 | 필수 |

---

## 10. 마일스톤

| 단계 | 내용 |
|------|------|
| 1 | Firebase 프로젝트 생성, Firestore/Auth/App Check/Crashlytics 활성화, `google-services.json` 연동 |
| 2 | Firestore 데이터 모델 + 보안 규칙 작성, 에뮬레이터 테스트 |
| 3 | `FirestoreRepository` 구현 — 읽기 연산(조회/목록/리포트/메뉴) |
| 4 | 쓰기 연산 — 채번 트랜잭션, 주문 저장/삭제, 외상 정산, 메뉴 저장 |
| 5 | 기존 화면/도메인 모델 연결, Room 메뉴 캐시 처리 결정·반영 |
| 6 | DynamoDB → Firestore ETL 및 수치 검증 |
| 7 | 컷오버(앱 배포) + AWS 폐기 |
| (병행) | Crashlytics 매핑 자동 업로드 검증 |

---

## 11. 리스크 / 미결 사항

- [ ] **보안 규칙 설계** — 본 아키텍처 최대 리스크. App Check + Auth + 규칙 조합과 회귀 테스트 범위 확정.
- [ ] **파생 정합성** — 쓰기 팬아웃이 어긋나면 목록/리포트가 틀어짐. 모든 갱신을 WriteBatch로 원자 적용하고, **재생성 잡(FR-6)으로 주기적/수동 복구** 가능하게 해 위험을 봉인. (orders가 source of truth)
- [ ] **리포트 장기 구간** — 일별 롤업이라 연 단위는 ~365 읽기. 필요 시 `monthlyRollups` 2차 계층 추가 검토.
- [ ] **문서 1MB 한도** — `daySummaries`/`reportRollups` 맵이 한도 내인지(일 수백 건·메뉴 수십 종이면 수십 KB로 여유) 확인. 폭증 시 분할 전략.
- [ ] **로컬 카운터 초기 동기화** — 앱/세션 시작 시 `daySummaries/{today}`를 캐시 빈 상태에서 서버 1회 읽기 vs 리스너 구독으로 안전 확보.
- [ ] **Room 메뉴 캐시 거취** — Firestore 오프라인과 중복. 유지 vs 제거.
- [ ] **삭제 시 번호 재사용** — 주문 삭제 후 `lastOrderNum` 되돌리지 않음(번호 재사용 금지) 정책 확인.
- [ ] **벤더 락인** — Google 종속. 향후 BigQuery export로 분석 유연성 보완 가능.
- [ ] **AWS 폐기 시점** — Firestore 안정화 후 Lambda/DynamoDB/API GW 해체 일정.
- [x] 아키텍처 → **백엔드 제거 + Firestore 직접 접근 확정**
- [x] 크래시 수집 → **Firebase Crashlytics 확정**
- [x] 데이터 → **일회성 ETL(문서→문서) 이관 확정**
```
