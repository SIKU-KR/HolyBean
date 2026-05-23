package eloom.holybean.printer.network

/**
 * 영수증 명령 배열을 선언적으로 구성하는 빌더.
 * 기본값(left/normal/false)은 DTO에서 null로 남겨 JSON 직렬화 시 생략된다.
 */
class ReceiptBuilder {
    private val commands = mutableListOf<PrintCommandDto>()

    fun text(
        content: String,
        align: PrintAlign = PrintAlign.LEFT,
        bold: Boolean = false,
        underline: Boolean = false,
        size: PrintSize = PrintSize.NORMAL,
    ) = apply {
        commands += PrintCommandDto(
            type = "text",
            content = content,
            align = align.wireOrNull(),
            bold = if (bold) true else null,
            underline = if (underline) true else null,
            size = size.wireOrNull(),
        )
    }

    fun row(vararg segments: PrintSegmentDto) = apply {
        commands += PrintCommandDto(type = "row", columns = segments.toList())
    }

    fun divider(ch: Char = '=') = apply {
        commands += PrintCommandDto(type = "divider", ch = ch.toString())
    }

    fun blank() = apply {
        commands += PrintCommandDto(type = "blank")
    }

    fun cut() = apply {
        commands += PrintCommandDto(type = "cut")
    }

    fun build(): List<PrintCommandDto> = commands.toList()

    companion object {
        /** row()에 넣을 세그먼트 헬퍼. */
        fun seg(
            content: String,
            align: PrintAlign = PrintAlign.LEFT,
            bold: Boolean = false,
            underline: Boolean = false,
            size: PrintSize = PrintSize.NORMAL,
        ): PrintSegmentDto = PrintSegmentDto(
            content = content,
            align = align.wireOrNull(),
            bold = if (bold) true else null,
            underline = if (underline) true else null,
            size = size.wireOrNull(),
        )
    }
}
