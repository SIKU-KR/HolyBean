# DynamoDB → Firestore 마이그레이션 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** AWS DynamoDB(`holybean`, `holybean-menu`)의 주문/메뉴 데이터를 Firestore(`holybean-e4201`)로 일회성 이관하는 Python 스크립트를 만든다.

**Architecture:** 순수 변환/집계 로직(`migration/transform.py`)과 쓰기 계획/실행(`migration/writes.py`)을 I/O 없이 단위 테스트 가능하게 분리하고, CLI 오케스트레이션 스크립트(`migrate_dynamo_to_firestore.py`)가 DynamoDB 스캔 → 변환 → Firestore 기록을 연결한다. 모든 쓰기는 `set`(덮어쓰기)이라 멱등하며 `--dry-run`과 기록 후 검증 패스를 둔다.

**Tech Stack:** Python 3, boto3(DynamoDB), firebase-admin(Firestore), pytest

설계 문서: `android/docs/superpowers/specs/2026-05-27-dynamodb-to-firestore-migration-design.md`

모든 경로는 `/Users/benn/dev/personal/HolyBean/android/` 기준 상대경로다. 명령은 `android/scripts/` 디렉터리에서 실행한다.

---

## File Structure

- `scripts/requirements.txt` — 의존성(boto3, firebase-admin, pytest)
- `scripts/.gitignore` — service account 키, venv 제외
- `scripts/README.md` — 사용법, 자격증명, 실행 절차
- `scripts/migration/__init__.py` — 패키지 마커
- `scripts/migration/transform.py` — 순수 함수: DynamoDB 역직렬화, 주문/메뉴 변환, 집계 계산
- `scripts/migration/writes.py` — `DocWrite`, 청킹, 쓰기 계획(`plan_*`), 배치 실행(`execute_writes`)
- `scripts/migrate_dynamo_to_firestore.py` — CLI 오케스트레이션(스캔, 변환 연결, dry-run, 검증)
- `scripts/tests/__init__.py` — 테스트 패키지 마커
- `scripts/tests/test_transform.py` — transform 단위 테스트
- `scripts/tests/test_writes.py` — writes 단위 테스트(Fake Firestore)

---

## Task 1: 프로젝트 스캐폴딩

**Files:**
- Create: `scripts/requirements.txt`
- Create: `scripts/.gitignore`
- Create: `scripts/migration/__init__.py`
- Create: `scripts/tests/__init__.py`
- Create: `scripts/README.md`

- [ ] **Step 1: requirements.txt 작성**

`scripts/requirements.txt`:
```
boto3==1.34.131
firebase-admin==6.5.0
pytest==8.2.2
```

- [ ] **Step 2: .gitignore 작성**

`scripts/.gitignore`:
```
.venv/
__pycache__/
*.pyc
service-account*.json
```

- [ ] **Step 3: 빈 패키지 마커 생성**

`scripts/migration/__init__.py`: (빈 파일)
`scripts/tests/__init__.py`: (빈 파일)

- [ ] **Step 4: README 작성**

`scripts/README.md`:
```markdown
# DynamoDB → Firestore 마이그레이션

구버전 DynamoDB(`holybean`, `holybean-menu`)의 주문/메뉴 데이터를
Firestore(`holybean-e4201`)로 일회성 이관한다.

## 준비

1. 가상환경 + 의존성:
   ```
   python3 -m venv .venv
   source .venv/bin/activate
   pip install -r requirements.txt
   ```
2. AWS: CLI 로그인 상태 사용(region `ap-northeast-2`).
3. Firebase: 콘솔 > 프로젝트 설정 > 서비스 계정 > 새 비공개 키 생성으로
   service account JSON을 받아 이 디렉터리에 둔다(커밋 금지, .gitignore 처리됨).

## 실행

dry-run(기록 없이 변환 결과만 확인):
```
python migrate_dynamo_to_firestore.py --service-account service-account.json --dry-run
```

실제 이관:
```
python migrate_dynamo_to_firestore.py --service-account service-account.json
```

기록 후 자동 검증 패스가 주문 수/집계 합계를 대조한다.

## 테스트
```
pytest
```
```

