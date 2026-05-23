use thiserror::Error;

#[derive(Debug, Error)]
pub enum PrintError {
    #[error("printer write failed: {0}")]
    Write(String),
    #[error("printer device unavailable: {0}")]
    Unavailable(String),
}
