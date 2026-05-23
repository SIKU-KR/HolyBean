import { initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import { COLLECTIONS, MENU_CURRENT_DOC } from "./schema.js";
import { mapDynamoMenu } from "./mapDynamo.js";

// AWS 레거시 메뉴 API에서 메뉴만 가져와 Firestore menu/current 에 적재한다.
// 비밀값은 커밋하지 않고 환경변수로 주입한다.
//   HOLYBEAN_AWS_APIKEY   (필수) AWS API Gateway apikey 헤더
//   HOLYBEAN_AWS_BASE_URL (선택) 기본값 = 운영 API Gateway
//   FIREBASE_PROJECT      (선택) 기본값 = holybean-e4201 (Admin SDK는 ADC 사용)
const BASE_URL =
  process.env.HOLYBEAN_AWS_BASE_URL ??
  "https://vk0i6j4tfi.execute-api.ap-northeast-2.amazonaws.com";
const API_KEY = process.env.HOLYBEAN_AWS_APIKEY;
const PROJECT = process.env.FIREBASE_PROJECT ?? "holybean-e4201";

async function fetchMenu(): Promise<any[]> {
  if (!API_KEY) throw new Error("HOLYBEAN_AWS_APIKEY 환경변수가 필요합니다.");
  const res = await fetch(`${BASE_URL}/menu`, {
    headers: { apikey: API_KEY, "Content-Type": "application/json" },
  });
  if (!res.ok) throw new Error(`AWS /menu 응답 오류: HTTP ${res.status}`);
  let payload: any = await res.json();
  // API Gateway가 {statusCode, body} 형태로 감싸는 경우 한 번 더 푼다.
  if (payload && typeof payload === "object" && !("menulist" in payload) && typeof payload.body === "string") {
    payload = JSON.parse(payload.body);
  }
  const menulist = payload?.menulist ?? [];
  if (!Array.isArray(menulist) || menulist.length === 0) {
    throw new Error("AWS 응답에 menulist가 비어 있습니다.");
  }
  return menulist;
}

async function main() {
  const menulist = await fetchMenu();
  const items = mapDynamoMenu(menulist); // order -> placement
  console.log(`AWS에서 메뉴 ${items.length}개를 가져왔습니다. (예: ${JSON.stringify(items[0])})`);

  initializeApp({ projectId: PROJECT });
  const db = getFirestore();
  await db
    .collection(COLLECTIONS.menu)
    .doc(MENU_CURRENT_DOC)
    .set({ items, updatedAt: new Date() });

  console.log(`menu/current 적재 완료: ${items.length}개 항목 (project=${PROJECT}).`);
}

main()
  .then(() => process.exit(0))
  .catch((e) => {
    console.error(e);
    process.exit(1);
  });
