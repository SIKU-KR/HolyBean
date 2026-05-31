# Project: HolyBean

<div align="left">
<img src="https://img.shields.io/badge/Kotlin-7F52FF?style=for-the-badge&logo=Kotlin&logoColor=white"><img src="https://img.shields.io/badge/Firebase-DD2C00?style=for-the-badge&logo=firebase&logoColor=white"><img src="https://img.shields.io/badge/Rust-000000?style=for-the-badge&logo=rust&logoColor=white"><img src="https://img.shields.io/badge/Raspberry_Pi-A22846?style=for-the-badge&logo=raspberrypi&logoColor=white">
</div>

## 📌 프로젝트 개요 (1인 개발)

> 매주 평균 40건 이상(일요일만 운영), 총 5,400건 이상 거래 처리(2026.05 기준)

**강원도 원주시 이룸교회 카페의 비효율적인 종이 주문서 및 엑셀 기반 관리 시스템을 대체하기 위해 개발된 안드로이드 기반 맞춤형 POS 솔루션입니다.** 2024년 1월부터 현재까지 **실제 운영 환경에서 안정적으로 사용**되고 있으며, 기존 수기 방식 대비 **업무 효율 증대 및 오류 감소 효과**를 성공적으로 입증했습니다. 

## 💡 주요 기능

- **직관적인 주문 관리:** Jetpack Compose 기반 UI/UX, 다양한 결제 옵션(현금, 외상, 복수 결제), 안정적인 영수증 출력(Raspberry Pi 프린트 서버 연동)
- **효율적인 매출 관리:** 일별/기간별 매출 데이터 조회 및 분석, 판매 이력 자동 기록, 보고서 생성 및 출력, **웹 매출 대시보드**(Firebase Hosting)에서 Excel 내보내기
- **안정적인 데이터 관리:** Firestore 직접 접근 + **오프라인 영속성**으로 인터넷 단절에도 읽기/쓰기 및 자동 동기화, App Check·Auth·보안 규칙 기반 인가

## 🔧 시스템 구성 및 아키텍처

모노레포 구조로 세 개의 빌드 가능한 서브시스템으로 구성됩니다. (`android/` · `firebase/` · `pi/`)

### 클라이언트 (Android)

- **Kotlin + Jetpack Compose 기반 네이티브 앱** (단일 Activity, Material3 디자인 시스템)
- **MVVM 아키텍처:** UI와 비즈니스 로직 분리, 테스트 용이성 및 유지보수성 향상
- **Coroutines:** `launchSafely` 표준 패턴 + main-safe 리포지토리로 효율적·안전한 비동기 처리
- **Hilt:** 의존성 주입을 통한 모듈화 및 코드 간결성 증대
- **Cloud Firestore SDK:** 백엔드 없이 데이터를 직접 읽고 쓰며, 오프라인 캐시로 로컬 데이터 영속성 지원

### 데이터 백엔드 (Firebase)

- **Cloud Firestore:** 읽기는 point read 1건(고정 경로), 쓰기 시 사전 집계(pre-aggregation)로 설계. `orders`가 source of truth이고 나머지는 재생성 가능한 파생 문서
- **Firebase Auth + App Check:** 익명/단일 운영 계정 인증 + Play Integrity 앱 증명
- **보안 규칙(Security Rules):** 인가의 최종 경계, 에뮬레이터 회귀 테스트로 검증
- **Firebase Hosting:** 정적 웹 매출 대시보드(`holybean.web.app`)
- **Crashlytics:** Android 크래시/ANR을 난독화 복원하여 단일 콘솔에서 수집

### 인쇄 서버 (Raspberry Pi, Rust)

- **Rust HTTP 프린트 서버:** Android가 보낸 구조화 JSON 명령을 `PrintCommand` 합타입으로 받아 ESC/POS로 변환(EUC-KR 한글 폭 계산·컬럼 레이아웃 포함)
- **잡 큐 직렬화:** 동시 출력 시 영수증이 섞이지 않도록 보장, 실패 3회 자동 재시도
- **systemd:** 부팅 자동 시작 + 크래시 자동 복구, `mDNS`로 주소 광고(앱이 빌드 고정 IP 없이 런타임 탐색)

## 🛠 기술 스택

### 클라이언트 개발

