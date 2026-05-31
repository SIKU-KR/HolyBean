package eloom.holybean.printer.polymorphism

import eloom.holybean.data.model.PrinterDTO
import eloom.holybean.data.model.ReportDetailItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportPrinterTest {

    @Test
    fun `report has date title, totals and three-column detail rows`() {
        val dto = PrinterDTO(
            startdate = "2026-05-01",
            enddate = "2026-05-23",
            reportData = mapOf("총합" to 100000, "현금" to 60000),
            reportDetailItem = listOf(ReportDetailItem(name = "아메리카노", quantity = 10, subtotal = 40000)),
        )
        val commands = ReportPrinter().makeCommands(dto)

        val texts = commands.filter { it.type == "text" }.mapNotNull { it.content }
        assertTrue(texts.any { it == "2026-05-01~" })
        assertTrue(texts.any { it == "총 판매금액 : 100000" })

        val headerRow = commands.first { it.type == "row" }
        assertEquals(3, headerRow.columns!!.size)
        assertEquals("이름", headerRow.columns!![0].content)
        assertEquals("right", headerRow.columns!![1].align)

        val detailRow = commands.last { it.type == "row" }
        assertEquals("아메리카노", detailRow.columns!![0].content)
        assertEquals("10", detailRow.columns!![1].content)
        assertEquals("40000", detailRow.columns!![2].content)
        assertEquals("cut", commands.last().type)
    }
}
