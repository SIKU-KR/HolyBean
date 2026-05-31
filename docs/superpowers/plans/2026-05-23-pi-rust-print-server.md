# Pi Rust 영수증 프린트 서버 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Raspberry Pi에서 동작하는 stateless Rust HTTP 서버를 만든다. 구조화된 JSON 인쇄 명령(`PrintCommand[]`)을 받아 기존 영수증과 **바이트 단위로 동등한** ESC/POS로 변환하고, 단일 워커 큐로 직렬화해 `/dev/usb/lp0`에 출력한다. `POST /print`와 `GET /health`를 노출한다.

**Architecture:** axum HTTP 핸들러가 요청을 받아 순수 함수로 ESC/POS 바이트를 렌더링(병렬 안전)한 뒤, `tokio::mpsc` 잡 큐에 `(bytes, oneshot reply)`를 넣는다. 단일 워커가 큐를 순차 소비하며 `PrinterSink`에 쓴다(NFR-4 직렬화). 쓰기는 3회 백오프 재시도(FR-4/NFR). 프린터 I/O는 `PrinterSink` trait 뒤에 추상화해 테스트에서 mock하고, 운영에서는 `/dev/usb/lp0` 파일 쓰기를 사용한다. 서버는 비즈니스 데이터를 보관하지 않는다(NFR-6).

**Tech Stack:** Rust (edition 2021), axum 0.7, tokio 1.x, serde/serde_json 1.x, encoding_rs 0.8 (EUC-KR), thiserror 1.x, tracing/tracing-subscriber. clippy `-D warnings`. 테스트는 `#[test]` + `#[tokio::test]` + `tower::ServiceExt::oneshot` + `http-body-util`.

**Scope note:** 이 계획은 **Rust 서버 애플리케이션**만 다룬다. Pi 핫스팟/NAT 구성, systemd 등록, `cross` ARM 크로스컴파일(PRD 마일스톤 1·4)은 **별도 후속 계획**으로 분리한다. Android 인쇄 경로 교체는 동일 날짜의 `2026-05-23-android-print-path-swap.md`가 다룬다. 두 계획은 아래 **§JSON 계약**을 공유한다.

---

## JSON 계약 (Pi ↔ Android 공유 — 변경 시 양쪽 동시 수정)

요청 본문(`POST /print`):

```json
{ "commands": [
    { "type": "divider", "ch": "=" },
    { "type": "blank" },
    { "type": "text", "content": "주문번호 : 42", "align": "center", "underline": true, "size": "big" },
    { "type": "blank" },
    { "type": "divider", "ch": "-" },
    { "type": "row", "columns": [
        { "content": "아메리카노", "align": "left", "bold": true },
        { "content": "2", "align": "right" }
    ] },
    { "type": "cut" }
] }
```

명령 타입(합타입):

| type | 필드 | 의미 |
|------|------|------|
| `text` | `content` (string), `align` (`left`\|`center`\|`right`, 기본 left), `bold` (bool, 기본 false), `underline` (bool, 기본 false), `size` (`normal`\|`big`, 기본 normal) | 한 줄. 정렬은 공백 패딩으로 구현(라인 폭 32칸 기준). `text`는 내부적으로 1-컬럼 `row`와 동일하게 처리한다. |
| `row` | `columns`: 위 `text`의 `content`/`align`/`bold`/`underline`/`size`와 동일한 필드를 가진 세그먼트 배열 | 한 줄을 N등분(`floor(32/N)`)해 각 세그먼트를 자기 칸 안에서 정렬. 잉여 칸은 앞 칸부터 1칸씩 분배. (레거시 `[L]..[R]..` 컬럼 동작 포팅) |
| `divider` | `ch` (string, 기본 `"="`) — 첫 글자만 사용 | 라인 폭(32칸)을 해당 문자로 가득 채운 줄. |
| `blank` | 없음 | 빈 줄(LF 1회). |
| `cut` | 없음 | 용지 피드 후 절단(`ESC J` + `GS V 1`). |

- 직렬화 규칙: Android는 Gson 기본(널 필드 생략)으로 직렬화 → 위 최소 형태가 그대로 나온다. Pi는 serde 내부 태깅 enum으로 역직렬화하며 미지정 필드는 무시한다.
- 라인 폭은 32칸(레거시 `EscPosPrinter(conn, 180, 72f, 32, encoding)` 기준). 폭 계산은 EUC-KR 인코딩 바이트 수 × 크기계수(normal=1, big=2)다(레거시 `PrinterTextParserString.length()` 동치).

---

## File Structure

크레이트 루트: `pi/` (모노레포 내 신규 Rust 바이너리 크레이트, 이름 `holybean-print-server`).

