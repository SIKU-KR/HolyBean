# HolyBean POS 프로젝트
<div> 
<img src="https://img.shields.io/badge/Kotlin-7F52FF?style=for-the-badge&logo=Kotlin&logoColor=white">
<img src="https://img.shields.io/badge/Android-34A853?style=for-the-badge&logo=Android&logoColor=white">
<img src="https://img.shields.io/badge/SQLite-003B57?style=for-the-badge&logo=SQLite&logoColor=white">
</div>

<br/>
강원도 원주시 소재의 이룸교회의 카페에서 사용중인 안드로이드 POS 시스템입니다.
<br>
Bluetooth 연결을 통한 ESC/POS 명령 타입의 영수증 프린터를 지원합니다.

### 사용중인 하드웨어

- Galaxy Tab A7 lite (8.7”) with Android 14
- SEWOO SLK-TS400 Bluetooth ESC/POS printer

---

### Try it !
<li>터미널에서 디렉토리를 열고 아래의 명령어를 통해 다운로드 해주세요.</li>
<code>git clone https://github.com/SIKU-KR/HolyBean.git</code>
<li>안드로이드 스튜디오에서 Build하여 바로 실행이 가능합니다!</li>

---

### 사진
![img1](./docs/imgsrc/IMG_4935.jpg) <br/>
업데이트 중입니다....

---

### 기능

- 주문받기
- 주문에 대한 영수증 출력
- 오늘(현재) 주문 목록 및 상세 조회
- 사용자가 지정한 기간에 대한 매출 현황 조회
- 매출 현황이 적힌 영수증 출력
- *메뉴 관리 시스템 (WEB 개발예정)* **(TODO, 2024 Summer)**
- *JAVA SPRING을 이용한 DATABASE의 서버화* **(TODO, 2024 Summer)**

---

### 기술 목록

- Kotlin
- SQLite
- [@Dantsu/ESCPOS-ThermalPrinter-Android](https://github.com/DantSu/ESCPOS-ThermalPrinter-Android)

---

### Updates

| version | details                                                                                                                                                                            |
|---------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 1.0.0   | 베타 테스팅 시작 (24.01.28)                                                                                                                                                 |
| 1.1.0   | 기능 추가 (24.02.02) <br> 1. 외상 구매에 관한 처리기능 <br> 2. 판매 리포트 조회 및 출력 <br> 3. 주문 옵션 추가(일회용컵/머그컵 옵션) <br> 4. 안드로이드 전체화면 적용                 |
| 1.2.0   | 기능 추가 (24.02.21) <br> 1. 주문 내역 삭제 기능                                                                                                       |
| 1.2.1   | 마이너 업데이트 (24.02.24) <br> 1. 매출보고서 영수증 출력 로직 수정 <br> 2. 판매 리포트 창 수정                                                                                  |
| 1.2.2   | 마이너 업데이트 (24.03.02) <br> 1. 메뉴 목록을 .csv 저장형식으로 변경하여 편리한 수정방법 적용 <br> 2. 쿠폰과 할인 옵션에 대한 탭 추가 (1.3.0 에서 삭제)                                    |
| 1.3.0   | 기능 추가 (24.03.13) <br> 1. 주문 시 복수 결제옵션 선택 기능 추가 <br> 2. 데이터 베이스 기능 수정 <br> 3. 쿠폰 추가 버튼 적용 <br> 4. menu.csv에 진열 순서에 대한 정보를 추가하고 메뉴 정렬옵션 추가 | 
| 1.3.1 | 데이터베이스 관련 로직 최적화 (24.05.01) <br> SQL query 수정으로 프로그램 최적화 <br> 커밋 참고 https://github.com/SIKU-KR/HolyBean/commit/648ff257b84a0ef59f777bda8fed06ecebdd4065 |
| 1.3.2 | 기능 추가 (24.06.07) <br> 1. APP 실행 시 전체화면 모드 (몰입 모드) 실행 |
| 1.3.3 | 버그 수정 (24.07.16) <br> 1. menu.csv 수정 후 특정 메뉴 추가 안됨. <br> 사유 : 정렬되지 않은 데이터에 대한 binary search 사용 |

---

### Bug fix

---

### Contacts
Email : peter012677@konkuk.ac.kr
