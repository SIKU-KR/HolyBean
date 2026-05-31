package eloom.holybean.data.firestore

import eloom.holybean.data.model.ReportDetailItem
import eloom.holybean.data.model.SalesReport

object ReportAggregation {

    data class MenuSale(val quantity: Int, val sales: Int)
    data class DailyRollup(
        val menuSales: Map<String, MenuSale>,
        val paymentSales: Map<String, Int>
    )

    fun combine(rollups: List<DailyRollup>): SalesReport {
        val menuAcc = HashMap<String, MenuSale>()
        val payAcc = HashMap<String, Int>()
        var total = 0

        for (r in rollups) {
            for ((name, sale) in r.menuSales) {
                val cur = menuAcc[name]
                menuAcc[name] = if (cur == null) sale
                    else MenuSale(cur.quantity + sale.quantity, cur.sales + sale.sales)
            }
            for ((method, amount) in r.paymentSales) {
                payAcc[method] = (payAcc[method] ?: 0) + amount
                total += amount
            }
        }

        val menuList = menuAcc.entries
            .sortedByDescending { it.value.quantity }
            .map { ReportDetailItem(it.key, it.value.quantity, it.value.sales) }

        payAcc["총합"] = total
        return SalesReport(menuList, payAcc)
    }
}
