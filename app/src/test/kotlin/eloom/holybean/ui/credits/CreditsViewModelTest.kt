package eloom.holybean.ui.credits

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import eloom.holybean.data.model.CreditItem
import eloom.holybean.data.model.OrdersDetailItem
import eloom.holybean.data.repository.LambdaRepository
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class CreditsViewModelTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private lateinit var creditsViewModel: CreditsViewModel
    private val lambdaRepository: LambdaRepository = mockk()
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        // Mock the initial loadCredits call to prevent automatic execution
        coEvery { lambdaRepository.getCreditsList() } returns arrayListOf()
        creditsViewModel = CreditsViewModel(lambdaRepository, testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        clearAllMocks()
    }

    @Test
    fun `uiState should be initialized correctly`() = runTest {
        // Given & When
        val initialState = creditsViewModel.uiState.first()

        // Then
        assertEquals(emptyList<CreditItem>(), initialState.creditsList)
        assertEquals(0, initialState.selectedOrderNumber)
        assertEquals(0, initialState.selectedOrderTotal)
        assertEquals("", initialState.selectedOrderDate)
        assertEquals(emptyList<OrdersDetailItem>(), initialState.orderDetails)
        assertEquals(false, initialState.isLoading)
    }

    @Test
    fun `loadCredits should update uiState with credits list on success`() = runTest {
        // Given
        val testViewModel = CreditsViewModel(lambdaRepository, testDispatcher)
        val mockCredits = arrayListOf(
            CreditItem(orderId = 1, totalAmount = 5000, date = "2024-01-01", orderer = "John"),
            CreditItem(orderId = 2, totalAmount = 3000, date = "2024-01-02", orderer = "Jane")
        )

        coEvery { lambdaRepository.getCreditsList() } returns mockCredits

        // When
        testViewModel.loadCredits()

        // Then
        val uiState = testViewModel.uiState.first()
        assertEquals(mockCredits, uiState.creditsList)
        assertEquals(false, uiState.isLoading)
    }

    @Test
    fun `loadCredits should emit toast event on repository failure`() = runTest {
        // Given
        val testViewModel = CreditsViewModel(lambdaRepository, testDispatcher)
        val errorMessage = "Network error"
        val exception = RuntimeException(errorMessage)

        coEvery { lambdaRepository.getCreditsList() } throws exception

        // Collect events before triggering the action
        val events = mutableListOf<CreditsViewModel.CreditsUiEvent>()
        val collectJob = launch {
            testViewModel.uiEvent.collect { events.add(it) }
        }

        // When
        testViewModel.loadCredits()

        // Wait for the coroutine to complete
        advanceUntilIdle()

        // Then
        assertEquals(1, events.size)
        assertTrue(events.first() is CreditsViewModel.CreditsUiEvent.ShowToast)
        val expectedErrorMessage = "외상 목록을 불러오는 중 오류가 발생했습니다: $errorMessage"
        assertEquals(expectedErrorMessage, (events.first() as CreditsViewModel.CreditsUiEvent.ShowToast).message)

        collectJob.cancel()
    }

    @Test
    fun `selectOrder should update uiState with selected order info`() = runTest {
        // Given
        val orderNumber = 123
        val totalAmount = 5000
        val date = "2024-01-01"

        // When
        creditsViewModel.selectOrder(orderNumber, totalAmount, date)

        // Then
        val uiState = creditsViewModel.uiState.first()
        assertEquals(orderNumber, uiState.selectedOrderNumber)
        assertEquals(totalAmount, uiState.selectedOrderTotal)
        assertEquals(date, uiState.selectedOrderDate)
        assertEquals(emptyList<OrdersDetailItem>(), uiState.orderDetails)
    }

    @Test
    fun `fetchOrderDetail should update uiState on success`() = runTest {
        // Given
        val orderNumber = 123
        val date = "2024-01-01"
        val mockOrderDetails = arrayListOf(
            OrdersDetailItem(name = "Americano", count = 2, subtotal = 8000),
            OrdersDetailItem(name = "Latte", count = 1, subtotal = 4500)
        )

        creditsViewModel.selectOrder(orderNumber, 5000, date)
        coEvery { lambdaRepository.getOrderDetail(date, orderNumber) } returns mockOrderDetails

        // When
        creditsViewModel.fetchOrderDetail()

        // Then
        coVerify { lambdaRepository.getOrderDetail(date, orderNumber) }
        val uiState = creditsViewModel.uiState.first()
        assertEquals(mockOrderDetails, uiState.orderDetails)
    }

    @Test
    fun `fetchOrderDetail should emit toast event when no order selected`() = runTest {
        // Given - no order selected (default state)
        val testViewModel = CreditsViewModel(lambdaRepository, testDispatcher)

        // Collect events before triggering the action
        val events = mutableListOf<CreditsViewModel.CreditsUiEvent>()
        val collectJob = launch {
            testViewModel.uiEvent.collect { events.add(it) }
        }

        // When
        testViewModel.fetchOrderDetail()

        // Wait for the coroutine to complete
        advanceUntilIdle()

        // Then
        assertEquals(1, events.size)
        assertTrue(events.first() is CreditsViewModel.CreditsUiEvent.ShowToast)
        assertEquals("주문을 선택해주세요", (events.first() as CreditsViewModel.CreditsUiEvent.ShowToast).message)

        collectJob.cancel()
    }

    @Test
    fun `fetchOrderDetail should emit toast event when order list is empty`() = runTest {
        // Given
        val testViewModel = CreditsViewModel(lambdaRepository, testDispatcher)
        val orderNumber = 123
        val date = "2024-01-01"

        testViewModel.selectOrder(orderNumber, 5000, date)
        coEvery { lambdaRepository.getOrderDetail(date, orderNumber) } returns arrayListOf()

        // Collect events before triggering the action
        val events = mutableListOf<CreditsViewModel.CreditsUiEvent>()
        val collectJob = launch {
            testViewModel.uiEvent.collect { events.add(it) }
        }

        // When
        testViewModel.fetchOrderDetail()

        // Wait for the coroutine to complete
        advanceUntilIdle()

        // Then
        assertEquals(1, events.size)
        assertTrue(events.first() is CreditsViewModel.CreditsUiEvent.ShowToast)
        assertEquals("주문 내역이 없습니다.", (events.first() as CreditsViewModel.CreditsUiEvent.ShowToast).message)

        collectJob.cancel()
    }

    @Test
    fun `fetchOrderDetail should emit error event on repository failure`() = runTest {
        // Given
        val testViewModel = CreditsViewModel(lambdaRepository, testDispatcher)
        val orderNumber = 456
        val date = "2024-01-01"
        val errorMessage = "Network error"
        val exception = RuntimeException(errorMessage)

        testViewModel.selectOrder(orderNumber, 5000, date)
        coEvery { lambdaRepository.getOrderDetail(any(), any()) } throws exception

        // Collect events before triggering the action
        val events = mutableListOf<CreditsViewModel.CreditsUiEvent>()
        val collectJob = launch {
            testViewModel.uiEvent.collect { events.add(it) }
        }

        // When
        testViewModel.fetchOrderDetail()

        // Wait for the coroutine to complete
        advanceUntilIdle()

        // Then
        assertEquals(1, events.size)
        assertTrue(events.first() is CreditsViewModel.CreditsUiEvent.ShowToast)
        val expectedErrorMessage = "주문 조회 중 오류가 발생했습니다: $errorMessage"
        assertEquals(expectedErrorMessage, (events.first() as CreditsViewModel.CreditsUiEvent.ShowToast).message)

        collectJob.cancel()
    }

    @Test
    fun `handleDeleteButton should emit toast event when no order selected`() = runTest {
        // Given - no order selected (default state)
        val testViewModel = CreditsViewModel(lambdaRepository, testDispatcher)

        // Collect events before triggering the action
        val events = mutableListOf<CreditsViewModel.CreditsUiEvent>()
        val collectJob = launch {
            testViewModel.uiEvent.collect { events.add(it) }
        }

        // When
        testViewModel.handleDeleteButton()

        // Wait for the coroutine to complete
        advanceUntilIdle()

        // Then
        assertEquals(1, events.size)
        assertTrue(events.first() is CreditsViewModel.CreditsUiEvent.ShowToast)
        assertEquals("주문을 선택해주세요", (events.first() as CreditsViewModel.CreditsUiEvent.ShowToast).message)

        collectJob.cancel()
    }

    @Test
    fun `handleDeleteButton should call setCreditOrderPaid successfully and emit success events`() = runTest {
        // Given
        val orderNumber = 123
        val date = "2024-01-01"
        val testViewModel = CreditsViewModel(lambdaRepository, testDispatcher)

        testViewModel.selectOrder(orderNumber, 5000, date)
        coEvery { lambdaRepository.setCreditOrderPaid(date, orderNumber) } just Runs

        // Collect events before triggering the action, and clear any existing events from init
        val events = mutableListOf<CreditsViewModel.CreditsUiEvent>()
        val collectJob = launch {
            testViewModel.uiEvent.collect { events.add(it) }
        }

        // Clear any events from loadCredits during initialization
        advanceUntilIdle()
        events.clear()

        // When
        testViewModel.handleDeleteButton()

        // Wait for the coroutine to complete
        advanceUntilIdle()

        // Then
        coVerify { lambdaRepository.setCreditOrderPaid(date, orderNumber) }
        assertEquals(2, events.size)
        assertTrue(events[0] is CreditsViewModel.CreditsUiEvent.ShowToast)
        assertEquals("외상이 성공적으로 처리되었습니다.", (events[0] as CreditsViewModel.CreditsUiEvent.ShowToast).message)
        assertTrue(events[1] is CreditsViewModel.CreditsUiEvent.RefreshCredits)

        collectJob.cancel()
    }

    @Test
    fun `handleDeleteButton should emit error event on repository failure`() = runTest {
        // Given
        val orderNumber = 456
        val date = "2024-01-01"
        val errorMessage = "Network error"
        val exception = RuntimeException(errorMessage)
        val testViewModel = CreditsViewModel(lambdaRepository, testDispatcher)

        testViewModel.selectOrder(orderNumber, 5000, date)
        coEvery { lambdaRepository.setCreditOrderPaid(any(), any()) } throws exception

        // Collect events before triggering the action
        val events = mutableListOf<CreditsViewModel.CreditsUiEvent>()
        val collectJob = launch {
            testViewModel.uiEvent.collect { events.add(it) }
        }

        // When
        testViewModel.handleDeleteButton()

        // Wait for the coroutine to complete
        advanceUntilIdle()

        // Then
        assertEquals(1, events.size)
        assertTrue(events.first() is CreditsViewModel.CreditsUiEvent.ShowToast)
        val expectedErrorMessage = "외상 처리 중 오류가 발생했습니다: $errorMessage"
        assertEquals(expectedErrorMessage, (events.first() as CreditsViewModel.CreditsUiEvent.ShowToast).message)

        collectJob.cancel()
    }
}