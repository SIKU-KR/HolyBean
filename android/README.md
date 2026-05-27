# HolyBean Android

## 로컬 개발 (debug 빌드)

debug 빌드는 외부 의존성을 실제로 호출하지 않도록 자동 분기됩니다 (`BuildConfig.DEBUG`).

| 의존성 | release | debug |
|--------|---------|-------|
| Firestore / Auth | 운영 Firebase | 로컬 에뮬레이터 (`10.0.2.2:8080` / `:9099`) |
| 프린터 (Pi 서버) | 실제 기기 호출 | no-op (`FakePrintServerApi`, Logcat 출력만) |

`10.0.2.2`는 안드로이드 에뮬레이터에서 호스트(맥)의 `localhost`를 가리키는 주소입니다.

### 1. Firebase 에뮬레이터 띄우기

debug 빌드를 실행하기 **전에** 한 번 띄워두면 됩니다 (작업 세션 동안 계속 사용).
Firestore 에뮬레이터는 Java 21이 필요합니다.

```bash
cd ../firebase
PATH="/opt/homebrew/opt/openjdk@21/bin:$PATH" firebase emulators:start
```

- 에뮬레이터 UI: http://localhost:4000 — 저장된 주문/매출 데이터를 눈으로 확인
- 데이터는 에뮬레이터 종료 시 사라집니다 (운영 데이터는 건드리지 않음)

> 에뮬레이터를 띄우지 않고 debug 앱을 실행하면 Firestore 쓰기가 **연결 실패**합니다
> (운영으로 가지 않고 실패). 작업 전 이 단계를 잊지 마세요.

### 2. Android Studio에서 debug 실행

Run/Debug 버튼으로 실행하면 코드가 알아서 에뮬레이터 / no-op 프린터에 연결됩니다.

### 3. 동작 확인

- **Firestore 저장**: 에뮬레이터 UI(`localhost:4000`)에서 쓰기 반영 확인
- **프린터**: Logcat에서 `FakePrintServerApi` 태그로 출력 명령 확인 (실제 기기 호출 없음)

### release 빌드

분기는 전부 `BuildConfig.DEBUG` 안에 있으므로 release 빌드 동작에는 영향이 없습니다.
