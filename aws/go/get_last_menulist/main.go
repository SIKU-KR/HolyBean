package main

import (
	"context"
	"encoding/json"

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

type MenuListResponse struct {
	Timestamp string        `json:"timestamp"`
	MenuList  []interface{} `json:"menulist"`
}

type MenuItem struct {
	PK        string        `json:"pk" dynamodbav:"pk"`
	SK        string        `json:"sk" dynamodbav:"sk"`
	MenuItems []interface{} `json:"menu_items" dynamodbav:"menu_items"`
}

func handleGetLastMenuList(ctx context.Context, request events.APIGatewayProxyRequest) (Response, error) {
	// Load AWS configuration
	cfg, err := config.LoadDefaultConfig(ctx, config.WithRegion("ap-northeast-2"))
	if err != nil {
		return Response{
			StatusCode: 500,
			Headers: map[string]string{
				"Content-Type": "application/json",
			},
			Body: `{"message": "Error loading AWS config"}`,
		}, nil
	}

	// Create DynamoDB client
	client := dynamodb.NewFromConfig(cfg)

	// Query parameters
	queryInput := &dynamodb.QueryInput{
		TableName:              aws.String("holybean-menu"),
		KeyConditionExpression: aws.String("pk = :pk"),
		ExpressionAttributeValues: map[string]types.AttributeValue{
			":pk": &types.AttributeValueMemberS{Value: "default"},
		},
		ScanIndexForward: aws.Bool(false), // Sort descending
		Limit:            aws.Int32(1),
	}

	// Execute query
	result, err := client.Query(ctx, queryInput)
	if err != nil {
		return Response{
			StatusCode: 500,
			Headers: map[string]string{
				"Content-Type": "application/json",
			},
			Body: `{"message": "Error querying DynamoDB"}`,
		}, nil
	}

	// Check if items exist
	if len(result.Items) == 0 {
		return Response{
			StatusCode: 404,
			Headers: map[string]string{
				"Content-Type": "application/json",
			},
			Body: `{"message": "No menu items found."}`,
		}, nil
	}

	// Unmarshal the latest item
	var latestItem MenuItem
	err = attributevalue.UnmarshalMap(result.Items[0], &latestItem)
	if err != nil {
		return Response{
			StatusCode: 500,
			Headers: map[string]string{
				"Content-Type": "application/json",
			},
			Body: `{"message": "Error unmarshaling item"}`,
		}, nil
	}

	// Prepare response
	response := MenuListResponse{
		Timestamp: latestItem.SK,
		MenuList:  latestItem.MenuItems,
	}

	body, err := json.Marshal(response)
	if err != nil {
		return Response{
			StatusCode: 500,
			Headers: map[string]string{
				"Content-Type": "application/json",
			},
			Body: `{"message": "Error marshaling response"}`,
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
	lambda.Start(handleGetLastMenuList)
}
