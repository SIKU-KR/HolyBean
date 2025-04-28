# Project: HolyBean

<img src="https://img.shields.io/badge/Kotlin-7F52FF?style=for-the-badge&logo=Kotlin&logoColor=white">
<img src="https://img.shields.io/badge/Android-34A853?style=for-the-badge&logo=Android&logoColor=white">
<img src="https://img.shields.io/badge/AWS_Lambda-FF9900?style=for-the-badge&logo=amazonaws&logoColor=white">
<img src="https://img.shields.io/badge/DynamoDB-4053D6?style=for-the-badge&logo=amazondynamodb&logoColor=white">
<img src="https://img.shields.io/badge/SQLite-003B57?style=for-the-badge&logo=SQLite&logoColor=white">
<img src="https://img.shields.io/badge/Python-3776AB?style=for-the-badge&logo=python&logoColor=white">

## 📌 프로젝트 개요 (1인 개발)

**강원도 원주시 이룸교회 카페의 비효율적인 종이 주문서 및 엑셀 기반 관리 시스템을 대체하기 위해 개발된 안드로이드 기반 맞춤형 POS 솔루션입니다.** 블루투스 프린터 연동 및 클라우드 기반 데이터 관리를 통해 **매출 관리의 편의성과 정확성을 크게 향상**시켰으며, 2024년 1월부터 현재까지 **실제 운영 환경에서 안정적으로 사용**되고 있습니다. (매주 평균 40건 이상, 총 2,650건 이상의 거래 처리 - 2025.04 기준)

기존 수기 방식 대비 **업무 효율 증대 및 오류 감소 효과**를 성공적으로 입증했습니다.

## 💡 주요 기능

- **직관적인 주문 관리:** 쉬운 UI/UX, 다양한 결제 옵션(현금, 외상, 복수 결제), 실시간 영수증 출력(블루투스 연동)
- **효율적인 매출 관리:** 일별/기간별 매출 데이터 조회 및 분석, 판매 이력 자동 기록, 보고서 생성 및 출력
- **안정적인 데이터 관리:** AWS 클라우드(DynamoDB, Lambda) 연동을 통한 데이터 백업, 동기화 및 확장성 확보

## 🔧 시스템 구성 및 아키텍처

### 클라이언트 (Android)
- **Kotlin 기반 네이티브 앱**
- **MVVM 아키텍처:** UI와 비즈니스 로직 분리, 테스트 용이성 및 유지보수성 향상
- **Retrofit2 & Coroutines:** 효율적인 비동기 REST API 통신
- **Hilt:** 의존성 주입을 통한 모듈화 및 코드 간결성 증대
- **SQLite:** 로컬 데이터 캐싱 및 오프라인 기능 지원

### 서버 (AWS Serverless)
- **AWS Lambda (Python):** 이벤트 기반 마이크로서비스 구현, 서버 관리 부담 최소화
- **AWS DynamoDB:** 뛰어난 확장성과 성능을 제공하는 NoSQL 데이터베이스
- **AWS API Gateway:** RESTful API 엔드포인트 관리 및 보안 강화
- **AWS CloudWatch:** 실시간 모니터링 및 로그 분석

### 데이터 흐름
```
[안드로이드 클라이언트] ↔ [AWS API Gateway] ↔ [AWS Lambda] ↔ [AWS DynamoDB]
```

## 🛠 기술 스택

### 클라이언트 개발
* **주요 기술**: Kotlin, Android SDK, MVVM 패턴
* **비동기 & 네트워킹**: Coroutines, Retrofit2
* **데이터 관리**: SQLite, 클라우드 동기화

### 서버 & 클라우드
* **Compute:** AWS Lambda (Python)
* **Database:** AWS DynamoDB (NoSQL)
* **API:** AWS API Gateway
* **Monitoring:** AWS CloudWatch

### 하드웨어 통합
* **디바이스**: Galaxy Tab A7 lite (Android 기반)
* **주변기기**: Bluetooth 영수증 프린터 (ESC/POS 프로토콜)

## 📱 실제 운영 환경 및 성과

- **운영 기간:** 2024년 1월 ~ 현재
- **사용처:** 이룸교회 카페 (실제 상업 환경)
- **누적 처리 주문:** 약 2,650건 (2025년 4월 28일 기준)
- **주요 성과:**
    - 기존 수기 및 엑셀 관리 방식 대비 **업무 시간 단축 및 효율성 증대**
    - 데이터 입력 오류 감소로 인한 **매출 집계 정확성 향상**
    - 사용자(카페 운영 담당자)로부터 긍정적인 피드백 확보 및 지속적인 기능 개선 진행

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

## 📝 업데이트 이력

| **버전** | **날짜** | **주요 내용** |
| --- | --- | --- |
| **1.0.0** | 2024.01.28 | 초기 버전 출시 및 베타 테스팅 시작 |
| **1.1.0** | 2024.02.02 | 외상 기능, 판매 리포트, 주문 옵션 추가, 전체 화면 적용 |
| **1.2.0** | 2024.02.21 | 주문 내역 삭제 기능 추가 |
| **1.2.1** | 2024.02.24 | 매출 보고서 출력 로직 개선 |
| **1.2.2** | 2024.03.02 | CSV 기반 메뉴 관리 기능 도입 |
| **1.3.0** | 2024.03.13 | 복수 결제, DB 구조 개선, 메뉴 정렬 옵션 추가 |
| **1.3.1** | 2024.05.01 | **SQL 쿼리 최적화를 통한 성능 향상 (코드 40% 감소)** |
| **1.3.2** | 2024.06.07 | 몰입 모드 자동 실행 기능 추가 |
| **1.4.0** | 2024.08.29 | 코드 리팩토링 및 DB 추가 개선 |
| **1.4.1** | 2024.09.02 | 주문 번호 관련 데이터 타입 오류 수정 |
| **1.4.2** | 2024.09.23 | 메뉴 카테고리 버그 수정, UUID 도입 (중복 데이터 방지) |
| **2.0.0** | 2024.12.28 | **AWS 클라우드 연동 (Lambda, DynamoDB), MVVM 패턴 적용, Coroutine 도입** |
| **2.1.0** | 2025.03.12 | 모든 기능 클라우드 연동 완료, 메뉴 순서 변경 오류 해결 |

## 🔒 라이선스

본 프로젝트는 이룸교회 맞춤형으로 개발되었습니다.
