package eloom.holybean.printer.escpos

import eloom.holybean.printer.network.PrintSegmentDto
import eloom.holybean.printer.network.PrintSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EscposLayoutTest {

    @Test
    fun `ascii is one cell each`() {
        assertEquals(3, displayWidth("abc", PrintSize.NORMAL))
    }

    @Test
    fun `hangul is two cells each`() {
        assertEquals(4, displayWidth("가나", PrintSize.NORMAL))
    }

    @Test
    fun `big size doubles width`() {
        assertEquals(4, displayWidth("ab", PrintSize.BIG))
        assertEquals(4, displayWidth("가", PrintSize.BIG))
    }

    private fun seg(content: String, align: String = "left") = PrintSegmentDto(
        content = content,
        align = align,
        bold = false,
        underline = false,
        size = "normal",
    )

    private fun flatten(runs: List<EscposRun>): String =
        runs.joinToString("") { it.text }

    @Test
    fun `single left column pads right to full width`() {
        val runs = layoutRow(listOf(seg("ab", "left")))
        val line = flatten(runs)
        assertEquals(LINE_WIDTH, line.length)
        assertTrue(line.startsWith("ab"))
        assertEquals(" ".repeat(LINE_WIDTH - 2), line.substring(2))
    }

    @Test
    fun `single right column pads left`() {
        val runs = layoutRow(listOf(seg("ab", "right")))
        val line = flatten(runs)
        assertEquals(LINE_WIDTH, line.length)
        assertTrue(line.endsWith("ab"))
    }

    @Test
    fun `single center column balances padding`() {
        val runs = layoutRow(listOf(seg("ab", "center")))
        val line = flatten(runs)
        assertEquals(LINE_WIDTH, line.length)
        val left = (LINE_WIDTH - 2) / 2
        assertEquals(" ".repeat(left), line.substring(0, left))
        assertEquals("ab", line.substring(left, left + 2))
    }

    @Test
    fun `two columns left and right fill line`() {
        val runs = layoutRow(listOf(seg("name", "left"), seg("2", "right")))
        val line = flatten(runs)
        assertEquals(LINE_WIDTH, line.length)
        assertTrue(line.startsWith("name"))
        assertTrue(line.endsWith("2"))
    }

    @Test
    fun `three columns distribute forgotten chars`() {
        val runs = layoutRow(
            listOf(
                seg("a", "left"),
                seg("b", "right"),
                seg("c", "right"),
            )
        )
        val line = flatten(runs)
        assertEquals(LINE_WIDTH, line.length)
    }

    @Test
    fun `padding runs are unstyled`() {
        val runs = layoutRow(
            listOf(
                PrintSegmentDto(
                    content = "x",
                    align = "center",
                    bold = true,
                    underline = true,
                    size = "big",
                )
            )
        )
        val contentRun = runs.first { it.text == "x" }
        assertEquals(PrintSize.BIG, contentRun.style.size)
        assertTrue(contentRun.style.bold && contentRun.style.underline)
        for (run in runs.filter { it.text.trim().isEmpty() }) {
            assertEquals(EscposStyle(), run.style)
        }
    }
}