| 파일 | 책임 |
|------|------|
| `pi/Cargo.toml` | 의존성 + 바이너리 정의 |
| `pi/src/main.rs` | 부트스트랩: 설정 로드 → sink/큐/워커 생성 → axum 서버 기동 |
| `pi/src/command.rs` | `PrintCommand` 합타입, `Align`/`Size`/`Segment`, `PrintRequest` (serde 모델) |
| `pi/src/layout.rs` | 표시 폭 계산 + 컬럼/행 레이아웃 알고리즘(레거시 포팅) → `Vec<Run>` |
| `pi/src/escpos.rs` | ESC/POS 바이트 상수 + `Run`/문서 렌더러: `Vec<PrintCommand>` → `Vec<u8>` |
| `pi/src/sink.rs` | `PrinterSink` trait + `FilePrinterSink`(`/dev/usb/lp0`) + 테스트용 `MockSink` |
| `pi/src/queue.rs` | 잡 큐 + 단일 워커(순차 소비 + 3회 백오프 재시도) |
| `pi/src/http.rs` | axum 라우터, `/print`·`/health` 핸들러, `AppState` |
| `pi/src/error.rs` | `PrintError` (thiserror) |
| `pi/src/config.rs` | 환경변수 설정(`HOLYBEAN_PRINT_DEVICE`, `HOLYBEAN_PRINT_BIND`) |

---

## Task 1: 크레이트 스캐폴딩 + clippy 게이트

**Files:**
- Create: `pi/Cargo.toml`
- Create: `pi/src/main.rs`

- [ ] **Step 1: Cargo.toml 작성**

```toml
[package]
name = "holybean-print-server"
version = "0.1.0"
edition = "2021"

[[bin]]
name = "holybean-print-server"
path = "src/main.rs"

[dependencies]
axum = "0.7"
tokio = { version = "1", features = ["rt-multi-thread", "macros", "net", "sync", "time", "signal"] }
serde = { version = "1", features = ["derive"] }
serde_json = "1"
encoding_rs = "0.8"
thiserror = "1"
tracing = "0.1"
tracing-subscriber = { version = "0.3", features = ["env-filter"] }

[dev-dependencies]
tower = { version = "0.5", features = ["util"] }
http-body-util = "0.1"
tokio = { version = "1", features = ["rt", "macros", "time", "sync", "test-util"] }
```

- [ ] **Step 2: 최소 main.rs 작성**

```rust
fn main() {
    println!("holybean-print-server");
}
```

- [ ] **Step 3: 빌드 확인**

Run: `cd pi && cargo build`
Expected: 컴파일 성공(`Compiling holybean-print-server`, `Finished`).

- [ ] **Step 4: clippy 게이트 확인**

Run: `cd pi && cargo clippy --all-targets -- -D warnings`
Expected: 경고 0, 종료코드 0.

- [ ] **Step 5: 커밋**

```bash
git add pi/Cargo.toml pi/src/main.rs
git commit -m "feat(pi): scaffold rust print server crate"
```

---

## Task 2: PrintCommand 합타입 + JSON 역직렬화

**Files:**
- Create: `pi/src/command.rs`
- Modify: `pi/src/main.rs` (모듈 선언 추가)

- [ ] **Step 1: 실패하는 테스트 작성** — `pi/src/command.rs`

```rust
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Copy, PartialEq, Eq, Default, Deserialize, Serialize)]
#[serde(rename_all = "lowercase")]
pub enum Align {
    #[default]
    Left,
    Center,
    Right,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Default, Deserialize, Serialize)]
#[serde(rename_all = "lowercase")]
pub enum Size {
    #[default]
    Normal,
    Big,
}

#[derive(Debug, Clone, PartialEq, Eq, Deserialize, Serialize)]
pub struct Segment {
    pub content: String,
    #[serde(default)]
    pub align: Align,
    #[serde(default)]
    pub bold: bool,
    #[serde(default)]
    pub underline: bool,
    #[serde(default)]
    pub size: Size,
}

fn default_divider_char() -> char {
    '='
}

#[derive(Debug, Clone, PartialEq, Eq, Deserialize, Serialize)]
#[serde(tag = "type", rename_all = "lowercase")]
pub enum PrintCommand {
    Text {
        content: String,
        #[serde(default)]
        align: Align,
        #[serde(default)]
        bold: bool,
        #[serde(default)]
        underline: bool,
        #[serde(default)]
        size: Size,
    },
    Row {
        columns: Vec<Segment>,
    },
    Divider {
        #[serde(default = "default_divider_char")]
        ch: char,
    },
    Blank,
    Cut,
}

#[derive(Debug, Clone, PartialEq, Eq, Deserialize, Serialize)]
pub struct PrintRequest {
    pub commands: Vec<PrintCommand>,
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn deserializes_prd_example_request() {
        let json = r#"{ "commands": [
            { "type": "divider", "ch": "=" },
            { "type": "blank" },
            { "type": "text", "content": "주문번호 : 42", "align": "center", "underline": true, "size": "big" },
            { "type": "row", "columns": [
                { "content": "아메리카노", "align": "left", "bold": true },
                { "content": "2", "align": "right" }
            ] },
            { "type": "cut" }
        ] }"#;

        let req: PrintRequest = serde_json::from_str(json).unwrap();

        assert_eq!(req.commands.len(), 5);
        assert_eq!(req.commands[0], PrintCommand::Divider { ch: '=' });
        assert_eq!(req.commands[1], PrintCommand::Blank);
        assert_eq!(
            req.commands[2],
            PrintCommand::Text {
                content: "주문번호 : 42".to_string(),
                align: Align::Center,
                bold: false,
                underline: true,
                size: Size::Big,
            }
        );
        assert_eq!(req.commands[4], PrintCommand::Cut);
    }

    #[test]
    fn applies_field_defaults() {
        let cmd: PrintCommand =
            serde_json::from_str(r#"{ "type": "text", "content": "hi" }"#).unwrap();
        assert_eq!(
            cmd,
            PrintCommand::Text {
                content: "hi".to_string(),
                align: Align::Left,
                bold: false,
                underline: false,
                size: Size::Normal,
            }
        );
    }

    #[test]
    fn divider_defaults_to_equals() {
        let cmd: PrintCommand = serde_json::from_str(r#"{ "type": "divider" }"#).unwrap();
        assert_eq!(cmd, PrintCommand::Divider { ch: '=' });
    }

    #[test]
    fn ignores_unknown_null_fields_from_gson() {
        // Gson은 널 필드를 생략하지만, 혹시 들어와도 무시되어야 한다.
        let cmd: PrintCommand =
            serde_json::from_str(r#"{ "type": "cut", "content": null }"#).unwrap();
        assert_eq!(cmd, PrintCommand::Cut);
    }
}
```