- [ ] **Step 5: 의존성 설치 확인**

Run:
```
cd /Users/benn/dev/personal/HolyBean/android/scripts && python3 -m venv .venv && source .venv/bin/activate && pip install -r requirements.txt && python -c "import boto3, firebase_admin, pytest; print('ok')"
```
Expected: 마지막 줄에 `ok` 출력

- [ ] **Step 6: Commit**

```bash
cd /Users/benn/dev/personal/HolyBean
git add android/scripts/requirements.txt android/scripts/.gitignore android/scripts/migration/__init__.py android/scripts/tests/__init__.py android/scripts/README.md
git commit -m "chore(migration): scaffold dynamo→firestore migration script"
```

---

## Task 2: DynamoDB 항목 역직렬화

DynamoDB 항목은 `{"S": "x"}`, `{"N": "5"}`, `{"NULL": true}`, `{"L": [...]}`, `{"M": {...}}` 형식이다. boto3 `TypeDeserializer`로 평범한 파이썬 값으로 바꾼다(숫자는 `Decimal`로 옴 — 이후 태스크에서 `int` 변환).

**Files:**
- Create: `scripts/migration/transform.py`
- Test: `scripts/tests/test_transform.py`

- [ ] **Step 1: 실패하는 테스트 작성**

`scripts/tests/test_transform.py`:
```python
from decimal import Decimal

from migration.transform import deserialize_item

SAMPLE_ORDER = {
    "orderDate": {"S": "2024-06-30"},
    "orderNum": {"N": "1"},
    "totalAmount": {"N": "28500"},
    "customerName": {"S": "식당"},
    "creditStatus": {"N": "0"},
    "orderItems": {"L": [
        {"M": {"itemName": {"S": "아이스 아메리카노"}, "unitPrice": {"N": "2000"},
               "quantity": {"N": "8"}, "subtotal": {"N": "16000"}}},
    ]},
    "paymentMethods": {"L": [
        {"M": {"method": {"S": "무료제공"}, "amount": {"N": "28500"}}},
    ]},
}


def test_deserialize_item_basic_fields():
    result = deserialize_item(SAMPLE_ORDER)
    assert result["orderDate"] == "2024-06-30"
    assert result["orderNum"] == Decimal("1")
    assert result["customerName"] == "식당"
    assert result["orderItems"][0]["itemName"] == "아이스 아메리카노"
    assert result["paymentMethods"][0]["amount"] == Decimal("28500")


def test_deserialize_item_null_customer_name():
    item = dict(SAMPLE_ORDER, customerName={"NULL": True})
    result = deserialize_item(item)
    assert result["customerName"] is None
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `cd /Users/benn/dev/personal/HolyBean/android/scripts && source .venv/bin/activate && pytest tests/test_transform.py -v`
Expected: FAIL — `ModuleNotFoundError` 또는 `ImportError: cannot import name 'deserialize_item'`

- [ ] **Step 3: 구현**

`scripts/migration/transform.py`:
```python
"""DynamoDB → Firestore 변환을 위한 순수 함수 모음."""
from boto3.dynamodb.types import TypeDeserializer

_deserializer = TypeDeserializer()


