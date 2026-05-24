# 인쇄 오류 피드백 + 저비용 Android 표준 정합 — 설계

작성일: 2026-05-24 · 브랜치: v3

## 배경

v3에서 Compose 전면 재작성 + 접근성/시각 리프레시 + 간격/레이아웃 일관화까지
완료되어 **시각·접근성 레이어는 업계표준에 도달**했다. 남은 격차는 거의 전부
**운영 상태를 사용자에게 정직하게 보여주는 피드백**에 몰려 있다.

UI/UX 점검에서 드러난 항목 중, "물리적 단서가 없는 무음 실패"와 "명백한 표준
위반"만 추려 이번 스펙으로 다룬다. 오프라인/연결 인디케이터(A3)와 loading/empty/
error 3-상태(A4)는 가치는 있으나 범위가 크고 `FirestoreRepository` read 리팩터가
필요해 **별도 스펙으로 분리**한다.

### 핵심 발견 — Pi는 이미 구조화된 최종 실패 이유를 준다

Pi 프린트 서버(`pi/`)의 `JobSubmitter::submit()`은 재시도(3회)를 **모두 소진한 뒤**
HTTP 응답을 보낸다(`pi/src/queue.rs:48`, `pi/src/http.rs:49`). 따라서 Android의
`PiPrintClient.print()` POST가 반환되는 시점에 **최종적이고 구조화된 실패 이유**가
이미 손에 있다:

| 신호 | Pi 내부 의미 | 필요한 조치 |
|---|---|---|
| 연결 거부 / 타임아웃 (IOException) | Pi 박스 다운 / 네트워크 단절 | Pi 전원·네트워크 확인 |
| HTTP 503 `Unavailable` | `/dev/usb/lp0` 미존재 (`is_ready()`=false) | 프린터 전원·USB 확인 |
| HTTP 500 `Write` | 장치 open 됐으나 write/flush 실패 | 용지·덮개·상태 확인 |

현재 Android는 이 정보를 **전부 폐기**한다: `PiPrintClient`는 코드를 문자열에 묻은
일반 예외로 던지고(`PiPrintClient.kt:42`), `HomeViewModel.printReceipt`는
`runCatching{}.onFailure{ printStackTrace() }`로 완전히 삼킨다
(`HomeViewModel.kt:200-206`). 즉 친절한 오류 메시지는 **신규 인프라 0** — 버리던
데이터를 매핑해 표시만 하면 된다.

## 목표

1. 영수증 인쇄가 실패하면 캐셔에게 **원인별 조치 안내**를 띄우고 **재출력**을 제공한다.
2. 결제 처리 중임을 버튼에 시각적으로 표시해 "눌렸나?" 불확실성과 더블탭을 줄인다.
3. 무음 실패 경로를 Crashlytics non-fatal로 일관 기록해 개발자 원격 진단을 보장한다.
4. 이모지 아이콘(⚙ ✕)을 Material 벡터 아이콘으로 교체해 표준에 맞춘다.

## 범위 밖 (Non-goals)

- 오프라인/연결 상태 상시 인디케이터 (A3) — 별도 스펙.
- 주문기록·리포트의 loading/empty/error 3-상태 + `FirestoreRepository` read 리팩터 (A4) — 별도 스펙.
- 푸시 알림(FCM/Telegram/Slack) — Tier 1 인앱 + Crashlytics로 충분하다고 결론.
- 장바구니 항목 삭제 undo — YAGNI (재추가 1탭).
- 물리적 프린터 상태(용지걸림 등)의 정밀 진단 — Pi `is_ready()`는 경로 존재만 보므로
  503/500↔원인 매핑은 근사치다. 메시지는 단정형이 아닌 **제안형**으로 쓴다.

---

## 파트 P — 인쇄 오류 피드백 (핵심)

### P1. `PiPrintClient` → 구조화 실패 이유

`PiPrintClient.print()`이 일반 `PrintServerException` 대신 원인을 담은 타입을 던진다.

```kotlin
enum class PrintFailureReason { ServerUnreachable, PrinterOffline, PrinterError, Unknown }
class PrintServerException(
    val reason: PrintFailureReason,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
```

매핑(재시도 소진 후 최종 판정):
- `IOException`(연결 거부/타임아웃) → `ServerUnreachable`
- HTTP 503 → `PrinterOffline`
- HTTP 500 → `PrinterError`
- 그 외 비2xx/예외 → `Unknown`

