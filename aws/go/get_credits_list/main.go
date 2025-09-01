package main

import (
	"context"
	"encoding/json"

	"github.com/aws/aws-lambda-go/events"
	"github.com/aws/aws-lambda-go/lambda"
)

type Response struct {
	StatusCode int               `json:"statusCode"`
	Headers    map[string]string `json:"headers"`
	Body       string            `json:"body"`
}

type CreditsListResponse struct {
	Credits []interface{} `json:"credits"`
}

func handleGetCreditsList(ctx context.Context, request events.APIGatewayProxyRequest) (Response, error) {
	// Since the original Python function was empty, returning empty credits list
	creditsResponse := CreditsListResponse{
		Credits: []interface{}{},
	}

	body, err := json.Marshal(creditsResponse)
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
	lambda.Start(handleGetCreditsList)
}