def deserialize_item(item):
    """DynamoDB 항목(타입 태그 형식)을 평범한 파이썬 dict로 변환한다.

    숫자는 Decimal, NULL은 None으로 온다.
    """
    return {key: _deserializer.deserialize(value) for key, value in item.items()}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd /Users/benn/dev/personal/HolyBean/android/scripts && source .venv/bin/activate && pytest tests/test_transform.py -v`
Expected: 2 passed

- [ ] **Step 5: Commit**

```bash
cd /Users/benn/dev/personal/HolyBean
git add android/scripts/migration/transform.py android/scripts/tests/test_transform.py
git commit -m "feat(migration): deserialize dynamodb items"
```

---

## Task 3: 주문 문서 변환

역직렬화된 주문을 Firestore `orders` 문서 형태로 바꾼다. 필드 이름 변경(`itemName`→`name`, `paymentMethods`→`payments`), NULL customerName→`""`, 숫자 `int` 변환, `createdAt`는 orderDate 자정(KST)으로 합성.

**Files:**
- Modify: `scripts/migration/transform.py`
- Test: `scripts/tests/test_transform.py`

- [ ] **Step 1: 실패하는 테스트 작성** (`test_transform.py` 끝에 추가)

```python
from datetime import datetime, timedelta, timezone

from migration.transform import created_at_for, order_method_label, to_order_doc


def test_to_order_doc_renames_and_coerces():
    deserialized = deserialize_item(SAMPLE_ORDER)
    doc = to_order_doc(deserialized)
    assert doc == {
        "orderDate": "2024-06-30",
        "orderNum": 1,
        "totalAmount": 28500,
        "customerName": "식당",
        "creditStatus": 0,
        "items": [
            {"name": "아이스 아메리카노", "quantity": 8, "subtotal": 16000, "unitPrice": 2000},
        ],
        "payments": [
            {"method": "무료제공", "amount": 28500},
        ],
    }
    # 모든 숫자는 순수 int 여야 한다(Decimal 누수 방지).
    assert isinstance(doc["orderNum"], int)
    assert isinstance(doc["items"][0]["quantity"], int)


def test_to_order_doc_null_customer_name_becomes_empty_string():
    deserialized = deserialize_item(dict(SAMPLE_ORDER, customerName={"NULL": True}))
    doc = to_order_doc(deserialized)
    assert doc["customerName"] == ""


def test_order_method_label_joins_with_plus():
    assert order_method_label([{"method": "카드", "amount": 1}, {"method": "현금", "amount": 2}]) == "카드+현금"


def test_order_method_label_empty_is_unknown():
    assert order_method_label([]) == "Unknown"


def test_created_at_for_is_kst_midnight():
    dt = created_at_for("2024-06-30")
    assert (dt.year, dt.month, dt.day, dt.hour, dt.minute) == (2024, 6, 30, 0, 0)
    assert dt.utcoffset() == timedelta(hours=9)
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `cd /Users/benn/dev/personal/HolyBean/android/scripts && source .venv/bin/activate && pytest tests/test_transform.py -v`
Expected: FAIL — `ImportError: cannot import name 'to_order_doc'`

- [ ] **Step 3: 구현** (`transform.py`에 추가)

```python
from datetime import datetime, timedelta, timezone

KST = timezone(timedelta(hours=9))


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

    createdAt은 쓰기 계획 단계에서 created_at_for로 붙인다.
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
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd /Users/benn/dev/personal/HolyBean/android/scripts && source .venv/bin/activate && pytest tests/test_transform.py -v`
Expected: 7 passed

- [ ] **Step 5: Commit**

```bash
cd /Users/benn/dev/personal/HolyBean
git add android/scripts/migration/transform.py android/scripts/tests/test_transform.py
git commit -m "feat(migration): transform orders to firestore doc shape"
```

---

## Task 4: 집계 계산 (daySummaries / reportRollups / openCredits)

앱의 `OrderAggregation` + `postOrder` 로직을 복제한다. daySummaries는 전체 주문, reportRollups는 `creditStatus==0`만, openCredits는 `creditStatus==1`만.

**Files:**
- Modify: `scripts/migration/transform.py`
- Test: `scripts/tests/test_transform.py`

- [ ] **Step 1: 실패하는 테스트 작성** (`test_transform.py` 끝에 추가)

