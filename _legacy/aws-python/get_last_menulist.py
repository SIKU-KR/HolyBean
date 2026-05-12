import json
import boto3
from boto3.dynamodb.conditions import Key
from decimal import Decimal

# DynamoDB 리소스 및 테이블 객체 생성
dynamodb = boto3.resource('dynamodb')
table = dynamodb.Table('holybean-menu')

# Decimal 타입을 JSON 직렬화하기 위한 커스텀 인코더
class DecimalEncoder(json.JSONEncoder):
    def default(self, obj):
        if isinstance(obj, Decimal):
            # 정수인 경우 int로, 소수점이 있는 경우 float로 변환
            if obj % 1 == 0:
                return int(obj)
            else:
                return float(obj)
        return super(DecimalEncoder, self).default(obj)

def lambda_handler(event, context):
    try:
        # pk가 "default"인 항목들을 sk(타임스탬프)를 기준으로 내림차순 정렬하여 최신 항목을 가져옴
        response = table.query(
            KeyConditionExpression=Key('pk').eq("default"),
            ScanIndexForward=False,  # 내림차순 정렬
            Limit=1
        )
        items = response.get("Items", [])
        if not items:
            return {
                "statusCode": 404,
                "body": json.dumps({"message": "No menu items found."})
            }
        
        latest_item = items[0]
        result = {
            "timestamp": latest_item.get("sk"),
            "menulist": latest_item.get("menu_items", [])
        }
        return {
            "statusCode": 200,
            "body": json.dumps(result, cls=DecimalEncoder, ensure_ascii=False)
        }
    except Exception as e:
        return {
            "statusCode": 500,
            "body": json.dumps({"message": "Error retrieving latest menu", "error": str(e)})
        }
