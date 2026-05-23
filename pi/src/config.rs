use std::net::SocketAddr;

pub struct Config {
    pub device_path: String,
    pub bind: SocketAddr,
}

impl Config {
    /// 환경변수에서 설정을 읽는다.
    /// HOLYBEAN_PRINT_DEVICE (기본 /dev/usb/lp0), HOLYBEAN_PRINT_BIND (기본 0.0.0.0:9100)
    pub fn from_env() -> Self {
        let device_path =
            std::env::var("HOLYBEAN_PRINT_DEVICE").unwrap_or_else(|_| "/dev/usb/lp0".to_string());
        let bind = std::env::var("HOLYBEAN_PRINT_BIND")
            .unwrap_or_else(|_| "0.0.0.0:9100".to_string())
            .parse()
            .expect("HOLYBEAN_PRINT_BIND must be a valid socket address");
        Config { device_path, bind }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_default_bind() {
        let addr: SocketAddr = "0.0.0.0:9100".parse().unwrap();
        assert_eq!(addr.port(), 9100);
    }
}