```python
from migration.transform import build_aggregates, day_summary_entry


def _doc(date, num, credit_status, items, payments, customer="손님"):
    return {
        "orderDate": date, "orderNum": num, "totalAmount": sum(p["amount"] for p in payments),
        "customerName": customer, "creditStatus": credit_status,
        "items": items, "payments": payments,
    }


def test_day_summary_entry_shape():
    doc = _doc("2024-06-30", 1, 0,
               [{"name": "아메리카노", "quantity": 1, "subtotal": 2000, "unitPrice": 2000}],
               [{"method": "카드", "amount": 1000}, {"method": "현금", "amount": 1000}])
    assert day_summary_entry(doc) == {
        "customerName": "손님", "totalAmount": 2000,
        "orderMethod": "카드+현금", "creditStatus": 0,
    }


def test_build_aggregates_separates_settled_and_unpaid():
    settled = _doc("2024-06-30", 1, 0,
                   [{"name": "아메리카노", "quantity": 2, "subtotal": 4000, "unitPrice": 2000}],
                   [{"method": "카드", "amount": 4000}])
    unpaid = _doc("2024-06-30", 2, 1,
                  [{"name": "라떼", "quantity": 1, "subtotal": 2500, "unitPrice": 2500}],
                  [{"method": "외상", "amount": 2500}], customer="단골")

    day_summaries, report_rollups, open_credits = build_aggregates([settled, unpaid])

    # daySummaries: 두 주문 모두 포함, lastOrderNum = 2
    assert day_summaries["2024-06-30"]["lastOrderNum"] == 2
    assert set(day_summaries["2024-06-30"]["orders"].keys()) == {"1", "2"}

    # reportRollups: 정산(creditStatus==0)만
    rr = report_rollups["2024-06-30"]
    assert rr["menuSales"] == {"아메리카노": {"quantity": 2, "sales": 4000}}
    assert rr["paymentSales"] == {"카드": 4000}
    assert rr["total"] == 4000

    # openCredits: 미수(creditStatus==1)만
    assert open_credits == {
        "2024-06-30_2": {
            "customerName": "단골", "totalAmount": 2500, "orderNum": 2, "orderDate": "2024-06-30",
        }
    }


def test_build_aggregates_accumulates_same_menu_across_orders():
    a = _doc("2024-07-01", 1, 0,
             [{"name": "아메리카노", "quantity": 1, "subtotal": 2000, "unitPrice": 2000}],
             [{"method": "현금", "amount": 2000}])
    b = _doc("2024-07-01", 2, 0,
             [{"name": "아메리카노", "quantity": 3, "subtotal": 6000, "unitPrice": 2000}],
             [{"method": "현금", "amount": 6000}])
    _, report_rollups, _ = build_aggregates([a, b])
    rr = report_rollups["2024-07-01"]
    assert rr["menuSales"] == {"아메리카노": {"quantity": 4, "sales": 8000}}
    assert rr["paymentSales"] == {"현금": 8000}
    assert rr["total"] == 8000
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `cd /Users/benn/dev/personal/HolyBean/android/scripts && source .venv/bin/activate && pytest tests/test_transform.py -v`
Expected: FAIL — `ImportError: cannot import name 'build_aggregates'`

- [ ] **Step 3: 구현** (`transform.py`에 추가)

```python
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
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd /Users/benn/dev/personal/HolyBean/android/scripts && source .venv/bin/activate && pytest tests/test_transform.py -v`
Expected: 10 passed

- [ ] **Step 5: Commit**

```bash
cd /Users/benn/dev/personal/HolyBean
git add android/scripts/migration/transform.py android/scripts/tests/test_transform.py
git commit -m "feat(migration): compute daySummaries/reportRollups/openCredits aggregates"
```

---

## Task 5: 메뉴 변환

`holybean-menu`는 timestamp별 6개 버전. 최신 1개를 골라 `menu/current.items`로 변환(`order`→`placement`).

**Files:**
- Modify: `scripts/migration/transform.py`
- Test: `scripts/tests/test_transform.py`

- [ ] **Step 1: 실패하는 테스트 작성** (`test_transform.py` 끝에 추가)

```python
from migration.transform import pick_latest_menu, to_menu_items


