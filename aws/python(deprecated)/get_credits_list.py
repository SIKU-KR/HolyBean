import boto3
import json

# DynamoDB 클라이언트 초기화 (리전: ap-northeast-2)
ddb_client = boto3.client('dynamodb', region_name='ap-northeast-2')
TABLE_NAME = "holybean"
# 글로벌 보조 인덱스 이름 (필요에 따라 수정)
INDEX_NAME = "creditStatus-index"

def lambda_handler(event, context):
    # creditStatus가 1인 데이터 조회 (GSI를 사용)
    params = {
        'TableName': TABLE_NAME,
        'IndexName': INDEX_NAME,
        'KeyConditionExpression': 'creditStatus = :creditStatus',
        'ExpressionAttributeValues': {
            ':creditStatus': {'N': '1'},
        },
        # 정렬키(orderDate)를 기준으로 오름차순 정렬
        'ScanIndexForward': True
    }

    try:
        result = ddb_client.query(**params)

        if 'Items' not in result or len(result['Items']) == 0:
            return {
                'statusCode': 404,
                'headers': {'Content-Type': 'application/json'},
                'body': json.dumps({'message': "creditStatus가 1인 주문이 없습니다."}, ensure_ascii=False)
            }

        # 각 주문에서 필요한 필드만 추출
        filtered_orders = []
        for order in result['Items']:
            filtered_order = {
                'customerName': order.get('customerName', {}).get('S', ''),
                'totalAmount': int(order.get('totalAmount', {}).get('N', 0)),
                'orderNum': order.get('orderNum', {}).get('N', 0),
                'orderDate': order.get('orderDate', {}).get('S', '')
            }
            filtered_orders.append(filtered_order)

        # orderDate 기준 오름차순 정렬 (인덱스 정렬이 아닐 경우)
        filtered_orders.sort(key=lambda x: x.get('orderDate', ''))

        return {
            'statusCode': 200,
            'headers': {'Content-Type': 'application/json'},
            'body': json.dumps(filtered_orders, ensure_ascii=False)
        }
    except Exception as error:
        return {
            'statusCode': 500,
            'headers': {'Content-Type': 'application/json'},
            'body': json.dumps({'message': f"주문 조회 중 오류 발생: {str(error)}"}, ensure_ascii=False)
        }
