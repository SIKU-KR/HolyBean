# HolyBean 프린터 시스템 개선 계획

## 1. 현황 분석 및 문제점

### 1.1. 현재 구조
- `Printer` 추상 클래스를 `HomePrinter`, `OrdersPrinter` 등 다양한 구현 클래스가 상속받는 구조.
- `Printer` 클래스의 `init` 블록에서 `BluetoothPrintersConnections.selectFirstPaired()`를 호출하여 프린터 객체(`EscPosPrinter`)를 생성함.

### 1.2. 핵심 문제점
- **비효율적인 연결 반복**: 영수증 출력 등 프린터 기능이 필요할 때마다 새로운 `...Printer` 객체가 생성됨. 이 과정에서 부모 클래스의 `init` 블록이 매번 실행되어, 불필요한 블루투스 장치 검색 및 연결 시도가 반복적으로 발생함.
- **성능 및 안정성 저하**: 반복적인 연결 시도는 앱의 반응 속도를 저하시키고, 간헐적인 연결 실패의 원인이 되어 시스템 전체의 안정성을 떨어뜨림.

---

## 2. 1단계: 효율성 개선 (연결 로직 중앙화)

### 2.1. 목표
- `EscPosPrinter` 객체를 앱 전역에서 단 한 번만 생성하여 공유(싱글톤)하도록 구조를 변경하여, 불필요한 연결 오버헤드를 제거한다.

### 2.2. 실행 방안
1.  **Hilt 모듈 생성**: `@Singleton` 스코프로 `EscPosPrinter` 인스턴스를 제공하는 Hilt 모듈을 작성한다.
2.  **`PrinterManager` 도입**: Hilt로부터 `EscPosPrinter` 객체를 주입받는 싱글톤 `PrinterManager` 클래스를 생성한다. 이 클래스는 실제 프린터 제어(인쇄, 연결 해제)를 담당한다.
3.  **기존 `Printer` 클래스 리팩토링**:
    - `HomePrinter`, `OrdersPrinter` 등은 더 이상 `Printer`를 상속받지 않는다.
    - 이들의 역할은 순수하게 **출력할 문자열을 포맷팅**하는 유틸리티 객체 또는 함수로 변경한다.

---

## 3. 2단계: 안정성 및 오류 처리 강화

### 3.1. 목표
- 실제 매장 환경에서 발생할 수 있는 예기치 않은 연결 끊어짐에 능동적으로 대처하고, 현재 프린터 상태를 사용자에게 명확히 피드백한다.

### 3.2. 실행 방안
1.  **연결 상태 관리 도입**:
    - `PrinterManager` 내부에 `PrinterState` Enum 클래스(`CONNECTED`, `DISCONNECTED`, `CONNECTING`, `ERROR`)를 정의한다.
    - `StateFlow`를 사용하여 현재 프린터 상태를 외부에 노출시킨다. ViewModel/UI는 이 상태를 구독하여 실시간으로 UI(아이콘 색상, 텍스트 등)를 변경한다.
2.  **자동 재연결 로직 구현**:
    - `print()` 메소드 실행 시, 먼저 현재 연결 상태를 확인한다.
    - 만약 `DISCONNECTED` 상태라면, 자동으로 재연결을 시도한 후 인쇄를 진행한다.
3.  **강력한 예외 처리**:
    - 실제 인쇄 명령어(`printFormattedTextAndCut`)를 `try-catch` 블록으로 감싼다.
    - `IOException` 등 연결 관련 예외 발생 시, `StateFlow`의 상태를 `DISCONNECTED`로 변경하고, 인쇄 실패를 호출부에 반환하여 UI가 후속 처리(실패 알림 등)를 할 수 있도록 한다.