def test_pick_latest_menu_by_timestamp():
    versions = [
        {"timestamp": "2025-03-12T14:10:14.398852Z", "menu_items": []},
        {"timestamp": "2025-08-24T02:49:06.574721Z", "menu_items": [{"marker": "latest"}]},
        {"timestamp": "2025-06-19T12:00:03.452016Z", "menu_items": []},
    ]
    latest = pick_latest_menu(versions)
    assert latest["menu_items"] == [{"marker": "latest"}]


def test_to_menu_items_renames_order_to_placement_and_coerces():
    version = {"menu_items": [
        {"id": Decimal("1001"), "name": "아이스 아메리카노", "price": Decimal("2000"),
         "order": Decimal("1001"), "inuse": True},
    ]}
    items = to_menu_items(version)
    assert items == [
        {"id": 1001, "name": "아이스 아메리카노", "price": 2000, "placement": 1001, "inuse": True},
    ]
    assert isinstance(items[0]["id"], int)
    assert isinstance(items[0]["placement"], int)
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `cd /Users/benn/dev/personal/HolyBean/android/scripts && source .venv/bin/activate && pytest tests/test_transform.py -v`
Expected: FAIL — `ImportError: cannot import name 'pick_latest_menu'`

- [ ] **Step 3: 구현** (`transform.py`에 추가)

```python
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
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd /Users/benn/dev/personal/HolyBean/android/scripts && source .venv/bin/activate && pytest tests/test_transform.py -v`
Expected: 12 passed

- [ ] **Step 5: Commit**

```bash
cd /Users/benn/dev/personal/HolyBean
git add android/scripts/migration/transform.py android/scripts/tests/test_transform.py
git commit -m "feat(migration): transform menu to current doc shape"
```

---

## Task 6: 쓰기 계획 및 배치 실행

순수한 "쓰기 계획"(`DocWrite` 목록 생성)과 Firestore 배치 실행을 분리한다. 계획은 단위 테스트, 실행은 Fake Firestore로 배치 동작을 검증.

**Files:**
- Create: `scripts/migration/writes.py`
- Test: `scripts/tests/test_writes.py`

- [ ] **Step 1: 실패하는 테스트 작성**

