package eloom.holybean.data.repository

import eloom.holybean.data.model.MenuItem
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@ExperimentalCoroutinesApi
class MenuRepositoryTest {

    private lateinit var menuRepository: MenuRepository
    private val menuDao: MenuDao = mockk()

    @Before
    fun setUp() {
        menuRepository = MenuRepository(menuDao)
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun `getMenuList returns flow of menu items from dao`() = runTest {
        // Given
        val menuList = listOf(MenuItem(1, "Coffee", 100, 1, true))
        every { menuDao.getMenuList() } returns flowOf(menuList)

        // When
        val result = menuRepository.getMenuList().first()

        // Then
        assertEquals(menuList, result)
        verify { menuDao.getMenuList() }
    }

    @Test
    fun `overwriteMenuList calls dao's overwriteMenuList`() = runTest {
        // Given
        val menuList = listOf(MenuItem(1, "Coffee", 100, 1, true))
        coEvery { menuDao.overwriteMenuList(menuList) } just runs

        // When
        menuRepository.overwriteMenuList(menuList)

        // Then
        coVerify { menuDao.overwriteMenuList(menuList) }
    }

    @Test
    fun `saveMenuOrders calls dao's saveMenuOrders`() = runTest {
        // Given
        val menuList = listOf(MenuItem(1, "Coffee", 100, 1, true))
        coEvery { menuDao.saveMenuOrders(menuList) } just runs

        // When
        menuRepository.saveMenuOrders(menuList)

        // Then
        coVerify { menuDao.saveMenuOrders(menuList) }
    }

    @Test
    fun `updateSpecificMenu calls dao's updateMenu`() = runTest {
        // Given
        val menuItem = MenuItem(1, "Coffee", 100, 1, true)
        coEvery { menuDao.updateMenu(menuItem) } just runs

        // When
        menuRepository.updateSpecificMenu(menuItem)

        // Then
        coVerify { menuDao.updateMenu(menuItem) }
    }

    @Test
    fun `getNextAvailableIdForCategory calculates next available id`() = runTest {
        // Given
        val category = 1
        val startRange = 1001
        val endRange = 1999
        val ids = listOf(1001, 1002, 1004)
        coEvery { menuDao.getIdsInCategory(startRange, endRange) } returns ids

        // When
        val result = menuRepository.getNextAvailableIdForCategory(category)

        // Then
        assertEquals(1003, result)
    }

    @Test
    fun `getNextAvailableIdForCategory returns startRange if no ids exist`() = runTest {
        // Given
        val category = 1
        val startRange = 1001
        val endRange = 1999
        coEvery { menuDao.getIdsInCategory(startRange, endRange) } returns emptyList()

        // When
        val result = menuRepository.getNextAvailableIdForCategory(category)

        // Then
        assertEquals(startRange, result)
    }

    @Test
    fun `getNextAvailablePlacementForCategory calculates next available placement`() = runTest {
        // Given
        val category = 1
        val startRange = 1001
        val endRange = 1999
        val placements = listOf(1001, 1003, 1004)
        coEvery { menuDao.getPlacementsInCategory(startRange, endRange) } returns placements

        // When
        val result = menuRepository.getNextAvailablePlacementForCategory(category)

        // Then
        assertEquals(1002, result)
    }

    @Test
    fun `addMenu calls dao's addMenu`() = runTest {
        // Given
        val menuItem = MenuItem(1, "Coffee", 100, 1, true)
        coEvery { menuDao.addMenu(menuItem) } just runs

        // When
        menuRepository.addMenu(menuItem)

        // Then
        coVerify { menuDao.addMenu(menuItem) }
    }

    @Test
    fun `isValidMenuName returns true for new name`() = runTest {
        // Given
        val newName = "New Coffee"
        coEvery { menuDao.getCountByName(newName) } returns 0

        // When
        val result = menuRepository.isValidMenuName(newName)

        // Then
        assertTrue(result)
    }

    @Test
    fun `isValidMenuName returns false for existing name`() = runTest {
        // Given
        val existingName = "Coffee"
        coEvery { menuDao.getCountByName(existingName) } returns 1

        // When
        val result = menuRepository.isValidMenuName(existingName)

        // Then
        assertFalse(result)
    }

    @Test
    fun `getMenuListSync returns list of menu items from dao`() = runTest {
        // Given
        val menuList = listOf(MenuItem(1, "Coffee", 100, 1, true))
        every { menuDao.getMenuList() } returns flowOf(menuList)

        // When
        val result = menuRepository.getMenuListSync()

        // Then
        assertEquals(menuList, result)
        verify { menuDao.getMenuList() }
    }
}