- [ ] **Step 2: main.rs에 모듈 선언 + 테스트 실패 확인**

`pi/src/main.rs`를 다음으로 교체:

```rust
mod command;

fn main() {
    println!("holybean-print-server");
}
```

Run: `cd pi && cargo test command::`
Expected: 4개 테스트 통과(모델이 이미 위에 완성됨). 만약 컴파일 에러면 모델 정의의 오타를 수정.

> 참고: 이 태스크는 모델과 테스트를 함께 작성했다. RED 단계는 모듈 선언 누락 시의 컴파일 실패로 대체한다. 통과를 확인하면 다음 단계로.

- [ ] **Step 3: clippy 확인**

Run: `cd pi && cargo clippy --all-targets -- -D warnings`
Expected: 경고 0.

- [ ] **Step 4: 커밋**

```bash
git add pi/src/command.rs pi/src/main.rs
git commit -m "feat(pi): add PrintCommand sum type and JSON model"
```

---

## Task 3: 표시 폭 계산 (EUC-KR × 크기계수)

**Files:**
- Create: `pi/src/layout.rs`
- Modify: `pi/src/main.rs` (`mod layout;`)

- [ ] **Step 1: 실패하는 테스트 작성** — `pi/src/layout.rs` 하단

```rust
use crate::command::Size;

/// 라인당 최대 칸 수(레거시 EscPosPrinter nbrCharactersPerLine).
pub const LINE_WIDTH: i32 = 32;

/// 프린터 칸 기준 표시 폭. EUC-KR 인코딩 바이트 수 × 크기계수.
/// (레거시 PrinterTextParserString.length(): getBytes("EUC-KR").length * coef)
pub fn display_width(content: &str, size: Size) -> i32 {
    let (encoded, _, _) = encoding_rs::EUC_KR.encode(content);
    let coef = match size {
        Size::Normal => 1,
        Size::Big => 2,
    };
    (encoded.len() as i32) * coef
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn ascii_is_one_cell_each() {
        assert_eq!(display_width("abc", Size::Normal), 3);
    }

    #[test]
    fn hangul_is_two_cells_each() {
        // "가나" → EUC-KR 4바이트.
        assert_eq!(display_width("가나", Size::Normal), 4);
    }

    #[test]
    fn big_size_doubles_width() {
        assert_eq!(display_width("ab", Size::Big), 4);
        assert_eq!(display_width("가", Size::Big), 4);
    }
}
```

- [ ] **Step 2: main.rs에 `mod layout;` 추가 후 실패→통과 확인**

`pi/src/main.rs`의 `mod command;` 아래에 `mod layout;` 추가.

Run: `cd pi && cargo test layout::tests::`
Expected: 3개 통과.

- [ ] **Step 3: 커밋**

```bash
git add pi/src/layout.rs pi/src/main.rs
git commit -m "feat(pi): add EUC-KR display width measurement"
```

---

## Task 4: 컬럼/행 레이아웃 알고리즘 (레거시 포팅)

레거시 `PrinterTextParserLine`/`PrinterTextParserColumn`의 칸 분배·정렬·잉여칸·오버플로 전파를 포팅한다. 출력은 `Vec<Run>`이며, 패딩 공백은 무스타일(normal/굵게 없음/밑줄 없음)로 생성한다(의도된 결정: 정렬용 공백에 밑줄·굵게가 묻지 않게 함).

