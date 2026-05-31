import { CREDIT_SETTLED, creditKey } from "./schema.js";

interface OrderData {
  orderDate: string; orderNum: number; totalAmount: number; customerName: string; creditStatus: number;
  items: { name: string; quantity: number; subtotal: number; unitPrice: number }[];
  payments: { method: string; amount: number }[];
}

const orderMethodLabel = (p: OrderData["payments"]) =>
  p.length === 0 ? "Unknown" : p.map((x) => x.method).join("+");

export function rebuildDerived(orders: OrderData[]) {
  const daySummaries: Record<string, { lastOrderNum: number; orders: Record<string, any> }> = {};
  const reportRollups: Record<string, { menuSales: Record<string, { quantity: number; sales: number }>; paymentSales: Record<string, number>; total: number }> = {};
  const openCredits = { items: {} as Record<string, any> };

  for (const o of orders) {
    const day = (daySummaries[o.orderDate] ??= { lastOrderNum: 0, orders: {} });
    day.lastOrderNum = Math.max(day.lastOrderNum, o.orderNum);
    day.orders[String(o.orderNum)] = {
      customerName: o.customerName, totalAmount: o.totalAmount,
      orderMethod: orderMethodLabel(o.payments), creditStatus: o.creditStatus,
    };

    if (o.creditStatus === CREDIT_SETTLED) {
      const r = (reportRollups[o.orderDate] ??= { menuSales: {}, paymentSales: {}, total: 0 });
      for (const it of o.items) {
        const m = (r.menuSales[it.name] ??= { quantity: 0, sales: 0 });
        m.quantity += it.quantity; m.sales += it.subtotal;
      }
      for (const p of o.payments) {
        r.paymentSales[p.method] = (r.paymentSales[p.method] ?? 0) + p.amount;
        r.total += p.amount;
      }
    } else {
      openCredits.items[creditKey(o.orderDate, o.orderNum)] = {
        customerName: o.customerName, totalAmount: o.totalAmount, orderNum: o.orderNum, orderDate: o.orderDate,
      };
    }
  }
  return { daySummaries, reportRollups, openCredits };
}
