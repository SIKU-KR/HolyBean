package eloom.holybean.ui.menumanagement

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.google.firebase.crashlytics.FirebaseCrashlytics
import eloom.holybean.data.model.MenuItem
import eloom.holybean.data.repository.MenuRepository
import eloom.holybean.util.MainDispatcherRule
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class MenuManagementViewModelTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var viewModel: MenuManagementViewModel
    private val menuRepository: MenuRepository = mockk(relaxed = true)

    @Before
    fun setUp() {
        mockkStatic(FirebaseCrashlytics::class)
        every { FirebaseCrashlytics.getInstance() } returns mockk(relaxed = true)
        // Mock initial state
        every { menuRepository.getMenuList() } returns flowOf(emptyList())
        viewModel = MenuManagementViewModel(menuRepository)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `uiState should be initialized correctly`() = runTest {
        // Given & When
        val initialState = viewModel.uiState.first()

        // Then
        assertTrue(initialState.allMenuItems.isEmpty())
        assertTrue(initialState.filteredMenuItems.isEmpty())
        assertFalse(initialState.isLoading)
        assertEquals(1, initialState.selectedCategoryIndex) // Default to "HOT커피"
    }

    @Test
    fun `viewModel should load menu list from DB on init`() = runTest {
        // Given
        val mockMenuList = listOf(
            MenuItem(1001, "Americano", 4000, 1001, true),
            MenuItem(1002, "Latte", 4500, 1002, true)
        )
        every { menuRepository.getMenuList() } returns flowOf(mockMenuList)

        // When - ViewModel loads menu on init
        val testViewModel = MenuManagementViewModel(menuRepository)

        // Then
        val uiState = testViewModel.uiState.first()
        assertEquals(mockMenuList, uiState.allMenuItems)
    }

    @Test
    fun `onCategorySelected should filter menu items correctly`() = runTest {
        // Given
        val mockMenuList = listOf(
            MenuItem(1001, "Americano", 4000, 1001, true),
            MenuItem(2001, "Green Tea", 3000, 2001, true)
        )
        every { menuRepository.getMenuList() } returns flowOf(mockMenuList)
        val testViewModel = MenuManagementViewModel(menuRepository)

        // When
        testViewModel.onCategorySelected(0) // Category 1 (HOT커피)

        // Then
        val uiState = testViewModel.uiState.first()
        assertEquals(1, uiState.selectedCategoryIndex)
        assertEquals(1, uiState.filteredMenuItems.size)
        assertEquals("Americano", uiState.filteredMenuItems[0].name)
    }

    @Test
    fun `addMenu should emit success event and add menu on valid input`() = runTest {
        // Given
        val name = "New Coffee"
        val price = 5000

        coEvery { menuRepository.isValidMenuName(name) } returns true
        coEvery { menuRepository.addMenu(any()) } just runs
        every { menuRepository.getMenuList() } returns flowOf(emptyList())

        val events = mutableListOf<MenuManagementViewModel.UiEvent>()
        val collectJob = launch(mainDispatcherRule.dispatcher) { viewModel.uiEvent.collect { events.add(it) } }

        // When
        viewModel.addMenu(name, price)
        advanceUntilIdle()

        // Then
        coVerify { menuRepository.addMenu(any()) }
        assertEquals(1, events.size)
        assertTrue(events[0] is MenuManagementViewModel.UiEvent.ShowToast)
        assertEquals("메뉴가 추가되었습니다.", (events[0] as MenuManagementViewModel.UiEvent.ShowToast).message)

        collectJob.cancel()
    }

    @Test
    fun `addMenu should emit toast event on invalid name`() = runTest {
        // Given
        val name = "Existing Coffee"
        val price = 5000

        coEvery { menuRepository.isValidMenuName(name) } returns false

        val events = mutableListOf<MenuManagementViewModel.UiEvent>()
        val collectJob = launch(mainDispatcherRule.dispatcher) { viewModel.uiEvent.collect { events.add(it) } }

        // When
        viewModel.addMenu(name, price)
        advanceUntilIdle()

        // Then
        assertEquals(1, events.size)
        assertTrue(events.first() is MenuManagementViewModel.UiEvent.ShowToast)
        assertEquals("존재하는 메뉴입니다.", (events.first() as MenuManagementViewModel.UiEvent.ShowToast).message)

        collectJob.cancel()
    }

    @Test
    fun `updateMenu should emit success event and update menu`() = runTest {
        // Given
        val menuItemToUpdate = MenuItem(1001, "Americano", 4500, 1001, true)
        coEvery { menuRepository.updateSpecificMenu(any()) } just runs
        every { menuRepository.getMenuList() } returns flowOf(listOf(menuItemToUpdate))

        val events = mutableListOf<MenuManagementViewModel.UiEvent>()
        val collectJob = launch(mainDispatcherRule.dispatcher) { viewModel.uiEvent.collect { events.add(it) } }

        // When
        viewModel.updateMenu(menuItemToUpdate, "Americano", 4500)
        advanceUntilIdle()

        // Then
        coVerify { menuRepository.updateSpecificMenu(any()) }
        assertEquals(1, events.size)
        assertTrue(events[0] is MenuManagementViewModel.UiEvent.ShowToast)
        assertEquals("메뉴가 수정되었습니다.", (events[0] as MenuManagementViewModel.UiEvent.ShowToast).message)

        collectJob.cancel()
    }

    @Test
    fun `toggleMenuInUse should change menu status`() = runTest {
        // Given
        val menuItem = MenuItem(1001, "Americano", 4500, 1001, true)
        coEvery { menuRepository.updateSpecificMenu(any()) } just runs
        every { menuRepository.getMenuList() } returns flowOf(listOf(menuItem))
        val testViewModel = MenuManagementViewModel(menuRepository)

        val events = mutableListOf<MenuManagementViewModel.UiEvent>()
        val collectJob = launch(mainDispatcherRule.dispatcher) { testViewModel.uiEvent.collect { events.add(it) } }

        // When
        testViewModel.toggleMenuInUse(menuItem)
        advanceUntilIdle()

        // Then - state holds a NEW item instance toggled to false (original arg is left untouched by design)
        assertFalse(testViewModel.uiState.first().allMenuItems.first { it.id == 1001 }.inuse)
        coVerify { menuRepository.updateSpecificMenu(any()) }
        assertEquals(1, events.size)
        assertTrue(events.first() is MenuManagementViewModel.UiEvent.ShowToast)
        assertEquals("메뉴가 비활성화 되었습니다.", (events.first() as MenuManagementViewModel.UiEvent.ShowToast).message)

        collectJob.cancel()
    }

    @Test
    fun `toggleMenuInUse should emit new uiState reflecting the change`() = runTest {
        // Given - one in-use item in category 1, getMenuList is a one-shot flow that never re-emits
        val menuItem = MenuItem(1001, "Americano", 4500, 1001, true)
        coEvery { menuRepository.updateSpecificMenu(any()) } just runs
        every { menuRepository.getMenuList() } returns flowOf(listOf(menuItem))
        val testViewModel = MenuManagementViewModel(menuRepository)

        // sanity: item visible in the filtered (category 1) list and currently in use
        assertTrue(testViewModel.uiState.first().filteredMenuItems.first().inuse)

        // When
        testViewModel.toggleMenuInUse(menuItem)
        advanceUntilIdle()

        // Then - uiState reflects the toggle WITHOUT relying on a Firestore re-emission
        val state = testViewModel.uiState.first()
        assertFalse(state.allMenuItems.first { it.id == 1001 }.inuse)
        assertFalse(state.filteredMenuItems.first { it.id == 1001 }.inuse)
        coVerify { menuRepository.updateSpecificMenu(any()) }
    }

    @Test
    fun `updateMenu should emit new uiState reflecting name and price`() = runTest {
        // Given
        val menuItem = MenuItem(1001, "Americano", 4000, 1001, true)
        coEvery { menuRepository.updateSpecificMenu(any()) } just runs
        every { menuRepository.getMenuList() } returns flowOf(listOf(menuItem))
        val testViewModel = MenuManagementViewModel(menuRepository)

        // When
        testViewModel.updateMenu(menuItem, "Cafe Latte", 5000)
        advanceUntilIdle()

        // Then
        val state = testViewModel.uiState.first()
        val updated = state.filteredMenuItems.first { it.id == 1001 }
        assertEquals("Cafe Latte", updated.name)
        assertEquals(5000, updated.price)
    }

    @Test
    fun `moveItem should reorder menu items correctly`() = runTest {
        // Given
        val mockMenuList = listOf(
            MenuItem(1001, "Americano", 4000, 1001, true),
            MenuItem(1002, "Latte", 4500, 1002, true),
            MenuItem(1003, "Cappuccino", 4700, 1003, true)
        )
        every { menuRepository.getMenuList() } returns flowOf(mockMenuList)
        val testViewModel = MenuManagementViewModel(menuRepository)

        // When
        testViewModel.moveItem(0, 2) // Move first item to third position

        // Then
        val uiState = testViewModel.uiState.first()
        assertEquals("Latte", uiState.filteredMenuItems[0].name)
        assertEquals("Cappuccino", uiState.filteredMenuItems[1].name)
        assertEquals("Americano", uiState.filteredMenuItems[2].name)
    }

    @Test
    fun `saveMenuOrder should save menu orders and emit success message`() = runTest {
        // Given
        val mockMenuList = listOf(
            MenuItem(1001, "Americano", 4000, 1001, true)
        )
        every { menuRepository.getMenuList() } returns flowOf(mockMenuList)
        coEvery { menuRepository.saveMenuOrders(any()) } just runs
        val testViewModel = MenuManagementViewModel(menuRepository)

        val events = mutableListOf<MenuManagementViewModel.UiEvent>()
        val collectJob = launch(mainDispatcherRule.dispatcher) { testViewModel.uiEvent.collect { events.add(it) } }

        // When
        testViewModel.saveMenuOrder()
        advanceUntilIdle()

        // Then
        coVerify { menuRepository.saveMenuOrders(any()) }
        assertEquals(2, events.size)
        assertTrue(events[0] is MenuManagementViewModel.UiEvent.ShowToast)
        assertEquals("저장되었습니다.", (events[0] as MenuManagementViewModel.UiEvent.ShowToast).message)
        assertTrue(events[1] is MenuManagementViewModel.UiEvent.RefreshMenu)

        collectJob.cancel()
    }

    @Test
    fun `getNextAvailableId should return value from MenuRepository`() = runTest {
        // Given
        val expectedId = 1005
        coEvery { menuRepository.getNextAvailableIdForCategory(any()) } returns expectedId

        // When
        val result = viewModel.getNextAvailableId()

        // Then
        assertEquals(expectedId, result)
        coVerify { menuRepository.getNextAvailableIdForCategory(1) } // Default category
    }

    @Test
    fun `getNextAvailablePlacement should return value from MenuRepository`() = runTest {
        // Given
        val expectedPlacement = 1010
        coEvery { menuRepository.getNextAvailablePlacementForCategory(any()) } returns expectedPlacement

        // When
        val result = viewModel.getNextAvailablePlacement()

        // Then
        assertEquals(expectedPlacement, result)
        coVerify { menuRepository.getNextAvailablePlacementForCategory(1) } // Default category
    }

    @Test
    fun `password session verification should work correctly`() {
        // Given & When - Initially not verified
        assertFalse(viewModel.isPasswordSessionVerified())

        // When - Mark as verified
        viewModel.markPasswordSessionAsVerified()

        // Then
        assertTrue(viewModel.isPasswordSessionVerified())
    }
}
