import { describe, expect, it } from "vitest";
import { mapDynamoOrder, mapDynamoMenu } from "../src/mapDynamo.js";

describe("mapDynamoOrder", () => {
  it("orders 문서 형태로 매핑", () => {
    const src = {
      orderDate: "2026-05-23", orderNum: 3, totalAmount: 9000, customerName: "홍길동", creditStatus: 0,
      orderItems: [{ itemName: "아메리카노", quantity: 2, subtotal: 9000, unitPrice: 4500 }],
      paymentMethods: [{ method: "현금", amount: 9000 }],
    };
    const doc = mapDynamoOrder(src);
    expect(doc.id).toBe("2026-05-23_3");
    expect(doc.data.items).toEqual([{ name: "아메리카노", quantity: 2, subtotal: 9000, unitPrice: 4500 }]);
    expect(doc.data.payments).toEqual([{ method: "현금", amount: 9000 }]);
    expect(doc.data.creditStatus).toBe(0);
    expect(doc.data.createdAt).toBeInstanceOf(Date);
  });
});

describe("mapDynamoMenu", () => {
  it("menu/current items로 매핑", () => {
    const items = mapDynamoMenu([{ id: 1001, name: "아메리카노", price: 4500, order: 1001, inuse: true }]);
    expect(items).toEqual([{ id: 1001, name: "아메리카노", price: 4500, placement: 1001, inuse: true }]);
  });
});
