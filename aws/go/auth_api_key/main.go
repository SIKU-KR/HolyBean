package main

import (
	"context"
	"os"

	"github.com/aws/aws-lambda-go/events"
	"github.com/aws/aws-lambda-go/lambda"
)

type Response struct {
	IsAuthorized bool `json:"isAuthorized"`
}

func handleAuthApiKey(ctx context.Context, request events.APIGatewayProxyRequest) (Response, error) {
	apiKeyFromRequest := request.Headers["apikey"]
	if apiKeyFromRequest == "" {
		apiKeyFromRequest = request.Headers["Apikey"]
	}
	if apiKeyFromRequest == "" {
		apiKeyFromRequest = request.Headers["APIKEY"]
	}

	validApiKey := os.Getenv("VALID_API_KEY")
	isAuthorized := (apiKeyFromRequest == validApiKey)

	return Response{
		IsAuthorized: isAuthorized,
	}, nil
}

func main() {
	lambda.Start(handleAuthApiKey)
}