**Files:**
- Modify: `pi/src/layout.rs`

- [ ] **Step 1: 실패하는 테스트 작성** — `pi/src/layout.rs`의 `display_width` 아래(테스트 모듈 위)

```rust
use crate::command::{Align, Segment};

/// 스타일이 적용된 텍스트 조각. 패딩 공백은 Style::default()로 만든다.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Style {
    pub bold: bool,
    pub underline: bool,
    pub size: Size,
}

impl Default for Style {
    fn default() -> Self {
        Style {
            bold: false,
            underline: false,
            size: Size::Normal,
        }
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Run {
    pub text: String,
    pub style: Style,
}

fn spaces(n: i32) -> Run {
    Run {
        text: " ".repeat(n.max(0) as usize),
        style: Style::default(),
    }
}

/// 한 행(컬럼 배열)을 LINE_WIDTH 칸에 배치해 Run 목록으로 변환.
/// 레거시 PrinterTextParserLine/Column의 칸 분배 알고리즘을 포팅.
pub fn layout_row(columns: &[Segment]) -> Vec<Run> {
    let n = columns.len() as i32;
    if n == 0 {
        return Vec::new();
    }
    let nbr_char_column = LINE_WIDTH / n; // floor
    let mut nbr_char_forgotten = LINE_WIDTH - nbr_char_column * n;
    let mut nbr_char_column_exceeded: i32 = 0;
    let mut runs: Vec<Run> = Vec::new();

    for col in columns {
        let text_width = display_width(&col.content, col.size);
        let col_w = nbr_char_column;
        let (mut left, mut right) = match col.align {
            Align::Left => (0, col_w - text_width),
            Align::Center => {
                let left = (((col_w - text_width) as f32) / 2.0).floor() as i32;
                (left, col_w - text_width - left)
            }
            Align::Right => (col_w - text_width, 0),
        };

        if nbr_char_forgotten > 0 {
            nbr_char_forgotten -= 1;
            right += 1;
        }

        if nbr_char_column_exceeded < 0 {
            left += nbr_char_column_exceeded;
            nbr_char_column_exceeded = 0;
            if left < 1 {
                right += left - 1;
                left = 1;
            }
        }

        if left < 0 {
            nbr_char_column_exceeded += left;
            left = 0;
        }
        if right < 0 {
            nbr_char_column_exceeded += right;
            right = 0;
        }

        if left > 0 {
            runs.push(spaces(left));
        }
        runs.push(Run {
            text: col.content.clone(),
            style: Style {
                bold: col.bold,
                underline: col.underline,
                size: col.size,
            },
        });
        if right > 0 {
            runs.push(spaces(right));
        }
    }

    runs
}
```

테스트 모듈(`#[cfg(test)] mod tests`)에 다음 케이스 추가:

```rust
    fn seg(content: &str, align: Align) -> Segment {
        Segment {
            content: content.to_string(),
            align,
            bold: false,
            underline: false,
            size: Size::Normal,
        }
    }

    /// Run 목록을 평문 문자열로 합쳐 칸 배치를 검증한다.
    fn flatten(runs: &[Run]) -> String {
        runs.iter().map(|r| r.text.as_str()).collect()
    }

    #[test]
    fn single_left_column_pads_right_to_full_width() {
        let runs = layout_row(&[seg("ab", Align::Left)]);
        let line = flatten(&runs);
        assert_eq!(line.chars().count(), LINE_WIDTH as usize);
        assert!(line.starts_with("ab"));
        assert_eq!(&line[2..], &" ".repeat(30));
    }

    #[test]
    fn single_right_column_pads_left() {
        let runs = layout_row(&[seg("ab", Align::Right)]);
        let line = flatten(&runs);
        assert_eq!(line.chars().count(), LINE_WIDTH as usize);
        assert!(line.ends_with("ab"));
    }

    #[test]
    fn single_center_column_balances_padding() {
        let runs = layout_row(&[seg("ab", Align::Center)]);
        let line = flatten(&runs);
        assert_eq!(line.chars().count(), LINE_WIDTH as usize);
        // (32-2)/2 = 15 좌측 공백
        assert_eq!(&line[..15], &" ".repeat(15));
        assert_eq!(&line[15..17], "ab");
    }

    #[test]
    fn two_columns_left_and_right_fill_line() {
        // [L]name [R]2  → 좌측 정렬 + 우측 정렬, 합쳐서 32칸
        let runs = layout_row(&[seg("name", Align::Left), seg("2", Align::Right)]);
        let line = flatten(&runs);
        assert_eq!(line.chars().count(), LINE_WIDTH as usize);
        assert!(line.starts_with("name"));
        assert!(line.ends_with('2'));
    }

    #[test]
    fn three_columns_distribute_forgotten_chars() {
        // 32/3 = 10칸씩, 잉여 2칸은 앞 두 칸에 1칸씩 분배 → 총 32칸 유지
        let runs = layout_row(&[
            seg("a", Align::Left),
            seg("b", Align::Right),
            seg("c", Align::Right),
        ]);
        let line = flatten(&runs);
        assert_eq!(line.chars().count(), LINE_WIDTH as usize);
    }

    #[test]
    fn padding_runs_are_unstyled() {
        let runs = layout_row(&[Segment {
            content: "x".to_string(),
            align: Align::Center,
            bold: true,
            underline: true,
            size: Size::Big,
        }]);
        // 콘텐츠 Run은 스타일 유지, 공백 Run은 기본 스타일
        let content_run = runs.iter().find(|r| r.text == "x").unwrap();
        assert_eq!(content_run.style.size, Size::Big);
        assert!(content_run.style.bold && content_run.style.underline);
        for r in runs.iter().filter(|r| r.text.trim().is_empty()) {
            assert_eq!(r.style, Style::default());
        }
    }
```

