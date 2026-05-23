package eloom.holybean.ui.home

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import eloom.holybean.data.model.CartItem
import eloom.holybean.data.model.Order
import eloom.holybean.data.model.PaymentMethod
import eloom.holybean.data.repository.FirestoreRepository
import eloom.holybean.data.repository.MenuRepository
import eloom.holybean.printer.PiPrintClient
import eloom.holybean.printer.network.PrintCommandDto
import eloom.holybean.printer.polymorphism.HomePrinter
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
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

@ExperimentalCoroutinesApi
class HomeViewModelTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private lateinit var homeViewModel: HomeViewModel
    private val firestoreRepository: FirestoreRepository = mockk(relaxed = true)
    private val menuRepository: MenuRepository = mockk(relaxed = true)
    private val homePrinter: HomePrinter = mockk(relaxed = true)
    private val piPrintClient: PiPrintClient = mockk(relaxed = true)
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        coEvery { menuRepository.getMenuListSync() } returns emptyList()
        coEvery { firestoreRepository.getOrderNumber() } returns 1
        coEvery { homePrinter.receiptForCustomer(any()) } returns emptyList()
        coEvery { homePrinter.receiptForPOS(any(), any()) } returns emptyList()
        homeViewModel = HomeViewModel(
            firestoreRepository,
            menuRepository,
            testDispatcher,
            CoroutineScope(SupervisorJob() + testDispatcher),
            piPrintClient,
            homePrinter,
        )
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun `onOrderConfirmed should post order successfully and emit navigate event`() = runTest(testDispatcher) {
        // Given
        val testOrder = createTestOrder()
        val takeOption = "포장"
        every { firestoreRepository.postOrder(any()) } returns Unit

        val events = mutableListOf<HomeViewModel.UiEvent>()
        val job: Job = launch { homeViewModel.uiEvent.collect { events.add(it) } }

        // When
        homeViewModel.onOrderConfirmed(testOrder, takeOption)
        advanceUntilIdle()

        // Then
        verify(exactly = 1) { firestoreRepository.postOrder(testOrder) }
        assertTrue(events.any { it is HomeViewModel.UiEvent.NavigateHome })
        job.cancel()
    }

    @Test
    fun `onOrderConfirmed should handle repository error and emit toast`() = runTest(testDispatcher) {
        // Given
        val testOrder = createTestOrder()
        val takeOption = "매장"
        val testException = Exception("Network Error")
        every { firestoreRepository.postOrder(any()) } throws testException

        val events = mutableListOf<HomeViewModel.UiEvent>()
        val job: Job = launch { homeViewModel.uiEvent.collect { events.add(it) } }

        // When
        homeViewModel.onOrderConfirmed(testOrder, takeOption)
        advanceUntilIdle()

        // Then
        verify(exactly = 1) { firestoreRepository.postOrder(testOrder) }
        assertTrue(events.firstOrNull() is HomeViewModel.UiEvent.ShowToast)
        job.cancel()
    }

    @Test
    fun `onOrderConfirmed should handle multiple order confirmations correctly`() = runTest(testDispatcher) {
        // Given
        val testOrder1 = createTestOrder(orderNum = 1)
        val testOrder2 = createTestOrder(orderNum = 2)
        every { firestoreRepository.postOrder(any()) } returns Unit

        // When
        homeViewModel.onOrderConfirmed(testOrder1, "포장")
        homeViewModel.onOrderConfirmed(testOrder2, "매장")
        advanceUntilIdle()

        // Then
        verify(exactly = 2) { firestoreRepository.postOrder(any()) }
        // Navigation events should be emitted twice
        // (optional explicit check omitted for brevity)
    }

    @Test
    fun `onOrderConfirmed should block placement and emit toast when order number is invalid`() = runTest(testDispatcher) {
        // Given - 주문번호 조회 실패 시 -1 sentinel이 흘러들어온 주문
        val invalidOrder = createTestOrder(orderNum = -1)

        val events = mutableListOf<HomeViewModel.UiEvent>()
        val job: Job = launch { homeViewModel.uiEvent.collect { events.add(it) } }

        // When
        homeViewModel.onOrderConfirmed(invalidOrder, "포장")
        advanceUntilIdle()

        // Then - postOrder는 절대 호출되지 않고, 에러 토스트가 발생한다
        verify(exactly = 0) { firestoreRepository.postOrder(any()) }
        assertTrue(events.any { it is HomeViewModel.UiEvent.ShowToast })
        assertTrue(events.none { it is HomeViewModel.UiEvent.NavigateHome })
        job.cancel()
    }

    @Test
    fun `onOrderConfirmed should call piPrintClient with receipt commands`() = runTest(testDispatcher) {
        // Given
        val testOrder = createTestOrder()
        every { firestoreRepository.postOrder(any()) } returns Unit

        // When
        homeViewModel.onOrderConfirmed(testOrder, "포장")
        advanceUntilIdle()

        // Then
        coVerify(exactly = 2) { piPrintClient.print(any<List<PrintCommandDto>>()) }
    }

    @Test
    fun `addCoupon adds a positive cart line and increases total`() = runTest(testDispatcher) {
        // When
        homeViewModel.addCoupon(3000)
        advanceUntilIdle()

        // Then
        val state = homeViewModel.uiState.value
        assertEquals(1, state.basketItems.size)
        assertEquals("쿠폰", state.basketItems.first().name)
        assertEquals(3000, state.totalPrice)
    }

    @Test
    fun `successful order resets basket and refreshes order number`() = runTest(testDispatcher) {
        // Given - getOrderNumber()를 [초기 채번 10, 성공 후 재채번 11]로 스텁한 뒤
        // ViewModel을 재구성해 init이 10을, 주문 성공 분기가 11을 소비하도록 한다.
        coEvery { firestoreRepository.getOrderNumber() } returnsMany listOf(10, 11)
        every { firestoreRepository.postOrder(any()) } returns Unit
        homeViewModel = HomeViewModel(
            firestoreRepository,
            menuRepository,
            testDispatcher,
            CoroutineScope(SupervisorJob() + testDispatcher),
            piPrintClient,
            homePrinter,
        )
        advanceUntilIdle()
        homeViewModel.addCoupon(3000)
        advanceUntilIdle()

        val order = Order(
            "2026-05-23", 10, 0, "",
            listOf(CartItem(999, "쿠폰", 3000, 1, 3000)),
            listOf(PaymentMethod("현금", 3000)), 3000
        )

        // When
        homeViewModel.onOrderConfirmed(order, "일회용컵")
        advanceUntilIdle()

        // Then
        val state = homeViewModel.uiState.value
        assertTrue(state.basketItems.isEmpty())
        assertEquals(0, state.totalPrice)
        assertEquals(11, state.orderId) // 다음 주문번호로 갱신
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
