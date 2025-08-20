package eloom.holybean.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "menu")
data class MenuItem(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Int,

    @ColumnInfo(name = "name")
    var name: String,

    @ColumnInfo(name = "price")
    var price: Int,

    @ColumnInfo(name = "placement")
    var order: Int,

    @ColumnInfo(name = "inuse")
    var inuse: Boolean
)
