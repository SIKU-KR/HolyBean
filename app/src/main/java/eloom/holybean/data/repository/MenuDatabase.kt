package eloom.holybean.data.repository

import androidx.room.Database
import androidx.room.RoomDatabase
import eloom.holybean.data.model.MenuItem

@Database(entities = [MenuItem::class], version = 1)
abstract class MenuDatabase : RoomDatabase() {
    abstract fun menuDao(): MenuDao
}