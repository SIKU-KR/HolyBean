package eloom.holybean.network.dto

data class ResponseOrderDetail(
    val orderDate: String,
    val customerName: String,
    val totalAmount: Int,
    val orderItems: List<ResponseOrderItem>,
    val paymentMethods: List<ResponsePaymentMethod>,
    val creditStatus: Int,
    val orderNum: Int,
)
