package eloom.holybean.printer.polymorphism

import eloom.holybean.data.model.CartItem
import eloom.holybean.data.model.Order
import eloom.holybean.data.model.PaymentMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomePrinterTest {

    private val printer = HomePrinter()

    private fun order() = Order(
        orderDate = "2026-05-23",
        orderNum = 42,
        creditStatus = 0,
        customerName = "홍길동",
        orderItems = listOf(
            CartItem(id = 1, name = "아메리카노", price = 4000, count = 2, total = 8000),
        ),
        paymentMethods = listOf(PaymentMethod("현금", 8000)),
        totalAmount = 8000,
    )

    @Test
    fun `customer receipt has order number title and cut`() {
        val commands = printer.receiptForCustomer(order())

        assertEquals("divider", commands.first().type)
        assertEquals("cut", commands.last().type)

        val title = commands.first { it.type == "text" }
        assertEquals("주문번호 : 42", title.content)
        assertEquals("center", title.align)
        assertEquals("big", title.size)
        assertEquals(true, title.underline)

        val itemRow = commands.first { it.type == "row" }
        assertEquals("아메리카노", itemRow.columns!![0].content)
        assertEquals(true, itemRow.columns!![0].bold)
        assertEquals("2", itemRow.columns!![1].content)
        assertEquals("right", itemRow.columns!![1].align)
    }

    @Test
    fun `pos receipt includes option, customer, total, date`() {
        val commands = printer.receiptForPOS(order(), "포장")

        val texts = commands.filter { it.type == "text" }.mapNotNull { it.content }
        assertTrue(texts.any { it == "포장" })
        assertTrue(texts.any { it == "주문자 : 홍길동" })
        assertTrue(texts.any { it == "합계 : 8000" })
        assertTrue(texts.any { it == "2026-05-23" })
        assertEquals("cut", commands.last().type)
    }
}
