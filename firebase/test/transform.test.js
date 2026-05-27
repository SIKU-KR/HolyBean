import { describe, it, expect } from "vitest";
import { salesRows, totalCups, exportAOA, formatDateLabel, paymentRows, transactionAOA } from "../public/transform.js";

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

describe("paymentRows", () => {
  it("paymentSales를 금액 내림차순 {method, amount} 배열로 펴낸다", () => {
    expect(paymentRows({ paymentSales: { "쿠폰": 42000, "현금": 126500, "계좌이체": 50000 } })).toEqual([
      { method: "현금", amount: 126500 },
      { method: "계좌이체", amount: 50000 },
      { method: "쿠폰", amount: 42000 },
    ]);
  });
  it("paymentSales가 없으면 빈 배열", () => {
    expect(paymentRows({})).toEqual([]);
    expect(paymentRows(null)).toEqual([]);
  });
});

describe("transactionAOA", () => {
  const orders = [
    {
      orderNum: 3, customerName: "김철수", totalAmount: 5000, creditStatus: 1,
      items: [{ name: "아인슈페너", quantity: 1 }],
      payments: [{ method: "외상", amount: 5000 }],
    },
    {
      orderNum: 5, customerName: "", totalAmount: 9000, creditStatus: 0,
      items: [{ name: "아메리카노", quantity: 2 }, { name: "카페라떼", quantity: 1 }],
      payments: [{ method: "현금", amount: 5000 }, { method: "쿠폰", amount: 4000 }],
    },
  ];
  it("주문 한 건 = 한 행, 익명은 '-', 결제수단 결합, 합계 행을 만든다", () => {
    expect(transactionAOA(orders)).toEqual([
      ["주문번호", "고객명", "주문내역", "총액", "결제수단"],
      [3, "김철수", "아인슈페너 1개", 5000, "외상"],
      [5, "-", "아메리카노 2개, 카페라떼 1개", 9000, "현금+쿠폰"],
      ["합계", "", "", 14000, ""],
    ]);
  });
  it("빈 배열이면 헤더만 반환", () => {
    expect(transactionAOA([])).toEqual([
      ["주문번호", "고객명", "주문내역", "총액", "결제수단"],
    ]);
  });
});