* **주요 기술**: Kotlin, Jetpack Compose, Android SDK, MVVM 패턴
* **비동기 & DI**: Coroutines, Hilt
* **데이터 관리**: Cloud Firestore SDK (오프라인 영속성)

### 백엔드 & 클라우드 (Firebase)

* **Database:** Cloud Firestore (NoSQL, 직접 접근)
* **Auth/인가:** Firebase Auth + App Check + Security Rules
* **Hosting:** 웹 매출 대시보드
* **Monitoring:** Firebase Crashlytics

### 인쇄 서버

* **언어:** Rust (clippy `-D warnings` 엄격 적용)
* **프로토콜:** ESC/POS, 잡 큐 직렬화
* **운영:** systemd, mDNS

### 하드웨어 통합

* **디바이스**: Galaxy Tab A7 lite (Android 기반)
* **중계 장비**: Raspberry Pi 3B (이더넷 업링크 + USB)
* **주변기기**: 세우 SLK-TS400B 영수증 프린터 (USB, ESC/POS 프로토콜)

## 🔍 주요 기술적 도전 과제 및 해결 과정

### 1. 데이터베이스 쿼리 최적화 (v1.3.1)

- **도전 과제:** 초기 버전에서 복잡한 데이터 가공 로직이 앱 레벨에 집중되어 성능 저하 및 코드 복잡성 증가.
- **접근 방법:** 데이터베이스 심층 학습 후, SQL 고급 기능을 활용하여 DB 레벨에서 데이터 처리 로직 최적화 리팩토링 수행.
- **해결 방안:**
    - `GROUP BY`, `SUM`, `MIN` 등 집계 함수와 `ORDER BY`를 활용하여 DB에서 필요한 형태로 데이터를 직접 가공.
    - 트랜잭션 적용으로 데이터 무결성 강화.
- **성과:**
    - 관련 **코드 라인 수 약 40% 감소** 및 가독성 대폭 향상.
    - 앱 메모리 사용량 감소 및 **데이터 처리 속도 개선**.
    - **데이터베이스 부하 분산** 및 ACID 원칙 준수를 통한 안정성 확보.

```kotlin
// 개선 사례: 복잡한 매출 집계 로직을 SQL 쿼리로 최적화
// 변경 전: 앱에서 여러 단계에 걸쳐 데이터 필터링 및 집계 (약 30+ 라인)
// 변경 후: 단일 SQL 쿼리로 DB에서 직접 집계 (10 라인)
val optimizedQuery = """
    SELECT MIN($DETAILS_PRODUCT_ID) AS min_id, 
           $DETAILS_PRODUCT_NAME, 
           SUM($DETAILS_QUANTITY) AS total_quantity, 
           SUM($DETAILS_SUBTOTAL) AS total_subtotal
    FROM $DETAILS
    WHERE $DETAILS_DATE BETWEEN ? AND ?
    GROUP BY $DETAILS_PRODUCT_ID, $DETAILS_PRODUCT_NAME
    ORDER BY min_id
""".trimIndent()
// 주석: 이 쿼리는 특정 기간 동안의 상품별 총 판매 수량과 금액을 계산합니다.
```

### 2. 클라우드 기반 데이터 관리 시스템 구축 (v2.0)

- **도전 과제:** 로컬 SQLite DB만으로는 데이터 백업, 여러 기기 간 동기화, 데이터 손실 방지에 한계 존재.
- **해결 방법:** AWS 서버리스 아키텍처(Lambda, DynamoDB, API Gateway) 도입 결정.
- **구현 내용:**
    - 주요 데이터(주문 내역, 매출 정보 등)를 DynamoDB에 저장하고 Lambda 함수를 통해 API 제공.
    - 앱 실행 시 및 주기적으로 로컬 DB와 클라우드 DB 간 데이터 동기화 로직 구현.
- **성과:**
    - **데이터 안정성 및 영속성 대폭 향상.**
    - 기기 분실/파손 시에도 **데이터 복구 가능.**
    - 향후 다중 기기 지원 및 기능 확장을 위한 **확장성 확보.**

### 3. 비동기 처리 및 UI 반응성 개선 (v2.0)

- **도전 과제:** 네트워크 통신 및 DB 접근 시 메인 스레드 차단으로 인한 간헐적 UI 멈춤(ANR) 발생 가능성.
- **해결 방법:** Kotlin Coroutines 도입하여 모든 I/O 작업을 비동기적으로 처리.
- **구현 내용:**
    - `ViewModelScope`, `Dispatchers.IO` 등을 활용하여 네트워크 요청 및 DB 쿼리를 백그라운드 스레드에서 수행.
    - `LiveData` 또는 `StateFlow`를 사용하여 UI 업데이트를 안전하고 효율적으로 처리.
