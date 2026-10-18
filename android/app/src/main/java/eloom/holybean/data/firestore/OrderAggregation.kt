package eloom.holybean.data.firestore

import eloom.holybean.data.model.CartItem
import eloom.holybean.data.model.Order
import eloom.holybean.data.model.PaymentMethod

object OrderAggregation {

    data class MenuSaleDelta(val quantity: Int, val sales: Int)
    data class RollupDelta(
        val menuSales: Map<String, MenuSaleDelta>,
        val paymentSales: Map<String, Int>,
        val total: Int
    )

    fun orderMethodLabel(payments: List<PaymentMethod>): String =
        if (payments.isEmpty()) "Unknown" else payments.joinToString("+") { it.type }

    fun orderDoc(order: Order): Map<String, Any> = mapOf(
        "orderDate" to order.orderDate,
        "orderNum" to order.orderNum,
        "totalAmount" to order.totalAmount,
        "customerName" to order.customerName,
        "creditStatus" to order.creditStatus,
        "items" to order.orderItems.map {
            mapOf("name" to it.name, "quantity" to it.count, "subtotal" to it.total, "unitPrice" to it.price)
        },
        "payments" to order.paymentMethods.map {
            mapOf("method" to it.type, "amount" to it.amount)
        }
    )

    fun daySummaryEntry(order: Order): Map<String, Any> = mapOf(
        "customerName" to order.customerName,
        "totalAmount" to order.totalAmount,
        "orderMethod" to orderMethodLabel(order.paymentMethods),
        "creditStatus" to order.creditStatus
    )

    fun rollupDelta(order: Order): RollupDelta = rollupDelta(order.orderItems, order.paymentMethods)

    fun rollupDelta(items: List<CartItem>, payments: List<PaymentMethod>): RollupDelta {
        val menu = HashMap<String, MenuSaleDelta>()
        for (it in items) {
            val cur = menu[it.name]
            menu[it.name] = if (cur == null) MenuSaleDelta(it.count, it.total)
                else MenuSaleDelta(cur.quantity + it.count, cur.sales + it.total)
        }
        val pay = HashMap<String, Int>()
        var total = 0
        for (p in payments) {
            pay[p.type] = (pay[p.type] ?: 0) + p.amount
            total += p.amount
        }
        return RollupDelta(menu, pay, total)
    }
}
