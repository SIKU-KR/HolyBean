"""Firestore 쓰기 계획(순수) 및 배치 실행."""
from dataclasses import dataclass

from migration.transform import created_at_for


@dataclass
class DocWrite:
    collection: str
    doc_id: str
    data: dict


def chunked(items, size):
    """items를 size 크기 리스트들로 나눠 yield."""
    items = list(items)
    for start in range(0, len(items), size):
        yield items[start:start + size]


def plan_order_writes(order_docs):
    """orders 컬렉션 쓰기 계획. 각 문서에 createdAt(orderDate 자정 KST) 추가."""
    writes = []
    for od in order_docs:
        data = dict(od)
        data["createdAt"] = created_at_for(od["orderDate"])
        writes.append(DocWrite("orders", f'{od["orderDate"]}_{od["orderNum"]}', data))
    return writes


def plan_aggregate_writes(day_summaries, report_rollups, open_credits):
    """daySummaries/reportRollups(날짜별) + aggregates/openCredits(단일) 쓰기 계획."""
    writes = []
    for date, ds in day_summaries.items():
        writes.append(DocWrite("daySummaries", date, ds))
    for date, rr in report_rollups.items():
        writes.append(DocWrite("reportRollups", date, rr))
    writes.append(DocWrite("aggregates", "openCredits", {"items": open_credits}))
    return writes


def plan_menu_write(menu_items, now):
    """menu/current 쓰기 계획."""
    return DocWrite("menu", "current", {"items": menu_items, "updatedAt": now})


def execute_writes(db, writes, batch_size=500):
    """DocWrite 목록을 Firestore 배치(set)로 기록한다. Firestore 배치 상한은 500."""
    for chunk in chunked(writes, batch_size):
        batch = db.batch()
        for w in chunk:
            batch.set(db.collection(w.collection).document(w.doc_id), w.data)
        batch.commit()
