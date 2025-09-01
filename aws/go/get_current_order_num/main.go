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
	"github.com/aws/aws-sdk-go-v2/service/dynamodb/types"
)

// DynamoDB 클라이언트를 전역 변수로 선언하여 재사용합니다. (콜드 스타트 최적화)
var ddbClient *dynamodb.Client

const TABLE_NAME = "holybean"

// 응답 본문을 위한 구조체 정의
type ResponseBody struct {
	NextOrderNum int `json:"nextOrderNum"`
}

// DynamoDB 항목의 구조를 정의
type OrderItem struct {
	OrderDate string `dynamodbav:"orderDate"`
	OrderNum  int    `dynamodbav:"orderNum"`
}

// init() 함수는 main() 함수보다 먼저, Lambda 실행 환경이 초기화될 때 한 번만 실행됩니다.
// 여기에 AWS SDK 클라이언트와 같은 무거운 객체를 초기화하면 좋습니다.
func init() {
	region := os.Getenv("AWS_REGION")
	if region == "" {
		region = "ap-northeast-2" // 기본 리전 설정
	}

	cfg, err := config.LoadDefaultConfig(context.TODO(), config.WithRegion(region))
	if err != nil {
		log.Fatalf("unable to load SDK config, %v", err)
	}

	ddbClient = dynamodb.NewFromConfig(cfg)
	log.Println("DynamoDB client initialized successfully")
}

// 오늘 날짜를 'YYYY-MM-DD' 형식의 문자열로 반환합니다.
func getTodayDate() string {
	return time.Now().Format("2006-01-02")
}

// Lambda 핸들러 함수
func handler(ctx context.Context, request events.APIGatewayProxyRequest) (events.APIGatewayProxyResponse, error) {
	// 요청 이벤트 로깅
	log.Printf("Incoming event: %+v", request)

	todayDate := getTodayDate()
	log.Println("Today's date:", todayDate)

	// DynamoDB 쿼리 파라미터 설정
	tableName := TABLE_NAME
	keyCondition := "orderDate = :orderDate"
	scanIndexForward := false
	limit := int32(1)

	params := &dynamodb.QueryInput{
		TableName:              &tableName,
		KeyConditionExpression: &keyCondition,
		ExpressionAttributeValues: map[string]types.AttributeValue{
			":orderDate": &types.AttributeValueMemberS{Value: todayDate},
		},
		ScanIndexForward: &scanIndexForward, // Sort Key(orderNum)를 내림차순으로 정렬
		Limit:            &limit,            // 가장 큰 orderNum 하나만 조회
	}

	log.Printf("DynamoDB query parameters: TableName=%s, KeyCondition=%s", TABLE_NAME, todayDate)

	// DynamoDB 쿼리 실행
	result, err := ddbClient.Query(ctx, params)
	if err != nil {
		log.Printf("Error during DynamoDB query: %v", err)
		return events.APIGatewayProxyResponse{
			StatusCode: 500,
			Body:       fmt.Sprintf(`{"message": "Error generating next order number: %s"}`, err.Error()),
		}, nil
	}

	log.Printf("DynamoDB query result count: %d", len(result.Items))

	nextOrderNum := 1
	if len(result.Items) > 0 {
		// Go SDK는 결과를 Go 구조체로 쉽게 변환(unmarshal)할 수 있습니다.
		var item OrderItem
		err := attributevalue.UnmarshalMap(result.Items[0], &item)
		if err != nil {
			log.Printf("Failed to unmarshal DynamoDB item: %v", err)
			return events.APIGatewayProxyResponse{
				StatusCode: 500,
				Body:       fmt.Sprintf(`{"message": "Failed to parse query result: %s"}`, err.Error()),
			}, nil
		}

		currentMaxOrderNum := item.OrderNum
		nextOrderNum = currentMaxOrderNum + 1
		log.Printf("Current max order number: %d", currentMaxOrderNum)
	} else {
		log.Println("No items found for today's date. Returning order number 1")
	}

	log.Printf("Next order number: %d", nextOrderNum)

	// 성공 응답 생성
	responseBody := ResponseBody{NextOrderNum: nextOrderNum}
	jsonBody, err := json.Marshal(responseBody)
	if err != nil {
		log.Printf("Error marshalling response body: %v", err)
		return events.APIGatewayProxyResponse{
			StatusCode: 500,
			Body:       fmt.Sprintf(`{"message": "Failed to create response: %s"}`, err.Error()),
		}, nil
	}

	return events.APIGatewayProxyResponse{
		StatusCode: 200,
		Headers:    map[string]string{"Content-Type": "application/json"},
		Body:       string(jsonBody),
	}, nil
}

// main 함수는 Lambda 런타임에 핸들러를 등록하는 역할을 합니다.
func main() {
	lambda.Start(handler)
}
