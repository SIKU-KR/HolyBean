package eloom.holybean.data.firestore

import eloom.holybean.data.model.CartItem
import eloom.holybean.data.model.Order
import eloom.holybean.data.model.PaymentMethod
import org.junit.Assert.assertEquals
import org.junit.Test

class OrderAggregationTest {

    private fun order(
        creditStatus: Int = 0,
        payments: List<PaymentMethod> = listOf(PaymentMethod("현금", 9000)),
        items: List<CartItem> = listOf(CartItem(1001, "아메리카노", 4500, 2, 9000))
    ) = Order("2026-05-23", 3, creditStatus, "홍길동", items, payments, 9000)

    @Test
    fun `orderMethod는 결제수단을 플러스로 결합`() {
        assertEquals("현금+쿠폰", OrderAggregation.orderMethodLabel(
            listOf(PaymentMethod("현금", 5000), PaymentMethod("쿠폰", 4000))
        ))
    }

    @Test
    fun `결제수단이 없으면 Unknown`() {
        assertEquals("Unknown", OrderAggregation.orderMethodLabel(emptyList()))
    }

    @Test
    fun `daySummary 항목은 customerName totalAmount orderMethod creditStatus`() {
        val entry = OrderAggregation.daySummaryEntry(order())
        assertEquals("홍길동", entry["customerName"])
        assertEquals(9000, entry["totalAmount"])
        assertEquals("현금", entry["orderMethod"])
        assertEquals(0, entry["creditStatus"])
    }

    @Test
    fun `rollupDelta는 메뉴 수량과 매출 결제수단 총합을 계산`() {
        val delta = OrderAggregation.rollupDelta(order())
        assertEquals(2, delta.menuSales["아메리카노"]!!.quantity)
        assertEquals(9000, delta.menuSales["아메리카노"]!!.sales)
        assertEquals(9000, delta.paymentSales["현금"])
        assertEquals(9000, delta.total)
    }

    @Test
    fun `orderDoc 매핑은 items와 payments를 문서 형태로 변환`() {
        val doc = OrderAggregation.orderDoc(order())
        @Suppress("UNCHECKED_CAST")
        val item = (doc["items"] as List<Map<String, Any>>).first()
        assertEquals("아메리카노", item["name"])
        assertEquals(2, item["quantity"])
        assertEquals(9000, item["subtotal"])
        assertEquals(4500, item["unitPrice"])
        @Suppress("UNCHECKED_CAST")
        val pay = (doc["payments"] as List<Map<String, Any>>).first()
        assertEquals("현금", pay["method"])
        assertEquals(9000, pay["amount"])
        assertEquals(3, doc["orderNum"])
        assertEquals(0, doc["creditStatus"])
    }
}
