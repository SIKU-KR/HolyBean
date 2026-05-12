package eloom.holybean.network.dto

data class ResponseOrderItem(
    val unitPrice: Int,
    val itemName: String,
    val quantity: Int,
    val subtotal: Int,
)
