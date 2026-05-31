package eloom.holybean.data.firestore

import eloom.holybean.data.firestore.ReportAggregation.DailyRollup
import eloom.holybean.data.firestore.ReportAggregation.MenuSale
import org.junit.Assert.assertEquals
import org.junit.Test

class ReportAggregationTest {

    @Test
    fun `여러 날 롤업을 메뉴별로 합산하고 수량 내림차순 정렬`() {
        val day1 = DailyRollup(
            menuSales = mapOf("아메리카노" to MenuSale(2, 9000), "라떼" to MenuSale(1, 5000)),
            paymentSales = mapOf("현금" to 14000)
        )
        val day2 = DailyRollup(
            menuSales = mapOf("아메리카노" to MenuSale(3, 13500)),
            paymentSales = mapOf("쿠폰" to 13500)
        )

        val report = ReportAggregation.combine(listOf(day1, day2))

        assertEquals(
            listOf(
                eloom.holybean.data.model.ReportDetailItem("아메리카노", 5, 22500),
                eloom.holybean.data.model.ReportDetailItem("라떼", 1, 5000)
            ),
            report.menuSales
        )
    }

    @Test
    fun `결제수단을 합산하고 총합 키를 추가`() {
        val report = ReportAggregation.combine(
            listOf(DailyRollup(emptyMap(), mapOf("현금" to 14000, "쿠폰" to 13500)))
        )
        assertEquals(14000, report.paymentSales["현금"])
        assertEquals(13500, report.paymentSales["쿠폰"])
        assertEquals(27500, report.paymentSales["총합"])
    }

    @Test
    fun `빈 입력은 빈 메뉴와 총합 0`() {
        val report = ReportAggregation.combine(emptyList())
        assertEquals(emptyList<Any>(), report.menuSales)
        assertEquals(0, report.paymentSales["총합"])
    }
}
