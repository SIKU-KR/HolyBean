// 순수 데이터 변환 — DOM/Firebase 의존성 없음 (브라우저와 vitest 양쪽에서 import)

/** rollup.menuSales를 {name, quantity, sales} 배열로 펴서 개수 내림차순 정렬 */
export function salesRows(rollup) {
  const menuSales = (rollup && rollup.menuSales) || {};
  return Object.entries(menuSales)
    .map(([name, v]) => ({ name, quantity: v.quantity, sales: v.sales }))
    .sort((a, b) => b.quantity - a.quantity);
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

/** "2026-05-27" -> "5월 27일 (화)" */
export function formatDateLabel(date) {
  const [y, m, d] = date.split("-").map(Number);
  const dow = ["일", "월", "화", "수", "목", "금", "토"][new Date(y, m - 1, d).getDay()];
  return `${m}월 ${d}일 (${dow})`;
}
