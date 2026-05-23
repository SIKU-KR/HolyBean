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
