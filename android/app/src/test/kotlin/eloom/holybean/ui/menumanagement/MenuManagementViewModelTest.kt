package eloom.holybean.ui.menumanagement

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import eloom.holybean.data.model.MenuItem
import eloom.holybean.data.repository.LambdaRepository
import eloom.holybean.data.repository.MenuRepository
import io.mockk.*
import kotlinx.coroutines.Dispatchers
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

    private lateinit var viewModel: MenuManagementViewModel
    private val menuRepository: MenuRepository = mockk(relaxed = true)
    private val lambdaRepository: LambdaRepository = mockk(relaxed = true)
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        // Mock initial state
        every { menuRepository.getMenuList() } returns flowOf(emptyList())
        coEvery { lambdaRepository.getLastedSavedMenuList() } returns arrayListOf()
        viewModel = MenuManagementViewModel(menuRepository, lambdaRepository, testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        clearAllMocks()
    }

    @Test
    fun `uiState should be initialized correctly`() = runTest(testDispatcher) {
        // Given & When
        val initialState = viewModel.uiState.first()

        // Then
        assertTrue(initialState.allMenuItems.isEmpty())
        assertTrue(initialState.filteredMenuItems.isEmpty())
        assertFalse(initialState.isLoading)
        assertEquals(1, initialState.selectedCategoryIndex) // Default to "ICE커피"
    }

    @Test
    fun `viewModel should load menu list from DB on init`() = runTest(testDispatcher) {
        // Given
        val mockMenuList = listOf(
            MenuItem(1001, "Americano", 4000, 1001, true),
            MenuItem(1002, "Latte", 4500, 1002, true)
        )
        every { menuRepository.getMenuList() } returns flowOf(mockMenuList)

        // When - ViewModel loads menu on init
        val testViewModel = MenuManagementViewModel(menuRepository, lambdaRepository, testDispatcher)

        // Then
        val uiState = testViewModel.uiState.first()
        assertEquals(mockMenuList, uiState.allMenuItems)
    }

    @Test
    fun `onCategorySelected should filter menu items correctly`() = runTest(testDispatcher) {
        // Given
        val mockMenuList = listOf(
            MenuItem(1001, "Americano", 4000, 1001, true),
            MenuItem(2001, "Green Tea", 3000, 2001, true)
        )
        every { menuRepository.getMenuList() } returns flowOf(mockMenuList)
        val testViewModel = MenuManagementViewModel(menuRepository, lambdaRepository, testDispatcher)

        // When
        testViewModel.onCategorySelected(0) // Category 1 (ICE커피)

        // Then
        val uiState = testViewModel.uiState.first()
        assertEquals(1, uiState.selectedCategoryIndex)
        assertEquals(1, uiState.filteredMenuItems.size)
        assertEquals("Americano", uiState.filteredMenuItems[0].name)
    }

    @Test
    fun `addMenu should emit success event and add menu on valid input`() = runTest(testDispatcher) {
        // Given
        val name = "New Coffee"
        val id = 1001
        val price = 5000
        val placement = 1001

        coEvery { menuRepository.isValidMenuName(name) } returns true
        coEvery { menuRepository.addMenu(any()) } just runs
        every { menuRepository.getMenuList() } returns flowOf(emptyList())

        val events = mutableListOf<MenuManagementViewModel.UiEvent>()
        val collectJob = launch { viewModel.uiEvent.collect { events.add(it) } }

        // When
        viewModel.addMenu(id, name, price, placement)
        advanceUntilIdle()

        // Then
        coVerify { menuRepository.addMenu(any()) }
        assertEquals(1, events.size)
        assertTrue(events[0] is MenuManagementViewModel.UiEvent.ShowToast)
        assertEquals("메뉴가 추가되었습니다.", (events[0] as MenuManagementViewModel.UiEvent.ShowToast).message)

        collectJob.cancel()
    }

    @Test
    fun `addMenu should emit toast event on invalid name`() = runTest(testDispatcher) {
        // Given
        val name = "Existing Coffee"
        val id = 1001
        val price = 5000
        val placement = 1001

        coEvery { menuRepository.isValidMenuName(name) } returns false

        val events = mutableListOf<MenuManagementViewModel.UiEvent>()
        val collectJob = launch { viewModel.uiEvent.collect { events.add(it) } }

        // When
        viewModel.addMenu(id, name, price, placement)
        advanceUntilIdle()

        // Then
        assertEquals(1, events.size)
        assertTrue(events.first() is MenuManagementViewModel.UiEvent.ShowToast)
        assertEquals("존재하는 메뉴입니다.", (events.first() as MenuManagementViewModel.UiEvent.ShowToast).message)

        collectJob.cancel()
    }

    @Test
    fun `updateMenu should emit success event and update menu`() = runTest(testDispatcher) {
        // Given
        val menuItemToUpdate = MenuItem(1001, "Americano", 4500, 1001, true)
        coEvery { menuRepository.updateSpecificMenu(any()) } just runs
        every { menuRepository.getMenuList() } returns flowOf(listOf(menuItemToUpdate))

        val events = mutableListOf<MenuManagementViewModel.UiEvent>()
        val collectJob = launch { viewModel.uiEvent.collect { events.add(it) } }

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
    fun `toggleMenuInUse should change menu status`() = runTest(testDispatcher) {
        // Given
        val menuItem = MenuItem(1001, "Americano", 4500, 1001, true)
        coEvery { menuRepository.updateSpecificMenu(any()) } just runs
        every { menuRepository.getMenuList() } returns flowOf(listOf(menuItem))

        val events = mutableListOf<MenuManagementViewModel.UiEvent>()
        val collectJob = launch { viewModel.uiEvent.collect { events.add(it) } }

        // When
        viewModel.toggleMenuInUse(menuItem)
        advanceUntilIdle()

        // Then
        assertFalse(menuItem.inuse) // Should be toggled to false
        coVerify { menuRepository.updateSpecificMenu(any()) }
        assertEquals(1, events.size)
        assertTrue(events.first() is MenuManagementViewModel.UiEvent.ShowToast)
        assertEquals("메뉴가 비활성화 되었습니다.", (events.first() as MenuManagementViewModel.UiEvent.ShowToast).message)

        collectJob.cancel()
    }

    @Test
    fun `moveItem should reorder menu items correctly`() = runTest(testDispatcher) {
        // Given
        val mockMenuList = listOf(
            MenuItem(1001, "Americano", 4000, 1001, true),
            MenuItem(1002, "Latte", 4500, 1002, true),
            MenuItem(1003, "Cappuccino", 4700, 1003, true)
        )
        every { menuRepository.getMenuList() } returns flowOf(mockMenuList)
        val testViewModel = MenuManagementViewModel(menuRepository, lambdaRepository, testDispatcher)

        // When
        testViewModel.moveItem(0, 2) // Move first item to third position

        // Then
        val uiState = testViewModel.uiState.first()
        assertEquals("Latte", uiState.filteredMenuItems[0].name)
        assertEquals("Cappuccino", uiState.filteredMenuItems[1].name)
        assertEquals("Americano", uiState.filteredMenuItems[2].name)
    }

    @Test
    fun `saveMenuOrder should save menu orders and emit success message`() = runTest(testDispatcher) {
        // Given
        val mockMenuList = listOf(
            MenuItem(1001, "Americano", 4000, 1001, true)
        )
        every { menuRepository.getMenuList() } returns flowOf(mockMenuList)
        coEvery { menuRepository.saveMenuOrders(any()) } just runs
        val testViewModel = MenuManagementViewModel(menuRepository, lambdaRepository, testDispatcher)

        val events = mutableListOf<MenuManagementViewModel.UiEvent>()
        val collectJob = launch { testViewModel.uiEvent.collect { events.add(it) } }

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
    fun `getMenuListFromServer should update local DB on success`() = runTest(testDispatcher) {
        // Given
        val remoteMenuList = arrayListOf(
            MenuItem(1001, "Remote Americano", 4200, 1001, true),
            MenuItem(1002, "Remote Latte", 4700, 1002, true)
        )
        coEvery { lambdaRepository.getLastedSavedMenuList() } returns remoteMenuList
        coEvery { menuRepository.overwriteMenuList(any()) } just runs
        every { menuRepository.getMenuList() } returns flowOf(emptyList())

        val events = mutableListOf<MenuManagementViewModel.UiEvent>()
        val collectJob = launch { viewModel.uiEvent.collect { events.add(it) } }

        // When
        viewModel.getMenuListFromServer()
        advanceUntilIdle()

        // Then
        coVerify { lambdaRepository.getLastedSavedMenuList() }
        coVerify { menuRepository.overwriteMenuList(remoteMenuList) }
        assertEquals(2, events.size)
        assertTrue(events[0] is MenuManagementViewModel.UiEvent.ShowToast)
        assertEquals("태블릿에 저장 완료", (events[0] as MenuManagementViewModel.UiEvent.ShowToast).message)
        assertTrue(events[1] is MenuManagementViewModel.UiEvent.RefreshMenu)

        collectJob.cancel()
    }

    @Test
    fun `getMenuListFromServer should emit toast event on repository failure`() = runTest(testDispatcher) {
        // Given
        val errorMessage = "Sync failed"
        coEvery { lambdaRepository.getLastedSavedMenuList() } throws RuntimeException(errorMessage)

        val events = mutableListOf<MenuManagementViewModel.UiEvent>()
        val collectJob = launch { viewModel.uiEvent.collect { events.add(it) } }

        // When
        viewModel.getMenuListFromServer()
        advanceUntilIdle()

        // Then
        coVerify(exactly = 0) { menuRepository.overwriteMenuList(any()) }
        assertEquals(1, events.size)
        assertTrue(events.first() is MenuManagementViewModel.UiEvent.ShowToast)
        val expectedMessage = "데이터 가져오기 실패: $errorMessage"
        assertEquals(expectedMessage, (events.first() as MenuManagementViewModel.UiEvent.ShowToast).message)

        collectJob.cancel()
    }

    @Test
    fun `saveMenuListToServer should emit success message on success`() = runTest(testDispatcher) {
        // Given
        val menuList = listOf(
            MenuItem(1001, "Americano", 4000, 1001, true)
        )
        coEvery { menuRepository.getMenuListSync() } returns menuList
        coEvery { lambdaRepository.saveMenuListToServer(any()) } just runs

        val events = mutableListOf<MenuManagementViewModel.UiEvent>()
        val collectJob = launch { viewModel.uiEvent.collect { events.add(it) } }

        // When
        viewModel.saveMenuListToServer()
        advanceUntilIdle()

        // Then
        coVerify { lambdaRepository.saveMenuListToServer(menuList) }
        assertEquals(1, events.size)
        assertTrue(events.first() is MenuManagementViewModel.UiEvent.ShowToast)
        assertEquals("서버에 저장 완료", (events.first() as MenuManagementViewModel.UiEvent.ShowToast).message)

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