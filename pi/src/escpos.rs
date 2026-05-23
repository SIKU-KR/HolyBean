use crate::layout::Run;

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
}
