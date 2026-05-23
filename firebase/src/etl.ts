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
