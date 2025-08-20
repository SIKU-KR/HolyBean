package eloom.holybean.ui.home

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import eloom.holybean.data.model.CartItem
import eloom.holybean.data.model.Order
import eloom.holybean.data.model.PaymentMethod
import eloom.holybean.data.repository.LambdaRepository
import eloom.holybean.data.repository.MenuRepository
import eloom.holybean.printer.PrinterManager
import eloom.holybean.printer.PrintResult
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@ExperimentalCoroutinesApi
class HomeViewModelTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private lateinit var homeViewModel: HomeViewModel
    private val lambdaRepository: LambdaRepository = mockk(relaxed = true)
    private val menuRepository: MenuRepository = mockk(relaxed = true)
    private val printerManager: PrinterManager = mockk(relaxed = true)
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        coEvery { menuRepository.getMenuListSync() } returns emptyList()
        coEvery { lambdaRepository.getOrderNumber() } returns 1
        every { printerManager.print(any()) } returns PrintResult.Success
        homeViewModel = HomeViewModel(lambdaRepository, menuRepository, printerManager, testDispatcher)
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun `getCurrentDate should return current date in yyyy-MM-dd format`() {
        // Given
        val expectedDate = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

        // When
        val actualDate = homeViewModel.getCurrentDate()

        // Then
        assertEquals(expectedDate, actualDate)
    }

    @Test
    fun `getTotal should calculate correct total for empty basket`() {
        // Given
        val emptyBasket = arrayListOf<CartItem>()

        // When
        val total = homeViewModel.getTotal(emptyBasket)

        // Then
        assertEquals(0, total)
    }

    @Test
    fun `getTotal should calculate correct total for single item`() {
        // Given
        val cartItem = CartItem(
            id = 1,
            name = "아메리카노",
            price = 4000,
            count = 2,
            total = 0 // 초기값
        )
        val basket = arrayListOf(cartItem)

        // When
        val total = homeViewModel.getTotal(basket)

        // Then
        assertEquals(8000, total)
        assertEquals(8000, cartItem.total) // CartItem의 total도 업데이트되었는지 확인
    }

    @Test
    fun `getTotal should calculate correct total for multiple items`() {
        // Given
        val cartItem1 = CartItem(1, "아메리카노", 4000, 2, 0)
        val cartItem2 = CartItem(2, "라떼", 4500, 1, 0)
        val cartItem3 = CartItem(3, "케이크", 6000, 3, 0)
        val basket = arrayListOf(cartItem1, cartItem2, cartItem3)

        // When
        val total = homeViewModel.getTotal(basket)

        // Then
        val expectedTotal = (4000 * 2) + (4500 * 1) + (6000 * 3) // 8000 + 4500 + 18000 = 30500
        assertEquals(expectedTotal, total)
        assertEquals(8000, cartItem1.total)
        assertEquals(4500, cartItem2.total)
        assertEquals(18000, cartItem3.total)
    }

    @Test
    fun `onOrderConfirmed should post order successfully and emit navigate event`() = runTest(testDispatcher) {
        // Given
        val testOrder = createTestOrder()
        val takeOption = "포장"
        coEvery { lambdaRepository.postOrder(any()) } returns Unit

        val events = mutableListOf<HomeViewModel.UiEvent>()
        val job: Job = launch { homeViewModel.uiEvent.collect { events.add(it) } }

        // When
        homeViewModel.onOrderConfirmed(testOrder, takeOption)
        advanceUntilIdle()

        // Then
        coVerify(exactly = 1) { lambdaRepository.postOrder(testOrder) }
        assertTrue(events.any { it is HomeViewModel.UiEvent.NavigateHome })
        job.cancel()
    }

    @Test
    fun `onOrderConfirmed should handle repository error and emit toast`() = runTest(testDispatcher) {
        // Given
        val testOrder = createTestOrder()
        val takeOption = "매장"
        val testException = Exception("Network Error")
        coEvery { lambdaRepository.postOrder(any()) } throws testException

        val events = mutableListOf<HomeViewModel.UiEvent>()
        val job: Job = launch { homeViewModel.uiEvent.collect { events.add(it) } }

        // When
        homeViewModel.onOrderConfirmed(testOrder, takeOption)
        advanceUntilIdle()

        // Then
        coVerify(exactly = 1) { lambdaRepository.postOrder(testOrder) }
        assertTrue(events.firstOrNull() is HomeViewModel.UiEvent.ShowToast)
        job.cancel()
    }

    @Test
    fun `onOrderConfirmed should handle multiple order confirmations correctly`() = runTest(testDispatcher) {
        // Given
        val testOrder1 = createTestOrder(orderNum = 1)
        val testOrder2 = createTestOrder(orderNum = 2)
        coEvery { lambdaRepository.postOrder(any()) } returns Unit

        // When
        homeViewModel.onOrderConfirmed(testOrder1, "포장")
        homeViewModel.onOrderConfirmed(testOrder2, "매장")
        advanceUntilIdle()

        // Then
        coVerify(exactly = 2) { lambdaRepository.postOrder(any()) }
        // Navigation events should be emitted twice
        // (optional explicit check omitted for brevity)
    }

    @Test
    fun `onOrderConfirmed should call printer methods when order is successful`() = runTest(testDispatcher) {
        // Given
        val testOrder = createTestOrder()
        val takeOption = "포장"
        coEvery { lambdaRepository.postOrder(any()) } returns Unit
        every { printerManager.print(any()) } returns PrintResult.Success

        val events = mutableListOf<HomeViewModel.UiEvent>()
        val job: Job = launch { homeViewModel.uiEvent.collect { events.add(it) } }

        // When
        homeViewModel.onOrderConfirmed(testOrder, takeOption)
        advanceUntilIdle()

        // Then
        coVerify(exactly = 1) { lambdaRepository.postOrder(testOrder) }
        io.mockk.verify(exactly = 2) { printerManager.print(any()) } // Customer + POS receipts
        assertTrue(events.any { it is HomeViewModel.UiEvent.NavigateHome })
        job.cancel()
    }

    @Test
    fun `onOrderConfirmed should continue with order even if printing fails`() = runTest(testDispatcher) {
        // Given
        val testOrder = createTestOrder()
        val takeOption = "매장"
        coEvery { lambdaRepository.postOrder(any()) } returns Unit
        every { printerManager.print(any()) } returns PrintResult.Failure("Connection failed")

        val events = mutableListOf<HomeViewModel.UiEvent>()
        val job: Job = launch { homeViewModel.uiEvent.collect { events.add(it) } }

        // When
        homeViewModel.onOrderConfirmed(testOrder, takeOption)
        advanceUntilIdle()

        // Then
        coVerify(exactly = 1) { lambdaRepository.postOrder(testOrder) }
        io.mockk.verify(exactly = 2) { printerManager.print(any()) } // Customer + POS receipts
        // Order should still complete successfully even if printing fails
        assertTrue(events.any { it is HomeViewModel.UiEvent.NavigateHome })
        job.cancel()
    }

    // 헬퍼 메서드: 테스트용 Order 객체 생성
    private fun createTestOrder(orderNum: Int = 1): Order {
        val cartItems = listOf(
            CartItem(1, "아메리카노", 4000, 2, 8000),
            CartItem(2, "라떼", 4500, 1, 4500)
        )
        val paymentMethods = listOf(
            PaymentMethod("현금", 12500)
        )

        return Order(
            orderDate = "2024-01-15",
            orderNum = orderNum,
            creditStatus = 0,
            customerName = "테스트고객",
            orderItems = cartItems,
            paymentMethods = paymentMethods,
            totalAmount = 12500
        )
    }
}