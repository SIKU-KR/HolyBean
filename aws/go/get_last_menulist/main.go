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
	log.Printf("Starting handleGetLastMenuList function")
	log.Printf("Request: %+v", request)
	
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

	// Query parameters
	log.Printf("Preparing DynamoDB query for table: holybean-menu, pk: default")
	queryInput := &dynamodb.QueryInput{
		TableName:              aws.String("holybean-menu"),
		KeyConditionExpression: aws.String("pk = :pk"),
		ExpressionAttributeValues: map[string]types.AttributeValue{
			":pk": &types.AttributeValueMemberS{Value: "default"},
		},
		ScanIndexForward: aws.Bool(false), // Sort descending
		Limit:            aws.Int32(1),
	}
	log.Printf("Query input: %+v", queryInput)

	// Execute query
	log.Printf("Executing DynamoDB query")
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

	// Check if items exist
	if len(result.Items) == 0 {
		log.Printf("No menu items found in DynamoDB")
		return Response{
			StatusCode: 404,
			Headers: map[string]string{
				"Content-Type": "application/json",
			},
			Body: `{"message": "No menu items found."}`,
		}, nil
	}

	// Log the raw item for debugging
	log.Printf("Raw DynamoDB item: %+v", result.Items[0])

	// Unmarshal the latest item
	log.Printf("Unmarshaling latest item")
	var latestItem MenuItem
	err = attributevalue.UnmarshalMap(result.Items[0], &latestItem)
	if err != nil {
		log.Printf("Error unmarshaling item: %v", err)
		log.Printf("Item structure: %+v", result.Items[0])
		return Response{
			StatusCode: 500,
			Headers: map[string]string{
				"Content-Type": "application/json",
			},
			Body: fmt.Sprintf(`{"message": "Error unmarshaling item", "error": "%s"}`, err.Error()),
		}, nil
	}
	log.Printf("Successfully unmarshaled item: pk=%s, sk=%s, menu_items_count=%d", latestItem.PK, latestItem.SK, len(latestItem.MenuItems))

	// Prepare response
	log.Printf("Preparing response")
	response := MenuListResponse{
		Timestamp: latestItem.SK,
		MenuList:  latestItem.MenuItems,
	}
	log.Printf("Response prepared: timestamp=%s, menulist_count=%d", response.Timestamp, len(response.MenuList))

	body, err := json.Marshal(response)
	if err != nil {
		log.Printf("Error marshaling response: %v", err)
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
	lambda.Start(handleGetLastMenuList)
}
