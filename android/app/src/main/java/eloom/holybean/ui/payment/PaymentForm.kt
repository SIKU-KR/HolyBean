package eloom.holybean.ui.payment

import eloom.holybean.data.model.CartItem
import eloom.holybean.data.model.Order
import eloom.holybean.data.model.PaymentMethod

data class SplitLine(val label: String, val amount: Int)

data class PaymentSelection(
    val cupOption: String,
    val firstMethod: String,
    val ordererName: String,
    val splitEnabled: Boolean,
    val secondMethod: String?,
    val secondAmountText: String,
)

object PaymentForm {
    val methods = listOf("현금", "쿠폰", "계좌이체", "외상", "무료쿠폰", "무료제공")
    private val secondPool = listOf("계좌이체", "현금", "쿠폰", "무료쿠폰")
    private fun needsOrderer(m: String?) = m == "계좌이체" || m == "외상"

    fun secondCandidates(first: String): List<String> = secondPool.filter { it != first }

    /** 분할결제 ON 시 1번째(잔액)·2번째 수단의 금액 분배 라인. 입력이 유효하지 않으면 빈 리스트. */
    fun splitBreakdown(first: String, second: String?, total: Int, secondAmountText: String): List<SplitLine> {
        if (second == null) return emptyList()
        val secondAmount = secondAmountText.toIntOrNull() ?: return emptyList()
        val remainder = total - secondAmount
        if (secondAmount <= 0 || remainder <= 0) return emptyList()
        return listOf(
            SplitLine("$first (잔액)", remainder),
            SplitLine(second, secondAmount),
        )
    }

    fun build(sel: PaymentSelection, cart: List<CartItem>, total: Int, orderId: Int, date: String): Result<Order> {
        val first = sel.firstMethod
        if (!sel.splitEnabled) {
            if (needsOrderer(first) && sel.ordererName.isBlank())
                return Result.failure(IllegalStateException("주문자를 입력하세요"))
            return Result.success(
                order(date, orderId, credit(first), sel.ordererName, cart,
                    listOf(PaymentMethod(first, total)), total)
            )
        }
        val second = sel.secondMethod ?: return Result.failure(IllegalStateException("2번째 수단을 선택하세요"))
        val secondAmount = sel.secondAmountText.toIntOrNull()
            ?: return Result.failure(IllegalStateException("올바른 금액이 아닙니다"))
        if (secondAmount <= 0) return Result.failure(IllegalStateException("올바른 금액이 아닙니다"))
        val remainder = total - secondAmount
        if (remainder <= 0) return Result.failure(IllegalStateException("분할 금액을 확인하세요"))
        if ((needsOrderer(first) || needsOrderer(second)) && sel.ordererName.isBlank())
            return Result.failure(IllegalStateException("주문자를 입력하세요"))
        return Result.success(
            order(date, orderId, credit(first, second), sel.ordererName, cart,
                listOf(PaymentMethod(first, remainder), PaymentMethod(second, secondAmount)), total)
        )
    }

    private fun credit(vararg m: String) = if (m.any { it == "외상" }) 1 else 0

    private fun order(
        date: String, num: Int, credit: Int, name: String, items: List<CartItem>,
        methods: List<PaymentMethod>, total: Int,
    ) = Order(
        orderDate = date,
        orderNum = num,
        creditStatus = credit,
        customerName = name,
        orderItems = items,
        paymentMethods = methods,
        totalAmount = total,
    )
}
