package main

import (
	"context"
	"encoding/json"
	"sort"
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
	Error   string `json:"error"`
	Message string `json:"message,omitempty"`
}

type MenuSale struct {
	QuantitySold int     `json:"quantitySold"`
	TotalSales   float64 `json:"totalSales"`
}

type ReportResponse struct {
	MenuSales          map[string]MenuSale `json:"menuSales"`
	PaymentMethodSales map[string]float64  `json:"paymentMethodSales"`
}

type OrderItem struct {
	ItemName string  `json:"itemName"`
	Quantity int     `json:"quantity"`
	Subtotal float64 `json:"subtotal"`
}

type PaymentMethod struct {
	Method string  `json:"method"`
	Amount float64 `json:"amount"`
}

type Order struct {
	OrderDate      string          `json:"orderDate"`
	OrderNum       int             `json:"orderNum"`
	OrderItems     []OrderItem     `json:"orderItems"`
	PaymentMethods []PaymentMethod `json:"paymentMethods"`
	CreditStatus   int             `json:"creditStatus"`
}

func handleGetReport(ctx context.Context, request events.APIGatewayProxyRequest) (Response, error) {
	// Extract query parameters
	queryParams := request.QueryStringParameters
	startDateStr := queryParams["start"]
	endDateStr := queryParams["end"]

	// Validate query parameters
	if startDateStr == "" || endDateStr == "" {
		errorBody, _ := json.Marshal(ErrorResponse{Error: "start 및 end 파라미터가 필요합니다."})
		return Response{
			StatusCode: 400,
			Headers: map[string]string{
				"Content-Type": "application/json",
			},
			Body: string(errorBody),
		}, nil
	}

	// Parse and validate dates
	startDate, err := time.Parse("2006-01-02", startDateStr)
	if err != nil {
		errorBody, _ := json.Marshal(ErrorResponse{Error: "날짜 형식은 YYYY-MM-DD 여야 합니다."})
		return Response{
			StatusCode: 400,
			Headers: map[string]string{
				"Content-Type": "application/json",
			},
			Body: string(errorBody),
		}, nil
	}

	endDate, err := time.Parse("2006-01-02", endDateStr)
	if err != nil {
		errorBody, _ := json.Marshal(ErrorResponse{Error: "날짜 형식은 YYYY-MM-DD 여야 합니다."})
		return Response{
			StatusCode: 400,
			Headers: map[string]string{
				"Content-Type": "application/json",
			},
			Body: string(errorBody),
		}, nil
	}

	if startDate.After(endDate) {
		errorBody, _ := json.Marshal(ErrorResponse{Error: "start 날짜는 end 날짜보다 이전이어야 합니다."})
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
		errorBody, _ := json.Marshal(ErrorResponse{Error: "서버 에러가 발생했습니다.", Message: err.Error()})
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

	// Set up scan parameters with filter
	filterExpression := "orderDate BETWEEN :start AND :end AND creditStatus = :status"
	expressionAttributeValues := map[string]types.AttributeValue{
		":start":  &types.AttributeValueMemberS{Value: startDateStr},
		":end":    &types.AttributeValueMemberS{Value: endDateStr},
		":status": &types.AttributeValueMemberN{Value: "0"},
	}

	// Create paginator
	input := &dynamodb.ScanInput{
		TableName:                 aws.String("holybean"),
		FilterExpression:          aws.String(filterExpression),
		ExpressionAttributeValues: expressionAttributeValues,
	}

	var items []map[string]interface{}

	// Paginate through all results
	paginator := dynamodb.NewScanPaginator(client, input)
	for paginator.HasMorePages() {
		page, err := paginator.NextPage(ctx)
		if err != nil {
			errorBody, _ := json.Marshal(ErrorResponse{Error: "서버 에러가 발생했습니다.", Message: err.Error()})
			return Response{
				StatusCode: 500,
				Headers: map[string]string{
					"Content-Type": "application/json",
				},
				Body: string(errorBody),
			}, nil
		}

		for _, item := range page.Items {
			var deserializedItem map[string]interface{}
			err = attributevalue.UnmarshalMap(item, &deserializedItem)
			if err != nil {
				continue // Skip items that can't be unmarshaled
			}
			items = append(items, deserializedItem)
		}
	}

	// Initialize aggregation maps
	menuSales := make(map[string]MenuSale)
	paymentMethodSales := make(map[string]float64)
	totalPaymentAmount := 0.0

	// Process each item
	for _, item := range items {
		// Process order items
		if orderItemsRaw, ok := item["orderItems"].([]interface{}); ok {
			for _, orderItemRaw := range orderItemsRaw {
				if orderItem, ok := orderItemRaw.(map[string]interface{}); ok {
					itemName := "Unknown"
					if name, ok := orderItem["itemName"].(string); ok {
						itemName = name
					}

					quantity := 0
					if q, ok := orderItem["quantity"].(float64); ok {
						quantity = int(q)
					}

					subtotal := 0.0
					if s, ok := orderItem["subtotal"].(float64); ok {
						subtotal = s
					}

					// Update menu sales
					if menuSale, exists := menuSales[itemName]; exists {
						menuSale.QuantitySold += quantity
						menuSale.TotalSales += subtotal
						menuSales[itemName] = menuSale
					} else {
						menuSales[itemName] = MenuSale{
							QuantitySold: quantity,
							TotalSales:   subtotal,
						}
					}
				}
			}
		}

		// Process payment methods
		if paymentMethodsRaw, ok := item["paymentMethods"].([]interface{}); ok {
			for _, paymentRaw := range paymentMethodsRaw {
				if payment, ok := paymentRaw.(map[string]interface{}); ok {
					method := "Unknown"
					if m, ok := payment["method"].(string); ok {
						method = m
					}

					amount := 0.0
					if a, ok := payment["amount"].(float64); ok {
						amount = a
					}

					// Update payment method sales
					paymentMethodSales[method] += amount
					totalPaymentAmount += amount
				}
			}
		}
	}

	// Sort menu sales by quantity sold (descending)
	sortedMenuSales := make(map[string]MenuSale)

	// Convert to slice for sorting
	type menuSaleItem struct {
		name string
		sale MenuSale
	}
	var menuSaleSlice []menuSaleItem
	for name, sale := range menuSales {
		menuSaleSlice = append(menuSaleSlice, menuSaleItem{name: name, sale: sale})
	}

	// Sort by quantity sold descending
	sort.Slice(menuSaleSlice, func(i, j int) bool {
		return menuSaleSlice[i].sale.QuantitySold > menuSaleSlice[j].sale.QuantitySold
	})

	// Create sorted map
	for _, item := range menuSaleSlice {
		sortedMenuSales[item.name] = item.sale
	}

	// Add total to payment method sales
	paymentMethodSales["총합"] = totalPaymentAmount

	// Prepare response
	result := ReportResponse{
		MenuSales:          sortedMenuSales,
		PaymentMethodSales: paymentMethodSales,
	}

	body, err := json.Marshal(result)
	if err != nil {
		errorBody, _ := json.Marshal(ErrorResponse{Error: "서버 에러가 발생했습니다.", Message: err.Error()})
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
	lambda.Start(handleGetReport)
}
