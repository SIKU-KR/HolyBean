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

func handleGetOrderItemSpecific(ctx context.Context, request events.APIGatewayProxyRequest) (Response, error) {
	// Extract query string parameters for orderDate and orderNum
	queryParams := request.QueryStringParameters
	orderDate := queryParams["orderDate"]
	orderNumStr := queryParams["orderNum"]

	// Return error if required parameters are missing
	if orderDate == "" || orderNumStr == "" {
		errorBody, _ := json.Marshal(ErrorResponse{Message: "Missing orderDate or orderNum"})
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

	// Get item from DynamoDB
	input := &dynamodb.GetItemInput{
		TableName: aws.String("holybean"),
		Key: map[string]types.AttributeValue{
			"orderDate": &types.AttributeValueMemberS{Value: orderDate},
			"orderNum":  &types.AttributeValueMemberN{Value: strconv.Itoa(orderNum)},
		},
	}

	result, err := client.GetItem(ctx, input)
	if err != nil {
		errorBody, _ := json.Marshal(ErrorResponse{Message: "Error fetching order: " + err.Error()})
		return Response{
			StatusCode: 500,
			Headers: map[string]string{
				"Content-Type": "application/json",
			},
			Body: string(errorBody),
		}, nil
	}

	// Check if item was found
	if result.Item == nil {
		errorBody, _ := json.Marshal(ErrorResponse{Message: "Order not found"})
		return Response{
			StatusCode: 404,
			Headers: map[string]string{
				"Content-Type": "application/json",
			},
			Body: string(errorBody),
		}, nil
	}

	// Convert DynamoDB item to map
	var item map[string]interface{}
	err = attributevalue.UnmarshalMap(result.Item, &item)
	if err != nil {
		errorBody, _ := json.Marshal(ErrorResponse{Message: "Error unmarshaling item"})
		return Response{
			StatusCode: 500,
			Headers: map[string]string{
				"Content-Type": "application/json",
			},
			Body: string(errorBody),
		}, nil
	}

	// Marshal item to JSON
	body, err := json.Marshal(item)
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
	lambda.Start(handleGetOrderItemSpecific)
}
