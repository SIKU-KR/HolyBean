import json
import boto3
from datetime import datetime
from zoneinfo import ZoneInfo
from boto3.dynamodb.types import TypeDeserializer
from decimal import Decimal

# TypeDeserializer 인스턴스 생성
deserializer = TypeDeserializer()

def deserialize_item(item):
    """DynamoDB 아이템을 파이썬 딕셔너리로 변환합니다."""
    return {k: deserializer.deserialize(v) for k, v in item.items()}

def convert_decimals(obj):
    """
    재귀적으로 객체 내의 Decimal을 int 또는 float으로 변환합니다.
    """
    if isinstance(obj, list):
        return [convert_decimals(i) for i in obj]
    elif isinstance(obj, dict):
        return {k: convert_decimals(v) for k, v in obj.items()}
    elif isinstance(obj, Decimal):
        return int(obj)
    else:
        return obj

def lambda_handler(event, context):
    # DynamoDB 클라이언트 초기화
    dynamodb_client = boto3.client('dynamodb')
    table_name = 'holybean'
    
    # KST 타임존 설정
    KST = ZoneInfo('Asia/Seoul')
    
    try:
        # 쿼리 파라미터 추출
        query_params = event.get('queryStringParameters', {})
        start_date_str = query_params.get('start')
        end_date_str = query_params.get('end')
        
        # 쿼리 파라미터 유효성 검사
        if not start_date_str or not end_date_str:
            return {
                'statusCode': 400,
                'headers': {
                    'Content-Type': 'application/json'
                },
                'body': json.dumps({'error': 'start 및 end 파라미터가 필요합니다.'}, ensure_ascii=False)
            }
        
        # 날짜 형식 검사 및 변환
        try:
            start_date = datetime.strptime(start_date_str, '%Y-%m-%d').replace(tzinfo=KST)
            end_date = datetime.strptime(end_date_str, '%Y-%m-%d').replace(tzinfo=KST)
        except ValueError:
            return {
                'statusCode': 400,
                'headers': {
                    'Content-Type': 'application/json'
                },
                'body': json.dumps({'error': '날짜 형식은 YYYY-MM-DD 여야 합니다.'}, ensure_ascii=False)
            }
        
        if start_date > end_date:
            return {
                'statusCode': 400,
                'headers': {
                    'Content-Type': 'application/json'
                },
                'body': json.dumps({'error': 'start 날짜는 end 날짜보다 이전이어야 합니다.'}, ensure_ascii=False)
            }
        
        # FilterExpression 및 ExpressionAttributeValues 설정
        # creditStatus가 0인 항목만 포함하여 삭제된 주문 제외
        filter_expression = "orderDate BETWEEN :start AND :end AND creditStatus = :status"
        expression_attribute_values = {
            ":start": {"S": start_date_str},
            ":end": {"S": end_date_str},
            ":status": {"N": "0"}
        }
        
        # DynamoDB Scan Paginator 초기화
        paginator = dynamodb_client.get_paginator('scan')
        response_iterator = paginator.paginate(
            TableName=table_name,
            FilterExpression=filter_expression,
            ExpressionAttributeValues=expression_attribute_values
        )
        
        items = []
        for page in response_iterator:
            scanned_items = page.get('Items', [])
            for item in scanned_items:
                deserialized = deserialize_item(item)
                items.append(deserialized)
        
        # 집계 초기화
        menu_sales = {}
        payment_method_sales = {}
        total_payment_amount = 0  # 결제 수단 총합
        
        for item in items:
            # 주문 아이템 집계
            order_items = item.get('orderItems', [])
            for order_item in order_items:
                item_name = order_item.get('itemName', 'Unknown')
                quantity = order_item.get('quantity', 0)
                subtotal = order_item.get('subtotal', 0)
                
                if item_name not in menu_sales:
                    menu_sales[item_name] = {
                        'quantitySold': 0,
                        'totalSales': 0
                    }
                
                menu_sales[item_name]['quantitySold'] += quantity
                menu_sales[item_name]['totalSales'] += subtotal
            
            # 결제 수단 집계
            payment_methods = item.get('paymentMethods', [])
            for payment in payment_methods:
                method = payment.get('method', 'Unknown')
                amount = payment.get('amount', 0)
                
                if method not in payment_method_sales:
                    payment_method_sales[method] = 0
                
                payment_method_sales[method] += amount
                total_payment_amount += amount  # 총합 누적
        
        # 메뉴별 판매 정보를 totalSales 기준 내림차순 정렬
        sorted_menu_sales = dict(sorted(menu_sales.items(), key=lambda x: x[1]['quantitySold'], reverse=True))
        
        # paymentMethodSales에 총합 추가
        payment_method_sales['총합'] = total_payment_amount
       
        # 응답 데이터 구성
        result = {
            'menuSales': sorted_menu_sales,
            'paymentMethodSales': payment_method_sales
        }
        
        # Decimal 변환
        result = convert_decimals(result)
        
        return {
            'statusCode': 200,
            'headers': {
                'Content-Type': 'application/json'
            },
            'body': json.dumps(result, ensure_ascii=False)
        }
    
    except Exception as e:
        # 서버 에러 처리
        return {
            'statusCode': 500,
            'headers': {
                'Content-Type': 'application/json'
            },
            'body': json.dumps({'error': '서버 에러가 발생했습니다.', 'message': str(e)}, ensure_ascii=False)
        }
