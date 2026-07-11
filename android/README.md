# HolyBean Android

## 로컬 개발 (debug 빌드)

debug 빌드는 외부 의존성을 실제로 호출하지 않도록 자동 분기됩니다 (`BuildConfig.DEBUG`).

| 의존성 | release | debug |
|--------|---------|-------|
| Firestore / Auth | 운영 Firebase | 로컬 에뮬레이터 (`10.0.2.2:8080` / `:9099`) |
| 프린터 | USB-C 직연결 프린터로 출력 | USB 장치 없으면 프린터 단계 실패(주문은 그대로 진행 가능) |

`10.0.2.2`는 안드로이드 에뮬레이터에서 호스트(맥)의 `localhost`를 가리키는 주소입니다.

### 1. 에뮬레이터 띄우고 메뉴 시드 (한 번에)

debug 빌드를 실행하기 **전에** 아래 한 줄이면 됩니다. 에뮬레이터를 띄우고,
준비되면 운영 메뉴 스냅샷을 자동 시드한 뒤 그대로 떠 있습니다.

```bash
cd ../firebase
./dev-emulator.sh
```

- 에뮬레이터 UI: http://localhost:4000 — 저장된 주문/매출 데이터를 눈으로 확인
- 종료: `Ctrl+C` (에뮬레이터도 함께 정리됩니다)
- 데이터는 종료 시 사라지므로, 켤 때마다 이 스크립트를 다시 실행하면 메뉴가 다시 채워집니다 (운영 데이터는 건드리지 않음)

> 에뮬레이터를 띄우지 않고 debug 앱을 실행하면 Firestore 쓰기가 **연결 실패**합니다
> (운영으로 가지 않고 실패). 작업 전 이 단계를 잊지 마세요.

**메뉴 시드 데이터 출처/갱신**
- 시드 데이터: `firebase/seed/menu-current.json` (운영 `menu/current` 스냅샷)
- 시드만 따로 다시 하려면 (에뮬레이터가 떠 있는 상태에서): `./seed-emulator.sh`
- 최신 운영 메뉴로 스냅샷을 갱신하려면:
  ```bash
  TOKEN=$(gcloud auth application-default print-access-token)
  curl -s -H "Authorization: Bearer $TOKEN" \
    "https://firestore.googleapis.com/v1/projects/holybean-e4201/databases/(default)/documents/menu/current" \
    | jq '{fields: .fields}' > seed/menu-current.json
  ```

### 2. Android Studio에서 debug 실행

Run/Debug 버튼으로 실행하면 코드가 알아서 에뮬레이터에 연결됩니다. 프린터는 USB-C로
연결된 실제 기기가 있을 때만 출력하며, 없으면 프린터 단계만 실패하고 주문은 진행됩니다.

### 3. 동작 확인

- **Firestore 저장**: 에뮬레이터 UI(`localhost:4000`)에서 쓰기 반영 확인
- **프린터**: USB-C로 프린터를 연결하면 실제 출력, 미연결 시 스플래시에서 "프린터 실패"로 표시(주문은 가능)

### release 빌드

분기는 전부 `BuildConfig.DEBUG` 안에 있으므로 release 빌드 동작에는 영향이 없습니다.
