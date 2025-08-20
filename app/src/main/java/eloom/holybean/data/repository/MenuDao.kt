package eloom.holybean.data.repository

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import eloom.holybean.data.model.MenuItem
import kotlinx.coroutines.flow.Flow

@Dao
interface MenuDao {
    @Query("SELECT * FROM menu ORDER BY id ASC")
    fun getMenuList(): Flow<List<MenuItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(menuList: List<MenuItem>)

    @Query("DELETE FROM menu")
    suspend fun deleteAll()

    @Transaction
    suspend fun overwriteMenuList(menuList: List<MenuItem>) {
        deleteAll()
        insertAll(menuList)
    }

    @Update
    suspend fun updateMenu(item: MenuItem)

    @Transaction
    suspend fun saveMenuOrders(items: List<MenuItem>) {
        items.forEach { item ->
            updatePlacement(item.id, item.order)
        }
    }

    @Query("UPDATE menu SET placement = :newPlacement WHERE id = :id")
    suspend fun updatePlacement(id: Int, newPlacement: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addMenu(item: MenuItem)

    @Query("SELECT COUNT(*) FROM menu WHERE name = :name")
    suspend fun getCountByName(name: String): Int

    @Query("SELECT id FROM menu WHERE id BETWEEN :startRange AND :endRange ORDER BY id ASC")
    suspend fun getIdsInCategory(startRange: Int, endRange: Int): List<Int>

    @Query("SELECT placement FROM menu WHERE placement BETWEEN :startRange AND :endRange ORDER BY placement ASC")
    suspend fun getPlacementsInCategory(startRange: Int, endRange: Int): List<Int>

}