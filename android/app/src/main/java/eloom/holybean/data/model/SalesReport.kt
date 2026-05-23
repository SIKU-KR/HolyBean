package eloom.holybean.data.model

data class SalesReport(
    val menuSales: List<ReportDetailItem>,   // 수량 내림차순
    val paymentSales: Map<String, Int>       // "총합" 키 포함
)
