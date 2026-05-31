package main

import (
	"context"
	"encoding/json"
	"errors"

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

type ErrorResponse struct {
	Message string `json:"message"`
}

type SuccessResponse struct {
	Message           string                          `json:"message"`
	UpdatedAttributes map[string]types.AttributeValue `json:"updatedAttributes,omitempty"`
}

func handleUpdateCreditStatus(ctx context.Context, request events.APIGatewayProxyRequest) (Response, error) {
	// Extract path parameters for orderNum and orderDate
	pathParams := request.PathParameters
	if pathParams == nil {
		errorBody, _ := json.Marshal(ErrorResponse{Message: "Path parameters are missing."})
		return Response{
			StatusCode: 400,
			Headers: map[string]string{
				"Content-Type": "application/json",
			},
			Body: string(errorBody),
		}, nil
	}

	orderNum := pathParams["number"]
	orderDate := pathParams["orderDate"]

	if orderNum == "" || orderDate == "" {
		errorBody, _ := json.Marshal(ErrorResponse{Message: "Both orderNum and orderDate must be provided in the path."})
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

	// Update item with condition expression
	input := &dynamodb.UpdateItemInput{
		TableName: aws.String("holybean"),
		Key: map[string]types.AttributeValue{
			"orderNum":  &types.AttributeValueMemberN{Value: orderNum},
			"orderDate": &types.AttributeValueMemberS{Value: orderDate},
		},
		UpdateExpression: aws.String("SET creditStatus = :newStatus"),
		ExpressionAttributeValues: map[string]types.AttributeValue{
			":newStatus": &types.AttributeValueMemberN{Value: "0"},
		},
		ConditionExpression: aws.String("attribute_exists(orderNum) AND attribute_exists(orderDate)"),
		ReturnValues:        types.ReturnValueUpdatedNew,
	}

	result, err := client.UpdateItem(ctx, input)
	if err != nil {
		// Check if the error is a conditional check failure
		var conditionCheckErr *types.ConditionalCheckFailedException
		if errors.As(err, &conditionCheckErr) {
			errorBody, _ := json.Marshal(ErrorResponse{Message: "Record not found or not updated."})
			return Response{
				StatusCode: 404,
				Headers: map[string]string{
					"Content-Type": "application/json",
				},
				Body: string(errorBody),
			}, nil
		}

		errorBody, _ := json.Marshal(ErrorResponse{Message: "Error updating order: " + err.Error()})
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
		Message:           "Order credit status updated to 0",
		UpdatedAttributes: result.Attributes,
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
	lambda.Start(handleUpdateCreditStatus)
}
