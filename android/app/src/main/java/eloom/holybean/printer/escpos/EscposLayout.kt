package eloom.holybean.printer.escpos

import eloom.holybean.printer.network.PrintAlign
import eloom.holybean.printer.network.PrintSegmentDto
import eloom.holybean.printer.network.PrintSize
import java.nio.charset.Charset
import kotlin.math.floor

const val LINE_WIDTH = 42

data class EscposStyle(
    val bold: Boolean = false,
    val underline: Boolean = false,
    val size: PrintSize = PrintSize.NORMAL,
)

data class EscposRun(
    val text: String,
    val style: EscposStyle = EscposStyle(),
)

fun displayWidth(content: String, size: PrintSize): Int {
    val encoded = content.toByteArray(eucKrCharset())
    val coef = when (size) {
        PrintSize.NORMAL -> 1
        PrintSize.BIG -> 2
    }
    return encoded.size * coef
}

private fun eucKrCharset(): Charset =
    try {
        Charset.forName("EUC-KR")
    } catch (_: Exception) {
        Charset.forName("MS949")
    }

fun layoutRow(columns: List<PrintSegmentDto>): List<EscposRun> {
    val n = columns.size
    if (n == 0) {
        return emptyList()
    }
    val nbrCharColumn = LINE_WIDTH / n
    var nbrCharForgotten = LINE_WIDTH - nbrCharColumn * n
    var nbrCharColumnExceeded = 0
    val runs = mutableListOf<EscposRun>()

    for (col in columns) {
        val size = parseSize(col.size)
        val align = parseAlign(col.align)
        val textWidth = displayWidth(col.content, size)
        val colW = nbrCharColumn
        var (left, right) = when (align) {
            PrintAlign.LEFT -> 0 to (colW - textWidth)
            PrintAlign.CENTER -> {
                val left = floor((colW - textWidth) / 2.0).toInt()
                left to (colW - textWidth - left)
            }
            PrintAlign.RIGHT -> (colW - textWidth) to 0
        }

        if (nbrCharForgotten > 0) {
            nbrCharForgotten -= 1
            right += 1
        }

        if (nbrCharColumnExceeded < 0) {
            left += nbrCharColumnExceeded
            nbrCharColumnExceeded = 0
            if (left < 1) {
                right += left - 1
                left = 1
            }
        }

        if (left < 0) {
            nbrCharColumnExceeded += left
            left = 0
        }
        if (right < 0) {
            nbrCharColumnExceeded += right
            right = 0
        }

        if (left > 0) {
            runs.add(EscposRun(" ".repeat(left)))
        }
        runs.add(
            EscposRun(
                text = col.content,
                style = EscposStyle(
                    bold = col.bold ?: false,
                    underline = col.underline ?: false,
                    size = size,
                ),
            )
        )
        if (right > 0) {
            runs.add(EscposRun(" ".repeat(right)))
        }
    }

    return runs
}

private fun parseSize(size: String?): PrintSize {
    return size?.let { wire ->
        PrintSize.entries.firstOrNull { it.wire == wire } ?: PrintSize.NORMAL
    } ?: PrintSize.NORMAL
}

private fun parseAlign(align: String?): PrintAlign {
    return align?.let { wire ->
        PrintAlign.entries.firstOrNull { it.wire == wire } ?: PrintAlign.LEFT
    } ?: PrintAlign.LEFT
}
