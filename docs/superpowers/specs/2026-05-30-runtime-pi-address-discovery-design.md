# 런타임 Pi 인쇄서버 주소 해석 (mDNS 하이브리드)

- **날짜:** 2026-05-30
- **브랜치:** v3
- **상태:** 설계 승인 대기 → 구현 계획 작성 예정

## 1. 배경 / 문제

현재 Android 앱은 Pi 인쇄서버 주소를 빌드 시점에 하드코딩한다.

```
android/app/build.gradle.kts:30
buildConfigField("String", "PRINT_SERVER_URL", "\"http://192.168.4.1:9100/\"")
```

이 값은 `PrintNetworkModule`에서 `@Singleton` Retrofit의 baseUrl로 **생성 시점에 고정**된다(`PrintNetworkModule.kt:40`). 그 결과 Pi가 자체 Wi-Fi 핫스팟(`192.168.4.1`)을 띄우고 태블릿이 거기 접속하는 토폴로지에만 동작한다.

원래 PRD(`v3_pi_prd.md`)는 "핫스팟 + Pi NAT 라우팅"으로 태블릿이 AWS에 도달하게 했다(FR-6). 그러나 **Firestore 마이그레이션이 완료되어 태블릿은 더 이상 AWS를 Pi NAT 경유로 타지 않는다.** 따라서 핫스팟+NAT 구조의 유일한 명분이 사라졌다.

**목표:** 앱이 Pi 인쇄서버 주소를 런타임에 해석하도록 바꿔, 핫스팟이든 일반 공유기든 네트워크 토폴로지에 무관하게 동작하게 한다. 동시에 Pi를 "공유기에 이더넷으로 물린 순수 인쇄서버"로 단순화한다.

## 2. 채택 결정 (브레인스토밍 결과)

| 결정 | 선택 | 근거 |
| --- | --- | --- |
| 주소 해석 방식 | **하이브리드: mDNS 자동탐색 + 수동 override** | IP 변동에 자동 복구 + 탐색 실패 시 항상 탈출구 |
| Pi 구성 범위 | **핫스팟+NAT 완전 은퇴, 순수 인쇄서버화** | Firestore 이후 NAT 명분 소멸, 유선이 더 안정적 |
| 해석/캐시 전략 | **머지드 캐시(마지막 성공 IP 영속화) + 실패 시 재탐색** | 평상시 인쇄 지연 0, IP 변동 시 자동 복구 |
| Retrofit 동적 주소 방식 | **방식 A: OkHttp 인터셉터 + 주소 리졸버** | 주소 관심사 격리, 기존 인쇄 파이프라인 무변경, 회귀 위험 최소 |

해석 우선순위: **수동 override > 영속화된 마지막 성공 IP > mDNS 재탐색**.

## 3. 토폴로지 / Pi 측

```
[공유기] ─이더넷→ [Pi : DHCP IP, :9100 인쇄서버 + avahi(_holybean-print._tcp 광고)]
   ├─Wi-Fi→ [태블릿]  ← 같은 서브넷, mDNS로 Pi 자동 발견
   └────→ 인터넷(Firestore)  ← 공유기가 태블릿/Pi 양쪽에 직접 제공
```

- Pi는 **유선 업링크 + `:9100` 인쇄서버 + avahi mDNS 광고**만 담당.
- `hostapd` / `dnsmasq` / `iptables NAT` 프로비저닝 **전부 제거**.
- 서버 바인딩은 현행 유지(`pi/src/config.rs`: 기본 `0.0.0.0:9100`, `HOLYBEAN_PRINT_BIND`로 변경 가능).
- Pi IP는 DHCP여도 무방(mDNS가 IP 변동을 흡수). 운영 안전망으로 공유기 DHCP 예약을 **문서로** 권고(코드 의존 아님).
- repo에 Pi 프로비저닝 자산이 없으므로 다음을 신규 추가:
  - `pi/deploy/holybean-print.service`(또는 동등) — avahi 서비스 정의(XML): 서비스 타입 `_holybean-print._tcp`, 포트 `9100`.
  - 셋업 문서 — 이더넷 연결, avahi-daemon 활성화, 핫스팟/NAT 제거 안내.

## 4. Android 컴포넌트 (방식 A)

### 4.1 `PrinterAddress`
호스트와 포트를 담는 값 객체.
```
data class PrinterAddress(val host: String, val port: Int)
```

### 4.2 `MdnsDiscovery` (인터페이스 + Android 구현)
- `NsdManager` 래퍼. `suspend fun discover(timeoutMs: Long): PrinterAddress?`.
- 서비스 타입 `_holybean-print._tcp` 탐색 → 첫 매칭 서비스 resolve → `host:port` 반환. 타임아웃/미발견 시 `null`.
- 탐색 동안 `WifiManager.MulticastLock` 획득·해제(일부 기기에서 mDNS 멀티캐스트 수신에 필요).
- **인터페이스로 추상화**해 테스트에서 fake 주입.

### 4.3 `PrinterAddressResolver` (@Singleton, DataStore 백킹)
영속 상태: `manual_override`(nullable, `"host"` 또는 `"host:port"`), `last_good`(nullable, `"host:port"`).

