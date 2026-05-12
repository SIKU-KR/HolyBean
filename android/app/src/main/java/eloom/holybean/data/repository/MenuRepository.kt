package eloom.holybean.data.repository

import eloom.holybean.data.model.MenuItem
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MenuRepository @Inject constructor(private val menuDao: MenuDao) {

    fun getMenuList() = menuDao.getMenuList()

    suspend fun overwriteMenuList(menuList: List<MenuItem>) {
        menuDao.overwriteMenuList(menuList)
    }

    suspend fun saveMenuOrders(items: List<MenuItem>) {
        menuDao.saveMenuOrders(items)
    }

    suspend fun updateSpecificMenu(item: MenuItem) {
        menuDao.updateMenu(item)
    }

    suspend fun getNextAvailableIdForCategory(category: Int): Int {
        val startRange = category * 1000 + 1
        val endRange = (category + 1) * 1000 - 1
        val ids = menuDao.getIdsInCategory(startRange, endRange)
        var nextId = startRange
        for (id in ids) {
            if (id > nextId) {
                break
            }
            nextId = id + 1
        }
        return if (nextId <= endRange) nextId else -1
    }

    suspend fun getNextAvailablePlacementForCategory(category: Int): Int {
        val startRange = category * 1000 + 1
        val endRange = (category + 1) * 1000 - 1
        val placements = menuDao.getPlacementsInCategory(startRange, endRange)
        var nextPlacement = startRange
        for (placement in placements) {
            if (placement > nextPlacement) {
                break
            }
            nextPlacement = placement + 1
        }
        return if (nextPlacement <= endRange) nextPlacement else -1
    }

    suspend fun addMenu(item: MenuItem) {
        menuDao.addMenu(item)
    }

    suspend fun isValidMenuName(newName: String): Boolean {
        return menuDao.getCountByName(newName) == 0
    }

    suspend fun getMenuListSync(): List<MenuItem> {
        return menuDao.getMenuList().first()
    }
}