`scripts/tests/test_writes.py`:
```python
from datetime import datetime, timezone

from migration.writes import (
    DocWrite, chunked, execute_writes,
    plan_aggregate_writes, plan_menu_write, plan_order_writes,
)


def test_chunked_splits_into_sized_groups():
    assert list(chunked([1, 2, 3, 4, 5], 2)) == [[1, 2], [3, 4], [5]]


def test_plan_order_writes_adds_created_at_and_doc_id():
    order_docs = [{
        "orderDate": "2024-06-30", "orderNum": 1, "totalAmount": 2000,
        "customerName": "", "creditStatus": 0, "items": [], "payments": [],
    }]
    writes = plan_order_writes(order_docs)
    assert len(writes) == 1
    w = writes[0]
    assert w.collection == "orders"
    assert w.doc_id == "2024-06-30_1"
    assert w.data["orderNum"] == 1
    assert w.data["createdAt"].utcoffset().total_seconds() == 9 * 3600
    assert (w.data["createdAt"].year, w.data["createdAt"].month, w.data["createdAt"].day) == (2024, 6, 30)


def test_plan_aggregate_writes_wraps_open_credits_in_items():
    day_summaries = {"2024-06-30": {"lastOrderNum": 1, "orders": {"1": {}}}}
    report_rollups = {"2024-06-30": {"menuSales": {}, "paymentSales": {}, "total": 0}}
    open_credits = {"2024-06-30_2": {"orderNum": 2}}

    writes = plan_aggregate_writes(day_summaries, report_rollups, open_credits)
    by_key = {(w.collection, w.doc_id): w.data for w in writes}

    assert by_key[("daySummaries", "2024-06-30")] == {"lastOrderNum": 1, "orders": {"1": {}}}
    assert by_key[("reportRollups", "2024-06-30")] == {"menuSales": {}, "paymentSales": {}, "total": 0}
    assert by_key[("aggregates", "openCredits")] == {"items": {"2024-06-30_2": {"orderNum": 2}}}


def test_plan_menu_write_sets_items_and_updated_at():
    now = datetime(2026, 5, 27, tzinfo=timezone.utc)
    w = plan_menu_write([{"id": 1001}], now)
    assert w.collection == "menu"
    assert w.doc_id == "current"
    assert w.data == {"items": [{"id": 1001}], "updatedAt": now}


# --- Fake Firestore: 배치 set/commit 동작 검증용 ---
class FakeDocRef:
    def __init__(self, collection, doc_id):
        self.collection = collection
        self.doc_id = doc_id


class FakeCollection:
    def __init__(self, name):
        self.name = name

    def document(self, doc_id):
        return FakeDocRef(self.name, doc_id)


class FakeBatch:
    def __init__(self, commits):
        self._commits = commits
        self._ops = []

    def set(self, ref, data):
        self._ops.append((ref.collection, ref.doc_id, data))

    def commit(self):
        self._commits.append(list(self._ops))


class FakeDb:
    def __init__(self):
        self.commits = []

    def collection(self, name):
        return FakeCollection(name)

    def batch(self):
        return FakeBatch(self.commits)


def test_execute_writes_batches_by_size():
    db = FakeDb()
    writes = [DocWrite("orders", str(i), {"n": i}) for i in range(5)]
    execute_writes(db, writes, batch_size=2)
    # 5개를 2개씩 → 3번 commit
    assert len(db.commits) == 3
    assert db.commits[0] == [("orders", "0", {"n": 0}), ("orders", "1", {"n": 1})]
    assert db.commits[2] == [("orders", "4", {"n": 4})]
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `cd /Users/benn/dev/personal/HolyBean/android/scripts && source .venv/bin/activate && pytest tests/test_writes.py -v`
Expected: FAIL — `ModuleNotFoundError: No module named 'migration.writes'`

- [ ] **Step 3: 구현**

`scripts/migration/writes.py`:
```python
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
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd /Users/benn/dev/personal/HolyBean/android/scripts && source .venv/bin/activate && pytest tests/test_writes.py -v`
Expected: 6 passed

- [ ] **Step 5: 전체 테스트 확인**

Run: `cd /Users/benn/dev/personal/HolyBean/android/scripts && source .venv/bin/activate && pytest -v`
Expected: 18 passed

- [ ] **Step 6: Commit**

```bash
cd /Users/benn/dev/personal/HolyBean
git add android/scripts/migration/writes.py android/scripts/tests/test_writes.py
git commit -m "feat(migration): plan and execute firestore batch writes"
```

---

## Task 7: CLI 오케스트레이션 스크립트

DynamoDB 스캔 → 변환 → (dry-run | 기록 + 검증)을 연결하는 진입점. 순수 로직은 앞 태스크에서 검증했으므로 여기서는 통합 + dry-run 스모크 테스트로 확인한다.

**Files:**
- Create: `scripts/migrate_dynamo_to_firestore.py`

- [ ] **Step 1: 스크립트 작성**

`scripts/migrate_dynamo_to_firestore.py`:
```python
"""DynamoDB(holybean, holybean-menu) → Firestore(holybean-e4201) 일회성 마이그레이션.

사용법:
    python migrate_dynamo_to_firestore.py --service-account sa.json [--dry-run]
"""
import argparse
import sys
from datetime import datetime, timezone

