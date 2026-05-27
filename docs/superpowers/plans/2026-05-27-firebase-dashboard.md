# Firebase 매출 대시보드 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 카페 운영자가 로그인 후 하루를 골라 그날의 총매출·전체 잔 수·메뉴별 판매 내역을 보고 엑셀로 내려받는, 순수 정적 웹 대시보드를 만든다.

**Architecture:** 빌드 단계 없는 정적 SPA. 브라우저가 Firebase Auth로 로그인하고 Firestore Web SDK로 `reportRollups/{date}`를 직접 읽으며, SheetJS로 클라이언트에서 엑셀을 생성한다. Firebase Hosting에 정적 파일로 배포하고 기존 `firestore.rules`를 그대로 재사용한다. 서버 코드는 없다.

**Tech Stack:** Vanilla HTML/CSS/JS (ES modules), Firebase JS SDK 11 (modular, CDN), SheetJS (CDN), Pretendard(CDN), Firebase Hosting, Firebase Emulator Suite, Vitest(순수 로직 단위 테스트).

**설계 문서:** `docs/superpowers/specs/2026-05-27-firebase-dashboard-design.md`

**디렉터리 결정:** 대시보드 정적 파일은 기존 `firebase/` 서브시스템 안 `firebase/public/`에 둔다. Hosting 설정을 `firebase/firebase.json`에 추가하므로 한 폴더에서 배포·에뮬레이션이 끝난다. 아래 모든 명령은 `firebase/` 디렉터리에서 실행한다.

---

## File Structure

- Create: `firebase/public/index.html` — 마크업 + CDN 링크 (로그인 + 대시보드 화면)
- Create: `firebase/public/styles.css` — 흰 배경 미니멀 스타일
- Create: `firebase/public/transform.js` — 순수 데이터 변환 함수 (테스트 대상)
- Create: `firebase/public/firebase-init.js` — Firebase 초기화 + 에뮬레이터 연결, `{ auth, db }` export
- Create: `firebase/public/app.js` — 컨트롤러 (인증 + Firestore 읽기 + 날짜 이동 + 렌더 + 엑셀 export)
- Create: `firebase/public/firebase-config.example.js` — 웹 설정 템플릿 (커밋됨)
- Create(수동): `firebase/public/firebase-config.js` — 실제 웹 설정 (gitignore됨)
- Create: `firebase/test/transform.test.js` — `transform.js` 단위 테스트
- Create: `firebase/scripts/seedDashboard.mjs` — 에뮬레이터에 샘플 데이터 + 테스트 계정 시드
- Modify: `firebase/firebase.json` — `hosting` + `auth`/`hosting` 에뮬레이터 추가
- Modify: `.gitignore` — `firebase/public/firebase-config.js` 추가

---

## Task 1: Hosting & 에뮬레이터 설정 + 스캐폴드

**Files:**
- Modify: `firebase/firebase.json`
- Modify: `.gitignore`
- Create: `firebase/public/index.html` (임시 플레이스홀더)

- [ ] **Step 1: `firebase/firebase.json`에 hosting과 auth/hosting 에뮬레이터 추가**

전체 파일을 아래로 교체:

```json
{
  "firestore": {
    "rules": "firestore.rules",
    "indexes": "firestore.indexes.json"
  },
  "hosting": {
    "public": "public",
    "ignore": ["firebase.json", "**/.*", "**/node_modules/**"]
  },
  "emulators": {
    "firestore": { "port": 8080 },
    "auth": { "port": 9099 },
    "hosting": { "port": 5000 },
    "ui": { "enabled": true }
  }
}
```

- [ ] **Step 2: `.gitignore`에 웹 설정 파일 추가**

`.gitignore`의 `# Firebase ETL secrets/data` 블록 끝(`firebase/.firebase/` 줄 다음)에 추가:

```
firebase/public/firebase-config.js
```

- [ ] **Step 3: 임시 `firebase/public/index.html` 생성**

```html
<!DOCTYPE html>
<html lang="ko"><head><meta charset="utf-8"><title>HolyBean</title></head>
<body><p>스캐폴드 OK</p></body></html>
```

- [ ] **Step 4: Hosting 에뮬레이터로 서빙 확인**

