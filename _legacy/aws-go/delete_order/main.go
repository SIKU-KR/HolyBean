package main

import (
	"context"
	"encoding/json"
	"strconv"

	"github.com/aws/aws-lambda-go/events"
	"github.com/aws/aws-lambda-go/lambda"
	"github.com/aws/aws-sdk-go-v2/aws"
	"github.com/aws/aws-sdk-go-v2/config"
	"github.com/aws/aws-sdk-go-v2/feature/dynamodb/attributevalue"
	"github.com/aws/aws-sdk-go-v2/service/dynamodb"
	"github.com/aws/aws-sdk-go-v2/service/dynamodb/types"
)

type Response struct {
	StatusCode int               `json:"statusCode"`
	Headers    map[string]string `json:"headers"`
	Body       string            `json:"body"`
}

type ErrorResponse struct {
	Message string `json:"message"`
}

type SuccessResponse struct {
	Message     string      `json:"message"`
	DeletedItem interface{} `json:"deletedItem"`
}

func handleDeleteOrder(ctx context.Context, request events.APIGatewayProxyRequest) (Response, error) {
	// Extract query string parameters for orderDate and orderNum
	queryParams := request.QueryStringParameters
	orderDate := queryParams["orderDate"]
	orderNumStr := queryParams["orderNum"]

	// Return error if required parameters are missing
	if orderDate == "" || orderNumStr == "" {
		errorBody, _ := json.Marshal(ErrorResponse{Message: "orderDate 또는 orderNum이 누락되었습니다."})
		return Response{
			StatusCode: 400,
			Headers: map[string]string{
				"Content-Type": "application/json",
			},
			Body: string(errorBody),
		}, nil
	}

	// Convert orderNum to integer
	orderNum, err := strconv.Atoi(orderNumStr)
	if err != nil {
		errorBody, _ := json.Marshal(ErrorResponse{Message: "Invalid orderNum format"})
		return Response{
			StatusCode: 400,
			Headers: map[string]string{
				"Content-Type": "application/json",
			},
			Body: string(errorBody),
		}, nil
	}

	// Load AWS configuration
	cfg, err := config.LoadDefaultConfig(ctx, config.WithRegion("ap-northeast-2"))
	if err != nil {
		errorBody, _ := json.Marshal(ErrorResponse{Message: "Error loading AWS config"})
		return Response{
			StatusCode: 500,
			Headers: map[string]string{
				"Content-Type": "application/json",
			},
			Body: string(errorBody),
		}, nil
	}

	// Create DynamoDB client
	client := dynamodb.NewFromConfig(cfg)

	// Delete item from DynamoDB with ReturnValues ALL_OLD
	input := &dynamodb.DeleteItemInput{
		TableName: aws.String("holybean"),
		Key: map[string]types.AttributeValue{
			"orderDate": &types.AttributeValueMemberS{Value: orderDate},
			"orderNum":  &types.AttributeValueMemberN{Value: strconv.Itoa(orderNum)},
		},
		ReturnValues: types.ReturnValueAllOld,
	}

	result, err := client.DeleteItem(ctx, input)
	if err != nil {
		errorBody, _ := json.Marshal(ErrorResponse{Message: "주문 삭제 중 오류 발생: " + err.Error()})
		return Response{
			StatusCode: 500,
			Headers: map[string]string{
				"Content-Type": "application/json",
			},
			Body: string(errorBody),
		}, nil
	}

	// Check if item was found and deleted
	if result.Attributes == nil || len(result.Attributes) == 0 {
		errorBody, _ := json.Marshal(ErrorResponse{Message: "해당 주문을 찾을 수 없습니다."})
		return Response{
			StatusCode: 404,
			Headers: map[string]string{
				"Content-Type": "application/json",
			},
			Body: string(errorBody),
		}, nil
	}

	// Convert deleted item to JSON-serializable format
	var deletedItem map[string]interface{}
	err = attributevalue.UnmarshalMap(result.Attributes, &deletedItem)
	if err != nil {
		errorBody, _ := json.Marshal(ErrorResponse{Message: "Error converting deleted item"})
		return Response{
			StatusCode: 500,
			Headers: map[string]string{
				"Content-Type": "application/json",
			},
			Body: string(errorBody),
		}, nil
	}

	// Return success response
	successResponse := SuccessResponse{
		Message:     "주문이 성공적으로 삭제되었습니다.",
		DeletedItem: deletedItem,
	}

	body, err := json.Marshal(successResponse)
	if err != nil {
		errorBody, _ := json.Marshal(ErrorResponse{Message: "Error marshaling response"})
		return Response{
			StatusCode: 500,
			Headers: map[string]string{
				"Content-Type": "application/json",
			},
			Body: string(errorBody),
		}, nil
	}

	return Response{
		StatusCode: 200,
		Headers: map[string]string{
			"Content-Type": "application/json",
		},
		Body: string(body),
	}, nil
}

func main() {
	lambda.Start(handleDeleteOrder)
}
