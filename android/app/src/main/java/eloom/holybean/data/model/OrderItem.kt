package eloom.holybean.data.model

data class OrderItem(
    val orderId: Int,
    val totalAmount: Int,
    val method: String,
    val orderer: String
)