Run (firebase/ 에서): `firebase emulators:start --only hosting`
브라우저로 `http://localhost:5000` 접속 → "스캐폴드 OK"가 보이면 성공. 확인 후 `Ctrl+C`로 종료.
Expected: 페이지에 "스캐폴드 OK" 표시.

- [ ] **Step 5: Commit**

```bash
git add firebase/firebase.json firebase/public/index.html .gitignore
git commit -m "feat(dashboard): add hosting config and scaffold public dir"
```

---

## Task 2: 순수 변환 모듈 (`transform.js`) — TDD

**Files:**
- Create: `firebase/public/transform.js`
- Test: `firebase/test/transform.test.js`

- [ ] **Step 1: 실패하는 테스트 작성 — `firebase/test/transform.test.js`**

```js
import { describe, it, expect } from "vitest";
import { salesRows, totalCups, exportAOA, formatDateLabel } from "../public/transform.js";

const rollup = {
  total: 142000,
  menuSales: {
    "카페라떼": { quantity: 11, sales: 38500 },
    "아메리카노": { quantity: 32, sales: 96000 },
    "쿠키": { quantity: 3, sales: 9000 },
  },
};

describe("salesRows", () => {
  it("개수 내림차순으로 정렬해 펴낸다", () => {
    expect(salesRows(rollup)).toEqual([
      { name: "아메리카노", quantity: 32, sales: 96000 },
      { name: "카페라떼", quantity: 11, sales: 38500 },
      { name: "쿠키", quantity: 3, sales: 9000 },
    ]);
  });
  it("menuSales가 없으면 빈 배열", () => {
    expect(salesRows({})).toEqual([]);
    expect(salesRows(null)).toEqual([]);
  });
});

describe("totalCups", () => {
  it("모든 행의 개수를 더한다", () => {
    expect(totalCups(salesRows(rollup))).toBe(46);
  });
  it("빈 배열은 0", () => {
    expect(totalCups([])).toBe(0);
  });
});

describe("exportAOA", () => {
  it("헤더 + 메뉴 행 + 합계 행을 만든다", () => {
    expect(exportAOA("2026-05-27", salesRows(rollup), rollup.total)).toEqual([
      ["메뉴", "개수", "금액"],
      ["아메리카노", 32, 96000],
      ["카페라떼", 11, 38500],
      ["쿠키", 3, 9000],
      ["합계", 46, 142000],
    ]);
  });
});

describe("formatDateLabel", () => {
  it("YYYY-MM-DD를 'M월 D일 (요일)'로 바꾼다", () => {
    // 2026-05-27은 화요일
    expect(formatDateLabel("2026-05-27")).toBe("5월 27일 (화)");
  });
});
```

- [ ] **Step 2: 테스트가 실패하는지 확인**

Run (firebase/ 에서): `npx vitest run test/transform.test.js`
Expected: FAIL — `transform.js`가 없어 import 에러.

- [ ] **Step 3: `firebase/public/transform.js` 구현**

