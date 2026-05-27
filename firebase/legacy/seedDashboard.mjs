/**
 * 에뮬레이터 시드 스크립트 — 대시보드 테스트용
 *
 * 실행 전 에뮬레이터가 기동 중이어야 함:
 *   firebase emulators:start --only firestore,auth
 *
 * 실행:
 *   FIRESTORE_EMULATOR_HOST=127.0.0.1:8080 node firebase/legacy/seedDashboard.mjs
 */

import { initializeApp, cert } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import { getAuth } from "firebase-admin/auth";

// 에뮬레이터 연결 (환경변수로 지정되어 있지 않으면 기본 포트 사용)
process.env.FIRESTORE_EMULATOR_HOST ??= "127.0.0.1:8080";
process.env.FIREBASE_AUTH_EMULATOR_HOST ??= "127.0.0.1:9099";

initializeApp({ projectId: "holybean-app" });
const db = getFirestore();
const auth = getAuth();

// ────────────────────────────────────────────────
// reportRollups 시드
// ────────────────────────────────────────────────
const rollups = {
  "2026-05-25": {
    total: 120500,
    menuSales: {
      아메리카노: { quantity: 20, sales: 60000 },
      카페라떼:   { quantity: 10, sales: 35000 },
      아인슈페너: { quantity: 5,  sales: 25500 },
    },
    paymentSales: { "현금": 120500 },
  },
  "2026-05-26": {
    total: 98500,
    menuSales: {
      아메리카노: { quantity: 15, sales: 45000 },
      카페라떼:   { quantity: 8,  sales: 28000 },
      아인슈페너: { quantity: 5,  sales: 25500 },
    },
    paymentSales: { "현금": 98500 },
  },
  "2026-05-27": {
    total: 168500,
    menuSales: {
      아메리카노: { quantity: 22, sales: 66000 },
      카페라떼:   { quantity: 12, sales: 42000 },
      아인슈페너: { quantity: 12, sales: 60500 },
    },
    paymentSales: { "현금": 126500, "쿠폰": 42000 },
  },
};

for (const [date, data] of Object.entries(rollups)) {
  await db.collection("reportRollups").doc(date).set(data);
}
console.log(`reportRollups ${Object.keys(rollups).length}건 시드 완료`);

// ────────────────────────────────────────────────
// 거래내역 엑셀 검증용 orders (화면 총매출은 reportRollups에서 오므로 합계는 일치하지 않아도 됨).
// ────────────────────────────────────────────────
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

// ────────────────────────────────────────────────
// 대시보드 테스트용 auth 사용자
// ────────────────────────────────────────────────
try {
  await auth.createUser({
    email: "test@holybean.app",
    password: "test1234",
    displayName: "테스트 관리자",
  });
  console.log("auth 사용자 생성 완료: test@holybean.app");
} catch (err) {
  if (err.code === "auth/email-already-exists") {
    console.log("auth 사용자 이미 존재: test@holybean.app");
  } else {
    throw err;
  }
}