- `current(): PrinterAddress?` — **인메모리 `@Volatile` 캐시**를 동기 반환(suspend 없음). 인터셉터가 매 요청에서 블로킹 없이 읽기 위함. 초기값은 init 시 DataStore에서 로드.
- `suspend fun rediscover(): PrinterAddress?` — override가 있으면 그대로 반환(탐색 생략). 없으면 `MdnsDiscovery.discover()` 실행 → 성공 시 `last_good` 영속화 + 인메모리 캐시 갱신 후 반환, 실패 시 기존 stale 값 유지.
- `suspend fun setManualOverride(value: String?)` — 영속화 + 캐시 갱신. `null`이면 override 해제.
- `val status: StateFlow<PrinterStatus>` — `Unknown` / `Resolving` / `Connected(PrinterAddress)` / `Unreachable`. UI 표시용.

기본 포트: 수동 입력이 호스트만일 경우 `9100`.

### 4.4 `PrinterHostInterceptor` (OkHttp Interceptor)
- 매 요청에서 `resolver.current()` 조회.
- 값이 있으면 요청 URL의 scheme/host/port를 해당 주소로 치환(상대경로 `print`/`health`는 그대로 유지).
- 값이 `null`이면 즉시 `IOException("printer address unknown")` → `PiPrintClient`에서 `ServerUnreachable`로 매핑.

### 4.5 `PrintNetworkModule` 변경
- Retrofit baseUrl을 더미 `http://holybean.invalid/`로(인터셉터가 실제 host 주입).
- `@Named("PrintServer")` OkHttp에 `PrinterHostInterceptor` 추가.
- **DEBUG 빌드는 `FakePrintServerApi` 유지**(Retrofit/인터셉터 미경유 — 기존 동작 보존).
- `BuildConfig.PRINT_SERVER_URL` 하드코딩 라인 제거(`build.gradle.kts:30`).

### 4.6 `PiPrintClient` 변경
- 기존 `Mutex` 직렬화 + `BackoffRetry`(maxAttempts=3) 구조 **유지**.
- `ServerUnreachable`(IOException) 첫 발생 시 재시도 전에 `resolver.rediscover()`를 **1회** 호출 → IP가 바뀌어도 다음 시도에서 새 주소로 복구.
- 최종 실패 시 기존 `PrintServerException(reason, ...)` 그대로 던져 UI 피드백 경로 보존.

## 5. UX

- **설정(전체 메뉴)에 "프린터 연결" 섹션 신설:**
  - 현재 상태 표시: `연결됨 host:port` / `탐색 중…` / `연결 안 됨`.
  - **[다시 탐색]** 버튼 → `resolver.rediscover()`.
  - **수동 주소 입력** 필드 + 저장/해제 → `resolver.setManualOverride()`.
  - 기존 `DevToolsViewModel`(이미 `printerUrl` 필드 보유)을 확장하거나 인접 ViewModel로 분리.
- **`StartupViewModel` 비차단 워밍업:** 앱 시작 시 `resolver.rediscover()`를 백그라운드로 1회 트리거해 첫 인쇄 지연을 0에 수렴. **시작 흐름을 블로킹하지 않음.**

## 6. 데이터 흐름 (인쇄 1건)

```
PiPrintClient.print(commands)
  → Mutex.withLock
    → api.print()  (인터셉터가 resolver.current() 주소로 전송)
       ├─ 성공 → 종료
       └─ ServerUnreachable(IOException)
            → resolver.rediscover()  (override>last_good>mDNS)
            → BackoffRetry 재시도
               ├─ 성공 → 종료
               └─ 소진 → PrintServerException → UI 피드백
```

## 7. 테스트

- **단위 테스트(신규):**
  - `PrinterAddressResolver`: 우선순위(override>last_good>mDNS), `last_good` 영속화/로드, `rediscover` 성공/실패 동작, override 설정/해제.
  - `PrinterHostInterceptor`: host/port 치환을 `MockWebServer`로 검증, 주소 null 시 IOException.
  - `PiPrintClient`: IOException → `rediscover` 1회 호출 → 재시도(fake api + fake resolver)로 검증.
  - `MdnsDiscovery`는 인터페이스 fake로 대체(실제 NSD는 단위 테스트 대상 아님).
- **회귀:** 기존 109개 단위 테스트 그린 유지, `assembleDebug` OK.
- **수동(실기기 + 공유기 Pi):**
  1. 자동 발견 — 태블릿이 mDNS로 Pi를 찾아 인쇄 성공.
  2. IP 변경 복구 — Pi 재부팅/IP 변경 후 인쇄 시 자동 재탐색·복구.
  3. mDNS 실패 폴백 — 수동 override 입력 시 정상 인쇄.

## 8. 범위 밖 / 메모

- DEBUG 빌드는 영향 없음(`FakePrintServerApi`).
- LAN 내부 통신이라 TLS/인증 미도입(기존과 동일한 보안 수준 유지).
- mDNS가 잡히지 않는 환경에서도 수동 override로 항상 탈출 가능.
- AWS/핫스팟/NAT 관련 잔여 정리(문서, 릴리스 빌드)는 housekeeping으로 별도.

## 9. 영향받는 파일 (예상)

- 변경: `android/app/build.gradle.kts`(PRINT_SERVER_URL 제거), `android/app/src/main/java/eloom/holybean/di/PrintNetworkModule.kt`, `.../printer/PiPrintClient.kt`, `.../ui/settings/DevToolsViewModel.kt`, `.../ui/startup/StartupViewModel.kt`.
- 신규: `.../printer/network/PrinterAddress.kt`, `.../printer/network/MdnsDiscovery.kt`, `.../printer/network/PrinterAddressResolver.kt`, `.../printer/network/PrinterHostInterceptor.kt`, "프린터 연결" UI, `pi/deploy/holybean-print.service` + Pi 셋업 문서.
