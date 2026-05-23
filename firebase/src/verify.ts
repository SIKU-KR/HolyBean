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
