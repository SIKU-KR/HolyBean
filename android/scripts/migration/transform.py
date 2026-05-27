"""DynamoDB → Firestore 변환을 위한 순수 함수 모음."""
from datetime import datetime, timedelta, timezone

from boto3.dynamodb.types import TypeDeserializer

KST = timezone(timedelta(hours=9))
_deserializer = TypeDeserializer()


def deserialize_item(item):
    """DynamoDB 항목(타입 태그 형식)을 평범한 파이썬 dict로 변환한다.

    숫자는 Decimal, NULL은 None으로 온다.
    """
    return {key: _deserializer.deserialize(value) for key, value in item.items()}


def order_method_label(payments):
    """결제수단 라벨: payments가 비면 'Unknown', 아니면 method를 '+'로 조인."""
    if not payments:
        return "Unknown"
    return "+".join(p["method"] for p in payments)


def created_at_for(order_date):
    """orderDate('YYYY-MM-DD') 자정(KST) datetime. Firestore가 Timestamp로 저장."""
    return datetime.strptime(order_date, "%Y-%m-%d").replace(tzinfo=KST)


def to_order_doc(order):
    """역직렬화된 주문 → Firestore orders 문서(createdAt 제외).

    createdAt은 쓰기 계획 단계(plan_order_writes)에서 붙인다.
    """
    return {
        "orderDate": order["orderDate"],
        "orderNum": int(order["orderNum"]),
        "totalAmount": int(order["totalAmount"]),
        "customerName": order.get("customerName") or "",
        "creditStatus": int(order["creditStatus"]),
        "items": [
            {
                "name": it["itemName"],
                "quantity": int(it["quantity"]),
                "subtotal": int(it["subtotal"]),
                "unitPrice": int(it["unitPrice"]),
            }
            for it in order.get("orderItems", [])
        ],
        "payments": [
            {"method": p["method"], "amount": int(p["amount"])}
            for p in order.get("paymentMethods", [])
        ],
    }


def day_summary_entry(order_doc):
    """daySummaries.orders[num] 항목. 모든 주문(정산 여부 무관)에 사용."""
    return {
        "customerName": order_doc["customerName"],
        "totalAmount": order_doc["totalAmount"],
        "orderMethod": order_method_label(order_doc["payments"]),
        "creditStatus": order_doc["creditStatus"],
    }


def build_aggregates(order_docs):
    """주문 문서 목록에서 (day_summaries, report_rollups, open_credits)를 계산한다.

    - day_summaries: 날짜별 모든 주문. {date: {"lastOrderNum", "orders": {numStr: entry}}}
    - report_rollups: creditStatus==0만. {date: {"menuSales", "paymentSales", "total"}}
    - open_credits: creditStatus==1만. {"{date}_{num}": {...}}
    """
    day_summaries = {}
    report_rollups = {}
    open_credits = {}

    for od in order_docs:
        date = od["orderDate"]
        num = od["orderNum"]

        ds = day_summaries.setdefault(date, {"lastOrderNum": 0, "orders": {}})
        ds["orders"][str(num)] = day_summary_entry(od)
        if num > ds["lastOrderNum"]:
            ds["lastOrderNum"] = num

        if od["creditStatus"] == 0:
            rr = report_rollups.setdefault(date, {"menuSales": {}, "paymentSales": {}, "total": 0})
            for it in od["items"]:
                ms = rr["menuSales"].setdefault(it["name"], {"quantity": 0, "sales": 0})
                ms["quantity"] += it["quantity"]
                ms["sales"] += it["subtotal"]
            for p in od["payments"]:
                rr["paymentSales"][p["method"]] = rr["paymentSales"].get(p["method"], 0) + p["amount"]
                rr["total"] += p["amount"]
        else:
            open_credits[f"{date}_{num}"] = {
                "customerName": od["customerName"],
                "totalAmount": od["totalAmount"],
                "orderNum": num,
                "orderDate": date,
            }

    return day_summaries, report_rollups, open_credits


def pick_latest_menu(menu_versions):
    """역직렬화된 메뉴 버전 목록에서 timestamp가 가장 큰(최신) 버전을 고른다."""
    return max(menu_versions, key=lambda v: v["timestamp"])


def to_menu_items(menu_version):
    """메뉴 버전 → menu/current.items. order 필드를 placement로 바꾼다."""
    return [
        {
            "id": int(m["id"]),
            "name": m["name"],
            "price": int(m["price"]),
            "placement": int(m["order"]),
            "inuse": bool(m["inuse"]),
        }
        for m in menu_version["menu_items"]
    ]