`response.code()`로 분기하고, IOException은 `withRetry`가 마지막에 던진 예외 타입으로
식별한다. 클라이언트 측 재시도(3회)는 그대로 둔다.

**파일:** `printer/PiPrintClient.kt`, `printer/network/PrintServerException.kt`

### P2. *(선택)* Pi 에러 JSON에 머신 코드 명시

HTTP 상태코드 스니핑 대신 계약을 명시화한다. `pi/src/http.rs`의 `/print` 에러 응답에
`code` 필드를 추가한다(~10줄, 기존 동작 불변).

```jsonc
// 503
{ "code": "device_unavailable", "error": "<raw detail>" }
// 500
{ "code": "write_failed", "error": "<raw detail>" }
```

Android는 우선 `code`를 읽고, 없으면 HTTP 상태코드로 폴백한다. Pi를 건드리지 않아도
P1은 상태코드만으로 동작하므로 이 항목은 독립적이며 선택이다.

**파일:** `pi/src/http.rs` (+ 기존 http 테스트의 본문 단언 보강)

### P3. `HomeViewModel` — 삼킴 제거 + 상태/재출력 데이터

```kotlin
data class PrintFailure(val orderNum: Int, val reason: PrintFailureReason)
// UiState 에 추가:
val printFailure: PrintFailure? = null
// 재출력용:
private var lastOrder: Pair<Order, String>? = null   // (order, takeOption)
```

- `onOrderConfirmed`에서 인쇄 직전 `lastOrder = data to takeOption` 보관하고, 이전 주문의
  잔여 `printFailure`를 `null`로 리셋(새 주문 시작 시 stale 스낵바 제거).
- `printReceipt`의 `applicationScope` 블록에서 `catch (e: PrintServerException)` →
  메인으로 전환해 `_uiState.update { it.copy(printFailure = PrintFailure(data.orderNum, e.reason)) }`.
  고객용/POS용 2장 중 어느 쪽이 먼저 실패하든 **주문당 1개로 코얼레스**(이미 실패 세팅돼
  있으면 덮어쓰지 않음). 동시에 Crashlytics 기록(파트 B).
- `fun reprintLastOrder()`: `lastOrder`로 `printReceipt` 재실행, 성공 시 `printFailure=null`.
- `fun dismissPrintFailure()`: `printFailure=null`.

인쇄는 지금처럼 `applicationScope`에서 실행해 **홈 복귀를 막지 않는다**. 실패만 surface 한다.

**파일:** `ui/home/HomeViewModel.kt`

### P4. 홈 Snackbar 표시 (Material 표준)

`HomeRoute`를 `Scaffold`로 감싸고 `SnackbarHost`를 단다(현재 Scaffold 없음).

```kotlin
val snackbarHostState = remember { SnackbarHostState() }
LaunchedEffect(state.printFailure) {
    val f = state.printFailure ?: return@LaunchedEffect
    val result = snackbarHostState.showSnackbar(
        message = printFailureMessage(f),      // 주문번호 + 제안형 안내
        actionLabel = "재출력",
        duration = SnackbarDuration.Indefinite, // 액션/닫기 전까지 유지
        withDismissAction = true,
    )
    when (result) {
        SnackbarResult.ActionPerformed -> sharedViewModel.reprintLastOrder()
        SnackbarResult.Dismissed -> sharedViewModel.dismissPrintFailure()
    }
}
```

원인별 제안형 메시지(`printFailureMessage`):
- `ServerUnreachable` → "{n}번 영수증 출력 실패 — 프린터 서버에 연결되지 않았어요. Pi 전원·네트워크 확인 후 재출력하세요."
- `PrinterOffline` → "{n}번 영수증 출력 실패 — 프린터가 응답하지 않아요. 전원·USB 연결 확인 후 재출력하세요."
- `PrinterError` → "{n}번 영수증 출력 실패 — 출력에 실패했어요. 용지·덮개 상태 확인 후 재출력하세요."
- `Unknown` → "{n}번 영수증 출력 실패 — 다시 출력해 주세요."

`HomeViewModel`이 OrderFlow 그래프 스코프라 인쇄가 끝날 때 Home은 이미 복귀해 있어
Snackbar가 정상 노출된다.

**파일:** `ui/home/HomeScreen.kt`(`HomeRoute`)

---

## 파트 A1 — 결제 처리 로딩 상태

현재 private `orderInFlight`(`HomeViewModel.kt:69`)를 `UiState.isSubmitting`으로 승격한다.

