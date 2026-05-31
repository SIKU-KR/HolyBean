package eloom.holybean.ui.payment

import eloom.holybean.data.model.CartItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentFormTest {
    private val cart = listOf(CartItem(1001, "아메리카노", 3500, 2, 7000))
    private fun base() = PaymentSelection(
        cupOption = "일회용컵", firstMethod = "현금", ordererName = "",
        splitEnabled = false, secondMethod = null, secondAmountText = "",
    )

    @Test fun `single cash builds one payment method`() {
        val r = PaymentForm.build(base(), cart, total = 7000, orderId = 5, date = "2026-05-23")
        assertTrue(r.isSuccess)
        val order = r.getOrThrow()
        assertEquals(1, order.paymentMethods.size)
        assertEquals("현금", order.paymentMethods[0].type)
        assertEquals(7000, order.paymentMethods[0].amount)
        assertEquals(0, order.creditStatus)
    }

    @Test fun `account transfer requires orderer name`() {
        val r = PaymentForm.build(base().copy(firstMethod = "계좌이체"), cart, 7000, 5, "2026-05-23")
        assertTrue(r.isFailure)
    }

    @Test fun `credit sets creditStatus 1`() {
        val r = PaymentForm.build(base().copy(firstMethod = "외상", ordererName = "홍길동"), cart, 7000, 5, "2026-05-23")
        assertEquals(1, r.getOrThrow().creditStatus)
    }

    @Test fun `split divides remainder to first method`() {
        val sel = base().copy(splitEnabled = true, secondMethod = "계좌이체", secondAmountText = "2000", ordererName = "홍길동")
        val order = PaymentForm.build(sel, cart, 7000, 5, "2026-05-23").getOrThrow()
        assertEquals(2, order.paymentMethods.size)
        assertEquals(5000, order.paymentMethods[0].amount) // 현금 잔액
        assertEquals(2000, order.paymentMethods[1].amount) // 계좌이체
    }

    @Test fun `split remainder must be positive`() {
        val sel = base().copy(splitEnabled = true, secondMethod = "쿠폰", secondAmountText = "7000")
        assertTrue(PaymentForm.build(sel, cart, 7000, 5, "2026-05-23").isFailure)
    }

    @Test fun `split second amount must be positive`() {
        val sel = base().copy(splitEnabled = true, secondMethod = "쿠폰", secondAmountText = "0")
        assertTrue(PaymentForm.build(sel, cart, 7000, 5, "2026-05-23").isFailure)
    }

    @Test fun `second method candidates exclude chosen first`() {
        assertEquals(listOf("계좌이체", "쿠폰", "무료쿠폰"), PaymentForm.secondCandidates("현금"))
        assertEquals(listOf("현금", "쿠폰", "무료쿠폰"), PaymentForm.secondCandidates("계좌이체"))
    }

    @Test fun `split breakdown returns first-remainder and second-amount lines`() {
        val lines = PaymentForm.splitBreakdown(first = "현금", second = "계좌이체", total = 15000, secondAmountText = "5000")
        assertEquals(2, lines.size)
        assertEquals("현금 (잔액)", lines[0].label)
        assertEquals(10000, lines[0].amount)
        assertEquals("계좌이체", lines[1].label)
        assertEquals(5000, lines[1].amount)
    }

    @Test fun `split breakdown empty when amount invalid or non-positive remainder`() {
        assertTrue(PaymentForm.splitBreakdown("현금", "계좌이체", 15000, "").isEmpty())
        assertTrue(PaymentForm.splitBreakdown("현금", "계좌이체", 15000, "0").isEmpty())
        assertTrue(PaymentForm.splitBreakdown("현금", "계좌이체", 15000, "15000").isEmpty())
        assertTrue(PaymentForm.splitBreakdown("현금", null, 15000, "5000").isEmpty())
    }
}