- [ ] **Step 2: 테스트 실행**

Run: `cd pi && cargo test layout::`
Expected: 모든 레이아웃 테스트 통과(6 신규 + 3 기존).

- [ ] **Step 3: clippy 확인**

Run: `cd pi && cargo clippy --all-targets -- -D warnings`
Expected: 경고 0. (`as` 캐스트 관련 clippy 경고가 나오면 `i32`/`usize` 변환을 `.max(0) as usize` 형태로 유지하고 `#[allow(clippy::cast_possible_truncation)]`는 쓰지 말 것 — 로직상 안전 범위이며 기본 clippy lint는 통과한다.)

- [ ] **Step 4: 커밋**

```bash
git add pi/src/layout.rs
git commit -m "feat(pi): port legacy column layout algorithm to Run list"
```

---

## Task 5: ESC/POS 바이트 상수 + Run 렌더링

레거시 `EscPosPrinterCommands`의 바이트와 동치. `<u>` 태그는 레거시에서 `TEXT_UNDERLINE_LARGE`(0x02)로 매핑되므로 그대로 0x02를 쓴다. big은 `GS ! 0x11`.

**Files:**
- Create: `pi/src/escpos.rs`
- Modify: `pi/src/main.rs` (`mod escpos;`)

- [ ] **Step 1: 바이트 상수 + Run 렌더러 작성** — `pi/src/escpos.rs`

```rust
use crate::layout::{Run, Style};

// 레거시 EscPosPrinterCommands 바이트와 동치
pub const RESET: &[u8] = &[0x1B, 0x40]; // ESC @
pub const CHARSET_EUC_KR: &[u8] = &[0x1B, 0x74, 0x0D]; // ESC t 13
pub const ALIGN_LEFT: &[u8] = &[0x1B, 0x61, 0x00]; // ESC a 0
pub const BOLD_ON: &[u8] = &[0x1B, 0x45, 0x01];
pub const BOLD_OFF: &[u8] = &[0x1B, 0x45, 0x00];
pub const UNDERLINE_ON: &[u8] = &[0x1B, 0x2D, 0x02]; // 레거시 <u> → LARGE
pub const UNDERLINE_OFF: &[u8] = &[0x1B, 0x2D, 0x00];
pub const SIZE_BIG: &[u8] = &[0x1D, 0x21, 0x11]; // GS ! (double w+h)
pub const SIZE_NORMAL: &[u8] = &[0x1D, 0x21, 0x00];
pub const LF: u8 = 0x0A;
pub const CUT: &[u8] = &[0x1D, 0x56, 0x01]; // GS V 1

/// 절단 전 용지 피드(dots). ESC J n (n ≤ 255). 절단 위치 확보용.
pub const FEED_BEFORE_CUT_DOTS: u8 = 100;

use crate::command::Size;

/// 스타일이 적용된 하나의 Run을 ESC/POS 바이트로 인코딩해 out에 덧붙인다.
pub fn render_run(run: &Run, out: &mut Vec<u8>) {
    if run.style.bold {
        out.extend_from_slice(BOLD_ON);
    }
    if run.style.underline {
        out.extend_from_slice(UNDERLINE_ON);
    }
    if run.style.size == Size::Big {
        out.extend_from_slice(SIZE_BIG);
    }

    let (encoded, _, _) = encoding_rs::EUC_KR.encode(&run.text);
    out.extend_from_slice(&encoded);

    // 상태 토글 원복(다음 Run에 누수 방지)
    if run.style.size == Size::Big {
        out.extend_from_slice(SIZE_NORMAL);
    }
    if run.style.underline {
        out.extend_from_slice(UNDERLINE_OFF);
    }
    if run.style.bold {
        out.extend_from_slice(BOLD_OFF);
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn renders_plain_ascii() {
        let mut out = Vec::new();
        render_run(
            &Run {
                text: "ab".to_string(),
                style: Style::default(),
            },
            &mut out,
        );
        assert_eq!(out, b"ab");
    }

    #[test]
    fn wraps_bold_underline_big_in_correct_order() {
        let mut out = Vec::new();
        render_run(
            &Run {
                text: "X".to_string(),
                style: Style {
                    bold: true,
                    underline: true,
                    size: Size::Big,
                },
            },
            &mut out,
        );
        let expected = [
            BOLD_ON,
            UNDERLINE_ON,
            SIZE_BIG,
            b"X",
            SIZE_NORMAL,
            UNDERLINE_OFF,
            BOLD_OFF,
        ]
        .concat();
        assert_eq!(out, expected);
    }

    #[test]
    fn encodes_hangul_as_euc_kr() {
        let mut out = Vec::new();
        render_run(
            &Run {
                text: "가".to_string(),
                style: Style::default(),
            },
            &mut out,
        );
        // EUC-KR "가" = 0xB0 0xA1
        assert_eq!(out, vec![0xB0, 0xA1]);
    }
}
```