- `onOrderConfirmed` 진입 시 코루틴 런치 **전에** 동기적으로 `isSubmitting=true`(메인 스레드
  단독 접근이라 안전), `finally`에서 `false`.
- `PaymentScreen`에 `isSubmitting`을 전달. "결제 완료" 버튼은 `enabled=!isSubmitting`,
  내용은 `isSubmitting`이면 `CircularProgressIndicator` + "처리 중…", 아니면 "결제 완료".
- 제출 중 폼 위에 가벼운 스크림 + 취소 버튼 비활성으로 중복 탭/이탈 차단.
- 실패(catch) 시 `isSubmitting=false` + 기존 Toast 유지(화면 잔류).

**파일:** `ui/home/HomeViewModel.kt`, `ui/payment/PaymentScreen.kt`(`PaymentRoute`+`PaymentScreen`)

> 비고: 정상 네트워크에선 write가 1초 미만이라 스피너가 짧게 보인다. 가치는 주로
> 느린/멈춘 네트워크 케이스의 피드백이며 비용이 낮아 포함한다.

---

## 파트 B — Crashlytics non-fatal 보강

신규 채널 없음. 무음 실패 경로를 일관되게 기록한다.

- 인쇄 실패(P3): `FirebaseCrashlytics.recordException` + 커스텀키 `orderNum`,
  `print_reason`(enum 이름).
- 주문 저장 실패: 기존 `catch`(`HomeViewModel.kt:191`)에 `recordException` 추가.
- 그 외 이미 기록 중인 `FirestoreRepository` 삼킨 read 에러는 현행 유지(누락 시 보강).

**파일:** `ui/home/HomeViewModel.kt` (+ 기록 누락 지점)

---

## 파트 C1 — 이모지 → Material 벡터 아이콘

이모지는 OEM별 렌더 편차·tint 불가·스크린리더 시맨틱 부재로 표준 위반이다.
`Icons.Filled.Settings`/`Icons.Filled.Close`는 `material-icons-core`에 포함되어
추가 의존성이 필요 없다.

- **"⚙ 설정" 타일:** `MenuTile`에 `icon: ImageVector? = null` 슬롯 추가, 라벨 위에
  렌더. Home은 `icon = Icons.Filled.Settings`, `name = "설정"`으로 호출. 아이콘은
  라벨이 함께 있으므로 장식용(`contentDescription = null`).
- **"✕ 취소"(Payment), "✕ 닫기"(Orders):** `OutlinedButton` 내부를
  `Icon(Icons.Filled.Close, contentDescription = null)` + 텍스트("취소"/"닫기")로 교체.

**파일:** `ui/components/MenuTile.kt`, `ui/home/HomeScreen.kt`,
`ui/payment/PaymentScreen.kt`, `ui/orders/OrdersScreen.kt`

---

## 테스트

- **P1:** `PiPrintClient` 단위 테스트 — MockWebServer/가짜 `PrintServerApi`로 503→
  `PrinterOffline`, 500→`PrinterError`, IOException→`ServerUnreachable` 매핑 검증.
- **P2:** Rust `http.rs` 테스트 — 503/500 응답 본문의 `code` 값 단언.
- **P3:** `HomeViewModelTest` — 인쇄 실패 시 `printFailure` 세팅, 2장 중 1개 코얼레스,
  `reprintLastOrder`/`dismissPrintFailure` 동작. (`PiPrintClient`는 페이크 주입)
- **A1:** `HomeViewModelTest` — `onOrderConfirmed` 동안 `isSubmitting` true→false 전이,
  실패 시 false 복귀.
- **C1/P4 (UI):** 기존 androidTest 흐름 유지. Snackbar 노출은 에뮬레이터에서 육안 확인
  (자동 테스트는 best-effort).
- 회귀: `:app:testDebugUnitTest` + `:app:assembleDebug` green, `cargo test`(pi) green.

## 구현 순서 (단계적, 각 단계 독립 커밋)

1. **P1** — `PiPrintClient` 구조화 실패 이유 + 테스트.
2. **P3 + P4** — VM 상태/재출력 + 홈 Snackbar. (P1 위에 쌓임)
3. **A1** — 결제 로딩 버튼.
4. **B** — Crashlytics 보강.
5. **C1** — 벡터 아이콘 교체.
6. **P2** *(선택)* — Pi 에러 코드 명시. (1과 독립이라 언제든 가능)

빌드 환경 주의사항은 메모리 `build-environment-quirks` 참고
(`JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew ...`, pi는 `cargo test`).
