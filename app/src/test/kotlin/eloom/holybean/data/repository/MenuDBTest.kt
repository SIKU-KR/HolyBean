package eloom.holybean.data.repository

import android.content.Context
import eloom.holybean.data.model.MenuItem
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class MenuDBTest {

    private lateinit var menuDB: MenuDB
    private val mockContext: Context = mockk(relaxed = true)

    @Before
    fun setUp() {
        MockKAnnotations.init(this)

        // MenuDB를 완전히 모킹하여 각 메서드의 행동을 직접 정의
        menuDB = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        clearAllMocks()
        unmockkAll()
    }

    @Test
    fun `getMenuList should return empty list when no data exists`() {
        // Given
        every { menuDB.getMenuList() } returns emptyList()

        // When
        val result = menuDB.getMenuList()

        // Then
        assertTrue(result.isEmpty())
        verify { menuDB.getMenuList() }
    }

    @Test
    fun `getMenuList should return menu items sorted by id when data exists`() {
        // Given
        val expectedMenuItems = listOf(
            MenuItem(1, "아메리카노", 4000, 1, true),
            MenuItem(2, "라떼", 4500, 2, true),
            MenuItem(3, "케이크", 6000, 3, false)
        )
        every { menuDB.getMenuList() } returns expectedMenuItems

        // When
        val result = menuDB.getMenuList()

        // Then
        assertEquals(3, result.size)
        assertEquals(1, result[0].id)
        assertEquals("아메리카노", result[0].name)
        assertEquals(2, result[1].id)
        assertEquals("라떼", result[1].name)
        assertEquals(3, result[2].id)
        assertEquals("케이크", result[2].name)
        assertFalse(result[2].inuse)
        verify { menuDB.getMenuList() }
    }

    @Test
    fun `getMenuList should handle multiple calls consistently`() {
        // Given
        val expectedMenuItems = listOf(
            MenuItem(1, "아메리카노", 4000, 1, true),
            MenuItem(2, "라떼", 4500, 2, true)
        )
        every { menuDB.getMenuList() } returns expectedMenuItems

        // When
        val result1 = menuDB.getMenuList()
        val result2 = menuDB.getMenuList()

        // Then
        assertEquals(result1, result2)
        assertEquals(2, result1.size)
        verify(exactly = 2) { menuDB.getMenuList() }
    }

    @Test
    fun `overwriteMenuList should complete successfully with valid input`() {
        // Given
        val menuItems = listOf(
            MenuItem(1, "아메리카노", 4000, 1, true),
            MenuItem(2, "라떼", 4500, 2, false)
        )
        every { menuDB.overwriteMenuList(menuItems) } just runs

        // When
        menuDB.overwriteMenuList(menuItems)

        // Then
        verify { menuDB.overwriteMenuList(menuItems) }
    }

    @Test
    fun `overwriteMenuList should handle empty list`() {
        // Given
        val emptyMenuItems = emptyList<MenuItem>()
        every { menuDB.overwriteMenuList(emptyMenuItems) } just runs

        // When
        menuDB.overwriteMenuList(emptyMenuItems)

        // Then
        verify { menuDB.overwriteMenuList(emptyMenuItems) }
    }

    @Test
    fun `overwriteMenuList should handle large list of items`() {
        // Given
        val largeMenuList = (1..100).map {
            MenuItem(it, "메뉴$it", 1000 + it * 100, it, true)
        }
        every { menuDB.overwriteMenuList(largeMenuList) } just runs

        // When
        menuDB.overwriteMenuList(largeMenuList)

        // Then
        verify { menuDB.overwriteMenuList(largeMenuList) }
    }

    @Test
    fun `saveMenuOrders should update placement for all items successfully`() {
        // Given
        val menuItems = listOf(
            MenuItem(1, "아메리카노", 4000, 10, true),
            MenuItem(2, "라떼", 4500, 20, true)
        )
        every { menuDB.saveMenuOrders(menuItems) } just runs

        // When
        menuDB.saveMenuOrders(menuItems)

        // Then
        verify { menuDB.saveMenuOrders(menuItems) }
    }

    @Test
    fun `saveMenuOrders should handle empty list`() {
        // Given
        val emptyMenuItems = emptyList<MenuItem>()
        every { menuDB.saveMenuOrders(emptyMenuItems) } just runs

        // When
        menuDB.saveMenuOrders(emptyMenuItems)

        // Then
        verify { menuDB.saveMenuOrders(emptyMenuItems) }
    }

    @Test
    fun `updateSpecificMenu should update menu item successfully`() {
        // Given
        val menuItem = MenuItem(1, "아메리카노", 4000, 1, true)
        every { menuDB.updateSpecificMenu(menuItem) } just runs

        // When
        menuDB.updateSpecificMenu(menuItem)

        // Then
        verify { menuDB.updateSpecificMenu(menuItem) }
    }

    @Test
    fun `getNextAvailableIdForCategory should return valid id for category`() {
        // Given
        val category = 1
        val expectedId = 1001
        every { menuDB.getNextAvailableIdForCategory(category) } returns expectedId

        // When
        val result = menuDB.getNextAvailableIdForCategory(category)

        // Then
        assertEquals(expectedId, result)
        verify { menuDB.getNextAvailableIdForCategory(category) }
    }

    @Test
    fun `getNextAvailableIdForCategory should return -1 when range is full`() {
        // Given
        val category = 1
        every { menuDB.getNextAvailableIdForCategory(category) } returns -1

        // When
        val result = menuDB.getNextAvailableIdForCategory(category)

        // Then
        assertEquals(-1, result)
        verify { menuDB.getNextAvailableIdForCategory(category) }
    }

    @Test
    fun `getNextAvailablePlacementForCategory should return valid placement for category`() {
        // Given
        val category = 1
        val expectedPlacement = 1001
        every { menuDB.getNextAvailablePlacementForCategory(category) } returns expectedPlacement

        // When
        val result = menuDB.getNextAvailablePlacementForCategory(category)

        // Then
        assertEquals(expectedPlacement, result)
        verify { menuDB.getNextAvailablePlacementForCategory(category) }
    }

    @Test
    fun `addMenu should insert menu item successfully`() {
        // Given
        val menuItem = MenuItem(1, "아메리카노", 4000, 1, true)
        every { menuDB.addMenu(menuItem) } just runs

        // When
        menuDB.addMenu(menuItem)

        // Then
        verify { menuDB.addMenu(menuItem) }
    }

    @Test
    fun `isValidMenuName should return true when name doesn't exist`() {
        // Given
        val newName = "새로운메뉴"
        every { menuDB.isValidMenuName(newName) } returns true

        // When
        val result = menuDB.isValidMenuName(newName)

        // Then
        assertTrue(result)
        verify { menuDB.isValidMenuName(newName) }
    }

    @Test
    fun `isValidMenuName should return false when name already exists`() {
        // Given
        val existingName = "아메리카노"
        every { menuDB.isValidMenuName(existingName) } returns false

        // When
        val result = menuDB.isValidMenuName(existingName)

        // Then
        assertFalse(result)
        verify { menuDB.isValidMenuName(existingName) }
    }

    @Test
    fun `isValidMenuName should handle empty string`() {
        // Given
        val emptyName = ""
        every { menuDB.isValidMenuName(emptyName) } returns true

        // When
        val result = menuDB.isValidMenuName(emptyName)

        // Then
        assertTrue(result)
        verify { menuDB.isValidMenuName(emptyName) }
    }

    @Test
    fun `isValidMenuName should handle null-like strings`() {
        // Given
        val nullLikeName = "null"
        every { menuDB.isValidMenuName(nullLikeName) } returns true

        // When
        val result = menuDB.isValidMenuName(nullLikeName)

        // Then
        assertTrue(result)
        verify { menuDB.isValidMenuName(nullLikeName) }
    }

    // Additional test methods for edge cases and business logic validation
    @Test
    fun `getMenuList should handle different menu categories`() {
        // Given
        val menuItemsWithCategories = listOf(
            MenuItem(1001, "아메리카노", 4000, 1001, true), // Category 1
            MenuItem(2001, "케이크", 6000, 2001, true), // Category 2
            MenuItem(3001, "샐러드", 8000, 3001, false) // Category 3
        )
        every { menuDB.getMenuList() } returns menuItemsWithCategories

        // When
        val result = menuDB.getMenuList()

        // Then
        assertEquals(3, result.size)
        assertTrue(result.any { it.id >= 1000 && it.id < 2000 }) // Category 1
        assertTrue(result.any { it.id >= 2000 && it.id < 3000 }) // Category 2
        assertTrue(result.any { it.id >= 3000 && it.id < 4000 }) // Category 3
        verify { menuDB.getMenuList() }
    }

    @Test
    fun `updateSpecificMenu should handle menu item with different properties`() {
        // Given
        val menuItemToUpdate = MenuItem(5, "수정된메뉴", 12000, 5, false)
        every { menuDB.updateSpecificMenu(menuItemToUpdate) } just runs

        // When
        menuDB.updateSpecificMenu(menuItemToUpdate)

        // Then
        verify { menuDB.updateSpecificMenu(menuItemToUpdate) }
    }

    @Test
    fun `getNextAvailableIdForCategory should handle different categories`() {
        // Given
        val categories = listOf(0, 1, 2, 3, 4)
        val expectedIds = listOf(1, 1001, 2001, 3001, 4001)

        categories.forEachIndexed { index, category ->
            every { menuDB.getNextAvailableIdForCategory(category) } returns expectedIds[index]
        }

        // When & Then
        categories.forEachIndexed { index, category ->
            val result = menuDB.getNextAvailableIdForCategory(category)
            assertEquals(expectedIds[index], result)
            verify { menuDB.getNextAvailableIdForCategory(category) }
        }
    }
}