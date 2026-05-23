use crate::command::PrintRequest;
use crate::error::PrintError;
use crate::escpos::render_document;
use crate::queue::JobSubmitter;
use crate::sink::PrinterSink;
use axum::extract::State;
use axum::http::StatusCode;
use axum::routing::{get, post};
use axum::{Json, Router};
use serde::Serialize;
use std::sync::Arc;

#[derive(Clone)]
pub struct AppState {
    pub submitter: JobSubmitter,
    pub sink: Arc<dyn PrinterSink>,
}

#[derive(Serialize)]
struct HealthBody {
    status: &'static str,
}

#[derive(Serialize)]
struct ErrorBody {
    error: String,
}

pub fn router(state: AppState) -> Router {
    Router::new()
        .route("/health", get(health))
        .route("/print", post(print))
        .with_state(state)
}

async fn health(State(state): State<AppState>) -> (StatusCode, Json<HealthBody>) {
    if state.sink.is_ready() {
        (StatusCode::OK, Json(HealthBody { status: "ok" }))
    } else {
        (
            StatusCode::SERVICE_UNAVAILABLE,
            Json(HealthBody {
                status: "printer_unavailable",
            }),
        )
    }
}

async fn print(
    State(state): State<AppState>,
    Json(req): Json<PrintRequest>,
) -> (StatusCode, Json<serde_json::Value>) {
    let bytes = render_document(&req.commands);
    match state.submitter.submit(bytes).await {
        Ok(()) => (StatusCode::OK, Json(serde_json::json!({ "status": "printed" }))),
        Err(PrintError::Unavailable(msg)) => (
            StatusCode::SERVICE_UNAVAILABLE,
            Json(serde_json::to_value(ErrorBody { error: msg }).unwrap()),
        ),
        Err(PrintError::Write(msg)) => (
            StatusCode::INTERNAL_SERVER_ERROR,
            Json(serde_json::to_value(ErrorBody { error: msg }).unwrap()),
        ),
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::queue::{spawn_worker, RetryPolicy};
    use crate::sink::testing::MockSink;
    use axum::body::Body;
    use axum::http::Request;
    use http_body_util::BodyExt;
    use tower::ServiceExt;

    fn app_with(sink: Arc<dyn PrinterSink>) -> Router {
        let submitter = spawn_worker(sink.clone(), RetryPolicy::default());
        router(AppState { submitter, sink })
    }

    #[tokio::test]
    async fn health_returns_ok_when_ready() {
        let app = app_with(Arc::new(MockSink::new()));
        let res = app
            .oneshot(
                Request::builder()
                    .uri("/health")
                    .body(Body::empty())
                    .unwrap(),
            )
            .await
            .unwrap();
        assert_eq!(res.status(), StatusCode::OK);
    }

    #[tokio::test]
    async fn print_valid_request_returns_200_and_writes() {
        let sink = Arc::new(MockSink::new());
        let app = app_with(sink.clone());
        let body = r#"{ "commands": [ { "type": "text", "content": "hi" }, { "type": "cut" } ] }"#;
        let res = app
            .oneshot(
                Request::builder()
                    .method("POST")
                    .uri("/print")
                    .header("content-type", "application/json")
                    .body(Body::from(body))
                    .unwrap(),
            )
            .await
            .unwrap();
        assert_eq!(res.status(), StatusCode::OK);
        assert_eq!(sink.write_count(), 1);
    }

    #[tokio::test]
    async fn print_malformed_json_returns_4xx() {
        let app = app_with(Arc::new(MockSink::new()));
        let res = app
            .oneshot(
                Request::builder()
                    .method("POST")
                    .uri("/print")
                    .header("content-type", "application/json")
                    .body(Body::from("{ not json"))
                    .unwrap(),
            )
            .await
            .unwrap();
        // axum Json 추출 실패 → 4xx
        assert!(res.status().is_client_error());
    }

    #[tokio::test]
    async fn print_returns_500_when_sink_always_fails() {
        let sink = Arc::new(MockSink::failing(99));
        let app = app_with(sink);
        let body = r#"{ "commands": [ { "type": "blank" } ] }"#;
        let res = app
            .oneshot(
                Request::builder()
                    .method("POST")
                    .uri("/print")
                    .header("content-type", "application/json")
                    .body(Body::from(body))
                    .unwrap(),
            )
            .await
            .unwrap();
        assert_eq!(res.status(), StatusCode::INTERNAL_SERVER_ERROR);
        // 본문에 error 키 존재 확인
        let bytes = res.into_body().collect().await.unwrap().to_bytes();
        let v: serde_json::Value = serde_json::from_slice(&bytes).unwrap();
        assert!(v.get("error").is_some());
    }
}
