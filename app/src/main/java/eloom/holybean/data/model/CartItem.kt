package eloom.holybean.data.model

data class CartItem(
    val id: Int,
    val name: String,
    val price: Int,
    var count: Int,
    var total: Int
)