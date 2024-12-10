import boto3
from datetime import datetime
import json

ddb_client = boto3.client('dynamodb', region_name='ap-northeast-2')
TABLE_NAME = "holybean"

def get_today_date():
    today = datetime.now()
    return today.strftime('%Y-%m-%d')

def return_order_num(number):
    return {
        'statusCode': 200,
                'headers': {
                    'Content-Type': 'application/json',
                },
                'body': json.dumps({'nextOrderNum': number})  # 첫 번째 주문 번호는 1
    }

def lambda_handler(event, context):
    today_date = get_today_date()

    params = {
        'TableName': TABLE_NAME,
        'KeyConditionExpression': 'orderDate = :orderDate',
        'ExpressionAttributeValues': {
            ':orderDate': {'S': today_date}
        },
        'ScanIndexForward': False,  # Sort Key인 orderNum을 내림차순으로 정렬
        'Limit': 1  # 가장 큰 orderNum 하나만 조회
    }

    try:
        result = ddb_client.query(**params)

        if len(result.get('Items', [])) == 0:
            return return_order_num(1)
        else:
            next_order_num = int(result['Items'][0]['orderNum']['N']) + 1
            return return_order_num(next_order_num)

    except Exception as error:
        return {
            'statusCode': 500,
            'headers': {
                'Content-Type': 'application/json',
            },
            'body': json.dumps({'message': f'Error generating next order number: {str(error)}'})
        }