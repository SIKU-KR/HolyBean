import json
import boto3
import decimal

# DynamoDB의 Decimal 타입 처리를 위한 커스텀 JSON 인코더
class DecimalEncoder(json.JSONEncoder):
    def default(self, obj):
        if isinstance(obj, decimal.Decimal):
            return int(obj) if obj % 1 == 0 else float(obj)
        return super(DecimalEncoder, self).default(obj)

def lambda_handler(event, context):
    try:
        # DynamoDB 리소스 및 테이블 참조
        dynamodb = boto3.resource('dynamodb')
        table = dynamodb.Table("holybean-menu")

        # 테이블 전체 스캔
        response = table.scan()
        items = response.get('Items', [])

        # 각 항목의 inuse 필드를 boolean으로 변환 (1 -> True, 0 -> False)
        for item in items:
            if 'inuse' in item:
                item['inuse'] = True if int(item['inuse']) == 1 else False

        # 'order' 키를 기준으로 오름차순 정렬 (숫자형)
        items.sort(key=lambda x: x.get('order', 0))

        # 응답 JSON 구성 (ensure_ascii=False로 한글이 제대로 표시됨)
        return {
            "statusCode": 200,
            "headers": {"Content-Type": "application/json"},
            "body": json.dumps({"menulist": items}, cls=DecimalEncoder, ensure_ascii=False)
        }

    except Exception as e:
        # 오류 발생 시 에러 메시지 반환
        return {
            "statusCode": 500,
            "headers": {"Content-Type": "application/json"},
            "body": json.dumps({"error": str(e)}, cls=DecimalEncoder, ensure_ascii=False)
        }