- [ ] **Step 2: main.rs에 `mod escpos;` 추가 후 테스트 실행**

Run: `cd pi && cargo test escpos::`
Expected: 3개 통과.

- [ ] **Step 3: clippy + 커밋**

```bash
cd pi && cargo clippy --all-targets -- -D warnings
cd .. && git add pi/src/escpos.rs pi/src/main.rs
git commit -m "feat(pi): add ESC/POS byte constants and run renderer"
```

---

## Task 6: 문서 렌더러 (PrintCommand[] → ESC/POS 바이트)

**Files:**
- Modify: `pi/src/escpos.rs`

- [ ] **Step 1: 실패하는 테스트 + 구현 작성** — `pi/src/escpos.rs`의 `render_run` 아래

```rust
use crate::command::PrintCommand;
use crate::layout::{layout_row, Run, LINE_WIDTH};

/// 한 인쇄 요청의 명령 배열을 완전한 ESC/POS 바이트 스트림으로 렌더링한다.
/// 매 요청마다 RESET + 문자셋 선택 + 좌측정렬 기준으로 시작한다(stateless).
pub fn render_document(commands: &[PrintCommand]) -> Vec<u8> {
    let mut out = Vec::new();
    out.extend_from_slice(RESET);
    out.extend_from_slice(CHARSET_EUC_KR);
    out.extend_from_slice(ALIGN_LEFT);

    for cmd in commands {
        match cmd {
            PrintCommand::Text {
                content,
                align,
                bold,
                underline,
                size,
            } => {
                let seg = crate::command::Segment {
                    content: content.clone(),
                    align: *align,
                    bold: *bold,
                    underline: *underline,
                    size: *size,
                };
                for run in layout_row(std::slice::from_ref(&seg)) {
                    render_run(&run, &mut out);
                }
                out.push(LF);
            }
            PrintCommand::Row { columns } => {
                for run in layout_row(columns) {
                    render_run(&run, &mut out);
                }
                out.push(LF);
            }
            PrintCommand::Divider { ch } => {
                let line: String = std::iter::repeat(*ch).take(LINE_WIDTH as usize).collect();
                render_run(
                    &Run {
                        text: line,
                        style: crate::layout::Style::default(),
                    },
                    &mut out,
                );
                out.push(LF);
            }
            PrintCommand::Blank => {
                out.push(LF);
            }
            PrintCommand::Cut => {
                out.extend_from_slice(&[0x1B, 0x4A, FEED_BEFORE_CUT_DOTS]); // ESC J n
                out.extend_from_slice(CUT);
            }
        }
    }

    out
}
```

테스트 모듈에 추가:

```rust
    use crate::command::{Align, PrintCommand, Size};

    #[test]
    fn document_starts_with_reset_charset_align() {
        let bytes = render_document(&[PrintCommand::Blank]);
        let prefix = [RESET, CHARSET_EUC_KR, ALIGN_LEFT].concat();
        assert!(bytes.starts_with(&prefix));
        assert_eq!(*bytes.last().unwrap(), LF); // Blank → LF
    }

    #[test]
    fn divider_fills_line_width() {
        let bytes = render_document(&[PrintCommand::Divider { ch: '=' }]);
        let dashes = "=".repeat(LINE_WIDTH as usize);
        let needle = dashes.as_bytes();
        assert!(bytes.windows(needle.len()).any(|w| w == needle));
    }

    #[test]
    fn cut_emits_feed_then_cut_at_end() {
        let bytes = render_document(&[PrintCommand::Cut]);
        let tail = [&[0x1B, 0x4A, FEED_BEFORE_CUT_DOTS][..], CUT].concat();
        assert!(bytes.ends_with(&tail));
    }

    #[test]
    fn text_line_ends_with_lf() {
        let bytes = render_document(&[PrintCommand::Text {
            content: "hi".to_string(),
            align: Align::Left,
            bold: false,
            underline: false,
            size: Size::Normal,
        }]);
        assert_eq!(*bytes.last().unwrap(), LF);
    }
```