```js
// 순수 데이터 변환 — DOM/Firebase 의존성 없음 (브라우저와 vitest 양쪽에서 import)

/** rollup.menuSales를 {name, quantity, sales} 배열로 펴서 개수 내림차순 정렬 */
export function salesRows(rollup) {
  const menuSales = (rollup && rollup.menuSales) || {};
  return Object.entries(menuSales)
    .map(([name, v]) => ({ name, quantity: v.quantity, sales: v.sales }))
    .sort((a, b) => b.quantity - a.quantity);
}

/** 판매 행 배열의 전체 잔 수 합 */
export function totalCups(rows) {
  return rows.reduce((sum, r) => sum + r.quantity, 0);
}

/** SheetJS aoa_to_sheet용 2차원 배열: 헤더 + 메뉴 행 + 합계 행 */
export function exportAOA(date, rows, total) {
  return [
    ["메뉴", "개수", "금액"],
    ...rows.map((r) => [r.name, r.quantity, r.sales]),
    ["합계", totalCups(rows), total],
  ];
}

/** "2026-05-27" -> "5월 27일 (화)" */
export function formatDateLabel(date) {
  const [y, m, d] = date.split("-").map(Number);
  const dow = ["일", "월", "화", "수", "목", "금", "토"][new Date(y, m - 1, d).getDay()];
  return `${m}월 ${d}일 (${dow})`;
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run (firebase/ 에서): `npx vitest run test/transform.test.js`
Expected: PASS — 4개 describe 블록 모두 통과.

- [ ] **Step 5: Commit**

```bash
git add firebase/public/transform.js firebase/test/transform.test.js
git commit -m "feat(dashboard): add tested pure transform module"
```

---

## Task 3: 웹 Firebase 설정 파일

**Files:**
- Create: `firebase/public/firebase-config.example.js`
- Create(수동): `firebase/public/firebase-config.js`

- [ ] **Step 1: `firebase/public/firebase-config.example.js` 생성 (커밋됨, 템플릿)**

```js
// Firebase 콘솔 > 프로젝트 설정 > 일반 > 웹 앱(</>) 등록 후 나오는 설정을 복사해
// 이 파일을 firebase-config.js 로 복사한 뒤 값을 채운다. firebase-config.js는 gitignore됨.
//
// 로컬 에뮬레이터 테스트만 할 때는 apiKey에 아무 문자열("demo-key")이나 넣어도 동작한다.
// projectId는 반드시 "holybean-e4201" 이어야 한다(에뮬레이터/실서버 동일).
export const firebaseConfig = {
  apiKey: "REPLACE_ME",
  authDomain: "holybean-e4201.firebaseapp.com",
  projectId: "holybean-e4201",
  appId: "REPLACE_ME",
};
```

- [ ] **Step 2: 로컬 `firebase-config.js` 생성 (수동, 커밋 안 함)**

에뮬레이터 테스트용으로 example을 복사해 더미 값으로 채운다:

```bash
cp firebase/public/firebase-config.example.js firebase/public/firebase-config.js
```

그 다음 `firebase/public/firebase-config.js`를 열어 `apiKey`를 `"demo-key"`, `appId`를 `"demo-app"`로 바꾼다. (실배포 시에는 콘솔의 실제 값으로 교체.)

> **App Check 주의:** 이 프로젝트는 안드로이드 앱에서 App Check를 쓴다. 만약 Firestore에 App Check **강제(enforcement)** 가 켜져 있으면, 웹 대시보드도 App Check(reCAPTCHA v3) 등록이 필요해 읽기가 막힐 수 있다. 실배포 단계(Task 8 이후)에서 콘솔의 App Check 설정을 확인하고, 강제가 켜져 있다면 웹 앱용 reCAPTCHA v3 사이트 키를 등록하거나 디버그 토큰을 추가한다. 에뮬레이터는 App Check를 강제하지 않으므로 로컬 검증에는 영향 없음.

- [ ] **Step 3: Commit (example만)**

```bash
git add firebase/public/firebase-config.example.js
git commit -m "feat(dashboard): add web firebase config template"
```

---

## Task 4: 에뮬레이터 시드 스크립트

**Files:**
- Create: `firebase/scripts/seedDashboard.mjs`

이 스크립트는 실행 중인 에뮬레이터에 샘플 `reportRollups` 문서들과 테스트 로그인 계정을 넣는다. 이후 Task 6~8의 수동 검증에 쓴다. (1회성 시드 스크립트이므로 단위 테스트는 작성하지 않는다.)

- [ ] **Step 1: `firebase/scripts/seedDashboard.mjs` 생성**

```js
// 에뮬레이터 전용 시드 스크립트.
// 사용법(별도 터미널, firebase/ 에서):
//   1) firebase emulators:start --only firestore,auth,hosting
//   2) node scripts/seedDashboard.mjs
import admin from "firebase-admin";

process.env.FIRESTORE_EMULATOR_HOST = "localhost:8080";
process.env.FIREBASE_AUTH_EMULATOR_HOST = "localhost:9099";

admin.initializeApp({ projectId: "holybean-e4201" });
const db = admin.firestore();
const auth = admin.auth();

