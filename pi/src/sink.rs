use crate::error::PrintError;
use std::fs::OpenOptions;
use std::io::Write;
use std::path::PathBuf;

/// 프린터로 바이트를 내보내는 추상화. 운영은 파일, 테스트는 mock.
pub trait PrinterSink: Send + Sync {
    fn write_all(&self, bytes: &[u8]) -> Result<(), PrintError>;
    /// /health에서 사용: 장치가 쓰기 가능한 상태인지(존재 여부) 확인.
    fn is_ready(&self) -> bool;
}

/// /dev/usb/lp0 등 캐릭터 디바이스에 직접 쓰는 sink.
pub struct FilePrinterSink {
    path: PathBuf,
}

impl FilePrinterSink {
    pub fn new(path: impl Into<PathBuf>) -> Self {
        FilePrinterSink { path: path.into() }
    }
}

impl PrinterSink for FilePrinterSink {
    fn write_all(&self, bytes: &[u8]) -> Result<(), PrintError> {
        let mut file = OpenOptions::new()
            .write(true)
            .create(true)
            .truncate(true)
            .open(&self.path)
            .map_err(|e| PrintError::Unavailable(format!("{}: {e}", self.path.display())))?;
        file.write_all(bytes)
            .map_err(|e| PrintError::Write(e.to_string()))?;
        file.flush().map_err(|e| PrintError::Write(e.to_string()))?;
        Ok(())
    }

    fn is_ready(&self) -> bool {
        self.path.exists()
    }
}

#[cfg(test)]
pub mod testing {
    use super::*;
    use std::sync::Mutex;

    /// 캡처/실패 주입용 테스트 sink.
    /// `fail_first`만큼 초기 쓰기를 실패시킨 뒤 성공한다.
    pub struct MockSink {
        pub writes: Mutex<Vec<Vec<u8>>>,
        fail_first: Mutex<u32>,
        ready: bool,
    }

    impl MockSink {
        pub fn new() -> Self {
            MockSink {
                writes: Mutex::new(Vec::new()),
                fail_first: Mutex::new(0),
                ready: true,
            }
        }

        pub fn failing(times: u32) -> Self {
            MockSink {
                writes: Mutex::new(Vec::new()),
                fail_first: Mutex::new(times),
                ready: true,
            }
        }

        pub fn write_count(&self) -> usize {
            self.writes.lock().unwrap().len()
        }
    }

    impl Default for MockSink {
        fn default() -> Self {
            Self::new()
        }
    }

    impl PrinterSink for MockSink {
        fn write_all(&self, bytes: &[u8]) -> Result<(), PrintError> {
            let mut remaining = self.fail_first.lock().unwrap();
            if *remaining > 0 {
                *remaining -= 1;
                return Err(PrintError::Write("injected failure".to_string()));
            }
            self.writes.lock().unwrap().push(bytes.to_vec());
            Ok(())
        }

        fn is_ready(&self) -> bool {
            self.ready
        }
    }
}

#[cfg(test)]
mod tests {
    use super::testing::MockSink;
    use super::*;

    #[test]
    fn file_sink_writes_bytes_to_path() {
        let dir = std::env::temp_dir();
        let path = dir.join(format!("holybean-sink-test-{}.bin", std::process::id()));
        let sink = FilePrinterSink::new(&path);
        sink.write_all(b"hello").unwrap();
        assert_eq!(std::fs::read(&path).unwrap(), b"hello");
        std::fs::remove_file(&path).ok();
    }

    #[test]
    fn file_sink_unavailable_for_missing_path() {
        let sink = FilePrinterSink::new("/nonexistent/dir/lp0");
        assert!(!sink.is_ready());
        assert!(matches!(
            sink.write_all(b"x"),
            Err(PrintError::Unavailable(_))
        ));
    }

    #[test]
    fn mock_sink_captures_and_injects_failures() {
        let sink = MockSink::failing(2);
        assert!(sink.write_all(b"a").is_err());
        assert!(sink.write_all(b"b").is_err());
        assert!(sink.write_all(b"c").is_ok());
        assert_eq!(sink.write_count(), 1);
    }
}