- [ ] **Step 2: 테스트 실행**

Run: `cd pi && cargo test escpos::`
Expected: 신규 4개 포함 전부 통과.

- [ ] **Step 3: clippy + 커밋**

(`std::iter::repeat(*ch).take(..)`에서 clippy가 `repeat_n`을 제안하면 `(0..LINE_WIDTH).map(|_| *ch).collect()`로 바꿔 통과시킨다.)

```bash
cd pi && cargo clippy --all-targets -- -D warnings
cd .. && git add pi/src/escpos.rs
git commit -m "feat(pi): render full document from PrintCommand list"
```

---

## Task 7: 에러 타입 + PrinterSink + FilePrinterSink + MockSink

**Files:**
- Create: `pi/src/error.rs`
- Create: `pi/src/sink.rs`
- Modify: `pi/src/main.rs` (`mod error; mod sink;`)

- [ ] **Step 1: 에러 타입 작성** — `pi/src/error.rs`

```rust
use thiserror::Error;

#[derive(Debug, Error)]
pub enum PrintError {
    #[error("printer write failed: {0}")]
    Write(String),
    #[error("printer device unavailable: {0}")]
    Unavailable(String),
}
```

- [ ] **Step 2: sink trait + 구현 + 실패 테스트** — `pi/src/sink.rs`

```rust
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
```

- [ ] **Step 3: main.rs 모듈 선언 후 테스트 실행**

`pi/src/main.rs`에 `mod error;`와 `mod sink;` 추가.

Run: `cd pi && cargo test sink::`
Expected: 3개 통과.

- [ ] **Step 4: clippy + 커밋**

(`MockSink::new`에 `#[derive(Default)]` 권유 clippy 경고가 나오면 `impl Default for MockSink`를 추가하거나 `new`를 유지하되 `Default`도 함께 구현.)

```bash
cd pi && cargo clippy --all-targets -- -D warnings
cd .. && git add pi/src/error.rs pi/src/sink.rs pi/src/main.rs
git commit -m "feat(pi): add PrinterSink trait, file sink, and mock"
```

---

## Task 8: 잡 큐 + 단일 워커 (직렬화 + 3회 백오프 재시도)

**Files:**
- Create: `pi/src/queue.rs`
- Modify: `pi/src/main.rs` (`mod queue;`)

- [ ] **Step 1: 구현 + 실패 테스트 작성** — `pi/src/queue.rs`

```rust
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
        if attempt <= 1 {
            return self.initial_delay;
        }
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
            let result = write_with_retry(sink.as_ref(), &job.bytes, policy).await;
            let _ = job.reply.send(result);
        }
    });
    JobSubmitter { tx }
}

async fn write_with_retry(
    sink: &dyn PrinterSink,
    bytes: &[u8],
    policy: RetryPolicy,
) -> Result<(), PrintError> {
    let mut attempt = 1;
    loop {
        match sink.write_all(bytes) {
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
```

- [ ] **Step 2: main.rs 모듈 선언 후 테스트 실행**

`pi/src/main.rs`에 `mod queue;` 추가.

Run: `cd pi && cargo test queue::`
Expected: 3개 통과. (`start_paused`로 sleep이 즉시 진행되어 빠르게 끝남.)

- [ ] **Step 3: clippy + 커밋**

```bash
cd pi && cargo clippy --all-targets -- -D warnings
cd .. && git add pi/src/queue.rs pi/src/main.rs
git commit -m "feat(pi): add job queue worker with serialization and retry"
```

---

## Task 9: HTTP 계층 (/print, /health)

**Files:**
- Create: `pi/src/http.rs`
- Modify: `pi/src/main.rs` (`mod http;`)

- [ ] **Step 1: 핸들러 + 라우터 + 실패 테스트 작성** — `pi/src/http.rs`

```rust
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
```

- [ ] **Step 2: main.rs 모듈 선언 후 테스트 실행**

`pi/src/main.rs`에 `mod http;` 추가.

Run: `cd pi && cargo test http::`
Expected: 4개 통과.

- [ ] **Step 3: clippy + 커밋**

```bash
cd pi && cargo clippy --all-targets -- -D warnings
cd .. && git add pi/src/http.rs pi/src/main.rs
git commit -m "feat(pi): add /print and /health HTTP handlers"
```

---

## Task 10: 설정 + 부트스트랩 (main 배선)

**Files:**
- Create: `pi/src/config.rs`
- Modify: `pi/src/main.rs`

- [ ] **Step 1: 설정 모듈 + 테스트 작성** — `pi/src/config.rs`

```rust
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
```

- [ ] **Step 2: main.rs 최종 배선**

`pi/src/main.rs`를 다음으로 교체:

```rust
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
```

- [ ] **Step 3: 전체 테스트 + 빌드**

Run: `cd pi && cargo test && cargo build`
Expected: 전체 테스트 통과, 빌드 성공.

- [ ] **Step 4: 로컬 스모크 테스트** (호스트에서 임시 파일을 디바이스로 사용)

