package eloom.holybean.printer.network

/**
 * 영수증 인쇄 명령 모델. 각 명령은 ESC/POS 렌더러가 프린터 바이트로 변환한다.
 */
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
