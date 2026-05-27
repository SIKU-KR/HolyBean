import { describe, it, expect } from "vitest";
import { salesRows, totalCups, exportAOA, formatDateLabel } from "../public/transform.js";

const rollup = {
  total: 142000,
  menuSales: {
    "카페라떼": { quantity: 11, sales: 38500 },
    "아메리카노": { quantity: 32, sales: 96000 },
    "쿠키": { quantity: 3, sales: 9000 },
  },
};

describe("salesRows", () => {
  it("개수 내림차순으로 정렬해 펴낸다", () => {
    expect(salesRows(rollup)).toEqual([
      { name: "아메리카노", quantity: 32, sales: 96000 },
      { name: "카페라떼", quantity: 11, sales: 38500 },
      { name: "쿠키", quantity: 3, sales: 9000 },
    ]);
  });
  it("menuSales가 없으면 빈 배열", () => {
    expect(salesRows({})).toEqual([]);
    expect(salesRows(null)).toEqual([]);
  });
});

describe("totalCups", () => {
  it("모든 행의 개수를 더한다", () => {
    expect(totalCups(salesRows(rollup))).toBe(46);
  });
  it("빈 배열은 0", () => {
    expect(totalCups([])).toBe(0);
  });
});

describe("exportAOA", () => {
  it("헤더 + 메뉴 행 + 합계 행을 만든다", () => {
    expect(exportAOA("2026-05-27", salesRows(rollup), rollup.total)).toEqual([
      ["메뉴", "개수", "금액"],
      ["아메리카노", 32, 96000],
      ["카페라떼", 11, 38500],
      ["쿠키", 3, 9000],
      ["합계", 46, 142000],
    ]);
  });
});

describe("formatDateLabel", () => {
  it("YYYY-MM-DD를 'M월 D일 (요일)'로 바꾼다", () => {
    // 2026-05-27은 수요일
    expect(formatDateLabel("2026-05-27")).toBe("5월 27일 (수)");
  });
});
