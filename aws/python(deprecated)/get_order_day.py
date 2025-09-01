import boto3
import json

# DynamoDB 클라이언트 초기화
ddb_client = boto3.client('dynamodb', region_name='ap-northeast-2')
TABLE_NAME = "holybean"

def lambda_handler(event, context):
    # 경로 파라미터에서 orderDate 가져오기
    path_parameters = event.get('pathParameters') or {}
    order_date = path_parameters.get('orderdate')

    # 필수 파라미터가 제공되지 않은 경우 400 에러 반환
    if not order_date:
        return {
            'statusCode': 400,
            'headers': {
                'Content-Type': 'application/json'
            },
            'body': json.dumps({'message': "Missing orderDate in path parameters"}),
        }

    # DynamoDB에서 해당 날짜(orderDate)에 해당하는 모든 아이템을 조회하는 쿼리
    params = {
        'TableName': TABLE_NAME,
        'KeyConditionExpression': 'orderDate = :orderDate',  # orderDate에 해당하는 모든 항목 조회
        'ExpressionAttributeValues': {
            ':orderDate': {'S': order_date},
        },
    }

    try:
        # DynamoDB에서 해당 orderDate에 해당하는 모든 데이터를 조회 (Query 사용)
        result = ddb_client.query(**params)

        # 아이템이 없는 경우 404 반환
        if 'Items' not in result or len(result['Items']) == 0:
            return {
                'statusCode': 404,
                'headers': {
                    'Content-Type': 'application/json'
                },
                'body': json.dumps({'message': "No orders found for the given date"}),
            }

        # 필요한 필드만 추출 (각 주문마다 customerName, totalAmount, orderMethod, orderNum)
        filtered_orders = []
        for order in result['Items']:
            customer_name = order.get('customerName', {}).get('S', '')
            total_amount = int(order.get('totalAmount', {}).get('N', 0))
            order_num = int(order.get('orderNum', {}).get('N', 0))
            
            # 결제 방법들 추출
            payment_methods = order.get('paymentMethods', {}).get('L', [])
            methods = [pm.get('M', {}).get('method', {}).get('S', 'Unknown') for pm in payment_methods]
            
            # 결제 방법이 2개 이상이면 '+'로 이어붙임, 그렇지 않으면 첫 번째 방법 사용
            if len(methods) > 1:
                order_method = '+'.join(methods)
            elif len(methods) == 1:
                order_method = methods[0]
            else:
                order_method = 'Unknown'

            # 필터링된 주문 정보 추가
            filtered_orders.append({
                'customerName': customer_name,
                'totalAmount': total_amount,
                'orderMethod': order_method,
                'orderNum': order_num
            })

        # 조회된 데이터 반환
        return {
            'statusCode': 200,
            'headers': {
                'Content-Type': 'application/json'
            },
            'body': json.dumps(filtered_orders),
        }
    except Exception as error:
        # 오류 발생 시 500 에러 반환
        return {
            'statusCode': 500,
            'headers': {
                'Content-Type': 'application/json'
            },
            'body': json.dumps({'message': f"Error fetching orders: {str(error)}"}),
        }