const rollups = {
  "2026-05-25": {
    total: 120500,
    paymentSales: { cash: 120500 },
    menuSales: {
      "아메리카노": { quantity: 28, sales: 84000 },
      "카페라떼": { quantity: 9, sales: 31500 },
      "복숭아아이스티": { quantity: 1, sales: 5000 },
    },
  },
  "2026-05-26": {
    total: 98000,
    paymentSales: { cash: 98000 },
    menuSales: {
      "아메리카노": { quantity: 21, sales: 63000 },
      "카페라떼": { quantity: 7, sales: 24500 },
      "쿠키": { quantity: 2, sales: 6000 },
      "아인슈페너": { quantity: 1, sales: 5000 },
    },
  },
  "2026-05-27": {
    total: 142000,
    paymentSales: { cash: 100000, card: 42000 },
    menuSales: {
      "아메리카노": { quantity: 32, sales: 96000 },
      "카페라떼": { quantity: 11, sales: 38500 },
      "아인슈페너": { quantity: 5, sales: 25000 },
      "쿠키": { quantity: 3, sales: 9000 },
    },
  },
};

for (const [date, data] of Object.entries(rollups)) {
  await db.collection("reportRollups").doc(date).set(data);
}

const email = "cafe@holybean.app";
const password = "holybean1234";
try {
  await auth.createUser({ email, password });
  console.log(`auth user 생성: ${email} / ${password}`);
} catch (e) {
  if (e.code === "auth/email-already-exists") console.log(`auth user 이미 존재: ${email}`);
  else throw e;
}

console.log(`reportRollups ${Object.keys(rollups).length}건 시드 완료`);
process.exit(0);
```

- [ ] **Step 2: package.json에 시드 스크립트 추가 (선택, 편의)**

`firebase/package.json`의 `scripts`에 추가:

```json
    "seed:dashboard": "node scripts/seedDashboard.mjs"
```

(`menu:import` 줄 뒤에 콤마 주의해서 추가.)

- [ ] **Step 3: Commit**

```bash
git add firebase/scripts/seedDashboard.mjs firebase/package.json
git commit -m "feat(dashboard): add emulator seed script for sample sales + test user"
```

---

## Task 5: 정적 화면 (`index.html` + `styles.css`)

**Files:**
- Modify: `firebase/public/index.html` (플레이스홀더 → 실제 마크업)
- Create: `firebase/public/styles.css`

- [ ] **Step 1: `firebase/public/index.html` 전체 교체**

```html
<!DOCTYPE html>
<html lang="ko">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>HolyBean 매출 대시보드</title>
  <link rel="stylesheet" href="https://cdn.jsdelivr.net/gh/orioncactus/pretendard@v1.3.9/dist/web/static/pretendard.css">
  <link rel="stylesheet" href="./styles.css">
  <script src="https://cdn.jsdelivr.net/npm/xlsx@0.18.5/dist/xlsx.full.min.js"></script>
</head>
<body>
  <div class="app">
    <!-- 로그인 -->
    <div id="login" hidden>
      <div class="brand">HolyBean</div>
      <h1>매출 대시보드</h1>
      <label for="email">이메일</label>
      <input id="email" type="email" autocomplete="username">
      <label for="pw">비밀번호</label>
      <input id="pw" type="password" autocomplete="current-password">
      <button class="primary" id="loginBtn">로그인</button>
      <p class="error" id="loginError" hidden></p>
    </div>

    <!-- 대시보드 -->
    <div id="dash" hidden>
      <div class="topbar">
        <span class="brand">HolyBean</span>
        <button class="out" id="logoutBtn">로그아웃</button>
      </div>
      <div class="datebar">
        <button class="nav" id="prev" aria-label="이전 날">◀</button>
        <span class="d tnum" id="dateLabel"></span>
        <button class="nav" id="next" aria-label="다음 날">▶</button>
      </div>
      <div class="stats">
        <div class="stat"><div class="lab">이날 총매출</div><div class="num tnum"><span id="total">0</span><span class="won">원</span></div></div>
        <div class="stat"><div class="lab">전체 잔 수</div><div class="num tnum"><span id="cups">0</span>잔</div></div>
      </div>
      <button class="export" id="exportBtn">⬇ 엑셀로 내려받기</button>
      <div class="list">
        <div class="head"><span class="t">판매 내역 <span class="sub">(많이 팔린 순)</span></span><span class="h">개수 · 금액</span></div>
        <div id="items"></div>
      </div>
    </div>

    <!-- 빈 상태 / 에러 -->
    <div id="message" class="message" hidden></div>
  </div>
  <script type="module" src="./app.js"></script>
