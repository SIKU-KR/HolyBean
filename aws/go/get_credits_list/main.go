package main

import (
	"context"
	"encoding/json"
	"fmt"
	"log"

	"github.com/aws/aws-lambda-go/events"
	"github.com/aws/aws-lambda-go/lambda"
	"github.com/aws/aws-sdk-go-v2/aws"
	"github.com/aws/aws-sdk-go-v2/config"
	"github.com/aws/aws-sdk-go-v2/service/dynamodb"
	"github.com/aws/aws-sdk-go-v2/service/dynamodb/types"
)

type Response struct {
	StatusCode int               `json:"statusCode"`
	Headers    map[string]string `json:"headers"`
	Body       string            `json:"body"`
}

type CreditItem struct {
	TotalAmount  int    `json:"totalAmount"`
	OrderNum     int    `json:"orderNum"`
	OrderDate    string `json:"orderDate"`
	CustomerName string `json:"customerName"`
}

// Helper functions to extract values from DynamoDB item attributes
func getStringValue(item map[string]types.AttributeValue, key string) string {
	if attr, exists := item[key]; exists {
		if s, ok := attr.(*types.AttributeValueMemberS); ok {
			return s.Value
		}
	}
	return ""
}

func getIntValue(item map[string]types.AttributeValue, key string) int {
	if attr, exists := item[key]; exists {
		if n, ok := attr.(*types.AttributeValueMemberN); ok {
			// Convert string number to int
			if val, err := json.Number(n.Value).Int64(); err == nil {
				return int(val)
			}
		}
	}
	return 0
}

func handleGetCreditsList(ctx context.Context, request events.APIGatewayProxyRequest) (Response, error) {
	log.Printf("Starting handleGetCreditsList function")
	
	// Load AWS configuration
	log.Printf("Loading AWS configuration for region: ap-northeast-2")
	cfg, err := config.LoadDefaultConfig(ctx, config.WithRegion("ap-northeast-2"))
	if err != nil {
		log.Printf("Error loading AWS config: %v", err)
		return Response{
			StatusCode: 500,
			Headers: map[string]string{
				"Content-Type": "application/json",
			},
			Body: fmt.Sprintf(`{"message": "Error loading AWS config", "error": "%s"}`, err.Error()),
		}, nil
	}
	log.Printf("AWS configuration loaded successfully")

	// Create DynamoDB client
	log.Printf("Creating DynamoDB client")
	client := dynamodb.NewFromConfig(cfg)

	// Query GSI for credit orders (creditStatus = 1)
	log.Printf("Querying DynamoDB GSI for credit orders")
	queryInput := &dynamodb.QueryInput{
		TableName:              aws.String("holybean"),
		IndexName:              aws.String("creditStatus-index"),
		KeyConditionExpression: aws.String("creditStatus = :creditStatus"),
		ExpressionAttributeValues: map[string]types.AttributeValue{
			":creditStatus": &types.AttributeValueMemberN{Value: "1"},
		},
		ScanIndexForward: aws.Bool(true), // ascending order by orderDate
	}

	// Execute query
	log.Printf("Executing DynamoDB query on GSI")
	result, err := client.Query(ctx, queryInput)
	if err != nil {
		log.Printf("Error querying DynamoDB: %v", err)
		return Response{
			StatusCode: 500,
			Headers: map[string]string{
				"Content-Type": "application/json",
			},
			Body: fmt.Sprintf(`{"message": "Error querying DynamoDB", "error": "%s"}`, err.Error()),
		}, nil
	}
	log.Printf("DynamoDB query successful, found %d items", len(result.Items))

	// Parse credit items (similar to Python code)
	var creditItems []CreditItem
	for _, item := range result.Items {
		creditItem := CreditItem{
			CustomerName: getStringValue(item, "customerName"),
			TotalAmount:  getIntValue(item, "totalAmount"),
			OrderNum:     getIntValue(item, "orderNum"),
			OrderDate:    getStringValue(item, "orderDate"),
		}
		creditItems = append(creditItems, creditItem)
	}
	log.Printf("Successfully parsed %d credit items", len(creditItems))

	// Return as direct array (not wrapped in an object)
	body, err := json.Marshal(creditItems)
	if err != nil {
		log.Printf("Error marshaling credit items: %v", err)
		return Response{
			StatusCode: 500,
			Headers: map[string]string{
				"Content-Type": "application/json",
			},
			Body: fmt.Sprintf(`{"message": "Error marshaling response", "error": "%s"}`, err.Error()),
		}, nil
	}
	log.Printf("Response body marshaled successfully, length: %d", len(body))

	log.Printf("Function completed successfully")
	return Response{
		StatusCode: 200,
		Headers: map[string]string{
			"Content-Type": "application/json",
		},
		Body: string(body),
	}, nil
}

func main() {
	lambda.Start(handleGetCreditsList)
}
