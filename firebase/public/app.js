import {
  signInWithEmailAndPassword, signOut, onAuthStateChanged,
} from "https://www.gstatic.com/firebasejs/11.1.0/firebase-auth.js";
import {
  collection, query, orderBy, getDocs, documentId, where,
} from "https://www.gstatic.com/firebasejs/11.1.0/firebase-firestore.js";
import { auth, db } from "./firebase-init.js";
import { salesRows, totalCups, exportAOA, formatDateLabel, paymentRows, transactionAOA } from "./transform.js";

const COLL = "reportRollups";
const $ = (id) => document.getElementById(id);
const won = (n) => n.toLocaleString("ko-KR");

// 로그인 후 reportRollups 전체를 ID 오름차순으로 로드한 배열 [{date, data}], 그리고 현재 인덱스
let days = [];
let idx = -1;

// ---------- 인증 ----------
onAuthStateChanged(auth, (user) => {
  if (user) {
    $("login").hidden = true;
    $("dash").hidden = false;
    if (idx === -1) loadDays(); // 토큰 갱신 시 재로드 방지
  } else {
    days = [];
    idx = -1;
    $("dash").hidden = true;
    $("message").hidden = true;
    $("login").hidden = false;
  }
});

$("loginBtn").addEventListener("click", async () => {
  $("loginError").hidden = true;
  // 아이디만 입력하면 @holybean.app을 붙여 이메일로 변환 (전체 이메일 입력도 허용)
  const raw = $("email").value.trim();
  const email = raw.includes("@") ? raw : `${raw}@holybean.app`;
  try {
    await signInWithEmailAndPassword(auth, email, $("pw").value);
  } catch {
    $("loginError").textContent = "로그인에 실패했습니다. 아이디와 비밀번호를 확인해주세요.";
    $("loginError").hidden = false;
  }
});

$("logoutBtn").addEventListener("click", () => signOut(auth));

// ---------- 로드 & 렌더 ----------
// Firestore는 문서 키 내림차순 정렬("descending key scan")을 지원하지 않으므로,
// 오름차순으로 전체를 한 번 로드해 메모리 배열에 담고 인덱스로 ◀▶ 탐색한다.
async function loadDays() {
  try {
    const snap = await getDocs(query(collection(db, COLL), orderBy(documentId(), "asc")));
    days = snap.docs.map((d) => ({ date: d.id, data: d.data() }));
    if (days.length === 0) return showMessage("아직 매출 기록이 없어요.");
    idx = days.length - 1; // 가장 최근 날
    render();
  } catch {
    showMessage("데이터를 불러오지 못했습니다. 잠시 후 다시 시도해주세요.");
  }
}

function move(dir) {
  const n = idx + (dir === "prev" ? -1 : 1);
  if (n < 0 || n >= days.length) return;
  idx = n;
  render();
}

function render() {
  $("message").hidden = true;
  $("dash").hidden = false;
  const { date, data } = days[idx];
  const rows = salesRows(data);

  $("dateLabel").textContent = formatDateLabel(date);
  $("total").textContent = won(Number(data.total) || 0);
  $("cups").textContent = totalCups(rows);

  const pays = paymentRows(data);
  $("payList").hidden = pays.length === 0;
  $("payments").innerHTML = pays.map((p) =>
    `<div class="item"><span class="name">${escapeHtml(p.method)}</span>` +
    `<span class="right"><span class="cnt tnum">${won(p.amount)}원</span></span></div>`).join("");

  $("items").innerHTML = rows.length
    ? rows.map((r) =>
        `<div class="item"><span class="name">${escapeHtml(r.name)}</span>` +
        `<span class="right"><span class="cnt tnum">${Number(r.quantity) || 0}개</span>` +
        `<span class="amt tnum">${won(Number(r.sales) || 0)}원</span></span></div>`).join("")
    : `<div class="empty">이날 판매된 메뉴가 없어요.</div>`;

  $("exportBtn").disabled = rows.length === 0;
  $("prev").disabled = idx <= 0;
  $("next").disabled = idx >= days.length - 1;
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
$("exportBtn").addEventListener("click", async () => {
  if (idx < 0) return;
  const { date, data } = days[idx];
  const rows = salesRows(data);
  const btn = $("exportBtn");
  btn.disabled = true;
  try {
    // 해당 날짜의 모든 주문(정산·외상 포함)을 조회해 거래내역 시트를 만든다.
    const snap = await getDocs(query(collection(db, "orders"), where("orderDate", "==", date)));
    const orders = snap.docs
      .map((d) => d.data())
      .sort((a, b) => (Number(a.orderNum) || 0) - (Number(b.orderNum) || 0));
    const wb = XLSX.utils.book_new();
    XLSX.utils.book_append_sheet(wb, XLSX.utils.aoa_to_sheet(transactionAOA(orders)), "거래내역");
    XLSX.utils.book_append_sheet(wb, XLSX.utils.aoa_to_sheet(exportAOA(date, rows, Number(data.total) || 0)), "메뉴집계");
    XLSX.writeFile(wb, `holybean-${date}.xlsx`);
  } catch {
    alert("엑셀 데이터를 불러오지 못했습니다. 잠시 후 다시 시도해주세요.");
  } finally {
    btn.disabled = rows.length === 0;
  }
});

// ---------- 날짜 이동 버튼 ----------
$("prev").addEventListener("click", () => move("prev"));
$("next").addEventListener("click", () => move("next"));
