use crate::error::PrintError;
use crate::sink::PrinterSink;
use std::sync::Arc;
use std::time::Duration;
use tokio::sync::{mpsc, oneshot};

/// 워커에 제출되는 단일 인쇄 잡: 렌더링된 바이트 + 결과 회신 채널.
pub struct Job {
    pub bytes: Vec<u8>,
    pub reply: oneshot::Sender<Result<(), PrintError>>,
}

/// 재시도 정책. (Android BackoffRetry print 정책과 동일 의도: 3회, 300ms ×2, 최대 1500ms)
#[derive(Clone, Copy)]
pub struct RetryPolicy {
    pub max_attempts: u32,
    pub initial_delay: Duration,
    pub multiplier: f64,
    pub max_delay: Duration,
}

impl Default for RetryPolicy {
    fn default() -> Self {
        RetryPolicy {
            max_attempts: 3,
            initial_delay: Duration::from_millis(300),
            multiplier: 2.0,
            max_delay: Duration::from_millis(1500),
        }
    }
}

impl RetryPolicy {
    fn delay_for(&self, attempt: u32) -> Duration {
        let raw = self.initial_delay.as_millis() as f64 * self.multiplier.powi((attempt - 1) as i32);
        Duration::from_millis(raw.min(self.max_delay.as_millis() as f64) as u64)
    }
}

/// 잡 제출 핸들. axum 핸들러가 복제해 사용한다.
#[derive(Clone)]
pub struct JobSubmitter {
    tx: mpsc::Sender<Job>,
}

impl JobSubmitter {
    /// 잡을 큐에 넣고 워커의 처리 결과를 기다린다.
    pub async fn submit(&self, bytes: Vec<u8>) -> Result<(), PrintError> {
        let (reply, rx) = oneshot::channel();
        self.tx
            .send(Job { bytes, reply })
            .await
            .map_err(|_| PrintError::Unavailable("print worker stopped".to_string()))?;
        rx.await
            .map_err(|_| PrintError::Unavailable("print worker dropped job".to_string()))?
    }
}

/// 큐와 워커를 생성한다. 워커는 잡을 순차 소비(직렬화)하며 sink에 재시도 쓰기를 한다.
/// 반환된 JobSubmitter로 잡을 제출한다.
pub fn spawn_worker(sink: Arc<dyn PrinterSink>, policy: RetryPolicy) -> JobSubmitter {
    let (tx, mut rx) = mpsc::channel::<Job>(64);
    tokio::spawn(async move {
        while let Some(job) = rx.recv().await {
            let result = write_with_retry(Arc::clone(&sink), job.bytes, policy).await;
            let _ = job.reply.send(result);
        }
    });
    JobSubmitter { tx }
}

async fn write_with_retry(
    sink: Arc<dyn PrinterSink>,
    bytes: Vec<u8>,
    policy: RetryPolicy,
) -> Result<(), PrintError> {
    let mut attempt = 1;
    loop {
        let sink_clone = Arc::clone(&sink);
        let bytes_owned = bytes.clone();
        let result = tokio::task::spawn_blocking(move || sink_clone.write_all(&bytes_owned))
            .await
            .map_err(|_| PrintError::Unavailable("print task panicked".to_string()))?;
        match result {
            Ok(()) => return Ok(()),
            Err(e) => {
                if attempt >= policy.max_attempts {
                    return Err(e);
                }
                tokio::time::sleep(policy.delay_for(attempt)).await;
                attempt += 1;
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::sink::testing::MockSink;

    #[tokio::test]
    async fn succeeds_and_serializes_jobs() {
        let sink = Arc::new(MockSink::new());
        let submitter = spawn_worker(sink.clone(), RetryPolicy::default());
        submitter.submit(b"first".to_vec()).await.unwrap();
        submitter.submit(b"second".to_vec()).await.unwrap();
        let writes = sink.writes.lock().unwrap();
        assert_eq!(writes.len(), 2);
        assert_eq!(writes[0], b"first");
        assert_eq!(writes[1], b"second"); // 순서 보존(직렬화)
    }

    #[tokio::test(start_paused = true)]
    async fn retries_transient_failures_then_succeeds() {
        let sink = Arc::new(MockSink::failing(2)); // 2회 실패 후 성공
        let submitter = spawn_worker(sink.clone(), RetryPolicy::default());
        submitter.submit(b"x".to_vec()).await.unwrap();
        assert_eq!(sink.write_count(), 1);
    }

    #[tokio::test(start_paused = true)]
    async fn gives_up_after_max_attempts() {
        let sink = Arc::new(MockSink::failing(5)); // 항상 실패(>3)
        let submitter = spawn_worker(sink, RetryPolicy::default());
        let result = submitter.submit(b"x".to_vec()).await;
        assert!(matches!(result, Err(PrintError::Write(_))));
    }
}