</body>
</html>
```

- [ ] **Step 2: `firebase/public/styles.css` 생성**

```css
:root{
  --bg:#ffffff; --ink:#1b1b1b; --muted:#8a8a8a; --line:#ececec;
  --surface:#fafafa; --accent:#b5651d; --accent-soft:#f6ede2; --error:#c0392b;
}
*{box-sizing:border-box;margin:0;padding:0}
body{font-family:'Pretendard',system-ui,sans-serif;background:#f2f2f0;color:var(--ink);
  -webkit-font-smoothing:antialiased;min-height:100vh;display:flex;align-items:flex-start;justify-content:center;padding:24px 16px}
.app{width:100%;max-width:520px}
.tnum{font-variant-numeric:tabular-nums;letter-spacing:-.02em}
.brand{font-size:12px;letter-spacing:.18em;color:var(--accent);font-weight:700;text-transform:uppercase}

/* 로그인 */
#login{background:var(--bg);border:1px solid var(--line);border-radius:18px;padding:40px 28px;
  box-shadow:0 12px 40px rgba(0,0,0,.06);margin-top:8vh}
#login h1{font-size:24px;margin:6px 0 28px;font-weight:800}
#login label{display:block;font-size:13px;color:var(--muted);margin:14px 0 6px;font-weight:600}
#login input{width:100%;padding:13px 14px;border:1px solid var(--line);border-radius:10px;font-size:15px;font-family:inherit;background:#fff}
#login input:focus{outline:none;border-color:var(--accent)}
.primary{width:100%;margin-top:24px;padding:14px;border:none;border-radius:10px;background:var(--ink);color:#fff;
  font-size:15px;font-weight:700;font-family:inherit;cursor:pointer}
.primary:hover{opacity:.88}
.error{color:var(--error);font-size:13px;margin-top:12px}

/* 대시보드 */
.topbar{display:flex;justify-content:space-between;align-items:center;margin-bottom:18px}
.topbar .out{font-size:13px;color:var(--muted);background:none;border:none;cursor:pointer;font-family:inherit}
.datebar{display:flex;align-items:center;justify-content:space-between;background:var(--bg);border:1px solid var(--line);
  border-radius:14px;padding:12px 14px;margin-bottom:14px}
