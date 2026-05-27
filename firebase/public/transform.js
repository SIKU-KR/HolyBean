// 순수 데이터 변환 — DOM/Firebase 의존성 없음 (브라우저와 vitest 양쪽에서 import)

/** rollup.menuSales를 {name, quantity, sales} 배열로 펴서 개수 내림차순 정렬 */
export function salesRows(rollup) {
  const menuSales = (rollup && rollup.menuSales) || {};
  return Object.entries(menuSales)
    .map(([name, v]) => ({ name, quantity: v.quantity, sales: v.sales }))
    .sort((a, b) => b.quantity - a.quantity);
}

/** rollup.paymentSales를 {method, amount} 배열로 펴서 금액 내림차순 정렬.
 *  금액 0 이하(삭제/취소로 0원이 된 수단)는 제외 — 잘못된 노출 방지. */
export function paymentRows(rollup) {
  const paymentSales = (rollup && rollup.paymentSales) || {};
  return Object.entries(paymentSales)
    .map(([method, amount]) => ({ method, amount: Number(amount) || 0 }))
    .filter((p) => p.amount > 0)
    .sort((a, b) => b.amount - a.amount);
}

/** rollup이 사실상 빈 날인지 — 매출 0 + 실판매 메뉴 없음 + 결제수단 없음.
 *  주문이 전부 삭제/취소돼 0으로 남은 rollup을 날짜 목록에서 제외하는 데 사용. */
export function isEmptyRollup(rollup) {
  if ((Number(rollup && rollup.total) || 0) > 0) return false;
  if (salesRows(rollup).some((r) => (Number(r.quantity) || 0) > 0 || (Number(r.sales) || 0) > 0)) return false;
  if (paymentRows(rollup).length > 0) return false;
  return true;
}

/** 판매 행 배열의 전체 잔 수 합 */
export function totalCups(rows) {
  return rows.reduce((sum, r) => sum + r.quantity, 0);
}

/** SheetJS aoa_to_sheet용 2차원 배열: 헤더 + 메뉴 행 + 합계 행 */
export function exportAOA(date, rows, total) {
  return [
    ["메뉴", "개수", "금액"],
    ...rows.map((r) => [r.name, r.quantity, r.sales]),
    ["합계", totalCups(rows), total],
  ];
}

/** orders 문서 배열(orderNum 오름차순 가정) → 거래내역 시트 AOA: 헤더 + 주문 행 + 합계 행.
 *  빈 배열이면 헤더만 반환. 고객명 빈 값은 "-". 결제수단은 method를 "+"로 결합. */
export function transactionAOA(orders) {
  const header = ["주문번호", "고객명", "주문내역", "총액", "결제수단"];
  if (!orders || orders.length === 0) return [header];
  const rows = orders.map((o) => [
    Number(o.orderNum) || 0,
    o.customerName ? o.customerName : "-",
    (o.items || []).map((it) => `${it.name} ${Number(it.quantity) || 0}개`).join(", "),
    Number(o.totalAmount) || 0,
    (o.payments || []).map((p) => p.method).join("+"),
  ]);
  const totalSum = rows.reduce((s, r) => s + r[3], 0);
  return [header, ...rows, ["합계", "", "", totalSum, ""]];
}

/** "2026-05-27" -> "5월 27일 (수)" */
export function formatDateLabel(date) {
  const [y, m, d] = date.split("-").map(Number);
  const dow = ["일", "월", "화", "수", "목", "금", "토"][new Date(y, m - 1, d).getDay()];
  return `${m}월 ${d}일 (${dow})`;
}
