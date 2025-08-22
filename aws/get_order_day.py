import boto3
import json

# DynamoDB 클라이언트 초기화
ddb_client = boto3.client('dynamodb', region_name='ap-northeast-2')
TABLE_NAME = "eloom.holybean"

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
        filtered_orders = [
            {
                'customerName': order.get('customerName', {}).get('S', ''),
                'totalAmount': int(order.get('totalAmount', {}).get('N', 0)),
                'orderMethod': order.get('paymentMethods', {}).get('L', [{}])[0].get('M', {}).get('method', {}).get('S', 'Unknown'),
                'orderNum': int(order.get('orderNum', {}).get('N', 0))
            }
            for order in result['Items']
        ]

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