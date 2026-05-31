// 라이브러리가 아닌 바이너리 크레이트이므로, 통합 테스트에서 내부 함수를 직접 부를 수 없다.
// 대신 HTTP 경계로 검증한다(블랙박스). 서버를 임시 포트에 띄우고 실제 요청을 보낸다.
use std::process::{Child, Command};
use std::time::Duration;

struct ServerGuard(Child);
impl Drop for ServerGuard {
    fn drop(&mut self) {
        let _ = self.0.kill();
    }
}

fn wait_health(port: u16) {
    for _ in 0..50 {
        if let Ok(resp) = ureq_get(port, "/health") {
            if resp == 200 {
                return;
            }
        }
        std::thread::sleep(Duration::from_millis(100));
    }
    panic!("server did not become healthy");
}

// 의존성 없이 표준 라이브러리 TCP로 최소 HTTP GET/POST를 수행하는 헬퍼.
fn ureq_get(port: u16, path: &str) -> std::io::Result<u16> {
    use std::io::{Read, Write};
    let mut stream = std::net::TcpStream::connect(("127.0.0.1", port))?;
    write!(
        stream,
        "GET {path} HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n"
    )?;
    let mut buf = String::new();
    stream.read_to_string(&mut buf)?;
    let code = buf
        .split_whitespace()
        .nth(1)
        .and_then(|s| s.parse().ok())
        .unwrap_or(0);
    Ok(code)
}

fn http_post(port: u16, path: &str, body: &str) -> std::io::Result<u16> {
    use std::io::{Read, Write};
    let mut stream = std::net::TcpStream::connect(("127.0.0.1", port))?;
    write!(
        stream,
        "POST {path} HTTP/1.1\r\nHost: localhost\r\nContent-Type: application/json\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{body}",
        body.len()
    )?;
    let mut buf = String::new();
    stream.read_to_string(&mut buf)?;
    let code = buf
        .split_whitespace()
        .nth(1)
        .and_then(|s| s.parse().ok())
        .unwrap_or(0);
    Ok(code)
}

#[test]
fn customer_receipt_round_trips_through_server() {
    let port = 9123;
    let out = std::env::temp_dir().join("golden-customer.bin");
    let _ = std::fs::remove_file(&out);
    std::fs::write(&out, b"").unwrap();

    let child = Command::new(env!("CARGO_BIN_EXE_holybean-print-server"))
        .env("HOLYBEAN_PRINT_DEVICE", &out)
        .env("HOLYBEAN_PRINT_BIND", format!("127.0.0.1:{port}"))
        .spawn()
        .unwrap();
    let _guard = ServerGuard(child);
    wait_health(port);

    // 고객 영수증(HomePrinter.receiptTextForCustomer 동치)
    let body = r#"{ "commands": [
        { "type": "divider", "ch": "=" },
        { "type": "blank" },
        { "type": "text", "content": "주문번호 : 42", "align": "center", "underline": true, "size": "big" },
        { "type": "blank" },
        { "type": "divider", "ch": "-" },
        { "type": "blank" },
        { "type": "row", "columns": [ { "content": "아메리카노", "bold": true }, { "content": "2", "align": "right" } ] },
        { "type": "blank" },
        { "type": "divider", "ch": "=" },
        { "type": "cut" }
    ] }"#;
    assert_eq!(http_post(port, "/print", body).unwrap(), 200);

    let bytes = std::fs::read(&out).unwrap();
    assert!(bytes.starts_with(&[0x1B, 0x40, 0x1B, 0x74, 0x0D])); // RESET + charset
    assert!(bytes.ends_with(&[0x1D, 0x56, 0x01])); // 마지막은 CUT
    std::fs::remove_file(&out).ok();
}
