package eloom.holybean.data.model

data class MenuItem(
    val id: Int,
    var name: String,
    var price: Int,
    var order: Int,      // Firestore 필드명 placement
    var inuse: Boolean
)
