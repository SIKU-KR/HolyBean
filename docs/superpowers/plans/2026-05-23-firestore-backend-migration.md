# Firestore 백엔드 이전 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** AWS API Gateway + Go Lambda 10개 + DynamoDB로 구성된 데이터 백엔드를 제거하고, Android 앱(`eloom.holybean`)이 Firebase Firestore를 직접 읽고 쓰도록 재설계하며, 인가는 익명 Auth + App Check + 보안 규칙으로 대체하고 기존 데이터를 1회 이관한다.

**Architecture:** `orders`를 source of truth로 두고, `daySummaries`/`reportRollups`/`aggregates/openCredits`/`menu/current`은 쓰기 시점에 갱신되는 파생 문서다. 읽기는 전부 point read이며 리포트만 일수만큼 읽는다. 쓰기는 WriteBatch로 원자 적용하고 핫패스(주문 접수)는 로컬에서 완결된다. 집계/매핑 핵심 로직은 Firestore SDK에 의존하지 않는 순수 함수로 분리해 JVM 단위 테스트로 검증하고, Firestore I/O 글루와 보안 규칙은 에뮬레이터로 검증한다. ETL과 규칙 테스트는 Node.js/TypeScript로 작성한다.

**Tech Stack:** Kotlin + Hilt + Firebase BoM(Firestore/Auth/App Check/Crashlytics) + kotlinx-coroutines-play-services / Node.js + TypeScript + firebase-admin + @firebase/rules-unit-testing + Vitest / Firebase CLI 에뮬레이터.

**확정된 설계 결정 (브레인스토밍 결과):**
- **Room 메뉴 캐시 제거** — `MenuDatabase`/`MenuDao`/Room 의존성 삭제. `menu/current` Firestore 문서가 단일 소스이며 오프라인 영속성이 캐시 역할. `MenuItem`은 순수 데이터 클래스로 강등하고 id/placement 할당·정렬·카테고리 필터를 인메모리 리스트 기반으로 재구현.
- **ETL/규칙 테스트 스택 = Node.js/TypeScript** (`firebase/` 디렉터리).
- **익명 인증** — `request.auth != null` 게이트. App Check(Play Integrity)가 정품 앱 증명 담당.
- **범위 = 전체** — 앱 재설계 + 보안 규칙 + ETL + 컷오버(PRD 마일스톤 1~7).

---

## File Structure

### 신규 Firebase/Node 프로젝트 — `firebase/`
- `firebase/firebase.json` — 에뮬레이터·rules·firestore 설정
- `firebase/.firebaserc` — 프로젝트 별칭
- `firebase/firestore.rules` — 보안 규칙 (인가 최종 경계)
- `firebase/firestore.indexes.json` — 인덱스 (point read 설계라 비움)
- `firebase/package.json`, `firebase/tsconfig.json`
- `firebase/src/schema.ts` — 컬렉션 경로/필드 상수 (TS측 단일 정의)
- `firebase/src/rebuild.ts` — orders → 파생 문서 재생성 순수 로직 (FR-6)
- `firebase/src/mapDynamo.ts` — DynamoDB export → `orders` 문서 매핑 순수 로직
- `firebase/src/etl.ts` — Admin SDK로 export 적재 + 파생 재생성 실행 (CLI)
- `firebase/src/verify.ts` — 구/신 수치 대조 (FR-3 검증)
- `firebase/test/rebuild.test.ts`, `firebase/test/mapDynamo.test.ts`, `firebase/test/rules.test.ts`

### Android — 신규
- `android/app/google-services.json` — Firebase 설정 (gitignore)
- `android/app/src/main/java/eloom/holybean/data/firestore/FirestoreSchema.kt` — 경로/필드 상수
- `android/app/src/main/java/eloom/holybean/data/firestore/OrderAggregation.kt` — 순수 집계/매핑 로직
- `android/app/src/main/java/eloom/holybean/data/firestore/ReportAggregation.kt` — 일별 롤업 합산 순수 로직
- `android/app/src/main/java/eloom/holybean/data/model/SalesReport.kt` — 리포트 도메인 모델
- `android/app/src/main/java/eloom/holybean/di/FirebaseModule.kt` — FirebaseFirestore/FirebaseAuth provider
- `android/app/src/main/java/eloom/holybean/di/CoroutineModule.kt` — 디스패처 provider (NetworkModule에서 이관)
- `android/app/src/test/kotlin/eloom/holybean/data/firestore/OrderAggregationTest.kt`
- `android/app/src/test/kotlin/eloom/holybean/data/firestore/ReportAggregationTest.kt`

### Android — 수정
- `android/build.gradle.kts` — google-services / crashlytics classpath·plugin
- `android/app/build.gradle.kts` — Firebase 플러그인/의존성 추가, Retrofit·Room·API_KEY·BASE_URL 제거
- `android/app/src/main/java/eloom/holybean/AppClass.kt` — App Check 초기화 + 익명 로그인
- `android/app/src/main/java/eloom/holybean/data/repository/FirestoreRepository.kt` — `LambdaRepository` 대체 (rename + 재구현)
- `android/app/src/main/java/eloom/holybean/data/repository/MenuRepository.kt` — Firestore 기반 재작성
- `android/app/src/main/java/eloom/holybean/data/model/MenuItem.kt` — Room 어노테이션 제거
- `android/app/src/main/java/eloom/holybean/ui/home/HomeViewModel.kt` — `lambdaRepository` → `firestoreRepository`
- `android/app/src/main/java/eloom/holybean/ui/orderlist/OrdersViewModel.kt` — 동일
- `android/app/src/main/java/eloom/holybean/ui/credits/CreditsViewModel.kt` — 동일
- `android/app/src/main/java/eloom/holybean/ui/menumanagement/MenuManagementViewModel.kt` — 동일
- `android/app/src/main/java/eloom/holybean/ui/report/ReportViewModel.kt` — `ApiService` 직접 호출 → `firestoreRepository.getReport`
- 테스트 5종 (`*ViewModelTest.kt`) — mock 대상 교체
- `.gitignore` — `google-services.json` 추가

### Android — 삭제
- `network/ApiService.kt`, `network/RetrofitClient.kt`, `network/NetworkModule.kt`, `network/dto/*` (디스패처 provider는 CoroutineModule로 이관 후)
- `data/repository/MenuDatabase.kt`, `data/repository/MenuDao.kt`, `di/DatabaseModule.kt`
- `data/repository/LambdaRepository.kt` (FirestoreRepository로 대체), `data/repository/LambdaRepositoryTest.kt`, `data/repository/MenuRepositoryTest.kt`(Room 기반)

---

## 도메인 ↔ Firestore 매핑 (전 태스크 공통 참조)

**도메인 모델 (보존):**
- `Order(orderDate, orderNum, creditStatus, customerName, orderItems: List<CartItem>, paymentMethods: List<PaymentMethod>, totalAmount)`
- `CartItem(id, name, price, count, total)` · `PaymentMethod(type, amount)`
- `OrderItem(orderId, totalAmount, method, orderer)` (목록) · `OrdersDetailItem(name, count, subtotal)` (상세)
- `CreditItem(orderId, totalAmount, date, orderer)` · `ReportDetailItem(name, quantity, subtotal)`
- `MenuItem(id, name, price, order, inuse)` — Firestore 필드명은 `placement`(=`order`)

**Firestore 문서:**
```
orders/{date}_{num}
  orderDate:string, orderNum:number, totalAmount:number, customerName:string, creditStatus:number,
  items:    [ {name, quantity, subtotal, unitPrice} ]   // CartItem: name, count→quantity, total→subtotal, price→unitPrice
  payments: [ {method, amount} ]                          // PaymentMethod: type→method, amount
  createdAt: timestamp
daySummaries/{date}
  lastOrderNum:number, orders: { "{num}": {customerName, totalAmount, orderMethod, creditStatus} }
reportRollups/{date}
  menuSales: { "{itemName}": {quantity, sales} }, paymentSales: { "{method}": amount }, total:number
aggregates/openCredits
  items: { "{date}_{num}": {customerName, totalAmount, orderNum, orderDate} }
menu/current
  items: [ {id, name, price, placement, inuse} ], updatedAt: timestamp
```

**불변식 (레거시 동작 보존):**
- `creditStatus`: `1`=외상 미수, `0`=정산/일반결제. 리포트는 `creditStatus==0`만 집계.
- `orderMethod` = `payments`의 `method`를 `"+"`로 결합, 비면 `"Unknown"` (레거시 get_order_day와 동일).
- 리포트 메뉴는 **수량 내림차순** 정렬, `paymentSales`에 `"총합"` = 전체 결제금액 합계 키 추가 (레거시 get_report와 동일).
- 외상 정산 시 **원 주문일** `reportRollups`에 가산.

---

## Phase 1 — Firebase 프로젝트 & Gradle 연동 (마일스톤 1)

> **수동 선행 작업 (사람이 수행):** Firebase 콘솔에서 프로젝트 생성 → Android 앱(`eloom.holybean`) 등록 → `google-services.json` 다운로드 → `android/app/`에 배치. Firestore(프로덕션 모드)·Authentication(익명 공급자)·App Check·Crashlytics 활성화. App Check에 Play Integrity 등록 및 디버그 토큰 발급. 이 작업이 끝나야 Phase 1 빌드가 통과한다.

### Task 1.1: google-services.json 비공개화

**Files:**
- Modify: `.gitignore`

- [ ] **Step 1: `.gitignore`에 추가**

기존 내용 끝에 다음 줄을 추가한다:
```gitignore
# Firebase
android/app/google-services.json
```

- [ ] **Step 2: 추적 여부 확인**

Run: `git -C /Users/benn/dev/personal/HolyBean status --porcelain android/app/google-services.json`
Expected: 출력 없음(무시됨) — 파일이 이미 있다면 `!!` 또는 미표시.

- [ ] **Step 3: Commit**

```bash
git add .gitignore
git commit -m "chore: gitignore google-services.json"
```

### Task 1.2: 루트 Gradle에 Firebase 플러그인 classpath 추가

**Files:**
- Modify: `android/build.gradle.kts`

- [ ] **Step 1: 플러그인 선언 추가**

`android/build.gradle.kts`의 `plugins { }` 블록에 두 줄을 추가한다 (apply false):
```kotlin
plugins {
    id("com.android.application") version "8.11.2" apply false
    id("org.jetbrains.kotlin.android") version "2.2.20" apply false
    id("com.google.dagger.hilt.android") version "2.57.1" apply false
    id("com.google.devtools.ksp") version "2.2.20-2.0.2" apply false
    id("com.google.gms.google-services") version "4.4.2" apply false
    id("com.google.firebase.crashlytics") version "3.0.2" apply false
}
```

- [ ] **Step 2: 동기화 확인**

Run: `cd /Users/benn/dev/personal/HolyBean/android && ./gradlew help -q`
Expected: BUILD SUCCESSFUL (플러그인 해석 성공).

