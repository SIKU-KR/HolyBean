/**
 * 후속 보정(1회성): 골드메달 사과주스를 음료 → 기타로 이동.
 *
 * migrateMenuCategories.ts 반영 직후, 확정표에서 빠뜨린 건을 바로잡는다.
 * 이미 개편된 라이브 상태(현재 id) 기준으로 동작한다.
 *   - 4011 골드메달 사과주스 → 5025 (기타의 병음료 그룹 맨 앞)
 *   - 기존 5025~5032(과일사이다/분다버그/ㄴ아이스컵) → 한 칸씩 뒤로(5026~5033)
 *   - 그 외 항목은 그대로
 *
 * 사용:
 *   npx tsx moveAppleJuiceToEtc.ts            # dry-run
 *   npx tsx moveAppleJuiceToEtc.ts --commit   # 별도 백업 후 menu/current 갱신
 */
import { initializeApp } from "firebase-admin/app";
import { getFirestore, FieldValue } from "firebase-admin/firestore";
import * as fs from "fs";

initializeApp({ projectId: "holybean-e4201" });
const db = getFirestore();

const COMMIT = process.argv.includes("--commit");

/** 현재(개편 후) id → 보정 후 id. 명시되지 않은 id 는 그대로 유지. */
const REMAP: Record<number, number> = {
  4011: 5025, // 골드메달 사과주스 → 기타
  5025: 5026, // 과일사이다 (복숭아)
  5026: 5027, // 과일사이다 (수박)
  5027: 5028, // 과일사이다 (메론)
  5028: 5029, // 과일사이다 (사과)
  5029: 5030, // 분다버그 (자몽)
  5030: 5031, // 분다버그 (레몬)
  5031: 5032, // 분다버그 (망고)
  5032: 5033, // ㄴ 아이스컵
};

const CAT_NAMES: Record<number, string> = {
  1: "HOT커피", 2: "ICE커피", 3: "차", 4: "음료", 5: "기타",
};

(async () => {
  const ref = db.collection("menu").doc("current");
  const snap = await ref.get();
  if (!snap.exists) throw new Error("menu/current 문서가 없음");
  const data = snap.data()!;
  const items = (data.items ?? []) as Array<Record<string, any>>;
  if (items.length !== 82) throw new Error(`항목 수가 82가 아님 (${items.length})`);

  // 가드: 보정 대상이 예상한 메뉴인지 확인 (개편 후 상태가 맞는지 검증)
  const byId = new Map(items.map((it) => [it.id as number, it]));
  if (!byId.get(4011)?.name?.includes("사과주스"))
    throw new Error(`id 4011 이 사과주스가 아님: ${byId.get(4011)?.name}`);
  if (!byId.get(5025)?.name?.includes("과일사이다"))
    throw new Error(`id 5025 이 과일사이다가 아님: ${byId.get(5025)?.name}`);

  const newItems = items.map((it) => {
    const newId = REMAP[it.id as number] ?? (it.id as number);
    return { ...it, id: newId, placement: newId };
  });
  newItems.sort((a, b) => (a.id as number) - (b.id as number));

  // 변경분만 출력
  console.log("변경 항목:");
  for (const it of items) {
    const newId = REMAP[it.id as number];
    if (newId === undefined) continue;
    const cat = CAT_NAMES[Math.floor(newId / 1000)];
    console.log(`  ${it.id} → ${newId}  [${cat}]  ${it.name}`);
  }

  const dist: Record<string, number> = {};
  for (const it of newItems) {
    const cat = CAT_NAMES[Math.floor((it.id as number) / 1000)];
    dist[cat] = (dist[cat] ?? 0) + 1;
  }
  console.log("\n보정 후 카테고리 분포:", dist);

  // 무결성: 새 id 유일성
  const newIds = newItems.map((it) => it.id as number);
  if (new Set(newIds).size !== newIds.length) throw new Error("새 id 중복 발생");

  if (!COMMIT) {
    console.log("\n[DRY-RUN] 쓰기 없음. 실제 반영하려면 --commit 추가.");
    return;
  }

  // 별도 백업(원본 backup-20260527 은 보존)
  const stamp = "20260527-applejuice";
  const backupFile = `menu-backup-${stamp}.json`;
  fs.writeFileSync(backupFile, JSON.stringify(data, null, 2));
  await db.collection("menu").doc(`backup-${stamp}`).set(data);
  console.log(`\n백업 완료: ${backupFile} + menu/backup-${stamp}`);

  await ref.set({ ...data, items: newItems, updatedAt: FieldValue.serverTimestamp() });
  console.log("menu/current 갱신 완료.");

  const after = (await ref.get()).data()!.items as Array<Record<string, any>>;
  const moved = after.find((it) => it.name?.includes("사과주스"));
  console.log(
    `검증: 항목수=${after.length}, 사과주스 id=${moved?.id}(${moved && moved.id >= 5000 ? "기타 OK" : "FAIL"}), ` +
    `id==placement(${after.every((it) => it.id === it.placement) ? "OK" : "FAIL"})`
  );
})().catch((e) => {
  console.error("ERR", e.message);
  process.exit(1);
});
