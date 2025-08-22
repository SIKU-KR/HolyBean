import boto3
import json
from decimal import Decimal

# Initialize DynamoDB resource (DocumentClient)
dynamodb = boto3.resource('dynamodb', region_name='ap-northeast-2')
TABLE_NAME = "eloom.holybean"
table = dynamodb.Table(TABLE_NAME)

# Helper function to convert Decimal types to JSON-serializable data
def decimal_to_json(data):
    if isinstance(data, list):
        return [decimal_to_json(item) for item in data]
    elif isinstance(data, dict):
        return {k: decimal_to_json(v) for k, v in data.items()}
    elif isinstance(data, Decimal):
        # Convert to int if no decimal places, otherwise to float
        return int(data) if data % 1 == 0 else float(data)
    return data

def lambda_handler(event, context):
    # Extract query string parameters for orderDate and orderNum
    query_params = event.get('queryStringParameters') or {}
    order_date = query_params.get('orderDate')
    order_num = query_params.get('orderNum')

    # Return error if required parameters are missing
    if not order_date or not order_num:
        return {
            'statusCode': 400,
            'body': json.dumps({'message': "Missing orderDate or orderNum"}),
        }

    try:
        # Retrieve the item from DynamoDB using DocumentClient
        response = table.get_item(
            Key={
                'orderDate': order_date,  # Partition Key
                'orderNum': int(order_num)  # Sort Key, converted to integer
            }
        )

        # Check if item was found
        if 'Item' not in response:
            return {
                'statusCode': 404,
                'body': json.dumps({'message': "Order not found"}),
            }

        # Convert any Decimal types to JSON-serializable data
        item = decimal_to_json(response['Item'])

        # Return the item in standard JSON format
        return {
            'statusCode': 200,
            'headers': {
                'Content-Type': 'application/json',
            },
            'body': json.dumps(item, ensure_ascii=False),  # Prevents issues with non-ASCII characters
        }
    except Exception as error:
        # Return error if an exception occurred
        return {
            'statusCode': 500,
            'body': json.dumps({'message': f"Error fetching order: {str(error)}"}),
        }