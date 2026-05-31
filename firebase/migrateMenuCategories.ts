/**
 * 1회성 마이그레이션: 메뉴 카테고리 개편
 *   기존 [ICE커피, HOT커피, 에이드/스무디, 티/음료, 베이커리]
 *   → 신규 [HOT커피, ICE커피, 차, 음료, 기타]
 *
 * 카테고리는 id/placement 의 천단위 prefix 로 인코딩된다(id/1000 == 카테고리).
 * 주문/리포트는 메뉴를 name 기준으로 저장·집계하므로 id 재배치는 과거 데이터에 영향이 없다.
 * 따라서 본 스크립트는 각 항목의 id·placement 만 확정표대로 재배치하고 name·price·inuse 는 보존한다.
 *
 * 사용:
 *   npx tsx migrateMenuCategories.ts            # dry-run (출력만, 쓰기 없음)
 *   npx tsx migrateMenuCategories.ts --commit   # 백업 후 menu/current 갱신
 *
 * 확정표: docs/menu-recategorization-draft.md
 */
import { initializeApp } from "firebase-admin/app";
import { getFirestore, FieldValue } from "firebase-admin/firestore";
import * as fs from "fs";

initializeApp({ projectId: "holybean-e4201" });
const db = getFirestore();

const COMMIT = process.argv.includes("--commit");

/** 옛 id → 새 id (= 새 placement). 확정표와 1:1 대응. */
const REMAP: Record<number, number> = {
  // ── 기존 ICE커피(1xxx) → 새 ICE커피(2xxx)
  1001: 2001, 1002: 2002, 1003: 2003, 1004: 2004, 1005: 2005, 1006: 2006,
  1007: 2007, 1008: 2008, 1009: 2009, 1010: 2010, 1011: 2011, 1012: 2012,
  1013: 2013, 1014: 2014,
  // ── 기존 HOT커피(2xxx) → 새 HOT커피(1xxx)
  2001: 1001, 2002: 1002, 2003: 1003, 2004: 1004, 2005: 1005, 2006: 1006,
  2007: 1007, 2008: 1008, 2009: 1009, 2010: 1010, 2011: 1011, 2012: 1012,
  2013: 1013, 2014: 1014, 2015: 1015, 2016: 1016,
  // ── 기존 에이드/스무디(3xxx) → 새 음료(4001~4010)
  3001: 4001, 3002: 4002, 3003: 4003, 3004: 4004, 3005: 4005, 3006: 4006,
  3007: 4007, 3008: 4008, 3009: 4009, 3010: 4010,
  // ── 기존 티/음료(4xxx) → 분할
  4001: 3001, 4002: 3002, 4003: 3003, 4004: 3004, // 아이스티 → 차
  4005: 3005, 4006: 3006, 4007: 3007, 4008: 3008, // 자몽/유자/레몬/생강차 → 차
  4018: 3009,                                     // 레몬생강차 → 차
  4009: 4011,                                     // 골드메달 사과주스 → 음료
  4010: 5025, 4011: 5026, 4012: 5027, 4013: 5028, // 과일사이다 → 기타
  4014: 5029, 4015: 5030, 4016: 5031,             // 분다버그 → 기타
  4017: 5032,                                     // ㄴ 아이스컵 → 기타
  // ── 기존 베이커리(5xxx) → 새 기타(5xxx), 변동 없음
  5001: 5001, 5002: 5002, 5003: 5003, 5004: 5004, 5005: 5005, 5006: 5006,
  5007: 5007, 5008: 5008, 5009: 5009, 5010: 5010, 5011: 5011, 5012: 5012,
  5013: 5013, 5014: 5014, 5015: 5015, 5016: 5016, 5017: 5017, 5018: 5018,
  5019: 5019, 5020: 5020, 5021: 5021, 5022: 5022, 5023: 5023, 5024: 5024,
};

const CAT_NAMES: Record<number, string> = {
  1: "HOT커피", 2: "ICE커피", 3: "차", 4: "음료", 5: "기타",
};

function assertRemapSanity() {
  const olds = Object.keys(REMAP).map(Number);
  const news = Object.values(REMAP);
  if (new Set(olds).size !== olds.length) throw new Error("REMAP: 중복된 옛 id");
  if (new Set(news).size !== news.length) throw new Error("REMAP: 중복된 새 id");
  if (olds.length !== 82) throw new Error(`REMAP: 항목 수가 82가 아님 (${olds.length})`);
}

(async () => {
  assertRemapSanity();

  const ref = db.collection("menu").doc("current");
  const snap = await ref.get();
  if (!snap.exists) throw new Error("menu/current 문서가 없음");
  const data = snap.data()!;
  const items = (data.items ?? []) as Array<Record<string, any>>;

  // 라이브 데이터와 REMAP 정합성 확인
  const liveIds = items.map((it) => it.id as number);
  const missing = liveIds.filter((id) => !(id in REMAP));
  if (missing.length) throw new Error(`REMAP 누락된 라이브 id: ${missing.join(", ")}`);
  if (items.length !== Object.keys(REMAP).length)
    throw new Error(`라이브 항목 수(${items.length}) != REMAP(${Object.keys(REMAP).length})`);

  const byOldId = new Map(items.map((it) => [it.id as number, it]));

  const newItems = items.map((it) => {
    const newId = REMAP[it.id as number];
    return { ...it, id: newId, placement: newId };
  });
  newItems.sort((a, b) => (a.id as number) - (b.id as number));

  // 변경 미리보기 출력
  console.log(`총 ${items.length}개 항목\n`);
  const sortedOld = [...byOldId.keys()].sort((a, b) => a - b);
  console.log("옛ID → 새ID  [새카테고리]  이름");
  for (const oldId of sortedOld) {
    const it = byOldId.get(oldId)!;
    const newId = REMAP[oldId];
    const cat = CAT_NAMES[Math.floor(newId / 1000)];
    const mark = oldId === newId ? "   " : " * ";
    console.log(`${oldId} →${mark}${newId}  [${cat}]  ${it.name}`);
  }

  // 카테고리별 분포
  const dist: Record<string, number> = {};
  for (const it of newItems) {
    const cat = CAT_NAMES[Math.floor((it.id as number) / 1000)];
    dist[cat] = (dist[cat] ?? 0) + 1;
  }
  console.log("\n새 카테고리 분포:", dist);

  if (!COMMIT) {
    console.log("\n[DRY-RUN] 쓰기 없음. 실제 반영하려면 --commit 추가.");
    return;
  }

  // 백업: 로컬 JSON + Firestore menu/backup-20260527
  const stamp = "20260527";
  const backupFile = `menu-backup-${stamp}.json`;
  fs.writeFileSync(backupFile, JSON.stringify(data, null, 2));
  await db.collection("menu").doc(`backup-${stamp}`).set(data);
  console.log(`\n백업 완료: ${backupFile} + menu/backup-${stamp}`);

  // 실제 갱신
  await ref.set({ ...data, items: newItems, updatedAt: FieldValue.serverTimestamp() });
  console.log("menu/current 갱신 완료.");

  // 검증: 재조회
  const after = (await ref.get()).data()!.items as Array<Record<string, any>>;
  const okCount = after.length === 82;
  const okEncoding = after.every((it) => it.id === it.placement);
  console.log(`검증: 항목수=${after.length}(${okCount ? "OK" : "FAIL"}), id==placement(${okEncoding ? "OK" : "FAIL"})`);
})().catch((e) => {
  console.error("ERR", e.message);
  process.exit(1);
});