- [ ] **Step 3: Commit**

```bash
git add android/build.gradle.kts
git commit -m "build: add firebase gradle plugins to classpath"
```

### Task 1.3: 앱 모듈에 Firebase 의존성·플러그인 적용

**Files:**
- Modify: `android/app/build.gradle.kts`

- [ ] **Step 1: 플러그인 블록에 추가**

`plugins { }` 끝에 추가:
```kotlin
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
```

- [ ] **Step 2: Firebase 의존성 추가**

`dependencies { }` 안, Room 블록 위에 추가:
```kotlin
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-appcheck-playintegrity")
    implementation("com.google.firebase:firebase-appcheck-debug")
    implementation("com.google.firebase:firebase-crashlytics")
    implementation("com.google.firebase:firebase-analytics")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")
```

> Retrofit/Gson/Room 의존성은 Phase 5~6에서 제거한다(아직 `LambdaRepository`가 사용 중).

- [ ] **Step 3: 빌드 확인**

Run: `cd /Users/benn/dev/personal/HolyBean/android && ./gradlew :app:assembleDebug -q`
Expected: BUILD SUCCESSFUL. (`google-services.json`이 있어야 google-services 플러그인이 통과)

- [ ] **Step 4: Commit**

```bash
git add android/app/build.gradle.kts
git commit -m "build: add firebase SDK dependencies"
```

### Task 1.4: App Check 초기화 + 익명 로그인

**Files:**
- Modify: `android/app/src/main/java/eloom/holybean/AppClass.kt`

- [ ] **Step 1: AppClass 구현**

`AppClass.kt` 전체를 교체:
```kotlin
package eloom.holybean

import android.app.Application
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.auth.auth
import com.google.firebase.Firebase
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class AppClass : Application() {
    override fun onCreate() {
        super.onCreate()

        Firebase.appCheck.installAppCheckProviderFactory(
            if (BuildConfig.DEBUG) DebugAppCheckProviderFactory.getInstance()
            else PlayIntegrityAppCheckProviderFactory.getInstance()
        )

        if (Firebase.auth.currentUser == null) {
            Firebase.auth.signInAnonymously()
        }
    }
}
```

- [ ] **Step 2: 빌드 확인**

Run: `cd /Users/benn/dev/personal/HolyBean/android && ./gradlew :app:assembleDebug -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: 디바이스/에뮬레이터 실행 후 로그 확인**

Run: 앱 실행 후 `adb logcat -d | grep -i "appcheck\|signInAnonymously\|FirebaseAuth" | tail -20`
Expected: 익명 로그인 성공 로그, App Check 디버그 토큰 출력(콘솔에 등록 필요). 크래시 없음.

- [ ] **Step 4: Commit**

```bash
git add android/app/src/main/java/eloom/holybean/AppClass.kt
git commit -m "feat: init firebase app check and anonymous auth"
```

### Task 1.5: Firebase DI 모듈

**Files:**
- Create: `android/app/src/main/java/eloom/holybean/di/FirebaseModule.kt`

- [ ] **Step 1: 모듈 작성**

```kotlin
package eloom.holybean.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.firestore.persistentCacheSettings
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {

    @Provides
    @Singleton
    fun provideFirestore(): FirebaseFirestore {
        val db = FirebaseFirestore.getInstance()
        db.firestoreSettings = firestoreSettings {
            setLocalCacheSettings(persistentCacheSettings {})
        }
        return db
    }

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()
}
```

- [ ] **Step 2: 빌드 확인**

Run: `cd /Users/benn/dev/personal/HolyBean/android && ./gradlew :app:assembleDebug -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add android/app/src/main/java/eloom/holybean/di/FirebaseModule.kt
git commit -m "feat: add firestore DI module with offline persistence"
```

---

## Phase 2 — Firestore 데이터 모델 + 보안 규칙 + 에뮬레이터 테스트 (마일스톤 2)

### Task 2.1: Node/TS 프로젝트 스캐폴드

**Files:**
- Create: `firebase/package.json`, `firebase/tsconfig.json`, `firebase/firebase.json`, `firebase/.firebaserc`, `firebase/firestore.indexes.json`

- [ ] **Step 1: package.json**

`firebase/package.json`:
```json
{
  "name": "holybean-firebase",
  "version": "1.0.0",
  "private": true,
  "type": "module",
  "scripts": {
    "test": "vitest run",
    "test:rules": "firebase emulators:exec --only firestore \"vitest run test/rules.test.ts\"",
    "etl": "tsx src/etl.ts",
    "verify": "tsx src/verify.ts"
  },
  "devDependencies": {
    "@firebase/rules-unit-testing": "^3.0.4",
    "firebase": "^11.1.0",
    "firebase-admin": "^13.0.2",
    "tsx": "^4.19.2",
    "typescript": "^5.7.2",
    "vitest": "^2.1.8"
  }
}
```

- [ ] **Step 2: tsconfig.json**

`firebase/tsconfig.json`:
```json
{
  "compilerOptions": {
    "target": "ES2022",
    "module": "ESNext",
    "moduleResolution": "Bundler",
    "strict": true,
    "esModuleInterop": true,
    "skipLibCheck": true,
    "types": ["node"]
  },
  "include": ["src", "test"]
}
```

- [ ] **Step 3: firebase.json**

`firebase/firebase.json`:
```json
{
  "firestore": {
    "rules": "firestore.rules",
    "indexes": "firestore.indexes.json"
  },
  "emulators": {
    "firestore": { "port": 8080 },
    "ui": { "enabled": false }
  }
}
```

- [ ] **Step 4: .firebaserc / 빈 인덱스**

`firebase/.firebaserc`:
```json
{ "projects": { "default": "holybean-prod" } }
```
`firebase/firestore.indexes.json`:
```json
{ "indexes": [], "fieldOverrides": [] }
```

- [ ] **Step 5: 설치 확인**

Run: `cd /Users/benn/dev/personal/HolyBean/firebase && npm install && npx tsc --noEmit`
Expected: 설치 성공, 타입 에러 없음(소스 아직 없음 → 통과).

- [ ] **Step 6: Commit**

```bash
git add firebase/package.json firebase/tsconfig.json firebase/firebase.json firebase/.firebaserc firebase/firestore.indexes.json firebase/package-lock.json
git commit -m "chore: scaffold firebase node/ts project"
```

### Task 2.2: 보안 규칙 작성 (TDD — 에뮬레이터 테스트 먼저)

**Files:**
- Create: `firebase/test/rules.test.ts`
- Create: `firebase/firestore.rules`

- [ ] **Step 1: 실패하는 규칙 테스트 작성**

`firebase/test/rules.test.ts`:
```typescript
import { assertFails, assertSucceeds, initializeTestEnvironment, RulesTestEnvironment } from "@firebase/rules-unit-testing";
import { doc, getDoc, setDoc } from "firebase/firestore";
import { readFileSync } from "node:fs";
import { afterAll, beforeAll, beforeEach, describe, it } from "vitest";

let env: RulesTestEnvironment;

beforeAll(async () => {
  env = await initializeTestEnvironment({
    projectId: "holybean-test",
    firestore: { rules: readFileSync("firestore.rules", "utf8"), host: "127.0.0.1", port: 8080 },
  });
});
afterAll(async () => { await env.cleanup(); });
beforeEach(async () => { await env.clearFirestore(); });

const validOrder = {
  orderDate: "2026-05-23", orderNum: 1, totalAmount: 4500, customerName: "", creditStatus: 0,
  items: [{ name: "아메리카노", quantity: 1, subtotal: 4500, unitPrice: 4500 }],
  payments: [{ method: "현금", amount: 4500 }], createdAt: new Date(),
};

describe("firestore rules", () => {
  it("미인증 요청은 읽기/쓰기 거부", async () => {
    const db = env.unauthenticatedContext().firestore();
    await assertFails(getDoc(doc(db, "orders/2026-05-23_1")));
    await assertFails(setDoc(doc(db, "orders/2026-05-23_1"), validOrder));
  });

  it("인증 요청은 orders 읽기/쓰기 허용", async () => {
    const db = env.authenticatedContext("store").firestore();
    await assertSucceeds(setDoc(doc(db, "orders/2026-05-23_1"), validOrder));
    await assertSucceeds(getDoc(doc(db, "orders/2026-05-23_1")));
  });

  it("필수 필드 누락 order 쓰기 거부", async () => {
    const db = env.authenticatedContext("store").firestore();
    const { totalAmount, ...broken } = validOrder;
    await assertFails(setDoc(doc(db, "orders/2026-05-23_2"), broken));
  });

  it("totalAmount 타입 오류 거부", async () => {
    const db = env.authenticatedContext("store").firestore();
    await assertFails(setDoc(doc(db, "orders/2026-05-23_3"), { ...validOrder, totalAmount: "x" }));
  });

  it("파생/메뉴/집계 컬렉션은 인증 시 읽기·쓰기 허용", async () => {
    const db = env.authenticatedContext("store").firestore();
    await assertSucceeds(setDoc(doc(db, "daySummaries/2026-05-23"), { lastOrderNum: 1, orders: {} }));
    await assertSucceeds(setDoc(doc(db, "reportRollups/2026-05-23"), { menuSales: {}, paymentSales: {}, total: 0 }));
    await assertSucceeds(setDoc(doc(db, "aggregates/openCredits"), { items: {} }));
    await assertSucceeds(setDoc(doc(db, "menu/current"), { items: [], updatedAt: new Date() }));
  });

  it("알 수 없는 컬렉션 거부", async () => {
    const db = env.authenticatedContext("store").firestore();
    await assertFails(setDoc(doc(db, "secrets/x"), { a: 1 }));
  });
});
```

- [ ] **Step 2: 테스트 실행해 실패 확인**

Run: `cd /Users/benn/dev/personal/HolyBean/firebase && npm run test:rules`
Expected: FAIL — `firestore.rules` 미존재 또는 전부 거부되어 "허용" 테스트 실패.

- [ ] **Step 3: 규칙 작성**

`firebase/firestore.rules`:
```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {

    function signedIn() { return request.auth != null; }

    function validOrder(d) {
      return d.keys().hasAll(['orderDate','orderNum','totalAmount','customerName','creditStatus','items','payments'])
        && d.orderDate is string
        && d.orderNum is int
        && d.totalAmount is int
        && d.customerName is string
        && d.creditStatus is int
        && d.items is list
        && d.payments is list;
    }

    match /orders/{orderId} {
      allow read: if signedIn();
      allow write: if signedIn() && validOrder(request.resource.data);
      allow delete: if signedIn();
    }

    match /daySummaries/{date}   { allow read, write: if signedIn(); }
    match /reportRollups/{date}  { allow read, write: if signedIn(); }
    match /aggregates/{docId}    { allow read, write: if signedIn(); }
    match /menu/{docId}          { allow read, write: if signedIn(); }

    match /{document=**} { allow read, write: if false; }
  }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd /Users/benn/dev/personal/HolyBean/firebase && npm run test:rules`
Expected: PASS (6 tests).

- [ ] **Step 5: Commit**

```bash
git add firebase/firestore.rules firebase/test/rules.test.ts
git commit -m "feat: firestore security rules with emulator regression tests"
```

### Task 2.3: 규칙 배포 (수동 게이트)

**Files:** 없음 (CLI 실행)

- [ ] **Step 1: 배포**

Run (사람이 인증된 환경에서): `cd /Users/benn/dev/personal/HolyBean/firebase && firebase deploy --only firestore:rules`
Expected: "Deploy complete". 콘솔 Firestore > 규칙 탭에서 반영 확인.

> 실 프로젝트 인증이 필요하면 사용자에게 `! firebase login` 실행을 요청한다.

---

## Phase 3 — FirestoreRepository 읽기 연산 (마일스톤 3)

> 이 Phase에서 `LambdaRepository`를 `FirestoreRepository`로 대체하기 시작한다. 순수 매핑/집계 로직은 JVM 단위 테스트로 TDD하고, Firestore I/O 메서드는 에뮬레이터+수동으로 검증한다(SDK가 JVM 단위 테스트 불가).

### Task 3.1: 스키마 상수

**Files:**
- Create: `android/app/src/main/java/eloom/holybean/data/firestore/FirestoreSchema.kt`

- [ ] **Step 1: 작성**

```kotlin
package eloom.holybean.data.firestore

