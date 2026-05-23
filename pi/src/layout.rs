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