import boto3
import firebase_admin
from firebase_admin import credentials, firestore

from migration.transform import (
    build_aggregates, deserialize_item, pick_latest_menu, to_menu_items, to_order_doc,
)
from migration.writes import (
    execute_writes, plan_aggregate_writes, plan_menu_write, plan_order_writes,
)

ORDERS_TABLE = "holybean"
MENU_TABLE = "holybean-menu"
AWS_REGION = "ap-northeast-2"


def scan_all(dynamodb, table_name):
    """DynamoDB 테이블 전체를 페이지네이션 스캔해 항목 리스트로 반환."""
    paginator = dynamodb.get_paginator("scan")
    items = []
    for page in paginator.paginate(TableName=table_name):
        items.extend(page["Items"])
    return items


def load_orders(dynamodb):
    """orders 테이블을 읽어 Firestore 주문 문서 리스트로 변환."""
    raw = scan_all(dynamodb, ORDERS_TABLE)
    return [to_order_doc(deserialize_item(item)) for item in raw]


def load_menu_items(dynamodb):
    """menu 테이블에서 최신 버전을 골라 menu items 리스트로 변환."""
    raw = scan_all(dynamodb, MENU_TABLE)
    versions = [deserialize_item(item) for item in raw]
    return to_menu_items(pick_latest_menu(versions))


def print_summary(order_docs, day_summaries, report_rollups, open_credits, menu_items):
    dates = sorted({od["orderDate"] for od in order_docs})
    settled = sum(1 for od in order_docs if od["creditStatus"] == 0)
    print(f"주문: {len(order_docs)}건 (정산 {settled} / 미수 {len(order_docs) - settled})")
    print(f"날짜: {len(dates)}일 ({dates[0]} ~ {dates[-1]})" if dates else "날짜: 없음")
    print(f"daySummaries: {len(day_summaries)}일")
    print(f"reportRollups: {len(report_rollups)}일")
    print(f"openCredits: {len(open_credits)}건")
    print(f"menu items: {len(menu_items)}개")


def verify(db, order_docs, open_credits):
    """기록 후 검증: orders 문서 수와 openCredits 항목 수를 대조."""
    orders_count = db.collection("orders").count().get()[0][0].value
    expected_orders = len(order_docs)
    credits_snap = db.collection("aggregates").document("openCredits").get()
    actual_credits = len((credits_snap.to_dict() or {}).get("items", {}))

    ok = True
    print(f"검증 orders: Firestore {orders_count} vs 예상 {expected_orders}", end=" ")
    if orders_count == expected_orders:
        print("OK")
    else:
        print("불일치!"); ok = False
    print(f"검증 openCredits: Firestore {actual_credits} vs 예상 {len(open_credits)}", end=" ")
    if actual_credits == len(open_credits):
        print("OK")
    else:
        print("불일치!"); ok = False
    return ok


def main(argv=None):
    parser = argparse.ArgumentParser(description="DynamoDB → Firestore 마이그레이션")
    parser.add_argument("--service-account", required=True, help="Firebase service account JSON 경로")
    parser.add_argument("--dry-run", action="store_true", help="기록 없이 변환 결과만 출력")
    parser.add_argument("--aws-region", default=AWS_REGION)
    args = parser.parse_args(argv)

    dynamodb = boto3.client("dynamodb", region_name=args.aws_region)

    print("DynamoDB 스캔 중...")
    order_docs = load_orders(dynamodb)
    menu_items = load_menu_items(dynamodb)
    day_summaries, report_rollups, open_credits = build_aggregates(order_docs)

    print_summary(order_docs, day_summaries, report_rollups, open_credits, menu_items)

    if args.dry_run:
        print("\n[dry-run] 기록하지 않고 종료.")
        return 0

    firebase_admin.initialize_app(credentials.Certificate(args.service_account))
    db = firestore.client()

    print("\norders 기록 중...")
    execute_writes(db, plan_order_writes(order_docs))
    print("집계 기록 중...")
    execute_writes(db, plan_aggregate_writes(day_summaries, report_rollups, open_credits))
    print("menu 기록 중...")
    execute_writes(db, [plan_menu_write(menu_items, datetime.now(timezone.utc))])

    print("\n검증 중...")
    ok = verify(db, order_docs, open_credits)
    print("\n완료." if ok else "\n완료(검증 불일치 — 위 로그 확인).")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