object FirestoreSchema {
    const val ORDERS = "orders"
    const val DAY_SUMMARIES = "daySummaries"
    const val REPORT_ROLLUPS = "reportRollups"
    const val AGGREGATES = "aggregates"
    const val MENU = "menu"

    const val OPEN_CREDITS_DOC = "openCredits"
    const val MENU_CURRENT_DOC = "current"

    fun orderId(date: String, num: Int): String = "${date}_$num"
    fun creditKey(date: String, num: Int): String = "${date}_$num"

    const val CREDIT_UNPAID = 1
    const val CREDIT_SETTLED = 0
}
```

- [ ] **Step 2: 빌드 확인**

Run: `cd /Users/benn/dev/personal/HolyBean/android && ./gradlew :app:compileDebugKotlin -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add android/app/src/main/java/eloom/holybean/data/firestore/FirestoreSchema.kt
git commit -m "feat: add firestore schema constants"
```

### Task 3.2: SalesReport 도메인 모델

**Files:**
- Create: `android/app/src/main/java/eloom/holybean/data/model/SalesReport.kt`

- [ ] **Step 1: 작성**

```kotlin
package eloom.holybean.data.model

data class SalesReport(
    val menuSales: List<ReportDetailItem>,   // 수량 내림차순
    val paymentSales: Map<String, Int>       // "총합" 키 포함
)
```

- [ ] **Step 2: Commit**

```bash
git add android/app/src/main/java/eloom/holybean/data/model/SalesReport.kt
git commit -m "feat: add SalesReport domain model"
```

### Task 3.3: ReportAggregation 순수 로직 (TDD)

**Files:**
- Create: `android/app/src/test/kotlin/eloom/holybean/data/firestore/ReportAggregationTest.kt`
- Create: `android/app/src/main/java/eloom/holybean/data/firestore/ReportAggregation.kt`

- [ ] **Step 1: 실패 테스트 작성**

```kotlin
package eloom.holybean.data.firestore

import eloom.holybean.data.firestore.ReportAggregation.DailyRollup
import eloom.holybean.data.firestore.ReportAggregation.MenuSale
import org.junit.Assert.assertEquals
import org.junit.Test

class ReportAggregationTest {

    @Test
    fun `여러 날 롤업을 메뉴별로 합산하고 수량 내림차순 정렬`() {
        val day1 = DailyRollup(
            menuSales = mapOf("아메리카노" to MenuSale(2, 9000), "라떼" to MenuSale(1, 5000)),
            paymentSales = mapOf("현금" to 14000)
        )
        val day2 = DailyRollup(
            menuSales = mapOf("아메리카노" to MenuSale(3, 13500)),
            paymentSales = mapOf("쿠폰" to 13500)
        )

        val report = ReportAggregation.combine(listOf(day1, day2))

        assertEquals(
            listOf(
                eloom.holybean.data.model.ReportDetailItem("아메리카노", 5, 22500),
                eloom.holybean.data.model.ReportDetailItem("라떼", 1, 5000)
            ),
            report.menuSales
        )
    }

    @Test
    fun `결제수단을 합산하고 총합 키를 추가`() {
        val report = ReportAggregation.combine(
            listOf(DailyRollup(emptyMap(), mapOf("현금" to 14000, "쿠폰" to 13500)))
        )
        assertEquals(14000, report.paymentSales["현금"])
        assertEquals(13500, report.paymentSales["쿠폰"])
        assertEquals(27500, report.paymentSales["총합"])
    }

