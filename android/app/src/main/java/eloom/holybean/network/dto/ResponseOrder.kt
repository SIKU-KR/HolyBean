package eloom.holybean.network.dto

data class ResponseOrder(
    val customerName: String = "",
    val totalAmount: Int,
    val orderMethod: String,
    val orderNum: Int,
)
