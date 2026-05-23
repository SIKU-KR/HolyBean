import { describe, expect, it } from "vitest";
import { rebuildDerived } from "../src/rebuild.js";

const settled = {
  orderDate: "2026-05-23", orderNum: 1, totalAmount: 9000, customerName: "A", creditStatus: 0,
  items: [{ name: "아메리카노", quantity: 2, subtotal: 9000, unitPrice: 4500 }],
  payments: [{ method: "현금", amount: 9000 }],
};
const credit = {
  orderDate: "2026-05-23", orderNum: 2, totalAmount: 5000, customerName: "B", creditStatus: 1,
  items: [{ name: "라떼", quantity: 1, subtotal: 5000, unitPrice: 5000 }],
  payments: [{ method: "외상", amount: 5000 }],
};

describe("rebuildDerived", () => {
  const out = rebuildDerived([settled, credit]);

  it("daySummaries에 lastOrderNum과 항목", () => {
    const day = out.daySummaries["2026-05-23"];
    expect(day.lastOrderNum).toBe(2);
    expect(day.orders["1"]).toEqual({ customerName: "A", totalAmount: 9000, orderMethod: "현금", creditStatus: 0 });
    expect(day.orders["2"].orderMethod).toBe("외상");
  });

  it("reportRollups는 정산분만 집계", () => {
    const r = out.reportRollups["2026-05-23"];
    expect(r.menuSales["아메리카노"]).toEqual({ quantity: 2, sales: 9000 });
    expect(r.menuSales["라떼"]).toBeUndefined();
    expect(r.paymentSales["현금"]).toBe(9000);
    expect(r.total).toBe(9000);
  });

  it("openCredits는 미수만", () => {
    expect(out.openCredits.items["2026-05-23_2"]).toEqual({
      customerName: "B", totalAmount: 5000, orderNum: 2, orderDate: "2026-05-23",
    });
    expect(out.openCredits.items["2026-05-23_1"]).toBeUndefined();
  });
});
