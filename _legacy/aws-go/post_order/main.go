package main

import (
	"context"
	"encoding/json"
	"fmt"
	"log"
	"os"
	"time"

	"github.com/aws/aws-lambda-go/events"
	"github.com/aws/aws-lambda-go/lambda"
	"github.com/aws/aws-sdk-go-v2/config"
	"github.com/aws/aws-sdk-go-v2/feature/dynamodb/attributevalue"
	"github.com/aws/aws-sdk-go-v2/service/dynamodb"
)

// 전역 변수 및 상수
var ddbClient *dynamodb.Client

const TABLE_NAME = "holybean"

// === 구조체 정의 ===
// Go는 정적 타입 언어이므로, JSON과 DynamoDB 데이터를 다룰 구조체를 미리 정의합니다.

// 1. API Gateway 요청 본문(body)을 파싱하기 위한 구조체
type RequestBody struct {
	// 포인터(*) 타입으로 선언하여 'null' 값과 '존재하지 않는 필드'를 구분합니다.
	OrderNum       *int                   `json:"orderNum"`
	TotalAmount    *int                   `json:"totalAmount"`
	PaymentMethods []RequestPaymentMethod `json:"paymentMethods"`
	OrderItems     []RequestOrderItem     `json:"orderItems"`
	CreditStatus   *int                   `json:"creditStatus"`
	CustomerName   string                 `json:"customerName"` // Optional 필드
}

type RequestPaymentMethod struct {
	Type   string `json:"type"`
	Amount int    `json:"amount"`
}

type RequestOrderItem struct {
	Name  string `json:"name"`
	Count int    `json:"count"`
	Total int    `json:"total"`
	Price int    `json:"price"`
}

// 2. DynamoDB에 저장될 최종 형태의 데이터를 위한 구조체
// `dynamodbav` 태그는 DynamoDB의 속성 이름과 Go 구조체 필드를 매핑합니다.
type DynamoDBItem struct {
	OrderDate      string                `dynamodbav:"orderDate"`
	OrderNum       int                   `dynamodbav:"orderNum"`
	TotalAmount    int                   `dynamodbav:"totalAmount"`
	CustomerName   string                `dynamodbav:"customerName,omitempty"` // 비어있으면 저장하지 않음
	PaymentMethods []DynamoPaymentMethod `dynamodbav:"paymentMethods"`
	OrderItems     []DynamoOrderItem     `dynamodbav:"orderItems"`
	CreditStatus   int                   `dynamodbav:"creditStatus"`
}

type DynamoPaymentMethod struct {
	Method string `dynamodbav:"method"`
	Amount int    `dynamodbav:"amount"`
}

type DynamoOrderItem struct {
	ItemName  string `dynamodbav:"itemName"`
	Quantity  int    `dynamodbav:"quantity"`
	Subtotal  int    `dynamodbav:"subtotal"`
	UnitPrice int    `dynamodbav:"unitPrice"`
}

// === 초기화 함수 ===
func init() {
	region := os.Getenv("AWS_REGION")
	if region == "" {
		region = "ap-northeast-2"
	}
	cfg, err := config.LoadDefaultConfig(context.TODO(), config.WithRegion(region))
	if err != nil {
		log.Fatalf("SDK 설정 로드 실패, %v", err)
	}
	ddbClient = dynamodb.NewFromConfig(cfg)
	log.Println("DynamoDB 클라이언트 초기화 완료")
}

// === 헬퍼 함수 ===

// API 응답을 생성하는 헬퍼 함수
func createAPIResponse(statusCode int, body string) (events.APIGatewayProxyResponse, error) {
	return events.APIGatewayProxyResponse{
		StatusCode: statusCode,
		Headers:    map[string]string{"Content-Type": "application/json"},
		Body:       body,
	}, nil
}

// === Lambda 핸들러 ===
func handler(ctx context.Context, request events.APIGatewayProxyRequest) (events.APIGatewayProxyResponse, error) {
	log.Printf("수신된 이벤트: %s", request.Body)

	// 1. 요청 본문 파싱
	var body RequestBody
	err := json.Unmarshal([]byte(request.Body), &body)
	if err != nil {
		log.Printf("요청 본문 파싱 오류: %v", err)
		return createAPIResponse(400, `{"message": "잘못되거나 누락된 요청 본문입니다"}`)
	}

	// 2. 필수 필드 체크 (포인터가 nil인지 확인)
	if body.OrderNum == nil || body.TotalAmount == nil || body.PaymentMethods == nil || body.OrderItems == nil || body.CreditStatus == nil {
		log.Println("잘못된 요청: 하나 이상의 필수 필드가 null입니다")
		return createAPIResponse(400, `{"message": "잘못된 요청: 하나 이상의 필수 필드가 null입니다"}`)
	}

	// 3. 필드 변환 및 DynamoDB 아이템 생성
	// Python의 transform_fields와 DynamoDB 아이템 변환 과정을 하나의 로직으로 통합
	dynamoItem := DynamoDBItem{
		OrderDate:      time.Now().Format("2006-01-02"),
		OrderNum:       *body.OrderNum, // 포인터 역참조하여 값 사용
		TotalAmount:    *body.TotalAmount,
		CustomerName:   body.CustomerName,
		CreditStatus:   *body.CreditStatus,
		OrderItems:     make([]DynamoOrderItem, len(body.OrderItems)),
		PaymentMethods: make([]DynamoPaymentMethod, len(body.PaymentMethods)),
	}

	for i, item := range body.OrderItems {
		dynamoItem.OrderItems[i] = DynamoOrderItem{
			ItemName:  item.Name,
			Quantity:  item.Count,
			Subtotal:  item.Total,
			UnitPrice: item.Price,
		}
	}

	for i, method := range body.PaymentMethods {
		dynamoItem.PaymentMethods[i] = DynamoPaymentMethod{
			Method: method.Type,
			Amount: method.Amount,
		}
	}

	// 4. DynamoDB 형식으로 마샬링 (자동 변환)
	// Go SDK의 attributevalue.MarshalMap이 Python의 convert_to_dynamodb_format 함수 역할을 자동으로 수행
	item, err := attributevalue.MarshalMap(dynamoItem)
	if err != nil {
		log.Printf("DynamoDB 아이템 변환 오류: %v", err)
		return createAPIResponse(400, `{"message": "아이템 변환 중 오류 발생"}`)
	}
	log.Printf("DynamoDB에 저장될 아이템: %v", item)

	// 5. DynamoDB에 데이터 삽입
	tableName := TABLE_NAME
	_, err = ddbClient.PutItem(ctx, &dynamodb.PutItemInput{
		TableName: &tableName,
		Item:      item,
	})
	if err != nil {
		log.Printf("아이템 삽입 오류: %v", err)
		return createAPIResponse(500, fmt.Sprintf(`{"message": "아이템 삽입 오류: %s"}`, err.Error()))
	}

	log.Println("아이템이 성공적으로 삽입되었습니다.")

	successMsg := fmt.Sprintf(`{"message": "아이템이 성공적으로 삽입되었습니다", "orderDate": "%s"}`, dynamoItem.OrderDate)
	return createAPIResponse(200, successMsg)
}

// === main 함수 ===
func main() {
	lambda.Start(handler)
}
