package main

import (
	"context"
	"encoding/json"
	"time"

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
	Message   string `json:"message"`
	OrderDate string `json:"orderDate"`
}

type OrderItem struct {
	ItemName  string  `json:"itemName" dynamodbav:"itemName"`
	Quantity  int     `json:"quantity" dynamodbav:"quantity"`
	Subtotal  float64 `json:"subtotal" dynamodbav:"subtotal"`
	UnitPrice float64 `json:"unitPrice" dynamodbav:"unitPrice"`
}

type PaymentMethod struct {
	Amount float64 `json:"amount" dynamodbav:"amount"`
	Method string  `json:"method" dynamodbav:"method"`
}

type OrderRequest struct {
	OrderNum       int             `json:"orderNum"`
	TotalAmount    float64         `json:"totalAmount"`
	CustomerName   string          `json:"customerName"`
	PaymentMethods []PaymentMethod `json:"paymentMethods"`
	OrderItems     []OrderItem     `json:"orderItems"`
	CreditStatus   int             `json:"creditStatus"`
}

func getFormattedDate() string {
	return time.Now().Format("2006-01-02")
}

func transformOrderItems(items []map[string]interface{}) []OrderItem {
	var orderItems []OrderItem
	for _, item := range items {
		orderItem := OrderItem{
			ItemName:  item["name"].(string),
			Quantity:  int(item["count"].(float64)),
			Subtotal:  item["total"].(float64),
			UnitPrice: item["price"].(float64),
		}
		orderItems = append(orderItems, orderItem)
	}
	return orderItems
}

func transformPaymentMethods(methods []map[string]interface{}) []PaymentMethod {
	var paymentMethods []PaymentMethod
	for _, method := range methods {
		paymentMethod := PaymentMethod{
			Amount: method["amount"].(float64),
			Method: method["type"].(string),
		}
		paymentMethods = append(paymentMethods, paymentMethod)
	}
	return paymentMethods
}

func handleSaveMenuList(ctx context.Context, request events.APIGatewayProxyRequest) (Response, error) {
	currentDate := getFormattedDate()

	// Parse request body
	var body map[string]interface{}
	err := json.Unmarshal([]byte(request.Body), &body)
	if err != nil {
		errorBody, _ := json.Marshal(ErrorResponse{Message: "Invalid or missing request body"})
		return Response{
			StatusCode: 400,
			Headers: map[string]string{
				"Content-Type": "application/json",
			},
			Body: string(errorBody),
		}, nil
	}

	// Validate required fields
	requiredFields := []string{"orderNum", "totalAmount", "paymentMethods", "orderItems", "creditStatus"}
	for _, field := range requiredFields {
		if body[field] == nil {
			errorBody, _ := json.Marshal(ErrorResponse{Message: "Invalid request: One or more required fields are null"})
			return Response{
				StatusCode: 400,
				Headers: map[string]string{
					"Content-Type": "application/json",
				},
				Body: string(errorBody),
			}, nil
		}
	}

	// Transform fields
	orderItemsRaw := body["orderItems"].([]interface{})
	var orderItemsTransformed []map[string]interface{}
	for _, item := range orderItemsRaw {
		orderItemsTransformed = append(orderItemsTransformed, item.(map[string]interface{}))
	}

	paymentMethodsRaw := body["paymentMethods"].([]interface{})
	var paymentMethodsTransformed []map[string]interface{}
	for _, method := range paymentMethodsRaw {
		paymentMethodsTransformed = append(paymentMethodsTransformed, method.(map[string]interface{}))
	}

	orderItems := transformOrderItems(orderItemsTransformed)
	paymentMethods := transformPaymentMethods(paymentMethodsTransformed)

	customerName := ""
	if body["customerName"] != nil {
		customerName = body["customerName"].(string)
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

	// Create DynamoDB item
	orderNumValue, _ := attributevalue.Marshal(int(body["orderNum"].(float64)))
	totalAmountValue, _ := attributevalue.Marshal(body["totalAmount"].(float64))
	customerNameValue, _ := attributevalue.Marshal(customerName)
	paymentMethodsValue, _ := attributevalue.Marshal(paymentMethods)
	orderItemsValue, _ := attributevalue.Marshal(orderItems)
	creditStatusValue, _ := attributevalue.Marshal(int(body["creditStatus"].(float64)))

	item := map[string]types.AttributeValue{
		"orderDate":      &types.AttributeValueMemberS{Value: currentDate},
		"orderNum":       orderNumValue,
		"totalAmount":    totalAmountValue,
		"customerName":   customerNameValue,
		"paymentMethods": paymentMethodsValue,
		"orderItems":     orderItemsValue,
		"creditStatus":   creditStatusValue,
	}

	// Insert into DynamoDB
	input := &dynamodb.PutItemInput{
		TableName: aws.String("holybean"),
		Item:      item,
	}

	_, err = client.PutItem(ctx, input)
	if err != nil {
		errorBody, _ := json.Marshal(ErrorResponse{Message: "Error inserting item: " + err.Error()})
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
		Message:   "Item inserted successfully",
		OrderDate: currentDate,
	}

	responseBody, err := json.Marshal(successResponse)
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
		Body: string(responseBody),
	}, nil
}

func main() {
	lambda.Start(handleSaveMenuList)
}
