use crate::command::{PrintCommand, Size};
use crate::layout::{layout_row, Run, LINE_WIDTH};

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
/// 실기 보정 결과 255(≈31mm)에서 하단 여백이 적절함(2026-05-30, 세우 SLK-TS400B).
pub const FEED_BEFORE_CUT_DOTS: u8 = 255;

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
                let line: String = (0..LINE_WIDTH).map(|_| *ch).collect();
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

#[cfg(test)]
mod tests {
    use super::*;
    use crate::layout::Style;

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
}