- **성과:**
    - **앱의 전반적인 반응성 및 사용자 경험(UX) 향상.**
    - 비동기 코드의 가독성 및 유지보수성 개선.

### 4. 백엔드 제거 및 인쇄·UI 전면 재설계 (v3.0)

- **도전 과제:** AWS Lambda 10개가 실질적으로 순수 CRUD 패스스루였고, 불안정한 Bluetooth 인쇄가 단일 매장 운영에 비해 과도한 관리 비용을 유발.
- **핵심 질문:** "백엔드를 어디로 옮길까"가 아니라 **"백엔드가 필요한가"** 로 문제를 재정의.
- **해결 방안:**
    - **백엔드 제거** — 클라우드 서버를 삭제하고 Android가 Firestore를 직접 접근. 읽기는 point read 1건, 쓰기는 로컬 우선 WriteBatch + 사전 집계로 **핫패스 서버 왕복 0회** 달성. 인가는 App Check + Auth + 보안 규칙 3계층으로 대체.
    - **인쇄 경로 전환** — Bluetooth 인쇄를 Raspberry Pi 기반 유선 HTTP 인쇄로 교체. 영수증 포맷 책임을 Pi(Rust)로 이동시켜 **앱 배포 없이 포맷 변경** 가능. mDNS 런타임 주소 해석으로 고정 IP 의존 제거.
    - **UI 재작성** — Fragment/XML/ViewBinding을 단일 Activity Jetpack Compose로 전면 포팅, 접근성·대비·터치타깃 기준의 디자인 시스템 구축.
- **성과:**
    - **유지보수 대상 서버 제거** — 배포 대상이 앱(+Pi)으로 축소, 관측이 Firebase 단일 콘솔로 통합.
    - **인쇄 안정성 확보 및 인쇄/데이터 경로 격리** — 한쪽 장애가 다른 쪽에 전파되지 않음.
    - **오프라인 내구성 확보** — 인터넷 단절에도 주문 접수·조회가 로컬에서 완결.

## 📝 업데이트 이력

| **버전**    | **날짜**     | **주요 내용**                                                    |
|-----------|------------|--------------------------------------------------------------|
| **1.0.0** | 2024.01.28 | 초기 버전 출시 및 베타 테스팅 시작                                         |
| **1.1.0** | 2024.02.02 | 외상 기능, 판매 리포트, 주문 옵션 추가, 전체 화면 적용                            |
| **1.2.0** | 2024.02.21 | 주문 내역 삭제 기능 추가                                               |
| **1.2.1** | 2024.02.24 | 매출 보고서 출력 로직 개선                                              |
| **1.2.2** | 2024.03.02 | CSV 기반 메뉴 관리 기능 도입                                           |
| **1.3.0** | 2024.03.13 | 복수 결제, DB 구조 개선, 메뉴 정렬 옵션 추가                                 |
| **1.3.1** | 2024.05.01 | **SQL 쿼리 최적화를 통한 성능 향상 (코드 40% 감소)**                         |
| **1.3.2** | 2024.06.07 | 몰입 모드 자동 실행 기능 추가                                            |
| **1.4.0** | 2024.08.29 | 코드 리팩토링 및 DB 추가 개선                                           |
| **1.4.1** | 2024.09.02 | 주문 번호 관련 데이터 타입 오류 수정                                        |
| **1.4.2** | 2024.09.23 | 메뉴 카테고리 버그 수정, UUID 도입 (중복 데이터 방지)                           |
| **2.0.0** | 2024.12.28 | **AWS 클라우드 연동 (Lambda, DynamoDB), MVVM 패턴 적용, Coroutine 도입** |
| **2.1.0** | 2025.03.12 | 모든 기능 클라우드 연동 완료, 메뉴 순서 변경 오류 해결                             |
| **3.0.0** | 2026.05.31 | **Firestore 직접 접근, Bluetooth 인쇄→Raspberry Pi 유선 인쇄, Jetpack Compose 마이그레이션** |

## 🔒 라이선스

본 프로젝트는 이룸교회 맞춤형으로 개발되었습니다.
