import { orderId } from "./schema.js";

export interface OrderDoc { id: string; data: Record<string, unknown>; }

export function mapDynamoOrder(src: any): OrderDoc {
  return {
    id: orderId(src.orderDate, src.orderNum),
    data: {
      orderDate: src.orderDate,
      orderNum: src.orderNum,
      totalAmount: src.totalAmount,
      customerName: src.customerName ?? "",
      creditStatus: src.creditStatus,
      items: (src.orderItems ?? []).map((i: any) => ({
        name: i.itemName, quantity: i.quantity, subtotal: i.subtotal, unitPrice: i.unitPrice,
      })),
      payments: (src.paymentMethods ?? []).map((p: any) => ({ method: p.method, amount: p.amount })),
      createdAt: new Date(`${src.orderDate}T00:00:00+09:00`),
    },
  };
}

export function mapDynamoMenu(items: any[]): Record<string, unknown>[] {
  return items.map((m) => ({
    id: m.id, name: m.name, price: m.price, placement: m.order ?? m.placement, inuse: m.inuse,
  }));
}
