# 대시보드 결제수단 집계 + 거래 단위 엑셀 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 웹 대시보드에 결제수단별 매출 집계 섹션을 추가하고, 엑셀 다운로드를 "주문 한 건 = 한 행"의 거래내역 시트 + 기존 메뉴집계 시트로 확장한다.

**Architecture:** 화면 표시(A)는 이미 로드된 `reportRollups/{date}.paymentSales`만 사용해 추가 조회가 없다. 엑셀(B)은 클릭 시 해당 날짜 `orders`를 `where("orderDate","==",date)`로 조회해 2시트 워크북을 만든다. 데이터 변환은 DOM/Firebase 의존성 없는 순수함수(`transform.js`)에 두고 vitest로 단위테스트한다.

**Tech Stack:** 순수 정적 SPA(빌드 없음), Firebase JS SDK 11(CDN), SheetJS(CDN), vitest(단위), Firebase Emulator + puppeteer-core(e2e).

**선행 문서:** 스펙 `docs/superpowers/specs/2026-05-27-dashboard-payments-and-transaction-excel-design.md`

---

## 파일 구조

- `firebase/public/transform.js` — 순수함수에 `paymentRows`, `transactionAOA` 추가(기존 `salesRows/totalCups/exportAOA/formatDateLabel` 옆).
- `firebase/test/transform.test.js` — 위 두 함수 단위테스트 추가.
- `firebase/public/index.html` — 결제수단 섹션 마크업(`#payList`/`#payments`) 추가.
- `firebase/public/styles.css` — `#payList`에 하단 여백 한 줄 추가(나머지는 기존 `.list/.item` 스타일 재사용).
- `firebase/public/app.js` — import 보강(`where`, `paymentRows`, `transactionAOA`), `render()`에서 결제수단 렌더, 엑셀 핸들러를 `orders` 조회 + 2시트 생성으로 교체.
- `firebase/legacy/seedDashboard.mjs` — `paymentSales` 키를 한글로 교정하고 `orders`(정산·다중결제·외상 혼합) 시드 추가.

**중요 사실(조사 결과):** 프로덕션 `reportRollups.paymentSales` 키와 `orders[].payments[].method`는 **모두 한글**("현금","쿠폰","계좌이체","외상","무료쿠폰","무료제공"; "카드"는 존재하지 않음). 현재 시드만 영어(`cash`/`card`)라 Task 6에서 교정한다. `orders`는 `allow read: if signedIn()`으로 로그인 사용자 읽기가 이미 허용됨(규칙 변경 없음). 등식 단일 필드 쿼리라 복합 인덱스 불필요(`firestore.indexes.json` 변경 없음).

---

## Task 1: `paymentRows` 순수함수

**Files:**
- Modify: `firebase/public/transform.js`
- Test: `firebase/test/transform.test.js`

- [ ] **Step 1: 실패하는 테스트 작성**

`firebase/test/transform.test.js` 상단 import 줄을 아래로 교체(함수 2개 추가):

```js
import { salesRows, totalCups, exportAOA, formatDateLabel, paymentRows, transactionAOA } from "../public/transform.js";
```

파일 맨 끝에 추가:

```js
describe("paymentRows", () => {
  it("paymentSales를 금액 내림차순 {method, amount} 배열로 펴낸다", () => {
    expect(paymentRows({ paymentSales: { "현금": 126500, "쿠폰": 42000 } })).toEqual([
      { method: "현금", amount: 126500 },
      { method: "쿠폰", amount: 42000 },
    ]);
  });
  it("paymentSales가 없으면 빈 배열", () => {
    expect(paymentRows({})).toEqual([]);
    expect(paymentRows(null)).toEqual([]);
  });
});
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `cd firebase && npm test`
Expected: FAIL — "paymentRows is not a function" (또는 import 에러)

- [ ] **Step 3: 최소 구현 추가**

`firebase/public/transform.js`의 `salesRows` 함수 바로 아래에 추가:

```js
/** rollup.paymentSales를 {method, amount} 배열로 펴서 금액 내림차순 정렬 */
export function paymentRows(rollup) {
  const paymentSales = (rollup && rollup.paymentSales) || {};
  return Object.entries(paymentSales)
    .map(([method, amount]) => ({ method, amount: Number(amount) || 0 }))
    .sort((a, b) => b.amount - a.amount);
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd firebase && npm test`
Expected: PASS (paymentRows describe 2건 포함)

- [ ] **Step 5: 커밋**

```bash
git add firebase/public/transform.js firebase/test/transform.test.js
git commit -m "feat(dashboard): add paymentRows transform for payment aggregation"
```

---

## Task 2: `transactionAOA` 순수함수

**Files:**
- Modify: `firebase/public/transform.js`
- Test: `firebase/test/transform.test.js`

- [ ] **Step 1: 실패하는 테스트 작성**

`firebase/test/transform.test.js` 맨 끝에 추가:

```js
describe("transactionAOA", () => {
  const orders = [
    {
      orderNum: 3, customerName: "김철수", totalAmount: 5000, creditStatus: 1,
      items: [{ name: "아인슈페너", quantity: 1 }],
      payments: [{ method: "외상", amount: 5000 }],
    },
    {
      orderNum: 5, customerName: "", totalAmount: 9000, creditStatus: 0,
      items: [{ name: "아메리카노", quantity: 2 }, { name: "카페라떼", quantity: 1 }],
      payments: [{ method: "현금", amount: 5000 }, { method: "쿠폰", amount: 4000 }],
    },
  ];
  it("주문 한 건 = 한 행, 익명은 '-', 결제수단 결합, 합계 행을 만든다", () => {
    expect(transactionAOA(orders)).toEqual([
      ["주문번호", "고객명", "주문내역", "총액", "결제수단"],
      [3, "김철수", "아인슈페너 1개", 5000, "외상"],
      [5, "-", "아메리카노 2개, 카페라떼 1개", 9000, "현금+쿠폰"],
      ["합계", "", "", 14000, ""],
    ]);
  });
  it("빈 배열이면 헤더만 반환", () => {
    expect(transactionAOA([])).toEqual([
      ["주문번호", "고객명", "주문내역", "총액", "결제수단"],
    ]);
  });
});
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `cd firebase && npm test`
Expected: FAIL — "transactionAOA is not a function"

- [ ] **Step 3: 최소 구현 추가**

`firebase/public/transform.js`의 `exportAOA` 함수 바로 아래에 추가:

```js
/** orders 문서 배열(orderNum 오름차순 가정) → 거래내역 시트 AOA: 헤더 + 주문 행 + 합계 행.
 *  빈 배열이면 헤더만 반환. 고객명 빈 값은 "-". 결제수단은 method를 "+"로 결합. */
export function transactionAOA(orders) {
  const header = ["주문번호", "고객명", "주문내역", "총액", "결제수단"];
  if (orders.length === 0) return [header];
  const rows = orders.map((o) => [
    Number(o.orderNum) || 0,
    o.customerName ? o.customerName : "-",
    (o.items || []).map((it) => `${it.name} ${Number(it.quantity) || 0}개`).join(", "),
    Number(o.totalAmount) || 0,
    (o.payments || []).map((p) => p.method).join("+"),
  ]);
  const totalSum = rows.reduce((s, r) => s + r[3], 0);
  return [header, ...rows, ["합계", "", "", totalSum, ""]];
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd firebase && npm test`
Expected: PASS (transactionAOA describe 2건 포함, 전체 10건)

- [ ] **Step 5: 커밋**

```bash
git add firebase/public/transform.js firebase/test/transform.test.js
git commit -m "feat(dashboard): add transactionAOA transform for per-order Excel sheet"
```

---

## Task 3: 결제수단 섹션 마크업 + 여백

**Files:**
- Modify: `firebase/public/index.html`
- Modify: `firebase/public/styles.css`

- [ ] **Step 1: index.html에 결제수단 섹션 추가**

`firebase/public/index.html`에서 `</div>`(stats 닫기)와 `<button class="export" ...>` 사이에 `#payList`를 삽입한다. 아래 블록을 찾아서:

```html
      <button class="export" id="exportBtn">⬇ 엑셀로 내려받기</button>
```

바로 위에 추가:

```html
      <div class="list" id="payList" hidden>
        <div class="head"><span class="t">결제수단</span><span class="h">금액</span></div>
        <div id="payments"></div>
      </div>
```

- [ ] **Step 2: styles.css에 여백 한 줄 추가**

`firebase/public/styles.css`의 `.list{...}` 규칙(현재 44행 부근) 바로 아래에 추가:

```css
#payList{margin-bottom:14px}
```

- [ ] **Step 3: 커밋**

```bash
git add firebase/public/index.html firebase/public/styles.css
git commit -m "feat(dashboard): add payment-method section markup (reuses list style)"
```

---

## Task 4: `render()`에서 결제수단 표시

**Files:**
- Modify: `firebase/public/app.js`

- [ ] **Step 1: transform import에 두 함수 추가**

`firebase/public/app.js`의 transform import 줄을 교체:

```js
import { salesRows, totalCups, exportAOA, formatDateLabel } from "./transform.js";
```

→

```js
import { salesRows, totalCups, exportAOA, formatDateLabel, paymentRows, transactionAOA } from "./transform.js";
```

- [ ] **Step 2: render()에 결제수단 렌더 추가**

`render()` 안에서 `$("cups").textContent = totalCups(rows);` 줄 **다음에** 추가:

```js
  const pays = paymentRows(data);
  $("payList").hidden = pays.length === 0;
  $("payments").innerHTML = pays.map((p) =>
    `<div class="item"><span class="name">${escapeHtml(p.method)}</span>` +
    `<span class="right"><span class="cnt tnum">${won(p.amount)}원</span></span></div>`).join("");
```

- [ ] **Step 3: 수동 확인(에뮬레이터는 Task 8에서)**

`escapeHtml`, `won`, `paymentRows`가 모두 정의/임포트되어 있는지 확인. 구문 점검:

Run: `cd firebase && node --check public/app.js`
Expected: 출력 없음(정상). 단, `node --check`는 ESM import 경로를 실행하지 않으므로 구문만 검사한다.

- [ ] **Step 4: 커밋**

```bash
git add firebase/public/app.js
git commit -m "feat(dashboard): render payment-method aggregation on day view"
```

---

## Task 5: 엑셀 핸들러를 orders 조회 + 2시트로 교체

**Files:**
- Modify: `firebase/public/app.js`

- [ ] **Step 1: firestore import에 `where` 추가**

`firebase/public/app.js`의 firestore import 줄을 교체:

```js
import {
  collection, query, orderBy, getDocs, documentId,
} from "https://www.gstatic.com/firebasejs/11.1.0/firebase-firestore.js";
```

→

```js
import {
  collection, query, orderBy, getDocs, documentId, where,
} from "https://www.gstatic.com/firebasejs/11.1.0/firebase-firestore.js";
```

- [ ] **Step 2: 엑셀 export 핸들러 교체**

기존 동기 핸들러 전체:

```js
$("exportBtn").addEventListener("click", () => {
  if (idx < 0) return;
  const { date, data } = days[idx];
  const rows = salesRows(data);
  const aoa = exportAOA(date, rows, Number(data.total) || 0);
  const ws = XLSX.utils.aoa_to_sheet(aoa);
  const wb = XLSX.utils.book_new();
  XLSX.utils.book_append_sheet(wb, ws, date);
  XLSX.writeFile(wb, `holybean-${date}.xlsx`);
});
```

를 아래로 교체:

```js
$("exportBtn").addEventListener("click", async () => {
  if (idx < 0) return;
  const { date, data } = days[idx];
  const rows = salesRows(data);
  const btn = $("exportBtn");
  btn.disabled = true;
  try {
    // 해당 날짜의 모든 주문(정산·외상 포함)을 조회해 거래내역 시트를 만든다.
    const snap = await getDocs(query(collection(db, "orders"), where("orderDate", "==", date)));
    const orders = snap.docs
      .map((d) => d.data())
      .sort((a, b) => (Number(a.orderNum) || 0) - (Number(b.orderNum) || 0));
    const wb = XLSX.utils.book_new();
    XLSX.utils.book_append_sheet(wb, XLSX.utils.aoa_to_sheet(transactionAOA(orders)), "거래내역");
    XLSX.utils.book_append_sheet(wb, XLSX.utils.aoa_to_sheet(exportAOA(date, rows, Number(data.total) || 0)), "메뉴집계");
    XLSX.writeFile(wb, `holybean-${date}.xlsx`);
  } catch {
    alert("엑셀 데이터를 불러오지 못했습니다. 잠시 후 다시 시도해주세요.");
  } finally {
    btn.disabled = rows.length === 0;
  }
});
```

- [ ] **Step 3: 구문 점검**

Run: `cd firebase && node --check public/app.js`
Expected: 출력 없음(정상)

- [ ] **Step 4: 커밋**

```bash
git add firebase/public/app.js
git commit -m "feat(dashboard): export per-order 거래내역 + 메뉴집계 sheets from orders"
```

---

## Task 6: 에뮬레이터 시드 교정 (한글 paymentSales + orders)

**Files:**
- Modify: `firebase/legacy/seedDashboard.mjs`

- [ ] **Step 1: paymentSales 키를 한글로 교정**

`firebase/legacy/seedDashboard.mjs`의 세 `paymentSales`를 교체:

- `"2026-05-25"`: `paymentSales: { cash: 120500 },` → `paymentSales: { "현금": 120500 },`
- `"2026-05-26"`: `paymentSales: { cash: 98500 },` → `paymentSales: { "현금": 98500 },`
- `"2026-05-27"`: `paymentSales: { cash: 126500, card: 42000 },` → `paymentSales: { "현금": 126500, "쿠폰": 42000 },`

- [ ] **Step 2: orders 시드 추가**

`for (const [date, data] of Object.entries(rollups)) { ... }` 루프 **다음 줄에** 추가(정산 익명·다중결제·외상 세 케이스):

```js
// 거래내역 엑셀 검증용 orders (화면 총매출은 reportRollups에서 오므로 합계는 일치하지 않아도 됨).
const orders = {
  "2026-05-27_1": {
    orderDate: "2026-05-27", orderNum: 1, totalAmount: 6000, customerName: "", creditStatus: 0,
    items: [{ name: "아메리카노", quantity: 2, unitPrice: 3000, subtotal: 6000 }],
    payments: [{ method: "현금", amount: 6000 }],
  },
  "2026-05-27_2": {
    orderDate: "2026-05-27", orderNum: 2, totalAmount: 8500, customerName: "이영희", creditStatus: 0,
    items: [
      { name: "카페라떼", quantity: 1, unitPrice: 3500, subtotal: 3500 },
      { name: "아인슈페너", quantity: 1, unitPrice: 5000, subtotal: 5000 },
    ],
    payments: [{ method: "현금", amount: 5000 }, { method: "쿠폰", amount: 3500 }],
  },
  "2026-05-27_3": {
    orderDate: "2026-05-27", orderNum: 3, totalAmount: 5000, customerName: "김철수", creditStatus: 1,
    items: [{ name: "아인슈페너", quantity: 1, unitPrice: 5000, subtotal: 5000 }],
    payments: [{ method: "외상", amount: 5000 }],
  },
};
for (const [id, data] of Object.entries(orders)) {
  await db.collection("orders").doc(id).set(data);
}
console.log(`orders ${Object.keys(orders).length}건 시드 완료`);
```

- [ ] **Step 3: 커밋**

```bash
git add firebase/legacy/seedDashboard.mjs
git commit -m "test(dashboard): seed Korean paymentSales keys and orders for emulator"
```

---

## Task 7: 단위테스트 전체 통과 확인

**Files:**
- (변경 없음 — 확인만)

- [ ] **Step 1: 전체 단위테스트 실행**

Run: `cd firebase && npm test`
Expected: PASS — 총 10건(salesRows 2, totalCups 2, exportAOA 1, formatDateLabel 1, paymentRows 2, transactionAOA 2). 실패 시 해당 Task로 돌아가 수정.

---

## Task 8: 에뮬레이터 e2e + 프로덕션 스모크 (puppeteer)

**Files:**
- (검증 전용 — 일회용 스크립트, 커밋하지 않음. 참고: [[no-tdd-for-one-off-scripts]])

환경 메모: Java는 PATH에 없음 → 에뮬레이터 실행 시 `PATH="/opt/homebrew/opt/openjdk@21/bin:$PATH"` 필요. Chrome 실행 파일: `/Applications/Google Chrome.app/Contents/MacOS/Google Chrome`. `puppeteer-core`는 `firebase/node_modules`에 이미 설치돼 있음.

- [ ] **Step 1: 에뮬레이터 기동(터미널 A)**

Run:
```bash
cd /Users/benn/dev/personal/HolyBean/firebase && \
PATH="/opt/homebrew/opt/openjdk@21/bin:$PATH" firebase emulators:start --only firestore,auth,hosting
```
Expected: firestore:8080, auth:9099, hosting:5000 기동. (이 터미널은 켜둔다.)

- [ ] **Step 2: 시드 주입(터미널 B)**

Run: `cd /Users/benn/dev/personal/HolyBean/firebase && npm run seed:dashboard`
Expected: "reportRollups 3건 시드 완료", "orders 3건 시드 완료", auth user 생성/이미 존재 로그.

- [ ] **Step 3: e2e 스크립트 작성**

`/tmp/holybean-e2e.mjs` 생성:

```js
import puppeteer from "/Users/benn/dev/personal/HolyBean/firebase/node_modules/puppeteer-core/lib/esm/puppeteer/puppeteer-core.js";
import { existsSync, statSync, rmSync, mkdirSync } from "node:fs";

const DOWNLOAD_DIR = "/tmp/holybean-dl";
rmSync(DOWNLOAD_DIR, { recursive: true, force: true });
mkdirSync(DOWNLOAD_DIR, { recursive: true });

const browser = await puppeteer.launch({
  executablePath: "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
  headless: "new",
});
const page = await browser.newPage();
const errors = [];
page.on("console", (m) => { if (m.type() === "error") errors.push(m.text()); });
page.on("pageerror", (e) => errors.push(String(e)));

await page.goto("http://localhost:5000", { waitUntil: "networkidle0" });
await page.waitForSelector("#login:not([hidden])");
await page.type("#email", "cafe");      // 자동으로 @holybean.app 부착
await page.type("#pw", "holybean1234");
await page.click("#loginBtn");

// 결제수단 섹션 렌더 확인 (가장 최근 날짜 = 2026-05-27)
await page.waitForSelector("#payList:not([hidden]) #payments .item");
const payText = await page.$eval("#payments", (el) => el.textContent);
if (!payText.includes("현금")) throw new Error("결제수단에 '현금' 없음: " + payText);
console.log("결제수단 렌더 OK:", payText.replace(/\s+/g, " ").trim());

// 엑셀 다운로드 경로 설정 후 export 클릭
const client = await page.target().createCDPSession();
await client.send("Page.setDownloadBehavior", { behavior: "allow", downloadPath: DOWNLOAD_DIR });
await page.click("#exportBtn");

const file = `${DOWNLOAD_DIR}/holybean-2026-05-27.xlsx`;
for (let i = 0; i < 50 && !existsSync(file); i++) await new Promise((r) => setTimeout(r, 100));
if (!existsSync(file)) throw new Error("엑셀 파일 미생성: " + file);
if (statSync(file).size < 1000) throw new Error("엑셀 파일이 비정상적으로 작음");
console.log("엑셀 다운로드 OK:", file, statSync(file).size, "bytes");

if (errors.length) throw new Error("콘솔 에러: " + errors.join(" | "));
console.log("콘솔 에러 0건 — 에뮬레이터 e2e PASS");
await browser.close();
```

- [ ] **Step 4: e2e 실행**

Run: `node /tmp/holybean-e2e.mjs`
Expected: "결제수단 렌더 OK ...", "엑셀 다운로드 OK ...", "콘솔 에러 0건 — 에뮬레이터 e2e PASS". 엑셀이 생성됐다는 것은 `orders` 조회(규칙+쿼리)와 워크북 생성이 성공했음을 의미한다.

- [ ] **Step 5: (선택) 엑셀 내용 육안 확인**

Run: `cd firebase && node -e "const X=require('xlsx');const wb=X.readFile('/tmp/holybean-dl/holybean-2026-05-27.xlsx');console.log(wb.SheetNames);console.log(X.utils.sheet_to_json(wb.Sheets['거래내역'],{header:1}))"`
Expected: SheetNames `[ '거래내역', '메뉴집계' ]`, 거래내역에 익명 행의 고객명이 `-`, 3번 주문 결제수단이 `외상`, 2번 주문이 `현금+쿠폰`, 마지막에 합계 행. (`xlsx`는 node_modules에 있음.)

- [ ] **Step 6: 프로덕션 스모크**

`/tmp/holybean-e2e.mjs`를 복제한 `/tmp/holybean-prod.mjs`에서 다음만 바꿔 실행:
- `page.goto("https://holybean.web.app", ...)`
- 로그인: `#email`에 `holybean`, `#pw`에 `1q2w3e4r!`
- 결제수단 검증은 `#payments .item`이 1개 이상 렌더되는지로 완화(특정 문자열 대신 `await page.$$("#payments .item")` 길이 > 0). 최신 날짜에 paymentSales가 있으면 표시됨.
- 다운로드 파일명은 화면의 최신 날짜에 맞춰 `holybean-*.xlsx` 패턴으로 존재만 확인(디렉터리에 `.xlsx`가 1개 생기는지).

Run: `node /tmp/holybean-prod.mjs`
Expected: 결제수단 1건 이상 렌더, 엑셀 다운로드 성공, 콘솔 에러 0건.

- [ ] **Step 7: 정리**

에뮬레이터(터미널 A) 종료. 일회용 스크립트는 커밋하지 않는다(원하면 `rm /tmp/holybean-e2e.mjs /tmp/holybean-prod.mjs`).

---

## Self-Review (작성자 점검 결과)

- **스펙 커버리지:** A(결제수단 표시·금액 내림차순·빈값 숨김·리스트 스타일 재사용) → Task 1·3·4. B(orders 조회·주문 단위 행·익명 `-`·결제수단 결합·외상 포함·합계 행·2시트·파일명 유지) → Task 2·5. 시드 한글 키/ orders → Task 6. 검증(단위·에뮬 e2e·프로덕션 스모크) → Task 7·8. 누락 없음.
- **타입/명칭 일관성:** `paymentRows(rollup)→[{method,amount}]`, `transactionAOA(orders)→AOA`가 Task 2·4·5에서 동일하게 사용됨. DOM id `payList`/`payments`가 Task 3·4·8에서 일치. import 보강(`where`, `paymentRows`, `transactionAOA`)이 사용처(Task 4·5)와 일치.
- **플레이스홀더 없음:** 모든 코드 단계에 실제 코드/명령/기대출력 포함.
- **주의:** Task 4·5는 같은 파일(`app.js`)을 순차 수정하므로 순서대로 진행. 엑셀 실패 알림은 대시보드를 숨기지 않도록 `showMessage` 대신 `alert` 사용(설계의 "인라인" 대안).