.datebar .d{font-size:17px;font-weight:800}
.nav{width:40px;height:40px;border:1px solid var(--line);border-radius:10px;background:#fff;font-size:16px;cursor:pointer;color:var(--ink)}
.nav:hover:not(:disabled){background:var(--surface)}
.nav:disabled{opacity:.25;cursor:default}
.stats{display:flex;gap:12px;margin-bottom:14px}
.stat{flex:1;background:var(--bg);border:1px solid var(--line);border-radius:14px;padding:18px 16px}
.stat .lab{font-size:12px;color:var(--muted);font-weight:600;margin-bottom:6px}
.stat .num{font-size:28px;font-weight:800}
.stat .num .won{font-size:16px;font-weight:700;color:var(--muted);margin-left:2px}
.export{width:100%;display:flex;align-items:center;justify-content:center;gap:8px;padding:13px;margin-bottom:18px;
  border:1px solid var(--accent);background:var(--accent-soft);color:var(--accent);border-radius:12px;
  font-size:14px;font-weight:700;font-family:inherit;cursor:pointer}
.export:hover{background:#f0e2cf}
.export:disabled{opacity:.4;cursor:default}
.list{background:var(--bg);border:1px solid var(--line);border-radius:14px;overflow:hidden}
.list .head{display:flex;justify-content:space-between;padding:14px 16px;border-bottom:1px solid var(--line)}
.list .head .t{font-weight:800;font-size:15px}
.list .head .sub{color:var(--muted);font-weight:600;font-size:13px}
.list .head .h{font-size:12px;color:var(--muted);font-weight:600}
.item{display:flex;align-items:center;justify-content:space-between;padding:13px 16px;border-bottom:1px solid var(--line)}
.item:last-child{border-bottom:none}
.item .name{font-size:15px}
.item .right{display:flex;align-items:baseline;gap:14px}
.item .cnt{font-size:16px;font-weight:800;color:var(--accent);min-width:46px;text-align:right}
.item .amt{font-size:13px;color:var(--muted);min-width:74px;text-align:right}
.empty{padding:36px 16px;text-align:center;color:var(--muted);font-size:14px}
.message{margin-top:8vh;background:var(--bg);border:1px solid var(--line);border-radius:14px;padding:32px;text-align:center;color:var(--muted)}
```

- [ ] **Step 3: 정적 화면 육안 확인**

Run (firebase/ 에서): `firebase emulators:start --only hosting`
`http://localhost:5000` 접속. 두 화면(`#login`, `#dash`)은 `hidden`이라 아직 안 보인다 → 임시로 브라우저 콘솔에서 `document.getElementById('dash').hidden=false` 실행해 대시보드 레이아웃이 깨지지 않는지 확인. 확인 후 종료.
Expected: 날짜바·통계 카드 2개·엑셀 버튼·판매 내역 헤더가 흰 카드로 정렬되어 표시.

- [ ] **Step 4: Commit**

```bash
git add firebase/public/index.html firebase/public/styles.css
git commit -m "feat(dashboard): add login and dashboard markup with minimal styles"
```

---

## Task 6: Firebase 초기화 모듈 (`firebase-init.js`)

**Files:**
- Create: `firebase/public/firebase-init.js`

- [ ] **Step 1: `firebase/public/firebase-init.js` 생성**

```js
import { initializeApp } from "https://www.gstatic.com/firebasejs/11.1.0/firebase-app.js";
import { getAuth, connectAuthEmulator } from "https://www.gstatic.com/firebasejs/11.1.0/firebase-auth.js";
import { getFirestore, connectFirestoreEmulator } from "https://www.gstatic.com/firebasejs/11.1.0/firebase-firestore.js";
import { firebaseConfig } from "./firebase-config.js";

const app = initializeApp(firebaseConfig);
export const auth = getAuth(app);
export const db = getFirestore(app);

// localhost에서는 에뮬레이터에 연결
if (location.hostname === "localhost" || location.hostname === "127.0.0.1") {
  connectAuthEmulator(auth, "http://localhost:9099", { disableWarnings: true });
  connectFirestoreEmulator(db, "localhost", 8080);
}
```

- [ ] **Step 2: 모듈 로드/에뮬레이터 연결 확인**

별도 터미널에서 `firebase emulators:start --only firestore,auth,hosting` 실행 후 `http://localhost:5000` 접속, 브라우저 콘솔에 빨간 에러가 없는지 확인(특히 `firebase-config.js` import 성공, 에뮬레이터 연결 경고 없음). 확인 후 그대로 둔다(다음 태스크에서 계속 사용).
Expected: 콘솔에 모듈 로드 에러 없음. (`firebase-config.js` 미생성 시 Task 3 Step 2 수행.)

- [ ] **Step 3: Commit**

```bash
git add firebase/public/firebase-init.js
git commit -m "feat(dashboard): add firebase init module with emulator wiring"
```

---

## Task 7: 대시보드 컨트롤러 (`app.js`)

인증, Firestore 날짜 읽기/이동, 렌더링, 엑셀 export, 빈 상태/에러를 모두 담는 컨트롤러. 한 파일에 완결적으로 작성한다.

**Files:**
- Create: `firebase/public/app.js`

- [ ] **Step 1: `firebase/public/app.js` 전체 작성**

```js
import {
  signInWithEmailAndPassword, signOut, onAuthStateChanged,
} from "https://www.gstatic.com/firebasejs/11.1.0/firebase-auth.js";
import {
  collection, query, orderBy, limit, where, getDocs, documentId,
} from "https://www.gstatic.com/firebasejs/11.1.0/firebase-firestore.js";
import { auth, db } from "./firebase-init.js";
import { salesRows, totalCups, exportAOA, formatDateLabel } from "./transform.js";

const COLL = "reportRollups";
const $ = (id) => document.getElementById(id);
const won = (n) => n.toLocaleString("ko-KR");

// 현재 보고 있는 날: { date, data } 또는 null
let current = null;

// ---------- 인증 ----------
onAuthStateChanged(auth, (user) => {
  if (user) {
    $("login").hidden = true;
    $("dash").hidden = false;
    loadInitial();
  } else {
    $("dash").hidden = true;
    $("message").hidden = true;
    $("login").hidden = false;
  }
});

$("loginBtn").addEventListener("click", async () => {
  $("loginError").hidden = true;
  try {
    await signInWithEmailAndPassword(auth, $("email").value.trim(), $("pw").value);
  } catch {
    $("loginError").textContent = "로그인에 실패했습니다. 이메일과 비밀번호를 확인해주세요.";
    $("loginError").hidden = false;
  }
});

$("logoutBtn").addEventListener("click", () => signOut(auth));

// ---------- Firestore 날짜 조회 ----------
async function fetchLatest() {
  const snap = await getDocs(query(collection(db, COLL), orderBy(documentId(), "desc"), limit(1)));
  return snap.empty ? null : { date: snap.docs[0].id, data: snap.docs[0].data() };
}

async function fetchAdjacent(date, dir) {
  const op = dir === "prev" ? "<" : ">";
  const dir2 = dir === "prev" ? "desc" : "asc";
  const snap = await getDocs(query(
    collection(db, COLL),
    where(documentId(), op, date),
    orderBy(documentId(), dir2),
    limit(1),
  ));
  return snap.empty ? null : { date: snap.docs[0].id, data: snap.docs[0].data() };
}

// ---------- 로드 & 렌더 ----------
async function loadInitial() {
  try {
    const latest = await fetchLatest();
    if (!latest) return showMessage("아직 매출 기록이 없어요.");
    current = latest;
    await render();
  } catch {
    showMessage("데이터를 불러오지 못했습니다. 잠시 후 다시 시도해주세요.");
  }
}

async function move(dir) {
  if (!current) return;
  const next = await fetchAdjacent(current.date, dir);
  if (next) {
    current = next;
    await render();
  }
}

async function render() {
  $("message").hidden = true;
  $("dash").hidden = false;
  const { date, data } = current;
  const rows = salesRows(data);

  $("dateLabel").textContent = formatDateLabel(date);
  $("total").textContent = won(data.total ?? 0);
  $("cups").textContent = totalCups(rows);

  $("items").innerHTML = rows.length
    ? rows.map((r) =>
        `<div class="item"><span class="name">${escapeHtml(r.name)}</span>` +
        `<span class="right"><span class="cnt tnum">${r.quantity}개</span>` +
        `<span class="amt tnum">${won(r.sales)}원</span></span></div>`).join("")
    : `<div class="empty">이날 판매된 메뉴가 없어요.</div>`;

  $("exportBtn").disabled = rows.length === 0;

  // 화살표 활성/비활성 (양 옆 존재 여부)
  const [prev, next] = await Promise.all([
    fetchAdjacent(date, "prev"),
    fetchAdjacent(date, "next"),
  ]);
  $("prev").disabled = !prev;
  $("next").disabled = !next;
}

function showMessage(text) {
  $("dash").hidden = true;
  $("message").textContent = text;
  $("message").hidden = false;
}

function escapeHtml(s) {
  return s.replace(/[&<>"']/g, (c) =>
    ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
}

// ---------- 엑셀 export ----------
$("exportBtn").addEventListener("click", () => {
  if (!current) return;
  const rows = salesRows(current.data);
  const aoa = exportAOA(current.date, rows, current.data.total ?? 0);
  const ws = XLSX.utils.aoa_to_sheet(aoa);
  const wb = XLSX.utils.book_new();
  XLSX.utils.book_append_sheet(wb, ws, current.date);
  XLSX.writeFile(wb, `holybean-${current.date}.xlsx`);
});

// ---------- 날짜 이동 버튼 ----------
$("prev").addEventListener("click", () => move("prev"));
$("next").addEventListener("click", () => move("next"));
```

- [ ] **Step 2: Commit**

```bash
git add firebase/public/app.js
git commit -m "feat(dashboard): add controller with auth, date nav, render, and excel export"
```

---

## Task 8: 엔드투엔드 수동 검증

빌드/프레임워크가 없으므로 에뮬레이터 + 시드 데이터로 실제 흐름을 직접 확인한다.

- [ ] **Step 1: 에뮬레이터 기동 + 시드**

터미널 A (firebase/): `firebase emulators:start --only firestore,auth,hosting`
터미널 B (firebase/): `node scripts/seedDashboard.mjs`
Expected: 터미널 B에 "reportRollups 3건 시드 완료", "auth user 생성: cafe@holybean.app / holybean1234".

- [ ] **Step 2: 로그인 흐름**

`http://localhost:5000` 접속 → 로그인 화면 표시 확인. 틀린 비밀번호 입력 → 빨간 에러 메시지 확인. `cafe@holybean.app` / `holybean1234`로 로그인 → 대시보드 표시.
Expected: 잘못된 입력은 에러, 올바른 입력은 대시보드 진입.

- [ ] **Step 3: 최신 날짜 + 렌더 확인**

진입 시 가장 최근 날(`5월 27일 (수)`) 표시. 총매출 `168,500원`, 전체 잔 수 `51잔`, 판매 내역이 아메리카노(32개) → 카페라떼(11개) → 아인슈페너(5개) → 쿠키(3개) 순.
Expected: 개수 내림차순 정렬과 합계 일치(32+11+5+3=51).

- [ ] **Step 4: 날짜 이동 + 경계 비활성**

▶가 비활성인지 확인(최신 날). ◀로 5/26, 5/25 이동하며 값 갱신 확인. 5/25에서 ◀ 비활성 확인(가장 오래된 날).
Expected: 양 끝에서 해당 화살표 disabled, 중간 날짜는 양쪽 활성.

- [ ] **Step 5: 엑셀 다운로드**

임의의 날에서 "엑셀로 내려받기" 클릭 → `holybean-<날짜>.xlsx` 다운로드. 파일 열어 헤더(메뉴/개수/금액) + 메뉴 행 + 합계 행 확인.
Expected: 합계 행의 개수·금액이 화면 통계와 일치.

- [ ] **Step 6: 반응형 확인**

브라우저 개발자도구로 폰 폭(예: 390px)과 넓은 폭을 전환. 폰에서 꽉 차고 PC에서 가운데 정렬(좌우 여백) 확인. 레이아웃 깨짐 없음.
Expected: 두 폭 모두 정상.

- [ ] **Step 7: 빈/에러 상태 (선택 확인)**

빈 컬렉션 동작은 시드 전에 로그인하면 "아직 매출 기록이 없어요." 메시지로 확인 가능. (이미 시드했다면 생략 가능.)
Expected: 데이터 없을 때 안내 메시지 표시.

- [ ] **Step 8: 검증 기록 커밋 (코드 변경 시에만)**

검증 중 수정이 필요했다면 해당 변경을 커밋한다. 변경이 없으면 생략.

```bash
git status   # 변경 없으면 커밋 없음
```

> **실배포는 별도(이 계획 범위 밖):** `firebase-config.js`에 실제 콘솔 값 입력 → App Check 강제 여부 확인(Task 3 주의 참고) → `firebase deploy --only hosting`. 운영자에게 URL과 계정 전달.

---

## Self-Review 결과

- **스펙 커버리지:** 아키텍처(Task 1,6,7) / 로그인(7) / 날짜이동·실제있는날만(7 fetchAdjacent) / 총매출·전체잔수·판매내역 정렬(2,7) / 엑셀(2,7) / 인증·보안(6,7) / 비주얼(5) / 엣지(7) / 검증(8) — 모두 태스크에 매핑됨.
- **플레이스홀더:** 없음. (`firebase-config.js` 실제 값은 의도적으로 수동 단계로 분리.)
- **타입/이름 일관성:** `current={date,data}`, `salesRows/totalCups/exportAOA/formatDateLabel`, `fetchLatest/fetchAdjacent/loadInitial/move/render`, 엘리먼트 id(`login,dash,email,pw,loginBtn,loginError,logoutBtn,prev,next,dateLabel,total,cups,exportBtn,items,message`)가 HTML(Task5)·CSS·JS(Task7) 전반에서 일치.
