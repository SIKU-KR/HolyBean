import os

def lambda_handler(event, context):
    api_key_from_request = event['headers'].get('apikey')
    valid_api_key = os.getenv('VALID_API_KEY')
    is_authorized = (api_key_from_request == valid_api_key)
    return {
        "isAuthorized": is_authorized
    }