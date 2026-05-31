export const COLLECTIONS = {
  orders: "orders",
  daySummaries: "daySummaries",
  reportRollups: "reportRollups",
  aggregates: "aggregates",
  menu: "menu",
} as const;
export const OPEN_CREDITS_DOC = "openCredits";
export const MENU_CURRENT_DOC = "current";
export const CREDIT_SETTLED = 0;
export const CREDIT_UNPAID = 1;

export const orderId = (date: string, num: number) => `${date}_${num}`;
export const creditKey = (date: string, num: number) => `${date}_${num}`;
