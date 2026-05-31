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
