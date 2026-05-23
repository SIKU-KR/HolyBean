# 앱 시작 step: 스플래시 + 사전 로딩 + 헬스 체크

작성일: 2026-05-24
브랜치: v3

## 배경

현재 앱은 `MainActivity` → `HolyBeanNavHost` → 곧바로 `Home`(주문 화면)으로 진입한다.
스플래시/로딩 화면이 없고, 메뉴 목록과 주문번호는 `HomeViewModel.init`에서 로딩한다.
인터넷 연결 상태를 명시적으로 확인하는 코드는 없으며(Firestore SDK가 캐시/네트워크를
자동 관리), 프린트 서버 헬스 체크(`PiPrintClient.checkHealth()`)는 DevTools에서만 호출된다.

카페 운영 환경에서 앱 시작 시 (1) 핵심 데이터를 미리 로딩하고 (2) 인터넷 및 프린트 서버
상태를 확인해, 문제가 있으면 사용자가 인지하도록 하는 시작 step을 추가한다.

## 핵심 결정

- **진입 정책**: non-blocking. 체크 실패해도 영업이 멈추지 않도록 진입을 허용한다.
- **UI**: 전용 스플래시/로딩 화면. 항목별 진행 상태를 보여준다.
- **인터넷 체크**: 별도 네트워크 체크 없이, Firestore 데이터 로딩 성공을
  곧 인터넷+백엔드 연결 OK로 간주한다.
- **로딩 범위**: 메뉴 + 주문번호만. 리포트/미수금 등은 각 화면 진입 시 로딩한다.
- **실패 시 UX**: 자동 재시도 없음. 수동 `다시 시도` / `그대로 진입` 버튼만 제공한다.
- **데이터 공유 방식**: `MenuRepository` 인메모리 캐시. 스플래시가 채우고
  `HomeViewModel`이 캐시 우선 사용 → 이중 페치 제거.

## 아키텍처

NavHost의 start destination을 새 `SplashDest`로 변경한다. 스플래시가 사전 로딩과
헬스 체크를 수행한 뒤 `Home`으로 이동하며, `popUpTo(SplashDest) { inclusive = true }`로
백스택에서 스플래시를 제거한다(뒤로가기로 스플래시에 돌아오지 않도록).

기존 `MainActivity` → `HolyBeanNavHost` 구조는 그대로 두고 진입점만 한 단계 앞에 끼운다.

## 컴포넌트

### SplashScreen (Compose)
- 항목별 진행 상태(데이터 / 프린터)를 리스트로 표시한다.
  - 각 항목 상태: 진행 중 / 성공 / 실패 (DevToolsScreen의 색상 표시 패턴 참고).
- 실패 또는 경고 상태일 때 `다시 시도` / `그대로 진입` 버튼을 노출한다.
- `StartupViewModel`의 `StateFlow`를 구독하고, 진입 트리거 시 `onNavigateToHome` 콜백 호출.

### StartupViewModel (@HiltViewModel)
- 주입: `MenuRepository`, `FirestoreRepository`, `PiPrintClient`,
  `@Named("IO")` dispatcher.
- 데이터 로딩과 프린터 헬스 체크를 **병렬**로 실행(`async`/`awaitAll` 또는 독립 launch).
- 상태를 `StateFlow<StartupUiState>`로 노출한다.
- `retry()`: 실패한 작업을 다시 실행한다.
- 모두 성공 시 자동 진입 신호(예: `SharedFlow<Unit>` 또는 상태 플래그)를 발행한다.

```kotlin
data class StartupUiState(
    val data: StepStatus = StepStatus.Loading,    // 메뉴 + 주문번호
    val printer: StepStatus = StepStatus.Loading, // Pi 헬스 체크
)

enum class StepStatus { Loading, Success, Failed }
```

### MenuRepository 캐시 추가
- 인메모리 캐시: `@Volatile private var cachedMenu: List<MenuItem>? = null`.
- `getMenuListSync()` 성공 시 `cachedMenu`에 저장한다.
- 캐시 조회 API 추가(예: `fun getCachedMenu(): List<MenuItem>?`).
- 실시간 구독(`getMenuList()` Flow) 경로는 변경하지 않는다.

### PiPrintClient.checkHealth()
- 이미 존재(`PiPrintClient.kt:48-51`). 그대로 재사용한다.

## 데이터 흐름

스플래시 진입 → 두 작업을 병렬로 시작한다.

1. **데이터**: `getMenuListSync()`(→ `cachedMenu` 저장) + `getOrderNumber()`.
   둘 다 성공 = 인터넷+백엔드 OK → `data = Success`.
   하나라도 실패 → `data = Failed`.
2. **프린터**: `checkHealth()` → boolean → `printer = Success/Failed`.

판정 후 동작:

| 데이터 | 프린터 | 동작 |
|--------|--------|------|
| 성공 | 성공 | 잠깐 노출 후 **자동으로 Home 진입** |
| 성공 | 실패 | 프린터 경고 표시, `그대로 진입` 버튼 노출 (자동 진입 안 함) |
| 실패 | (무관) | `다시 시도` / `그대로 진입`(캐시 메뉴 사용) 버튼 |

Home 진입 후 `HomeViewModel`은 `getCachedMenu()`가 있으면 즉시 사용(이중 페치 제거),
없으면 기존대로 `getMenuListSync()` 호출. 주문번호는 휘발성이라
`HomeViewModel.init`의 `refreshOrderNumber()`(단일 read)를 유지해 최신값을 보장한다.

## 에러 처리

- 데이터 로딩은 `runCatching`으로 감싸 실패 시 상태에 반영한다.
  캐시가 있으면 `그대로 진입` 시 캐시 메뉴로 폴백한다.
- 프린터 실패는 경고일 뿐 진입을 막지 않는다.
- 자동 재시도는 하지 않는다(수동 `다시 시도`만).

## 테스트

`StartupViewModel` 단위 테스트(fake repository/client 사용):
- (a) 데이터·프린터 둘 다 성공 → 자동 진입 신호 발행.
- (b) 프린터만 실패 → `printer = Failed`, 자동 진입 신호 없음.
- (c) 데이터 실패 → `data = Failed`, 자동 진입 신호 없음.
- (d) `retry()` 호출 시 실패 작업 재실행 및 상태 갱신.

`MenuRepository` 캐시 동작 테스트:
- `getMenuListSync()` 성공 후 `getCachedMenu()`가 동일 목록을 반환하는지 확인.

## 범위 밖 (YAGNI)

- 자동 재시도/백오프.
- ConnectivityManager 기반 별도 네트워크 체크.
- 메뉴/주문번호 외 데이터(리포트, 미수금, 주문목록)의 사전 로딩.
- Android 12 SplashScreen API 연동(전용 Compose 화면으로 충분).
