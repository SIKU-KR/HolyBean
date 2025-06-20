package eloom.holybean.data.model

/**
 * 이걸로 마이그레이션
 */
data class Order(
    val orderDate: String,
    val orderNum: Int,
    val creditStatus: Int,
    val customerName: String,
    val orderItems: List<CartItem>,
    val paymentMethods: List<PaymentMethod>,
    val totalAmount: Int
)