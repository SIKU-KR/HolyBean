package eloom.holybean.printer.escpos

import eloom.holybean.printer.network.PrintCommandDto
import eloom.holybean.printer.network.PrintSegmentDto
import eloom.holybean.printer.network.PrintSize
import javax.inject.Inject

val RESET: List<Byte> = listOf(0x1B.toByte(), 0x40.toByte())
val CHARSET_EUC_KR: List<Byte> = listOf(0x1B.toByte(), 0x74.toByte(), 0x0D.toByte())
val ALIGN_LEFT: List<Byte> = listOf(0x1B.toByte(), 0x61.toByte(), 0x00.toByte())
val BOLD_ON: List<Byte> = listOf(0x1B.toByte(), 0x45.toByte(), 0x01.toByte())
val BOLD_OFF: List<Byte> = listOf(0x1B.toByte(), 0x45.toByte(), 0x00.toByte())
val UNDERLINE_ON: List<Byte> = listOf(0x1B.toByte(), 0x2D.toByte(), 0x02.toByte())
val UNDERLINE_OFF: List<Byte> = listOf(0x1B.toByte(), 0x2D.toByte(), 0x00.toByte())
val SIZE_BIG: List<Byte> = listOf(0x1D.toByte(), 0x21.toByte(), 0x11.toByte())
val SIZE_NORMAL: List<Byte> = listOf(0x1D.toByte(), 0x21.toByte(), 0x00.toByte())
val LF: Byte = 0x0A.toByte()
val CUT: List<Byte> = listOf(0x1D.toByte(), 0x56.toByte(), 0x01.toByte())

const val FEED_BEFORE_CUT_DOTS: Byte = 0xFF.toByte()

fun renderRun(run: EscposRun, out: MutableList<Byte>) {
    if (run.style.bold) {
        out.addAll(BOLD_ON)
    }
    if (run.style.underline) {
        out.addAll(UNDERLINE_ON)
    }
    if (run.style.size == PrintSize.BIG) {
        out.addAll(SIZE_BIG)
    }

    out.addAll(run.text.toByteArray(ESCPOS_CHARSET).toList())

    if (run.style.size == PrintSize.BIG) {
        out.addAll(SIZE_NORMAL)
    }
    if (run.style.underline) {
        out.addAll(UNDERLINE_OFF)
    }
    if (run.style.bold) {
        out.addAll(BOLD_OFF)
    }
}

fun renderDocument(commands: List<PrintCommandDto>): List<Byte> {
    val out = mutableListOf<Byte>()
    out.addAll(RESET)
    out.addAll(CHARSET_EUC_KR)
    out.addAll(ALIGN_LEFT)

    for (cmd in commands) {
        when (cmd.type) {
            "text" -> {
                val segment = PrintSegmentDto(
                    content = cmd.content ?: "",
                    align = cmd.align,
                    bold = cmd.bold,
                    underline = cmd.underline,
                    size = cmd.size,
                )
                for (run in layoutRow(listOf(segment))) {
                    renderRun(run, out)
                }
                out.add(LF)
            }
            "row" -> {
                for (run in layoutRow(cmd.columns ?: emptyList())) {
                    renderRun(run, out)
                }
                out.add(LF)
            }
            "divider" -> {
                val ch = cmd.ch?.firstOrNull() ?: '-'
                val line = ch.toString().repeat(LINE_WIDTH)
                renderRun(EscposRun(line), out)
                out.add(LF)
            }
            "blank" -> {
                out.add(LF)
            }
            "cut" -> {
                out.addAll(listOf(0x1B.toByte(), 0x4A.toByte(), FEED_BEFORE_CUT_DOTS))
                out.addAll(CUT)
            }
        }
    }

    return out
}

class EscposRenderer @Inject constructor() {
    fun render(commands: List<PrintCommandDto>): List<Byte> = renderDocument(commands)
}
