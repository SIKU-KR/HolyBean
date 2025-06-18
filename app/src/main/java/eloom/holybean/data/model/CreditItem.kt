package eloom.holybean.data.model

data class CreditItem (
    val orderId: Int,
    val totalAmount: Int,
    val date: String,
    val orderer: String
)