package eloom.holybean.printer.escpos

import eloom.holybean.printer.network.PrintCommandDto
import eloom.holybean.printer.network.PrintSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EscposRendererTest {

    @Test
    fun `renders plain ascii`() {
        val out = mutableListOf<Byte>()
        renderRun(EscposRun("ab"), out)
        assertEquals(listOf<Byte>(97, 98), out)
    }

    @Test
    fun `wraps bold underline big in correct order`() {
        val out = mutableListOf<Byte>()
        renderRun(
            EscposRun(
                text = "X",
                style = EscposStyle(
                    bold = true,
                    underline = true,
                    size = PrintSize.BIG,
                ),
            ),
            out,
        )
        val expected = BOLD_ON + UNDERLINE_ON + SIZE_BIG + listOf<Byte>(88) + SIZE_NORMAL + UNDERLINE_OFF + BOLD_OFF
        assertEquals(expected, out)
    }

    @Test
    fun `encodes hangul as euc kr`() {
        val out = mutableListOf<Byte>()
        renderRun(EscposRun("가"), out)
        assertEquals(listOf<Byte>(0xB0.toByte(), 0xA1.toByte()), out)
    }

    @Test
    fun `rendered bytes use shared charset and agree with layout width`() {
        val text = "아메리카노"
        val out = mutableListOf<Byte>()
        renderRun(EscposRun(text), out)
        assertEquals(text.toByteArray(ESCPOS_CHARSET).toList(), out)
        assertEquals(displayWidth(text, PrintSize.NORMAL), out.size)
    }

    @Test
    fun `document starts with reset charset align`() {
        val bytes = renderDocument(listOf(PrintCommandDto(type = "blank")))
        val prefix = RESET + CHARSET_EUC_KR + ALIGN_LEFT
        assertEquals(prefix, bytes.take(prefix.size))
        assertEquals(LF, bytes.last())
    }

    @Test
    fun `divider fills line width`() {
        val bytes = renderDocument(listOf(PrintCommandDto(type = "divider", ch = "=")))
        val dashes = "=".repeat(LINE_WIDTH)
        val needle = dashes.toByteArray(ESCPOS_CHARSET).toList()
        assertTrue(bytes.windowed(needle.size).any { it == needle })
    }

    @Test
    fun `cut emits feed then cut at end`() {
        val bytes = renderDocument(listOf(PrintCommandDto(type = "cut")))
        val tail = listOf<Byte>(0x1B, 0x4A, FEED_BEFORE_CUT_DOTS) + CUT
        assertEquals(tail, bytes.takeLast(tail.size))
    }

    @Test
    fun `text line ends with lf`() {
        val bytes = renderDocument(
            listOf(
                PrintCommandDto(
                    type = "text",
                    content = "hi",
                    align = "left",
                    bold = false,
                    underline = false,
                    size = "normal",
                )
            )
        )
        assertEquals(LF, bytes.last())
    }
}
