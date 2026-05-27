import {
  signInWithEmailAndPassword, signOut, onAuthStateChanged,
} from "https://www.gstatic.com/firebasejs/11.1.0/firebase-auth.js";
import {
  collection, query, orderBy, limit, where, getDocs, documentId,
} from "https://www.gstatic.com/firebasejs/11.1.0/firebase-firestore.js";
import { auth, db } from "./firebase-init.js";
import { salesRows, totalCups, exportAOA, formatDateLabel } from "./transform.js";

const COLL = "reportRollups";
const $ = (id) => document.getElementById(id);
const won = (n) => n.toLocaleString("ko-KR");

// 현재 보고 있는 날: { date, data } 또는 null
let current = null;

// ---------- 인증 ----------
onAuthStateChanged(auth, (user) => {
  if (user) {
    $("login").hidden = true;
    $("dash").hidden = false;
    if (!current) loadInitial();
  } else {
    current = null;
    $("dash").hidden = true;
    $("message").hidden = true;
    $("login").hidden = false;
  }
});

$("loginBtn").addEventListener("click", async () => {
  $("loginError").hidden = true;
  try {
    await signInWithEmailAndPassword(auth, $("email").value.trim(), $("pw").value);
  } catch {
    $("loginError").textContent = "로그인에 실패했습니다. 이메일과 비밀번호를 확인해주세요.";
    $("loginError").hidden = false;
  }
});

$("logoutBtn").addEventListener("click", () => signOut(auth));

// ---------- Firestore 날짜 조회 ----------
async function fetchLatest() {
  const snap = await getDocs(query(collection(db, COLL), orderBy(documentId(), "desc"), limit(1)));
  return snap.empty ? null : { date: snap.docs[0].id, data: snap.docs[0].data() };
}

async function fetchAdjacent(date, dir) {
  const op = dir === "prev" ? "<" : ">";
  const dir2 = dir === "prev" ? "desc" : "asc";
  const snap = await getDocs(query(
    collection(db, COLL),
    where(documentId(), op, date),
    orderBy(documentId(), dir2),
    limit(1),
  ));
  return snap.empty ? null : { date: snap.docs[0].id, data: snap.docs[0].data() };
}

// ---------- 로드 & 렌더 ----------
async function loadInitial() {
  try {
    const latest = await fetchLatest();
    if (!latest) return showMessage("아직 매출 기록이 없어요.");
    current = latest;
    await render();
  } catch {
    showMessage("데이터를 불러오지 못했습니다. 잠시 후 다시 시도해주세요.");
  }
}

let navigating = false;
async function move(dir) {
  if (!current || navigating) return;
  navigating = true;
  try {
    const next = await fetchAdjacent(current.date, dir);
    if (next) {
      current = next;
      await render();
    }
  } catch {
    showMessage("데이터를 불러오지 못했습니다. 잠시 후 다시 시도해주세요.");
  } finally {
    navigating = false;
  }
}

async function render() {
  $("message").hidden = true;
  $("dash").hidden = false;
  const { date, data } = current;
  const rows = salesRows(data);

  $("dateLabel").textContent = formatDateLabel(date);
  $("total").textContent = won(Number(data.total) || 0);
  $("cups").textContent = totalCups(rows);

  $("items").innerHTML = rows.length
    ? rows.map((r) =>
        `<div class="item"><span class="name">${escapeHtml(r.name)}</span>` +
        `<span class="right"><span class="cnt tnum">${Number(r.quantity) || 0}개</span>` +
        `<span class="amt tnum">${won(Number(r.sales) || 0)}원</span></span></div>`).join("")
    : `<div class="empty">이날 판매된 메뉴가 없어요.</div>`;

  $("exportBtn").disabled = rows.length === 0;

  // 화살표 활성/비활성 (양 옆 존재 여부)
  try {
    const [prev, next] = await Promise.all([
      fetchAdjacent(date, "prev"),
      fetchAdjacent(date, "next"),
    ]);
    $("prev").disabled = !prev;
    $("next").disabled = !next;
  } catch {
    // 경계 판별 실패 시 화살표는 그대로 둔다 (데이터는 이미 표시됨)
  }
}

function showMessage(text) {
  $("dash").hidden = true;
  $("message").textContent = text;
  $("message").hidden = false;
}

function escapeHtml(s) {
  return s.replace(/[&<>"']/g, (c) =>
    ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
}

// ---------- 엑셀 export ----------
$("exportBtn").addEventListener("click", () => {
  if (!current) return;
  const rows = salesRows(current.data);
  const aoa = exportAOA(current.date, rows, Number(current.data.total) || 0);
  const ws = XLSX.utils.aoa_to_sheet(aoa);
  const wb = XLSX.utils.book_new();
  XLSX.utils.book_append_sheet(wb, ws, current.date);
  XLSX.writeFile(wb, `holybean-${current.date}.xlsx`);
});

// ---------- 날짜 이동 버튼 ----------
$("prev").addEventListener("click", () => move("prev"));
$("next").addEventListener("click", () => move("next"));