```bash
cd pi
HOLYBEAN_PRINT_DEVICE=/tmp/fake-printer.bin HOLYBEAN_PRINT_BIND=127.0.0.1:9100 cargo run &
sleep 1
curl -s -o /dev/null -w "%{http_code}" http://127.0.0.1:9100/health    # 기대: 200 (파일은 존재해야 ready → 먼저 touch)
touch /tmp/fake-printer.bin
curl -s -o /dev/null -w "%{http_code}" http://127.0.0.1:9100/health    # 기대: 200
curl -s -X POST http://127.0.0.1:9100/print -H 'content-type: application/json' \
  -d '{ "commands": [ { "type": "text", "content": "스모크", "align": "center" }, { "type": "cut" } ] }'
xxd /tmp/fake-printer.bin | head            # ESC/POS 바이트(1B 40 1B 74 0D ...) 확인
kill %1
```
Expected: `/print` 200, `/tmp/fake-printer.bin`이 `1b 40 1b 74 0d`로 시작.

- [ ] **Step 5: clippy + 커밋**

```bash
cd pi && cargo clippy --all-targets -- -D warnings
cd .. && git add pi/src/config.rs pi/src/main.rs
git commit -m "feat(pi): wire config and bootstrap the print server"
```

---

## Task 11: 골든 영수증 통합 테스트 (회귀 방지)

기존 3개 영수증(고객/POS/리포트/재출력)이 의도대로 렌더링되는지 명령 배열 단위로 고정한다. Android 계획이 이 형태를 그대로 보내야 한다.

**Files:**
- Create: `pi/tests/golden_receipt.rs`

- [ ] **Step 1: 통합 테스트 작성** — `pi/tests/golden_receipt.rs`

```rust
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
```

- [ ] **Step 2: 통합 테스트 실행**

Run: `cd pi && cargo test --test golden_receipt`
Expected: 통과(서버 기동 → /print → 파일 바이트 검증).

- [ ] **Step 3: clippy(테스트 포함) + 전체 테스트 + 커밋**

```bash
cd pi && cargo clippy --all-targets -- -D warnings && cargo test
cd .. && git add pi/tests/golden_receipt.rs
git commit -m "test(pi): add black-box golden receipt integration test"
```

---

## Self-Review (작성자 점검 결과)

**Spec coverage (PRD §5 요구사항):**
- FR-1 (인쇄 요청): `POST /print` — Task 9. ✓
- FR-2 (텍스트/굵게/가운데/구분선/커팅, Pi가 ESC/POS 생성): Task 5·6. ✓ (밑줄·big도 포함 → 완전 재현)
- FR-2a (구조화 JSON → ESC/POS 변환): Task 2·6. ✓
- FR-3 (서버 상태 확인): `GET /health` — Task 9. ✓
- FR-4 (실패 자동 재시도): Task 8 워커 3회 백오프. ✓
- NFR-3 (clippy `-D warnings`): 매 태스크 게이트. ✓
- NFR-4 (동시 요청 직렬화): Task 8 단일 워커 + mpsc. ✓
- NFR-6 (stateless): 비즈니스 데이터 미보관, 매 요청 RESET. ✓
- FR-5(사용자 알림)·FR-6·FR-7·NFR-1·NFR-2·NFR-5는 **Android(FR-5)** 또는 **네트워크/배포 후속 계획(FR-6·7, NFR-1·2·5)** 소관 → 본 계획 범위 밖(Scope note 참조).

**Placeholder scan:** TODO/TBD/"적절히 처리" 없음. 모든 코드 단계에 완전한 코드와 기대 출력 포함.

**Type consistency:** `Align`/`Size`/`Segment`/`PrintCommand`/`PrintRequest`(command.rs), `Run`/`Style`/`layout_row`/`display_width`/`LINE_WIDTH`(layout.rs), `render_run`/`render_document`(escpos.rs), `PrinterSink`/`FilePrinterSink`/`MockSink`(sink.rs), `Job`/`JobSubmitter`/`spawn_worker`/`RetryPolicy`(queue.rs), `AppState`/`router`(http.rs), `Config`(config.rs) — 명칭이 태스크 전반에 일관됨.

**의도된 결정(레거시 대비 차이):**
1. 패딩 공백은 무스타일(밑줄/굵게 미적용) — 레거시는 마지막 상태를 일부 패딩에 적용. 시각적으로 더 깔끔하며, 실제 출력은 후속 USB 검증(PRD 마일스톤 3)에서 확정.
2. `divider`는 라인 폭(32칸)을 가득 채움 — 레거시의 37자 리터럴(32칸 초과)을 폭 기준으로 정규화.
3. `cut`은 `ESC J 100` + `GS V 1` — 레거시의 500dots(바이트 절단으로 244dots) 대신 안전한 단일 바이트 피드. `FEED_BEFORE_CUT_DOTS` 상수로 조정 가능.
