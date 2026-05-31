package main

import (
	"context"
	"encoding/json"
	"strings"

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

type OrderSummary struct {
	CustomerName string `json:"customerName"`
	TotalAmount  int    `json:"totalAmount"`
	OrderMethod  string `json:"orderMethod"`
	OrderNum     int    `json:"orderNum"`
}

func handleGetOrderDay(ctx context.Context, request events.APIGatewayProxyRequest) (Response, error) {
	// Extract orderDate from path parameters
	pathParams := request.PathParameters
	orderDate := pathParams["orderdate"]

	// Return error if required parameter is missing
	if orderDate == "" {
		errorBody, _ := json.Marshal(ErrorResponse{Message: "Missing orderDate in path parameters"})
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

	// Query for all items with the given orderDate
	input := &dynamodb.QueryInput{
		TableName:              aws.String("holybean"),
		KeyConditionExpression: aws.String("orderDate = :orderDate"),
		ExpressionAttributeValues: map[string]types.AttributeValue{
			":orderDate": &types.AttributeValueMemberS{Value: orderDate},
		},
	}

	result, err := client.Query(ctx, input)
	if err != nil {
		errorBody, _ := json.Marshal(ErrorResponse{Message: "Error fetching orders: " + err.Error()})
		return Response{
			StatusCode: 500,
			Headers: map[string]string{
				"Content-Type": "application/json",
			},
			Body: string(errorBody),
		}, nil
	}

	// Check if any items were found
	if len(result.Items) == 0 {
		errorBody, _ := json.Marshal(ErrorResponse{Message: "No orders found for the given date"})
		return Response{
			StatusCode: 404,
			Headers: map[string]string{
				"Content-Type": "application/json",
			},
			Body: string(errorBody),
		}, nil
	}

	// Process each order and extract needed fields
	var filteredOrders []OrderSummary
	for _, item := range result.Items {
		var order map[string]interface{}
		err = attributevalue.UnmarshalMap(item, &order)
		if err != nil {
			continue // Skip this item if unmarshal fails
		}

		// Extract fields
		customerName := ""
		if cn, ok := order["customerName"].(string); ok {
			customerName = cn
		}

		totalAmount := 0
		if ta, ok := order["totalAmount"].(float64); ok {
			totalAmount = int(ta)
		}

		orderNum := 0
		if on, ok := order["orderNum"].(float64); ok {
			orderNum = int(on)
		}

		// Extract payment methods
		var methods []string
		if paymentMethods, ok := order["paymentMethods"].([]interface{}); ok {
			for _, pm := range paymentMethods {
				if pmMap, ok := pm.(map[string]interface{}); ok {
					if method, ok := pmMap["method"].(string); ok {
						methods = append(methods, method)
					}
				}
			}
		}

		// Determine order method string
		orderMethod := "Unknown"
		if len(methods) > 1 {
			orderMethod = strings.Join(methods, "+")
		} else if len(methods) == 1 {
			orderMethod = methods[0]
		}

		// Add to filtered orders
		filteredOrders = append(filteredOrders, OrderSummary{
			CustomerName: customerName,
			TotalAmount:  totalAmount,
			OrderMethod:  orderMethod,
			OrderNum:     orderNum,
		})
	}

	// Marshal response
	body, err := json.Marshal(filteredOrders)
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
	lambda.Start(handleGetOrderDay)
}
