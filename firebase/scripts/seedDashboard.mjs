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
