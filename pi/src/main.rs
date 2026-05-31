mod command;
mod config;
mod error;
mod escpos;
mod http;
mod layout;
mod queue;
mod sink;

use std::sync::Arc;

use config::Config;
use queue::{spawn_worker, RetryPolicy};
use sink::{FilePrinterSink, PrinterSink};

#[tokio::main]
async fn main() {
    tracing_subscriber::fmt()
        .with_env_filter(
            tracing_subscriber::EnvFilter::try_from_default_env()
                .unwrap_or_else(|_| "info".into()),
        )
        .init();

    let config = Config::from_env();
    let sink: Arc<dyn PrinterSink> = Arc::new(FilePrinterSink::new(&config.device_path));
    let submitter = spawn_worker(sink.clone(), RetryPolicy::default());
    let state = http::AppState { submitter, sink };
    let app = http::router(state);

    let listener = tokio::net::TcpListener::bind(config.bind)
        .await
        .expect("failed to bind");
    tracing::info!(addr = %config.bind, device = %config.device_path, "print server started");
    axum::serve(listener, app).await.expect("server error");
}
