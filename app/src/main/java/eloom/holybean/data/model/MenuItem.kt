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
    val name: String,

    @ColumnInfo(name = "price")
    val price: Int,

    @ColumnInfo(name = "placement")
    val order: Int,

    @ColumnInfo(name = "inuse")
    val inuse: Boolean
)