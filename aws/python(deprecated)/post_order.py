import boto3
import json
from datetime import datetime

# DynamoDB 클라이언트 초기화
ddb_client = boto3.client('dynamodb', region_name='ap-northeast-2')
TABLE_NAME = "holybean"

# yyyy-mm-dd 형식으로 날짜를 반환하는 함수
def get_formatted_date():
    date = datetime.now()
    return date.strftime('%Y-%m-%d')

# DynamoDB 형식 변환 함수
def convert_to_dynamodb_format(data):
    """
    Python 데이터를 DynamoDB JSON 형식으로 변환
    """
    if isinstance(data, dict):
        return {'M': {k: convert_to_dynamodb_format(v) for k, v in data.items()}}
    elif isinstance(data, list):
        return {'L': [convert_to_dynamodb_format(v) for v in data]}
    elif isinstance(data, str):
        return {'S': data}
    elif isinstance(data, int) or isinstance(data, float):
        return {'N': str(data)}  # DynamoDB는 숫자를 문자열로 저장
    elif isinstance(data, bool):
        return {'BOOL': data}
    elif data is None:
        return {'NULL': True}
    else:
        raise TypeError(f"Unsupported type: {type(data)}")

def transform_fields(body):
    """
    첫 번째 객체의 필드 이름을 두 번째 객체 기준으로 변환
    """
    body['orderItems'] = [
        {
            "itemName": item["name"],
            "quantity": item["count"],
            "subtotal": item["total"],
            "unitPrice": item["price"]
        }
        for item in body["orderItems"]
    ]
    body['paymentMethods'] = [
        {
            "amount": method["amount"],
            "method": method["type"]
        }
        for method in body["paymentMethods"]
    ]
    return body

# Lambda 핸들러
def lambda_handler(event, context):
    current_date = get_formatted_date()

    # 요청 이벤트 로깅
    print("Incoming event:", json.dumps(event))

    # 요청 본문 파싱
    try:
        body = json.loads(event.get('body', ''))
        print("Parsed body:", json.dumps(body))
    except (TypeError, json.JSONDecodeError) as e:
        print("Error parsing request body:", str(e))
        return {
            'statusCode': 400,
            'headers': {
                'Content-Type': 'application/json',
            },
            'body': json.dumps({'message': "Invalid or missing request body"}),
        }

    # 필수 필드 체크
    required_fields = ['orderNum', 'totalAmount', 'paymentMethods', 'orderItems', 'creditStatus']
    if any(body.get(field) is None for field in required_fields):
        print("Invalid request: One or more required fields are null")
        return {
            'statusCode': 400,
            'headers': {
                'Content-Type': 'application/json',
            },
            'body': json.dumps({'message': "Invalid request: One or more required fields are null"}),
        }

    # 필드 변환
    try:
        body = transform_fields(body)
        print("Transformed body:", json.dumps(body))
    except Exception as e:
        print("Error during field transformation:", str(e))
        return {
            'statusCode': 400,
            'headers': {
                'Content-Type': 'application/json',
            },
            'body': json.dumps({'message': "Error in field transformation"}),
        }

    # DynamoDB 아이템 변환
    try:
        item = {
            'orderDate': {'S': current_date},  # yyyy-mm-dd 형식 (PK)
            'orderNum': {'N': str(body['orderNum'])},  # 주문 번호
            'totalAmount': {'N': str(body['totalAmount'])},  # 총 금액
            'customerName': {'S': body.get('customerName', '')},  # 고객 이름 (기본값 '')
            'paymentMethods': {
                'L': [
                    {'M': {k: convert_to_dynamodb_format(v) for k, v in method.items()}}
                    for method in body['paymentMethods']
                ]
            },  # 결제 방식 리스트
            'orderItems': {
                'L': [
                    {'M': {k: convert_to_dynamodb_format(v) for k, v in item.items()}}
                    for item in body['orderItems']
                ]
            },  # 주문 항목 리스트
            'creditStatus': {'N': str(body['creditStatus'])},  # 클라이언트에서 받은 값을 그대로 사용
        }
        print("Converted DynamoDB item:", json.dumps(item))
    except Exception as e:
        print("Error during item conversion:", str(e))
        return {
            'statusCode': 400,
            'headers': {
                'Content-Type': 'application/json',
            },
            'body': json.dumps({'message': "Error in item conversion"}),
        }

    # DynamoDB에 데이터 삽입
    try:
        ddb_client.put_item(TableName=TABLE_NAME, Item=item)
        print("Item inserted successfully:", json.dumps(item))
        return {
            'statusCode': 200,
            'headers': {
                'Content-Type': 'application/json',
            },
            'body': json.dumps({'message': "Item inserted successfully", 'orderDate': current_date}),
        }
    except Exception as error:
        print("Error inserting item:", str(error))
        return {
            'statusCode': 500,
            'headers': {
                'Content-Type': 'application/json',
            },
            'body': json.dumps({'message': f"Error inserting item: {str(error)}"}),
        }
        