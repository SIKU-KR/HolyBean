package eloom.holybean.printer.polymorphism

import eloom.holybean.data.model.OrdersDetailItem
import org.junit.Assert.assertEquals
import org.junit.Test

class OrdersPrinterTest {

    @Test
    fun `reprint receipt starts with reprint label and ends with cut`() {
        val commands = OrdersPrinter().makeCommands(
            orderNum = 7,
            basketList = listOf(OrdersDetailItem(name = "라떼", count = 3, subtotal = 12000)),
        )

        val first = commands.first()
        assertEquals("text", first.type)
        assertEquals("영수증 재출력", first.content)
        assertEquals("right", first.align)
        assertEquals("cut", commands.last().type)

        val itemRow = commands.first { it.type == "row" }
        assertEquals("라떼", itemRow.columns!![0].content)
        assertEquals("3", itemRow.columns!![1].content)
    }
}