```

- [ ] **Step 2: import 및 인자 파싱 스모크 확인**

Run: `cd /Users/benn/dev/personal/HolyBean/android/scripts && source .venv/bin/activate && python migrate_dynamo_to_firestore.py --help`
Expected: usage 메시지 출력(에러 없이), `--service-account`, `--dry-run` 옵션 표시

- [ ] **Step 3: dry-run 실행 (실제 DynamoDB 읽기, Firestore 미접속)**

Run: `cd /Users/benn/dev/personal/HolyBean/android/scripts && source .venv/bin/activate && python migrate_dynamo_to_firestore.py --service-account /dev/null --dry-run`
Expected: `주문: 5241건 ...`, `날짜: 126일 (2024-02-04 ~ 2026-05-24)`, `menu items: N개` 출력 후 `[dry-run] 기록하지 않고 종료.`
(--service-account는 dry-run에서 사용되지 않으므로 /dev/null로도 통과)

- [ ] **Step 4: Commit**

```bash
cd /Users/benn/dev/personal/HolyBean
git add android/scripts/migrate_dynamo_to_firestore.py
git commit -m "feat(migration): add CLI orchestration with dry-run and verification"
```

---

## Task 8: 실제 마이그레이션 실행 (수동, 자격증명 필요)

> 이 태스크는 Firebase service account JSON이 준비된 뒤 사용자가 직접 실행한다. 코드 변경 없음.

- [ ] **Step 1: service account 키 배치**

Firebase 콘솔 > 프로젝트 설정 > 서비스 계정 > 새 비공개 키 생성 → `scripts/service-account.json`으로 저장(.gitignore로 제외됨).

- [ ] **Step 2: 최종 dry-run으로 변환 결과 재확인**

Run: `cd /Users/benn/dev/personal/HolyBean/android/scripts && source .venv/bin/activate && python migrate_dynamo_to_firestore.py --service-account service-account.json --dry-run`
Expected: 주문 5241건, 날짜 126일 등 정상 출력

- [ ] **Step 3: 실제 이관 실행**

Run: `cd /Users/benn/dev/personal/HolyBean/android/scripts && source .venv/bin/activate && python migrate_dynamo_to_firestore.py --service-account service-account.json`
Expected: orders/집계/menu 기록 로그 후 `검증 orders: ... OK`, `검증 openCredits: ... OK`, 마지막 `완료.`

- [ ] **Step 4: 앱에서 확인**

앱을 실행해 보고서(reportRollups), 외상 목록(openCredits), 당일 주문(daySummaries)이 정상 표시되는지 확인.

---

## Self-Review 결과

- **Spec 커버리지:** orders 변환(T3), daySummaries/reportRollups/openCredits 집계 규칙(T4), 메뉴 최신 버전+placement(T5), createdAt KST 합성(T3/T6), NULL customerName→""(T3), 멱등 set·500 배치(T6), dry-run·검증 패스(T7) — 모두 태스크에 매핑됨.
- **Placeholder:** 없음. 모든 step에 실제 코드/명령/기대 출력 포함.
- **타입 일관성:** `to_order_doc`이 만드는 키(name/quantity/subtotal/unitPrice/method/amount/orderDate/orderNum/creditStatus)를 `build_aggregates`·`day_summary_entry`·`plan_*`가 동일하게 사용. `DocWrite(collection, doc_id, data)` 시그니처가 plan/execute/테스트에서 일치.
