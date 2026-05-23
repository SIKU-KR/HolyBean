use crate::command::{Align, Segment, Size};

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
}
