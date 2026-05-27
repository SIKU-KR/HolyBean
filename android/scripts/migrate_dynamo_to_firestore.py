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

# 이 필드들이 하나라도 없으면 내용 없는 깨진 스텁 주문으로 보고 건너뛴다.
# (customerName 누락은 익명 주문으로 정상 처리 — to_order_doc에서 ""로 채움)
REQUIRED_ORDER_FIELDS = ("totalAmount", "orderItems", "paymentMethods")


def scan_all(dynamodb, table_name):
    """DynamoDB 테이블 전체를 페이지네이션 스캔해 항목 리스트로 반환."""
    paginator = dynamodb.get_paginator("scan")
    items = []
    for page in paginator.paginate(TableName=table_name):
        items.extend(page["Items"])
    return items


def load_orders(dynamodb):
    """orders 테이블을 읽어 (주문 문서 리스트, 건너뛴 스텁 키 리스트)를 반환.

    핵심 필드가 없는 빈 스텁 주문은 건너뛴다.
    """
    raw = scan_all(dynamodb, ORDERS_TABLE)
    order_docs = []
    skipped = []
    for item in raw:
        d = deserialize_item(item)
        if any(f not in d for f in REQUIRED_ORDER_FIELDS):
            skipped.append(f'{d.get("orderDate")}_{int(d["orderNum"])}')
            continue
        order_docs.append(to_order_doc(d))
    return order_docs, skipped


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
    order_docs, skipped = load_orders(dynamodb)
    menu_items = load_menu_items(dynamodb)
    day_summaries, report_rollups, open_credits = build_aggregates(order_docs)

    print_summary(order_docs, day_summaries, report_rollups, open_credits, menu_items)
    if skipped:
        print(f"건너뜀(빈 스텁 주문 {len(skipped)}건): {', '.join(skipped)}")

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