    @Test
    fun `빈 입력은 빈 메뉴와 총합 0`() {
        val report = ReportAggregation.combine(emptyList())
        assertEquals(emptyList<Any>(), report.menuSales)
        assertEquals(0, report.paymentSales["총합"])
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `cd /Users/benn/dev/personal/HolyBean/android && ./gradlew :app:testDebugUnitTest --tests "*ReportAggregationTest" -q`
Expected: FAIL — `ReportAggregation` 미정의(컴파일 에러).

- [ ] **Step 3: 구현**

```kotlin
package eloom.holybean.data.firestore

import eloom.holybean.data.model.ReportDetailItem
import eloom.holybean.data.model.SalesReport

object ReportAggregation {

    data class MenuSale(val quantity: Int, val sales: Int)
    data class DailyRollup(
        val menuSales: Map<String, MenuSale>,
        val paymentSales: Map<String, Int>
    )

    fun combine(rollups: List<DailyRollup>): SalesReport {
        val menuAcc = HashMap<String, MenuSale>()
        val payAcc = HashMap<String, Int>()
        var total = 0

        for (r in rollups) {
            for ((name, sale) in r.menuSales) {
                val cur = menuAcc[name]
                menuAcc[name] = if (cur == null) sale
                    else MenuSale(cur.quantity + sale.quantity, cur.sales + sale.sales)
            }
            for ((method, amount) in r.paymentSales) {
                payAcc[method] = (payAcc[method] ?: 0) + amount
                total += amount
            }
        }

        val menuList = menuAcc.entries
            .sortedByDescending { it.value.quantity }
            .map { ReportDetailItem(it.key, it.value.quantity, it.value.sales) }

        payAcc["총합"] = total
        return SalesReport(menuList, payAcc)
    }
}
```

- [ ] **Step 4: 통과 확인**

Run: `cd /Users/benn/dev/personal/HolyBean/android && ./gradlew :app:testDebugUnitTest --tests "*ReportAggregationTest" -q`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/eloom/holybean/data/firestore/ReportAggregation.kt android/app/src/test/kotlin/eloom/holybean/data/firestore/ReportAggregationTest.kt
git commit -m "feat: report aggregation pure logic with tests"
```

### Task 3.4: OrderAggregation 순수 로직 (TDD)

**Files:**
- Create: `android/app/src/test/kotlin/eloom/holybean/data/firestore/OrderAggregationTest.kt`
- Create: `android/app/src/main/java/eloom/holybean/data/firestore/OrderAggregation.kt`

- [ ] **Step 1: 실패 테스트 작성**

```kotlin
package eloom.holybean.data.firestore

import eloom.holybean.data.model.CartItem
import eloom.holybean.data.model.Order
import eloom.holybean.data.model.PaymentMethod
import org.junit.Assert.assertEquals
import org.junit.Test

class OrderAggregationTest {

    private fun order(
        creditStatus: Int = 0,
        payments: List<PaymentMethod> = listOf(PaymentMethod("현금", 9000)),
        items: List<CartItem> = listOf(CartItem(1001, "아메리카노", 4500, 2, 9000))
    ) = Order("2026-05-23", 3, creditStatus, "홍길동", items, payments, 9000)

    @Test
    fun `orderMethod는 결제수단을 플러스로 결합`() {
        assertEquals("현금+쿠폰", OrderAggregation.orderMethodLabel(
            listOf(PaymentMethod("현금", 5000), PaymentMethod("쿠폰", 4000))
        ))
    }

    @Test
    fun `결제수단이 없으면 Unknown`() {
        assertEquals("Unknown", OrderAggregation.orderMethodLabel(emptyList()))
    }

    @Test
    fun `daySummary 항목은 customerName totalAmount orderMethod creditStatus`() {
        val entry = OrderAggregation.daySummaryEntry(order())
        assertEquals("홍길동", entry["customerName"])
        assertEquals(9000, entry["totalAmount"])
        assertEquals("현금", entry["orderMethod"])
        assertEquals(0, entry["creditStatus"])
    }

    @Test
    fun `rollupDelta는 메뉴 수량과 매출 결제수단 총합을 계산`() {
        val delta = OrderAggregation.rollupDelta(order())
        assertEquals(2, delta.menuSales["아메리카노"]!!.quantity)
        assertEquals(9000, delta.menuSales["아메리카노"]!!.sales)
        assertEquals(9000, delta.paymentSales["현금"])
        assertEquals(9000, delta.total)
    }

    @Test
    fun `orderDoc 매핑은 items와 payments를 문서 형태로 변환`() {
        val doc = OrderAggregation.orderDoc(order())
        @Suppress("UNCHECKED_CAST")
        val item = (doc["items"] as List<Map<String, Any>>).first()
        assertEquals("아메리카노", item["name"])
        assertEquals(2, item["quantity"])
        assertEquals(9000, item["subtotal"])
        assertEquals(4500, item["unitPrice"])
        @Suppress("UNCHECKED_CAST")
        val pay = (doc["payments"] as List<Map<String, Any>>).first()
        assertEquals("현금", pay["method"])
        assertEquals(9000, pay["amount"])
        assertEquals(3, doc["orderNum"])
        assertEquals(0, doc["creditStatus"])
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `cd /Users/benn/dev/personal/HolyBean/android && ./gradlew :app:testDebugUnitTest --tests "*OrderAggregationTest" -q`
Expected: FAIL — `OrderAggregation` 미정의.

- [ ] **Step 3: 구현**

```kotlin
package eloom.holybean.data.firestore

import eloom.holybean.data.model.CartItem
import eloom.holybean.data.model.Order
import eloom.holybean.data.model.PaymentMethod

object OrderAggregation {

    data class MenuSaleDelta(val quantity: Int, val sales: Int)
    data class RollupDelta(
        val menuSales: Map<String, MenuSaleDelta>,
        val paymentSales: Map<String, Int>,
        val total: Int
    )

    fun orderMethodLabel(payments: List<PaymentMethod>): String =
        if (payments.isEmpty()) "Unknown" else payments.joinToString("+") { it.type }

    fun orderDoc(order: Order): Map<String, Any> = mapOf(
        "orderDate" to order.orderDate,
        "orderNum" to order.orderNum,
        "totalAmount" to order.totalAmount,
        "customerName" to order.customerName,
        "creditStatus" to order.creditStatus,
        "items" to order.orderItems.map {
            mapOf("name" to it.name, "quantity" to it.count, "subtotal" to it.total, "unitPrice" to it.price)
        },
        "payments" to order.paymentMethods.map {
            mapOf("method" to it.type, "amount" to it.amount)
        }
    )

    fun daySummaryEntry(order: Order): Map<String, Any> = mapOf(
        "customerName" to order.customerName,
        "totalAmount" to order.totalAmount,
        "orderMethod" to orderMethodLabel(order.paymentMethods),
        "creditStatus" to order.creditStatus
    )

    fun rollupDelta(order: Order): RollupDelta = rollupDelta(order.orderItems, order.paymentMethods)

    fun rollupDelta(items: List<CartItem>, payments: List<PaymentMethod>): RollupDelta {
        val menu = HashMap<String, MenuSaleDelta>()
        for (it in items) {
            val cur = menu[it.name]
            menu[it.name] = if (cur == null) MenuSaleDelta(it.count, it.total)
                else MenuSaleDelta(cur.quantity + it.count, cur.sales + it.total)
        }
        val pay = HashMap<String, Int>()
        var total = 0
        for (p in payments) {
            pay[p.type] = (pay[p.type] ?: 0) + p.amount
            total += p.amount
        }
        return RollupDelta(menu, pay, total)
    }
}
```

- [ ] **Step 4: 통과 확인**

Run: `cd /Users/benn/dev/personal/HolyBean/android && ./gradlew :app:testDebugUnitTest --tests "*OrderAggregationTest" -q`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/eloom/holybean/data/firestore/OrderAggregation.kt android/app/src/test/kotlin/eloom/holybean/data/firestore/OrderAggregationTest.kt
git commit -m "feat: order aggregation pure logic with tests"
```

### Task 3.5: FirestoreRepository — 읽기 연산 구현

**Files:**
- Create: `android/app/src/main/java/eloom/holybean/data/repository/FirestoreRepository.kt`

> 이 태스크는 읽기 메서드(getOrderNumber/getOrdersOfDay/getOrderDetail/getCreditsList/getReport)만 구현한다. 쓰기 메서드는 Task 4.x에서 같은 파일에 추가한다. Firestore I/O라 JVM 단위 테스트 대신 Task 3.6 에뮬레이터 통합으로 검증한다.

- [ ] **Step 1: 구현 (읽기 메서드 + 골격)**

```kotlin
package eloom.holybean.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import eloom.holybean.data.firestore.FirestoreSchema
import eloom.holybean.data.firestore.ReportAggregation
import eloom.holybean.data.firestore.ReportAggregation.DailyRollup
import eloom.holybean.data.firestore.ReportAggregation.MenuSale
import eloom.holybean.data.model.*
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreRepository @Inject constructor(
    private val db: FirebaseFirestore
) {
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private fun today(): String = LocalDate.now().format(dateFormatter)

    suspend fun getOrderNumber(): Int {
        return try {
            val snap = db.collection(FirestoreSchema.DAY_SUMMARIES).document(today()).get().await()
            val last = (snap.getLong("lastOrderNum") ?: 0L).toInt()
            last + 1
        } catch (e: Exception) {
            e.printStackTrace()
            -1
        }
    }

    suspend fun getOrdersOfDay(): ArrayList<OrderItem> {
        return try {
            val snap = db.collection(FirestoreSchema.DAY_SUMMARIES).document(today()).get().await()
            @Suppress("UNCHECKED_CAST")
            val orders = (snap.get("orders") as? Map<String, Map<String, Any>>) ?: emptyMap()
            val list = orders.entries
                .sortedBy { it.key.toIntOrNull() ?: 0 }
                .map { (num, m) ->
                    OrderItem(
                        orderId = num.toInt(),
                        totalAmount = (m["totalAmount"] as? Number)?.toInt() ?: 0,
                        method = m["orderMethod"] as? String ?: "Unknown",
                        orderer = m["customerName"] as? String ?: ""
                    )
                }
            ArrayList(list)
        } catch (e: Exception) {
            e.printStackTrace()
            arrayListOf()
        }
    }

    suspend fun getOrderDetail(date: String, num: Int): ArrayList<OrdersDetailItem> {
        return try {
            val snap = db.collection(FirestoreSchema.ORDERS)
                .document(FirestoreSchema.orderId(date, num)).get().await()
            @Suppress("UNCHECKED_CAST")
            val items = (snap.get("items") as? List<Map<String, Any>>) ?: emptyList()
            ArrayList(items.map {
                OrdersDetailItem(
                    name = it["name"] as? String ?: "",
                    count = (it["quantity"] as? Number)?.toInt() ?: 0,
                    subtotal = (it["subtotal"] as? Number)?.toInt() ?: 0
                )
            })
        } catch (e: Exception) {
            e.printStackTrace()
            arrayListOf()
        }
    }

    suspend fun getCreditsList(): ArrayList<CreditItem> {
        return try {
            val snap = db.collection(FirestoreSchema.AGGREGATES)
                .document(FirestoreSchema.OPEN_CREDITS_DOC).get().await()
            @Suppress("UNCHECKED_CAST")
            val items = (snap.get("items") as? Map<String, Map<String, Any>>) ?: emptyMap()
            ArrayList(items.values.map {
                CreditItem(
                    orderId = (it["orderNum"] as? Number)?.toInt() ?: 0,
                    totalAmount = (it["totalAmount"] as? Number)?.toInt() ?: 0,
                    date = it["orderDate"] as? String ?: "",
                    orderer = it["customerName"] as? String ?: ""
                )
            }.sortedWith(compareBy({ it.date }, { it.orderId })))
        } catch (e: Exception) {
            e.printStackTrace()
            arrayListOf()
        }
    }

    suspend fun getReport(start: String, end: String): SalesReport {
        return try {
            val startDate = LocalDate.parse(start, dateFormatter)
            val endDate = LocalDate.parse(end, dateFormatter)
            val rollups = ArrayList<DailyRollup>()
            var d = startDate
            while (!d.isAfter(endDate)) {
                val snap = db.collection(FirestoreSchema.REPORT_ROLLUPS)
                    .document(d.format(dateFormatter)).get().await()
                if (snap.exists()) {
                    @Suppress("UNCHECKED_CAST")
                    val ms = (snap.get("menuSales") as? Map<String, Map<String, Any>>) ?: emptyMap()
                    @Suppress("UNCHECKED_CAST")
                    val ps = (snap.get("paymentSales") as? Map<String, Any>) ?: emptyMap()
                    rollups.add(
                        DailyRollup(
                            menuSales = ms.mapValues {
                                MenuSale(
                                    (it.value["quantity"] as? Number)?.toInt() ?: 0,
                                    (it.value["sales"] as? Number)?.toInt() ?: 0
                                )
                            },
                            paymentSales = ps.mapValues { (it.value as? Number)?.toInt() ?: 0 }
                        )
                    )
                }
                d = d.plusDays(1)
            }
            ReportAggregation.combine(rollups)
        } catch (e: Exception) {
            e.printStackTrace()
            SalesReport(emptyList(), mapOf("총합" to 0))
        }
    }
}
```

- [ ] **Step 2: 컴파일 확인**

Run: `cd /Users/benn/dev/personal/HolyBean/android && ./gradlew :app:compileDebugKotlin -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add android/app/src/main/java/eloom/holybean/data/repository/FirestoreRepository.kt
git commit -m "feat: FirestoreRepository read operations"
```

### Task 3.6: 읽기 연산 에뮬레이터 통합 검증 (수동 게이트)

**Files:** 없음

- [ ] **Step 1: 에뮬레이터에 시드 데이터 삽입 후 앱 연결**

Firestore 에뮬레이터(`firebase emulators:start --only firestore`)에 `daySummaries/{today}` (lastOrderNum, orders), `orders/{today}_1`, `aggregates/openCredits`, `reportRollups/{today}`를 수동 삽입한다. 디버그 빌드에서 `FirebaseFirestore`를 에뮬레이터로 가리키도록 임시 설정(`db.useEmulator("10.0.2.2", 8080)`)하거나 실 프로젝트 시드 데이터를 사용한다.

- [ ] **Step 2: 화면별 확인**
  - Home: 주문번호가 `lastOrderNum+1`로 표시
  - 주문목록: `daySummaries.orders` 항목이 번호순으로 표시, `orderMethod`/금액 일치
  - 주문상세: items가 정확히 표시
  - 외상: openCredits 항목 표시
  - 리포트: 기간 합산 결과·"총합" 일치

Expected: 5개 읽기 경로 모두 기대 데이터 표시, 크래시 없음. 불일치 시 systematic-debugging 적용.

---

## Phase 4 — FirestoreRepository 쓰기 연산 (마일스톤 4)

### Task 4.1: postOrder — 주문 저장 팬아웃

**Files:**
- Modify: `android/app/src/main/java/eloom/holybean/data/repository/FirestoreRepository.kt`

- [ ] **Step 1: import 추가**

파일 상단 import에 추가:
```kotlin
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import eloom.holybean.data.firestore.OrderAggregation
```

- [ ] **Step 2: postOrder 구현 (클래스 내부에 추가)**

```kotlin
    /** 핫패스: 로컬에 즉시 적용되고 동기화는 백그라운드(NFR-7). 서버 ack를 await하지 않는다. */
    fun postOrder(data: Order) {
        val batch = db.batch()
        val orderRef = db.collection(FirestoreSchema.ORDERS)
            .document(FirestoreSchema.orderId(data.orderDate, data.orderNum))
        batch.set(orderRef, OrderAggregation.orderDoc(data) + mapOf("createdAt" to FieldValue.serverTimestamp()))

        val dayRef = db.collection(FirestoreSchema.DAY_SUMMARIES).document(data.orderDate)
        batch.set(
            dayRef,
            mapOf(
                "lastOrderNum" to data.orderNum,
                "orders" to mapOf(data.orderNum.toString() to OrderAggregation.daySummaryEntry(data))
            ),
            SetOptions.merge()
        )

        if (data.creditStatus == FirestoreSchema.CREDIT_SETTLED) {
            applyRollupDelta(batch, data.orderDate, OrderAggregation.rollupDelta(data), sign = 1)
        } else {
            val creditsRef = db.collection(FirestoreSchema.AGGREGATES)
                .document(FirestoreSchema.OPEN_CREDITS_DOC)
            batch.set(
                creditsRef,
                mapOf("items" to mapOf(
                    FirestoreSchema.creditKey(data.orderDate, data.orderNum) to mapOf(
                        "customerName" to data.customerName,
                        "totalAmount" to data.totalAmount,
                        "orderNum" to data.orderNum,
                        "orderDate" to data.orderDate
                    )
                )),
                SetOptions.merge()
            )
        }
        batch.commit()  // await 하지 않음 — 로컬 즉시 반영, 동기화는 SDK가 큐잉
    }

    private fun applyRollupDelta(
        batch: com.google.firebase.firestore.WriteBatch,
        date: String,
        delta: OrderAggregation.RollupDelta,
        sign: Int
    ) {
        val ref = db.collection(FirestoreSchema.REPORT_ROLLUPS).document(date)
        val menu = delta.menuSales.mapValues { (_, v) ->
            mapOf(
                "quantity" to FieldValue.increment((v.quantity * sign).toLong()),
                "sales" to FieldValue.increment((v.sales * sign).toLong())
            )
        }
        val pay = delta.paymentSales.mapValues { (_, v) -> FieldValue.increment((v * sign).toLong()) }
        batch.set(
            ref,
            mapOf(
                "menuSales" to menu,
                "paymentSales" to pay,
                "total" to FieldValue.increment((delta.total * sign).toLong())
            ),
            SetOptions.merge()
        )
    }
```

- [ ] **Step 3: 컴파일 확인**

Run: `cd /Users/benn/dev/personal/HolyBean/android && ./gradlew :app:compileDebugKotlin -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add android/app/src/main/java/eloom/holybean/data/repository/FirestoreRepository.kt
git commit -m "feat: postOrder write fan-out"
```

### Task 4.2: setCreditOrderPaid — 외상 정산 팬아웃

**Files:**
- Modify: `android/app/src/main/java/eloom/holybean/data/repository/FirestoreRepository.kt`

- [ ] **Step 1: 메서드 추가**

```kotlin
    /** 외상 정산(1→0): 원 주문일 reportRollups에 가산. 주문 본문을 읽어 델타 계산. */
    suspend fun setCreditOrderPaid(date: String, num: Int) {
        try {
            val orderRef = db.collection(FirestoreSchema.ORDERS)
                .document(FirestoreSchema.orderId(date, num))
            val snap = orderRef.get().await()
            if (!snap.exists()) return
            if ((snap.getLong("creditStatus") ?: 0L).toInt() == FirestoreSchema.CREDIT_SETTLED) return

            @Suppress("UNCHECKED_CAST")
            val items = (snap.get("items") as? List<Map<String, Any>>) ?: emptyList()
            @Suppress("UNCHECKED_CAST")
            val payments = (snap.get("payments") as? List<Map<String, Any>>) ?: emptyList()

            val cartItems = items.map {
                eloom.holybean.data.model.CartItem(
                    0, it["name"] as? String ?: "", (it["unitPrice"] as? Number)?.toInt() ?: 0,
                    (it["quantity"] as? Number)?.toInt() ?: 0, (it["subtotal"] as? Number)?.toInt() ?: 0
                )
            }
            val paymentMethods = payments.map {
                eloom.holybean.data.model.PaymentMethod(
                    it["method"] as? String ?: "", (it["amount"] as? Number)?.toInt() ?: 0
                )
            }
            val delta = OrderAggregation.rollupDelta(cartItems, paymentMethods)

            val batch = db.batch()
            batch.update(orderRef, "creditStatus", FirestoreSchema.CREDIT_SETTLED)
            batch.update(
                db.collection(FirestoreSchema.DAY_SUMMARIES).document(date),
                "orders.$num.creditStatus", FirestoreSchema.CREDIT_SETTLED
            )
            batch.update(
                db.collection(FirestoreSchema.AGGREGATES).document(FirestoreSchema.OPEN_CREDITS_DOC),
                FieldPath.of("items", FirestoreSchema.creditKey(date, num)), FieldValue.delete()
            )
            applyRollupDelta(batch, date, delta, sign = 1)
            batch.commit().await()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
```

- [ ] **Step 2: 컴파일 확인**

Run: `cd /Users/benn/dev/personal/HolyBean/android && ./gradlew :app:compileDebugKotlin -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add android/app/src/main/java/eloom/holybean/data/repository/FirestoreRepository.kt
git commit -m "feat: setCreditOrderPaid settlement fan-out"
```

### Task 4.3: deleteOrder — 삭제 팬아웃

**Files:**
- Modify: `android/app/src/main/java/eloom/holybean/data/repository/FirestoreRepository.kt`

- [ ] **Step 1: 메서드 추가**

```kotlin
    /** 주문 삭제: 정산분이면 reportRollups 감산, 미수면 openCredits 제거. lastOrderNum은 되돌리지 않음(번호 재사용 금지). */
    suspend fun deleteOrder(date: String, num: Int): Boolean {
        return try {
            val orderRef = db.collection(FirestoreSchema.ORDERS)
                .document(FirestoreSchema.orderId(date, num))
            val snap = orderRef.get().await()
            if (!snap.exists()) return false
            val creditStatus = (snap.getLong("creditStatus") ?: 0L).toInt()

            val batch = db.batch()
            batch.delete(orderRef)
            batch.update(
                db.collection(FirestoreSchema.DAY_SUMMARIES).document(date),
                FieldPath.of("orders", num.toString()), FieldValue.delete()
            )
            if (creditStatus == FirestoreSchema.CREDIT_SETTLED) {
                @Suppress("UNCHECKED_CAST")
                val items = (snap.get("items") as? List<Map<String, Any>>) ?: emptyList()
                @Suppress("UNCHECKED_CAST")
                val payments = (snap.get("payments") as? List<Map<String, Any>>) ?: emptyList()
                val cartItems = items.map {
                    eloom.holybean.data.model.CartItem(
                        0, it["name"] as? String ?: "", (it["unitPrice"] as? Number)?.toInt() ?: 0,
                        (it["quantity"] as? Number)?.toInt() ?: 0, (it["subtotal"] as? Number)?.toInt() ?: 0
                    )
                }
                val paymentMethods = payments.map {
                    eloom.holybean.data.model.PaymentMethod(
                        it["method"] as? String ?: "", (it["amount"] as? Number)?.toInt() ?: 0
                    )
                }
                applyRollupDelta(batch, date, OrderAggregation.rollupDelta(cartItems, paymentMethods), sign = -1)
            } else {
                batch.update(
                    db.collection(FirestoreSchema.AGGREGATES).document(FirestoreSchema.OPEN_CREDITS_DOC),
                    FieldPath.of("items", FirestoreSchema.creditKey(date, num)), FieldValue.delete()
                )
            }
            batch.commit().await()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
```

- [ ] **Step 2: 컴파일 확인**

Run: `cd /Users/benn/dev/personal/HolyBean/android && ./gradlew :app:compileDebugKotlin -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add android/app/src/main/java/eloom/holybean/data/repository/FirestoreRepository.kt
git commit -m "feat: deleteOrder fan-out"
```

### Task 4.4: 쓰기 연산 에뮬레이터 통합 검증 (수동 게이트)

**Files:** 없음

- [ ] **Step 1: 시나리오 검증 (에뮬레이터)**
  - 일반결제 주문 저장 → `orders`/`daySummaries`/`reportRollups` 갱신, `lastOrderNum` 증가
  - 외상 주문 저장 → `openCredits` 추가, `reportRollups` 미변동
  - 외상 정산 → `openCredits` 제거, 원 주문일 `reportRollups` 가산, `creditStatus=0`
  - 정산분 삭제 → `reportRollups` 감산, `daySummaries` 항목 제거
  - 미수분 삭제 → `openCredits` 제거
  - 비행기모드(오프라인)에서 주문 저장 → UI 즉시 진행, 복구 시 동기화

Expected: 각 시나리오 후 에뮬레이터 문서 상태가 불변식과 일치. `reportRollups` 합계 = `orders` 직접 집계.

---

## Phase 5 — ViewModel/도메인 연결 + Room 제거 (마일스톤 5)

### Task 5.1: MenuItem Room 어노테이션 제거

**Files:**
- Modify: `android/app/src/main/java/eloom/holybean/data/model/MenuItem.kt`

- [ ] **Step 1: 순수 데이터 클래스로 교체**

```kotlin
package eloom.holybean.data.model

data class MenuItem(
    val id: Int,
    var name: String,
    var price: Int,
    var order: Int,      // Firestore 필드명 placement
    var inuse: Boolean
)
```

- [ ] **Step 2: Commit (컴파일은 5.2 이후 통과)**

```bash
git add android/app/src/main/java/eloom/holybean/data/model/MenuItem.kt
git commit -m "refactor: drop Room annotations from MenuItem"
```

### Task 5.2: MenuRepository — Firestore 기반 재작성

**Files:**
- Modify: `android/app/src/main/java/eloom/holybean/data/repository/MenuRepository.kt`

> 공개 메서드 시그니처는 보존(HomeViewModel·MenuManagementViewModel이 의존). id/placement 할당과 정렬은 `menu/current` 인메모리 리스트 기반으로 재구현. 기존 AWS 동기화 메서드(`saveMenuListToServer`/`getLastedSavedMenuList`)는 의미가 사라지므로 MenuManagementViewModel에서 제거(Task 5.5).

- [ ] **Step 1: 전체 교체**

```kotlin
package eloom.holybean.data.repository

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import eloom.holybean.data.firestore.FirestoreSchema
import eloom.holybean.data.model.MenuItem
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MenuRepository @Inject constructor(
    private val db: FirebaseFirestore
) {
    private fun menuDoc() = db.collection(FirestoreSchema.MENU).document(FirestoreSchema.MENU_CURRENT_DOC)

    private fun parse(raw: Any?): List<MenuItem> {
        @Suppress("UNCHECKED_CAST")
        val items = (raw as? List<Map<String, Any>>) ?: emptyList()
        return items.map {
            MenuItem(
                id = (it["id"] as? Number)?.toInt() ?: 0,
                name = it["name"] as? String ?: "",
                price = (it["price"] as? Number)?.toInt() ?: 0,
                order = (it["placement"] as? Number)?.toInt() ?: 0,
                inuse = it["inuse"] as? Boolean ?: true
            )
        }
    }

    private fun serialize(items: List<MenuItem>): List<Map<String, Any>> = items.map {
        mapOf("id" to it.id, "name" to it.name, "price" to it.price, "placement" to it.order, "inuse" to it.inuse)
    }

    /** menu/current 실시간 구독. */
    fun getMenuList(): Flow<List<MenuItem>> = callbackFlow {
        val reg = menuDoc().addSnapshotListener { snap, err ->
            if (err != null) { close(err); return@addSnapshotListener }
            trySend(parse(snap?.get("items")).sortedBy { it.id })
        }
        awaitClose { reg.remove() }
    }

    suspend fun getMenuListSync(): List<MenuItem> =
        parse(menuDoc().get().await().get("items")).sortedBy { it.id }

    private suspend fun writeAll(items: List<MenuItem>) {
        menuDoc().set(mapOf("items" to serialize(items), "updatedAt" to FieldValue.serverTimestamp())).await()
    }

    suspend fun overwriteMenuList(menuList: List<MenuItem>) = writeAll(menuList.sortedBy { it.id })

    /** 카테고리 내 placement 변경 저장: 해당 항목들의 order 갱신 후 전체 재기록. */
    suspend fun saveMenuOrders(items: List<MenuItem>) {
        val current = getMenuListSync().associateBy { it.id }.toMutableMap()
        items.forEach { current[it.id] = it }
        writeAll(current.values.sortedBy { it.id })
    }

    suspend fun updateSpecificMenu(item: MenuItem) {
        val current = getMenuListSync().associateBy { it.id }.toMutableMap()
        current[item.id] = item
        writeAll(current.values.sortedBy { it.id })
    }

    suspend fun addMenu(item: MenuItem) {
        val current = getMenuListSync().filter { it.id != item.id }
        writeAll((current + item).sortedBy { it.id })
    }

    suspend fun isValidMenuName(newName: String): Boolean =
        getMenuListSync().none { it.name == newName }

    suspend fun getNextAvailableIdForCategory(category: Int): Int =
        nextAvailable(getMenuListSync().map { it.id }, category)

    suspend fun getNextAvailablePlacementForCategory(category: Int): Int =
        nextAvailable(getMenuListSync().map { it.order }, category)

    private fun nextAvailable(values: List<Int>, category: Int): Int {
        val startRange = category * 1000 + 1
        val endRange = (category + 1) * 1000 - 1
        val sorted = values.filter { it in startRange..endRange }.sorted()
        var next = startRange
        for (v in sorted) {
            if (v > next) break
            next = v + 1
        }
        return if (next <= endRange) next else -1
    }
}
```

- [ ] **Step 2: 컴파일 확인 (DatabaseModule 삭제 전까지 일부 미사용 경고 가능)**

Run: `cd /Users/benn/dev/personal/HolyBean/android && ./gradlew :app:compileDebugKotlin -q`
Expected: BUILD SUCCESSFUL (MenuDao/MenuDatabase는 5.4에서 삭제).

- [ ] **Step 3: Commit**

```bash
git add android/app/src/main/java/eloom/holybean/data/repository/MenuRepository.kt
git commit -m "refactor: rewrite MenuRepository on firestore menu/current"
```

### Task 5.3: 디스패처 provider를 CoroutineModule로 이관

**Files:**
- Create: `android/app/src/main/java/eloom/holybean/di/CoroutineModule.kt`
- Delete: `android/app/src/main/java/eloom/holybean/network/NetworkModule.kt`

- [ ] **Step 1: CoroutineModule 작성 (NetworkModule의 디스패처 provider만 이관)**

```kotlin
package eloom.holybean.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CoroutineModule {

    @Provides @Singleton @Named("IO")
    fun provideIODispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides @Singleton @Named("Printer")
    fun providePrinterDispatcher(): CoroutineDispatcher = Dispatchers.IO.limitedParallelism(2)

    @Provides @Singleton @Named("ApplicationScope")
    fun provideApplicationScope(
        @Named("Printer") printerDispatcher: CoroutineDispatcher
    ): CoroutineScope = CoroutineScope(SupervisorJob() + printerDispatcher)
}
```

- [ ] **Step 2: NetworkModule 삭제**

Run: `rm /Users/benn/dev/personal/HolyBean/android/app/src/main/java/eloom/holybean/network/NetworkModule.kt`

- [ ] **Step 3: 컴파일 확인**

Run: `cd /Users/benn/dev/personal/HolyBean/android && ./gradlew :app:compileDebugKotlin -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add android/app/src/main/java/eloom/holybean/di/CoroutineModule.kt
git rm android/app/src/main/java/eloom/holybean/network/NetworkModule.kt
git commit -m "refactor: move dispatcher providers to CoroutineModule"
```

### Task 5.4: Room·네트워크 잔재 삭제

**Files:**
- Delete: `data/repository/MenuDatabase.kt`, `data/repository/MenuDao.kt`, `di/DatabaseModule.kt`
- Delete: `network/ApiService.kt`, `network/RetrofitClient.kt`, `network/dto/*`
- Delete: `data/repository/LambdaRepository.kt`
- Delete: `data/repository/LambdaRepositoryTest.kt`, `data/repository/MenuRepositoryTest.kt`
- Modify: `android/app/build.gradle.kts`

- [ ] **Step 1: 소스 삭제**

```bash
cd /Users/benn/dev/personal/HolyBean/android/app/src/main/java/eloom/holybean
rm data/repository/MenuDatabase.kt data/repository/MenuDao.kt di/DatabaseModule.kt
rm data/repository/LambdaRepository.kt
rm network/ApiService.kt network/RetrofitClient.kt
rm -r network/dto
cd /Users/benn/dev/personal/HolyBean/android/app/src/test/kotlin/eloom/holybean
rm data/repository/LambdaRepositoryTest.kt data/repository/MenuRepositoryTest.kt
```

- [ ] **Step 2: build.gradle.kts에서 Retrofit·Gson·Room·BuildConfig 제거**

다음 의존성 줄 삭제:
```kotlin
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.google.code.gson:gson:2.13.2")
```
Room 블록 삭제:
```kotlin
    val room_version = "2.8.0"
    implementation("androidx.room:room-runtime:$room_version")
    ksp("androidx.room:room-compiler:$room_version")
    implementation("androidx.room:room-ktx:$room_version")
```
`defaultConfig`에서 두 줄 삭제:
```kotlin
        buildConfigField("String", "API_KEY", getApiKey("apikey"))
        buildConfigField("String", "BASE_URL", "\"https://vk0i6j4tfi.execute-api.ap-northeast-2.amazonaws.com\"")
```
그리고 `getApiKey` 함수와 상단 `gradleLocalProperties` import 삭제.

- [ ] **Step 3: 컴파일 확인 (ViewModel 수정 전이라 실패 예상 — 5.5에서 해소)**

Run: `cd /Users/benn/dev/personal/HolyBean/android && ./gradlew :app:compileDebugKotlin -q`
Expected: FAIL — ViewModel들이 아직 `LambdaRepository`/`ApiService` 참조. (다음 태스크에서 수정)

- [ ] **Step 4: 커밋은 5.5 이후 함께 (잔재 삭제 + ViewModel 교체가 한 컴파일 단위)**

### Task 5.5: ViewModel 의존성 교체

**Files:**
- Modify: `ui/home/HomeViewModel.kt`, `ui/orderlist/OrdersViewModel.kt`, `ui/credits/CreditsViewModel.kt`, `ui/menumanagement/MenuManagementViewModel.kt`, `ui/report/ReportViewModel.kt`

- [ ] **Step 1: HomeViewModel — import·주입·호출 교체**

`import eloom.holybean.data.repository.LambdaRepository` → `import eloom.holybean.data.repository.FirestoreRepository`
생성자 파라미터 `private val lambdaRepository: LambdaRepository,` → `private val firestoreRepository: FirestoreRepository,`
본문 `lambdaRepository.getOrderNumber()` → `firestoreRepository.getOrderNumber()`
`lambdaRepository.postOrder(data)` → `firestoreRepository.postOrder(data)` (postOrder는 이제 비-suspend; 기존 try/catch 유지 가능, navigate는 그대로)

- [ ] **Step 2: OrdersViewModel — 동일 교체**

`LambdaRepository` import·주입명 → `FirestoreRepository`/`firestoreRepository`, 호출부 `getOrdersOfDay()`/`getOrderDetail()`/`deleteOrder()` 접두사 교체.

- [ ] **Step 3: CreditsViewModel — 동일 교체**

`getCreditsList()`/`getOrderDetail()`/`setCreditOrderPaid()` 접두사 교체.

- [ ] **Step 4: ReportViewModel — ApiService 제거, FirestoreRepository.getReport 사용**

`import eloom.holybean.network.ApiService` 삭제, `import eloom.holybean.data.repository.FirestoreRepository` 추가.
생성자 `private val apiService: ApiService,` → `private val firestoreRepository: FirestoreRepository,`
`loadReportData`의 try 블록 본문을 교체:
```kotlin
            try {
                _uiState.update { it.copy(isLoading = true, reportTitle = "$startDate ~ $endDate") }
                val report = firestoreRepository.getReport(startDate, endDate)
                _uiState.update {
                    it.copy(
                        reportDetailData = report.menuSales,
                        reportData = report.paymentSales,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false) }
                _uiEvent.tryEmit(ReportUiEvent.ShowError("리포트를 불러오는데 실패했습니다: ${e.localizedMessage}"))
            }
```

- [ ] **Step 5: MenuManagementViewModel — AWS 동기화 메서드 제거**

`import eloom.holybean.data.repository.LambdaRepository` 삭제, 생성자에서 `private val lambdaRepository: LambdaRepository,` 삭제.
`saveMenuListToServer()`와 `getMenuListFromServer()` 메서드 전체 삭제(메뉴는 이제 Firestore 단일 소스라 별도 서버 동기화 불필요). 이 메서드를 호출하던 Fragment 버튼이 있으면 제거(다음 Step에서 확인).

- [ ] **Step 6: MenuManagementFragment의 동기화 버튼 정리**

Run: `grep -rn "saveMenuListToServer\|getMenuListFromServer" /Users/benn/dev/personal/HolyBean/android/app/src/main`
Expected: ViewModel 외 참조가 있으면(Fragment 버튼 리스너) 해당 핸들러와 레이아웃 버튼을 제거. 참조 0이 되도록 정리.

- [ ] **Step 7: 전체 컴파일 확인**

Run: `cd /Users/benn/dev/personal/HolyBean/android && ./gradlew :app:compileDebugKotlin -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Commit (5.4 삭제 + 5.5 교체 통합)**

```bash
cd /Users/benn/dev/personal/HolyBean
git add -A android/app
git commit -m "refactor: switch ViewModels to FirestoreRepository, remove AWS/Room layer"
```

### Task 5.6: ViewModel 테스트 갱신

**Files:**
- Modify: `ui/home/HomeViewModelTest.kt`, `ui/orderlist/OrdersViewModelTest.kt`, `ui/credits/CreditsViewModelTest.kt`, `ui/menumanagement/MenuManagementViewModelTest.kt`, `ui/report/ReportViewModelTest.kt`

> 기존 패턴: MockK로 협력자 mock. `LambdaRepository`/`ApiService` mock을 `FirestoreRepository` mock으로 교체하고, `coEvery`/`every`(postOrder는 비-suspend → `every`), `coVerify`/`verify` 대상명을 맞춘다. MenuManagementViewModelTest는 삭제한 두 메서드 관련 테스트 제거.

- [ ] **Step 1: 각 테스트에서 mock 타입·스텁 교체**

각 `*ViewModelTest.kt`에서:
- `private val ... : LambdaRepository = mockk()` → `FirestoreRepository = mockk()` (변수명 자유, ViewModel 생성자 인자 순서 유지)
- `ReportViewModelTest`: `ApiService` mock → `FirestoreRepository` mock, `coEvery { apiService.getReport(...) } returns Response.success(dto)` → `coEvery { repo.getReport(any(), any()) } returns SalesReport(menuList, payMap)` 형태로 변경, 검증도 도메인 모델 기준으로.
- `HomeViewModelTest`: `postOrder`는 비-suspend이므로 `coEvery`→`every`, `coVerify`→`verify`.
- `MenuManagementViewModelTest`: `saveMenuListToServer`/`getMenuListFromServer` 관련 테스트 삭제, `LambdaRepository` 참조 제거.

- [ ] **Step 2: 단위 테스트 실행**

Run: `cd /Users/benn/dev/personal/HolyBean/android && ./gradlew :app:testDebugUnitTest -q`
Expected: PASS (모든 테스트). 실패 시 systematic-debugging.

- [ ] **Step 3: Commit**

```bash
git add android/app/src/test
git commit -m "test: update ViewModel tests for FirestoreRepository"
```

### Task 5.7: 전체 화면 수동 검증 (수동 게이트)

**Files:** 없음

- [ ] **Step 1: 실 디바이스에서 end-to-end**

주문 접수→인쇄, 주문목록 조회/삭제, 외상 조회/정산, 리포트 출력, 메뉴 추가/수정/순서변경/활성토글, 오프라인 주문 후 재동기화를 모두 확인.
Expected: 모든 플로우 정상, Firestore 콘솔 문서 상태가 불변식과 일치, Crashlytics에 신규 크래시 없음.

---

## Phase 6 — DynamoDB → Firestore ETL & 검증 (마일스톤 6)

> **수동 선행 작업:** DynamoDB `holybean`, `holybean-menu` 테이블을 JSON으로 export하여 `firebase/data/holybean.json`, `firebase/data/holybean-menu.json`에 배치. Admin SDK 서비스 계정 키를 `firebase/serviceAccount.json`에 배치(gitignore).

### Task 6.0: 비공개 입력 gitignore

**Files:**
- Modify: `.gitignore`

- [ ] **Step 1: 추가**

```gitignore
# Firebase ETL secrets/data
firebase/serviceAccount.json
firebase/data/
firebase/node_modules/
```

- [ ] **Step 2: Commit**

```bash
git add .gitignore
git commit -m "chore: gitignore firebase etl secrets and data"
```

### Task 6.1: 스키마 상수(TS)

**Files:**
- Create: `firebase/src/schema.ts`

- [ ] **Step 1: 작성**

```typescript
export const COLLECTIONS = {
  orders: "orders",
  daySummaries: "daySummaries",
  reportRollups: "reportRollups",
  aggregates: "aggregates",
  menu: "menu",
} as const;
export const OPEN_CREDITS_DOC = "openCredits";
export const MENU_CURRENT_DOC = "current";
export const CREDIT_SETTLED = 0;
export const CREDIT_UNPAID = 1;

export const orderId = (date: string, num: number) => `${date}_${num}`;
export const creditKey = (date: string, num: number) => `${date}_${num}`;
```

- [ ] **Step 2: Commit**

```bash
git add firebase/src/schema.ts
git commit -m "feat(etl): ts schema constants"
```

### Task 6.2: DynamoDB 매핑 (TDD)

**Files:**
- Create: `firebase/test/mapDynamo.test.ts`
- Create: `firebase/src/mapDynamo.ts`

> DynamoDB의 `orderItems`(itemName/quantity/subtotal/unitPrice)·`paymentMethods`(method/amount)는 이미 Firestore `orders` 형태와 거의 1:1. 매핑은 필드 추림 + `createdAt` 보강.

- [ ] **Step 1: 실패 테스트 작성**

`firebase/test/mapDynamo.test.ts`:
```typescript
import { describe, expect, it } from "vitest";
import { mapDynamoOrder, mapDynamoMenu } from "../src/mapDynamo.js";

describe("mapDynamoOrder", () => {
  it("orders 문서 형태로 매핑", () => {
    const src = {
      orderDate: "2026-05-23", orderNum: 3, totalAmount: 9000, customerName: "홍길동", creditStatus: 0,
      orderItems: [{ itemName: "아메리카노", quantity: 2, subtotal: 9000, unitPrice: 4500 }],
      paymentMethods: [{ method: "현금", amount: 9000 }],
    };
    const doc = mapDynamoOrder(src);
    expect(doc.id).toBe("2026-05-23_3");
    expect(doc.data.items).toEqual([{ name: "아메리카노", quantity: 2, subtotal: 9000, unitPrice: 4500 }]);
    expect(doc.data.payments).toEqual([{ method: "현금", amount: 9000 }]);
    expect(doc.data.creditStatus).toBe(0);
    expect(doc.data.createdAt).toBeInstanceOf(Date);
  });
});

describe("mapDynamoMenu", () => {
  it("menu/current items로 매핑", () => {
    const items = mapDynamoMenu([{ id: 1001, name: "아메리카노", price: 4500, order: 1001, inuse: true }]);
    expect(items).toEqual([{ id: 1001, name: "아메리카노", price: 4500, placement: 1001, inuse: true }]);
  });
});
```

- [ ] **Step 2: 실패 확인**

Run: `cd /Users/benn/dev/personal/HolyBean/firebase && npm test -- mapDynamo`
Expected: FAIL — `mapDynamo.ts` 미존재.

- [ ] **Step 3: 구현**

`firebase/src/mapDynamo.ts`:
```typescript
import { orderId } from "./schema.js";

export interface OrderDoc { id: string; data: Record<string, unknown>; }

export function mapDynamoOrder(src: any): OrderDoc {
  return {
    id: orderId(src.orderDate, src.orderNum),
    data: {
      orderDate: src.orderDate,
      orderNum: src.orderNum,
      totalAmount: src.totalAmount,
      customerName: src.customerName ?? "",
      creditStatus: src.creditStatus,
      items: (src.orderItems ?? []).map((i: any) => ({
        name: i.itemName, quantity: i.quantity, subtotal: i.subtotal, unitPrice: i.unitPrice,
      })),
      payments: (src.paymentMethods ?? []).map((p: any) => ({ method: p.method, amount: p.amount })),
      createdAt: new Date(`${src.orderDate}T00:00:00+09:00`),
    },
  };
}

export function mapDynamoMenu(items: any[]): Record<string, unknown>[] {
  return items.map((m) => ({
    id: m.id, name: m.name, price: m.price, placement: m.order ?? m.placement, inuse: m.inuse,
  }));
}
```

- [ ] **Step 4: 통과 확인**

Run: `cd /Users/benn/dev/personal/HolyBean/firebase && npm test -- mapDynamo`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add firebase/src/mapDynamo.ts firebase/test/mapDynamo.test.ts
git commit -m "feat(etl): dynamo->firestore mapping with tests"
```

### Task 6.3: 파생 문서 재생성 (TDD — FR-6)

**Files:**
- Create: `firebase/test/rebuild.test.ts`
- Create: `firebase/src/rebuild.ts`

> Android `OrderAggregation`과 동일한 불변식을 TS로 재현: orderMethod 결합, creditStatus==0만 reportRollups, 미수는 openCredits, daySummaries.lastOrderNum = 일자별 최대 orderNum.

- [ ] **Step 1: 실패 테스트 작성**

```typescript
import { describe, expect, it } from "vitest";
import { rebuildDerived } from "../src/rebuild.js";

const settled = {
  orderDate: "2026-05-23", orderNum: 1, totalAmount: 9000, customerName: "A", creditStatus: 0,
  items: [{ name: "아메리카노", quantity: 2, subtotal: 9000, unitPrice: 4500 }],
  payments: [{ method: "현금", amount: 9000 }],
};
const credit = {
  orderDate: "2026-05-23", orderNum: 2, totalAmount: 5000, customerName: "B", creditStatus: 1,
  items: [{ name: "라떼", quantity: 1, subtotal: 5000, unitPrice: 5000 }],
  payments: [{ method: "외상", amount: 5000 }],
};

describe("rebuildDerived", () => {
  const out = rebuildDerived([settled, credit]);

  it("daySummaries에 lastOrderNum과 항목", () => {
    const day = out.daySummaries["2026-05-23"];
    expect(day.lastOrderNum).toBe(2);
    expect(day.orders["1"]).toEqual({ customerName: "A", totalAmount: 9000, orderMethod: "현금", creditStatus: 0 });
    expect(day.orders["2"].orderMethod).toBe("외상");
  });

  it("reportRollups는 정산분만 집계", () => {
    const r = out.reportRollups["2026-05-23"];
    expect(r.menuSales["아메리카노"]).toEqual({ quantity: 2, sales: 9000 });
    expect(r.menuSales["라떼"]).toBeUndefined();
    expect(r.paymentSales["현금"]).toBe(9000);
    expect(r.total).toBe(9000);
  });

  it("openCredits는 미수만", () => {
    expect(out.openCredits.items["2026-05-23_2"]).toEqual({
      customerName: "B", totalAmount: 5000, orderNum: 2, orderDate: "2026-05-23",
    });
    expect(out.openCredits.items["2026-05-23_1"]).toBeUndefined();
  });
});
```

- [ ] **Step 2: 실패 확인**

Run: `cd /Users/benn/dev/personal/HolyBean/firebase && npm test -- rebuild`
Expected: FAIL — `rebuild.ts` 미존재.

- [ ] **Step 3: 구현**

`firebase/src/rebuild.ts`:
```typescript
import { CREDIT_SETTLED, creditKey } from "./schema.js";

interface OrderData {
  orderDate: string; orderNum: number; totalAmount: number; customerName: string; creditStatus: number;
  items: { name: string; quantity: number; subtotal: number; unitPrice: number }[];
  payments: { method: string; amount: number }[];
}

const orderMethodLabel = (p: OrderData["payments"]) =>
  p.length === 0 ? "Unknown" : p.map((x) => x.method).join("+");

export function rebuildDerived(orders: OrderData[]) {
  const daySummaries: Record<string, { lastOrderNum: number; orders: Record<string, any> }> = {};
  const reportRollups: Record<string, { menuSales: Record<string, { quantity: number; sales: number }>; paymentSales: Record<string, number>; total: number }> = {};
  const openCredits = { items: {} as Record<string, any> };

  for (const o of orders) {
    const day = (daySummaries[o.orderDate] ??= { lastOrderNum: 0, orders: {} });
    day.lastOrderNum = Math.max(day.lastOrderNum, o.orderNum);
    day.orders[String(o.orderNum)] = {
      customerName: o.customerName, totalAmount: o.totalAmount,
      orderMethod: orderMethodLabel(o.payments), creditStatus: o.creditStatus,
    };

    if (o.creditStatus === CREDIT_SETTLED) {
      const r = (reportRollups[o.orderDate] ??= { menuSales: {}, paymentSales: {}, total: 0 });
      for (const it of o.items) {
        const m = (r.menuSales[it.name] ??= { quantity: 0, sales: 0 });
        m.quantity += it.quantity; m.sales += it.subtotal;
      }
      for (const p of o.payments) {
        r.paymentSales[p.method] = (r.paymentSales[p.method] ?? 0) + p.amount;
        r.total += p.amount;
      }
    } else {
      openCredits.items[creditKey(o.orderDate, o.orderNum)] = {
        customerName: o.customerName, totalAmount: o.totalAmount, orderNum: o.orderNum, orderDate: o.orderDate,
      };
    }
  }
  return { daySummaries, reportRollups, openCredits };
}
```

- [ ] **Step 4: 통과 확인**

Run: `cd /Users/benn/dev/personal/HolyBean/firebase && npm test -- rebuild`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add firebase/src/rebuild.ts firebase/test/rebuild.test.ts
git commit -m "feat(etl): derived-doc rebuild with tests (FR-6)"
```

### Task 6.4: ETL 실행 스크립트

**Files:**
- Create: `firebase/src/etl.ts`

- [ ] **Step 1: 작성**

```typescript
import { cert, initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import { readFileSync } from "node:fs";
import { COLLECTIONS, MENU_CURRENT_DOC, OPEN_CREDITS_DOC } from "./schema.js";
import { mapDynamoMenu, mapDynamoOrder } from "./mapDynamo.js";
import { rebuildDerived } from "./rebuild.js";

const sa = JSON.parse(readFileSync("serviceAccount.json", "utf8"));
initializeApp({ credential: cert(sa) });
const db = getFirestore();

async function main() {
  const ordersRaw: any[] = JSON.parse(readFileSync("data/holybean.json", "utf8"));
  const menuRaw: any[] = JSON.parse(readFileSync("data/holybean-menu.json", "utf8"));

  // 1) orders 원본 적재
  let batch = db.batch(); let n = 0;
  const orderDatas: any[] = [];
  for (const src of ordersRaw) {
    const { id, data } = mapDynamoOrder(src);
    orderDatas.push(data);
    batch.set(db.collection(COLLECTIONS.orders).doc(id), data);
    if (++n % 400 === 0) { await batch.commit(); batch = db.batch(); }
  }
  await batch.commit();
  console.log(`orders 적재: ${ordersRaw.length}`);

  // 2) menu/current (holybean-menu 최신 항목 사용 — 가장 최근 timestamp)
  const latestMenu = menuRaw.sort((a, b) => String(b.timestamp).localeCompare(String(a.timestamp)))[0];
  await db.collection(COLLECTIONS.menu).doc(MENU_CURRENT_DOC).set({
    items: mapDynamoMenu(latestMenu.menulist ?? latestMenu.items ?? []),
    updatedAt: new Date(),
  });
  console.log("menu/current 적재 완료");

  // 3) 파생 재생성
  const { daySummaries, reportRollups, openCredits } = rebuildDerived(orderDatas);
  let b2 = db.batch(); let m = 0;
  for (const [date, doc] of Object.entries(daySummaries)) {
    b2.set(db.collection(COLLECTIONS.daySummaries).doc(date), doc);
    if (++m % 400 === 0) { await b2.commit(); b2 = db.batch(); }
  }
  for (const [date, doc] of Object.entries(reportRollups)) {
    b2.set(db.collection(COLLECTIONS.reportRollups).doc(date), doc);
    if (++m % 400 === 0) { await b2.commit(); b2 = db.batch(); }
  }
  b2.set(db.collection(COLLECTIONS.aggregates).doc(OPEN_CREDITS_DOC), openCredits);
  await b2.commit();
  console.log(`파생 재생성: daySummaries=${Object.keys(daySummaries).length}, reportRollups=${Object.keys(reportRollups).length}`);
}

main().then(() => process.exit(0)).catch((e) => { console.error(e); process.exit(1); });
```

- [ ] **Step 2: 타입 체크**

Run: `cd /Users/benn/dev/personal/HolyBean/firebase && npx tsc --noEmit`
Expected: 타입 에러 없음.

- [ ] **Step 3: 에뮬레이터 드라이런**

Run: `cd /Users/benn/dev/personal/HolyBean/firebase && FIRESTORE_EMULATOR_HOST=127.0.0.1:8080 firebase emulators:exec --only firestore "npm run etl"`
Expected: 적재/재생성 카운트 로그 출력, 에러 없음. (에뮬레이터에서는 serviceAccount 무시되나 Admin SDK는 동작 — 필요 시 projectId만 지정)

- [ ] **Step 4: Commit**

```bash
git add firebase/src/etl.ts
git commit -m "feat(etl): admin sdk load + derived rebuild runner"
```

### Task 6.5: 수치 검증 스크립트 (FR-3)

**Files:**
- Create: `firebase/src/verify.ts`

- [ ] **Step 1: 작성**

```typescript
import { initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import { COLLECTIONS } from "./schema.js";

initializeApp();
const db = getFirestore();

async function main() {
  // orders 직접 집계 (creditStatus==0) vs reportRollups 합계 대조
  const snap = await db.collection(COLLECTIONS.orders).get();
  let directTotal = 0;
  const directByDate: Record<string, number> = {};
  snap.forEach((d) => {
    const o = d.data() as any;
    if (o.creditStatus === 0) {
      const sum = (o.payments ?? []).reduce((s: number, p: any) => s + p.amount, 0);
      directTotal += sum;
      directByDate[o.orderDate] = (directByDate[o.orderDate] ?? 0) + sum;
    }
  });

  const rollups = await db.collection(COLLECTIONS.reportRollups).get();
  let rollupTotal = 0;
  rollups.forEach((d) => { rollupTotal += (d.data() as any).total ?? 0; });

  console.log(`orders 직접 정산 합계: ${directTotal}`);
  console.log(`reportRollups total 합계: ${rollupTotal}`);
  console.log(directTotal === rollupTotal ? "✅ 일치" : "❌ 불일치");
  console.log(`주문 건수: ${snap.size}`);
}
main().then(() => process.exit(0)).catch((e) => { console.error(e); process.exit(1); });
```

- [ ] **Step 2: 검증 실행 (실 프로젝트 또는 에뮬레이터)**

Run: `cd /Users/benn/dev/personal/HolyBean/firebase && npm run verify`
Expected: "✅ 일치", 주문 건수 = DynamoDB export 건수. 불일치 시 ETL 매핑/재생성 점검.

- [ ] **Step 3: 구·신 리포트 동일구간 비교**

기존 AWS `/report?start&end`(폐기 전) 결과와 앱의 Firestore 리포트를 동일 구간으로 비교. 메뉴별 수량/매출, 결제수단 합계, "총합" 일치 확인.
Expected: 수치 동일(FR-3 충족).

- [ ] **Step 4: Commit**

```bash
git add firebase/src/verify.ts
git commit -m "feat(etl): numeric verification script (FR-3)"
```

---

## Phase 7 — 컷오버 & AWS 폐기 (마일스톤 7)

### Task 7.1: 프로덕션 ETL 실행 (수동 게이트)

- [ ] **Step 1:** 최신 DynamoDB export로 `npm run etl`을 실 프로젝트 대상으로 실행.
- [ ] **Step 2:** `npm run verify` 및 구·신 리포트 비교로 수치 일치 확인.
- [ ] **Step 3:** 보안 규칙이 배포되어 있는지(Task 2.3) 재확인.

### Task 7.2: 릴리스 빌드 + Crashlytics 매핑 검증

- [ ] **Step 1: 릴리스 빌드**

Run: `cd /Users/benn/dev/personal/HolyBean/android && ./gradlew :app:assembleRelease -q`
Expected: BUILD SUCCESSFUL. (릴리스는 `isMinifyEnabled=false`이나 Crashlytics 매핑 업로드 플러그인 동작 확인)

- [ ] **Step 2: Crashlytics 매핑 업로드 확인**

Run: `cd /Users/benn/dev/personal/HolyBean/android && ./gradlew :app:uploadCrashlyticsMappingFileRelease -q`
Expected: BUILD SUCCESSFUL. Firebase 콘솔 Crashlytics에서 매핑 파일 수신 확인(NFR-5).

### Task 7.3: 배포 & AWS 폐기 (수동 게이트)

- [ ] **Step 1:** Firestore 버전 앱을 매장 기기에 배포, 실사용 1일 모니터링(Crashlytics·Firestore 사용량/규칙 거부 로그).
- [ ] **Step 2:** 안정 확인 후 AWS API Gateway·Lambda 10개·DynamoDB 2테이블을 일정 기간 읽기 보존 후 해체(NFR-1). `_legacy/aws-go`는 참고용으로 리포지터리에 보존.
- [ ] **Step 3:** 최종 커밋/태그.

```bash
cd /Users/benn/dev/personal/HolyBean
git add -A
git commit -m "chore: firestore cutover complete"
```

---

## 요구사항 → 태스크 매핑 (Self-Review)

| 요구사항 | 충족 태스크 |
|---|---|
| FR-1 (10개 동작 동등 제공) | 3.5(읽기) + 4.1~4.3(쓰기) + 5.2(메뉴) + 5.5(연결) |
| FR-2 (로컬 채번) | 3.5 getOrderNumber (`daySummaries.lastOrderNum+1`, 서버 왕복 없음) |
| FR-3 (리포트 동일) | 3.3 ReportAggregation + 3.5 getReport + 6.5 수치검증 |
| FR-4 (menu/current 점읽기) | 5.2 getMenuListSync |
| FR-5 (원본 적재 후 파생 재생성 이관) | 6.2~6.4 |
| FR-6 (재생성 잡) | 6.3 rebuildDerived |
| NFR-1 (백엔드 제거) | 5.4 삭제 + 7.3 폐기 |
| NFR-2 (App Check+Auth+규칙) | 1.4 + 2.2 |
| NFR-3 (규칙 에뮬레이터 회귀) | 2.2 rules.test.ts |
| NFR-4 (오프라인) | 1.5 persistentCache + 4.4 오프라인 시나리오 |
| NFR-5 (Crashlytics 난독화 복원) | 1.3 + 7.2 |
| NFR-6 (설정값 비공개) | 1.1 + 6.0 |
| NFR-7 (핫패스 로컬 완결) | 4.1 postOrder(commit await 안 함) |

## 미결/리스크 (실행 중 확인)
- **App Check 디버그 토큰 등록** — 디버그 빌드에서 logcat의 토큰을 Firebase 콘솔에 등록해야 개발 중 거부되지 않음(Task 1.4).
- **에뮬레이터 연결** — Task 3.6/4.4에서 디버그 빌드를 에뮬레이터로 가리키는 임시 설정 vs 실 프로젝트 시드 중 택일.
- **문서 1MB 한도** — `daySummaries`/`reportRollups` 맵 폭증 시 분할(PRD §11). 현 규모 무관.
- **삭제 시 번호 재사용 금지** — `deleteOrder`는 `lastOrderNum`을 되돌리지 않음(Task 4.3 주석).
- **MenuManagementFragment 동기화 버튼** — Task 5.5 Step 6에서 잔존 참조 정리 필요.
