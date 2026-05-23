package eloom.holybean.printer.network

/**
 * Pi 프린트 서버 JSON 계약과 1:1 매핑되는 DTO.
 * Gson 기본 동작(널 필드 생략)으로 직렬화하면 계약의 최소 형태가 된다.
 */
data class PrintRequestDto(
    val commands: List<PrintCommandDto>,
)

data class PrintCommandDto(
    val type: String,                       // "text" | "row" | "divider" | "blank" | "cut"
    val content: String? = null,
    val align: String? = null,              // "left" | "center" | "right"
    val bold: Boolean? = null,
    val underline: Boolean? = null,
    val size: String? = null,               // "normal" | "big"
    val columns: List<PrintSegmentDto>? = null,
    val ch: String? = null,                 // divider 문자(첫 글자만 사용)
)

data class PrintSegmentDto(
    val content: String,
    val align: String? = null,
    val bold: Boolean? = null,
    val underline: Boolean? = null,
    val size: String? = null,
)

enum class PrintAlign(val wire: String) {
    LEFT("left"),
    CENTER("center"),
    RIGHT("right");

    /** 기본값(LEFT)은 null로 만들어 JSON에서 생략 → 계약 최소 형태 유지. */
    fun wireOrNull(): String? = if (this == LEFT) null else wire
}

enum class PrintSize(val wire: String) {
    NORMAL("normal"),
    BIG("big");

    fun wireOrNull(): String? = if (this == NORMAL) null else wire
}
