import boto3
import json
from decimal import Decimal

# DynamoDB 리소스 초기화 (DocumentClient)
dynamodb = boto3.resource('dynamodb', region_name='ap-northeast-2')
TABLE_NAME = "holybean"
table = dynamodb.Table(TABLE_NAME)

# Decimal 타입을 JSON-직렬화 가능한 데이터로 변환하는 헬퍼 함수
def decimal_to_json(data):
    if isinstance(data, list):
        return [decimal_to_json(item) for item in data]
    elif isinstance(data, dict):
        return {k: decimal_to_json(v) for k, v in data.items()}
    elif isinstance(data, Decimal):
        # 소수점이 없으면 정수로, 있으면 실수로 변환
        return int(data) if data % 1 == 0 else float(data)
    return data

def lambda_handler(event, context):
    # 쿼리 문자열 매개변수에서 orderDate와 orderNum 추출
    query_params = event.get('queryStringParameters') or {}
    order_date = query_params.get('orderDate')
    order_num = query_params.get('orderNum')

    # 필수 매개변수가 누락된 경우 오류 반환
    if not order_date or not order_num:
        return {
            'statusCode': 400,
            'body': json.dumps({'message': "orderDate 또는 orderNum이 누락되었습니다."}),
        }

    try:
        # DynamoDB에서 아이템 삭제 (ReturnValues='ALL_OLD'로 삭제된 아이템 반환)
        response = table.delete_item(
            Key={
                'orderDate': order_date,  # 파티션 키
                'orderNum': int(order_num)  # 정렬 키, 정수로 변환
            },
            ReturnValues='ALL_OLD'
        )

        # 삭제된 아이템이 없는 경우 (존재하지 않던 아이템을 삭제 시도)
        if 'Attributes' not in response:
            return {
                'statusCode': 404,
                'body': json.dumps({'message': "해당 주문을 찾을 수 없습니다."}),
            }

        # 삭제된 아이템을 JSON-직렬화 가능한 형태로 변환
        deleted_item = decimal_to_json(response['Attributes'])

        # 성공적으로 삭제되었음을 반환
        return {
            'statusCode': 200,
            'headers': {
                'Content-Type': 'application/json',
            },
            'body': json.dumps({
                'message': "주문이 성공적으로 삭제되었습니다.",
                'deletedItem': deleted_item
            }, ensure_ascii=False),
        }
    except Exception as error:
        # 예외 발생 시 오류 메시지 반환
        return {
            'statusCode': 500,
            'body': json.dumps({'message': f"주문 삭제 중 오류 발생: {str(error)}"}),
        }
