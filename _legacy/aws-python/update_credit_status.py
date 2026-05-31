import boto3
import json
import logging
from botocore.exceptions import ClientError

# Logger 설정
logger = logging.getLogger()
logger.setLevel(logging.INFO)

# DynamoDB 클라이언트 초기화 (리전: ap-northeast-2)
ddb_client = boto3.client('dynamodb', region_name='ap-northeast-2')
TABLE_NAME = "holybean"

def lambda_handler(event, context):
    logger.info("Received event: %s", json.dumps(event))
    try:
        # path parameters에서 orderNum과 orderDate 추출
        path_params = event.get("pathParameters")
        if not path_params:
            logger.error("Path parameters are missing.")
            return {
                "statusCode": 400,
                "headers": {"Content-Type": "application/json"},
                "body": json.dumps({"message": "Path parameters are missing."}, ensure_ascii=False)
            }
        
        orderNum = path_params.get("number")
        orderDate = path_params.get("orderDate")
        
        if orderNum is None or orderDate is None:
            logger.error("Missing orderNum or orderDate in path parameters.")
            return {
                "statusCode": 400,
                "headers": {"Content-Type": "application/json"},
                "body": json.dumps({"message": "Both orderNum and orderDate must be provided in the path."}, ensure_ascii=False)
            }
        
        try:
            # DynamoDB 업데이트: record가 존재해야 업데이트가 수행되도록 조건 추가
            response = ddb_client.update_item(
                TableName=TABLE_NAME,
                Key={
                    "orderNum": {"N": str(orderNum)},
                    "orderDate": {"S": orderDate}
                },
                UpdateExpression="SET creditStatus = :newStatus",
                ExpressionAttributeValues={
                    ":newStatus": {"N": "0"}
                },
                # 조건: 해당 레코드가 존재하는지 확인
                ConditionExpression="attribute_exists(orderNum) AND attribute_exists(orderDate)",
                ReturnValues="UPDATED_NEW"
            )
        except ClientError as e:
            if e.response['Error']['Code'] == 'ConditionalCheckFailedException':
                logger.error("Record not found or condition check failed: %s", e)
                return {
                    "statusCode": 404,
                    "headers": {"Content-Type": "application/json"},
                    "body": json.dumps({"message": "Record not found or not updated."}, ensure_ascii=False)
                }
            else:
                logger.exception("DynamoDB update_item failed.")
                raise
        
        return {
            "statusCode": 200,
            "headers": {"Content-Type": "application/json"},
            "body": json.dumps({
                "message": "Order credit status updated to 0",
                "updatedAttributes": response.get("Attributes")
            }, ensure_ascii=False)
        }
    except Exception as error:
        logger.exception("Error updating order:")
        return {
            "statusCode": 500,
            "headers": {"Content-Type": "application/json"},
            "body": json.dumps({"message": f"Error updating order: {str(error)}"}, ensure_ascii=False)
        }
