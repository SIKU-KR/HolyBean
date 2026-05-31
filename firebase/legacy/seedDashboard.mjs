// 에뮬레이터 전용 시드 스크립트.
// 사용법(별도 터미널, firebase/ 에서):
//   1) firebase emulators:start --only firestore,auth,hosting
//   2) node legacy/seedDashboard.mjs   (또는 npm run seed:dashboard)
import admin from "firebase-admin";

process.env.FIRESTORE_EMULATOR_HOST = "localhost:8080";
process.env.FIREBASE_AUTH_EMULATOR_HOST = "localhost:9099";

admin.initializeApp({ projectId: "holybean-e4201" });
const db = admin.firestore();
const auth = admin.auth();

const rollups = {
  "2026-05-25": {
    total: 120500,
    paymentSales: { "현금": 120500 },
    menuSales: {
      "아메리카노": { quantity: 28, sales: 84000 },
      "카페라떼": { quantity: 9, sales: 31500 },
      "복숭아아이스티": { quantity: 1, sales: 5000 },
    },
  },
  "2026-05-26": {
    total: 98500,
    paymentSales: { "현금": 98500 },
    menuSales: {
      "아메리카노": { quantity: 21, sales: 63000 },
      "카페라떼": { quantity: 7, sales: 24500 },
      "쿠키": { quantity: 2, sales: 6000 },
      "아인슈페너": { quantity: 1, sales: 5000 },
    },
  },
  "2026-05-27": {
    total: 168500,
    paymentSales: { "현금": 126500, "쿠폰": 42000 },
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
