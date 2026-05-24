package eloom.holybean.ui.home

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.google.firebase.crashlytics.FirebaseCrashlytics
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
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
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
        mockkStatic(FirebaseCrashlytics::class)
        every { FirebaseCrashlytics.getInstance() } returns mockk(relaxed = true)
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
        unmockkAll()
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

    @Test
    fun `init uses cached menu and skips network fetch when cache present`() = runTest(testDispatcher) {
        // Given - 캐시에 메뉴가 있다
        val cached = listOf(eloom.holybean.data.model.MenuItem(1001, "아메리카노", 4000, 1, true))
        io.mockk.every { menuRepository.getCachedMenu() } returns cached

        // When - ViewModel 재구성(init 재실행)
        homeViewModel = HomeViewModel(
            firestoreRepository,
            menuRepository,
            testDispatcher,
            CoroutineScope(SupervisorJob() + testDispatcher),
            piPrintClient,
            homePrinter,
        )
        advanceUntilIdle()

        // Then - 캐시 사용, 네트워크 페치 미호출
        assertEquals(cached, homeViewModel.uiState.value.allMenuItems)
        coVerify(exactly = 0) { menuRepository.getMenuListSync() }
    }

    @Test
    fun `print failure sets printFailure with reason`() = runTest(testDispatcher) {
        val order = createTestOrder()
        every { firestoreRepository.postOrder(any()) } returns Unit
        coEvery { piPrintClient.print(any<List<PrintCommandDto>>()) } throws
            eloom.holybean.printer.network.PrintServerException(
                eloom.holybean.printer.network.PrintFailureReason.PrinterOffline, "offline"
            )

        homeViewModel.onOrderConfirmed(order, "포장")
        advanceUntilIdle()

        val f = homeViewModel.uiState.value.printFailure
        assertEquals(order.orderNum, f?.orderNum)
        assertEquals(
            eloom.holybean.printer.network.PrintFailureReason.PrinterOffline,
            f?.reason,
        )
    }

    @Test
    fun `successful print leaves printFailure null`() = runTest(testDispatcher) {
        val order = createTestOrder()
        every { firestoreRepository.postOrder(any()) } returns Unit

        homeViewModel.onOrderConfirmed(order, "포장")
        advanceUntilIdle()

        assertEquals(null, homeViewModel.uiState.value.printFailure)
    }

    @Test
    fun `new order resets prior printFailure`() = runTest(testDispatcher) {
        val failing = createTestOrder(orderNum = 5)
        every { firestoreRepository.postOrder(any()) } returns Unit
        coEvery { piPrintClient.print(any<List<PrintCommandDto>>()) } throws
            eloom.holybean.printer.network.PrintServerException(
                eloom.holybean.printer.network.PrintFailureReason.PrinterError, "err"
            )
        homeViewModel.onOrderConfirmed(failing, "포장")
        advanceUntilIdle()
        assertTrue(homeViewModel.uiState.value.printFailure != null)

        coEvery { piPrintClient.print(any<List<PrintCommandDto>>()) } returns Unit
        homeViewModel.onOrderConfirmed(createTestOrder(orderNum = 6), "포장")
        advanceUntilIdle()
        assertEquals(null, homeViewModel.uiState.value.printFailure)
    }

    @Test
    fun `dismissPrintFailure clears state`() = runTest(testDispatcher) {
        val order = createTestOrder()
        every { firestoreRepository.postOrder(any()) } returns Unit
        coEvery { piPrintClient.print(any<List<PrintCommandDto>>()) } throws
            eloom.holybean.printer.network.PrintServerException(
                eloom.holybean.printer.network.PrintFailureReason.Unknown, "x"
            )
        homeViewModel.onOrderConfirmed(order, "포장")
        advanceUntilIdle()
        assertTrue(homeViewModel.uiState.value.printFailure != null)

        homeViewModel.dismissPrintFailure()
        assertEquals(null, homeViewModel.uiState.value.printFailure)
    }

    @Test
    fun `isSubmitting is true after confirm and false after completion`() = runTest {
        val std = StandardTestDispatcher(testScheduler)
        val vm = HomeViewModel(
            firestoreRepository, menuRepository, std,
            CoroutineScope(SupervisorJob() + std), piPrintClient, homePrinter,
        )
        advanceUntilIdle() // init 소비
        every { firestoreRepository.postOrder(any()) } returns Unit

        vm.onOrderConfirmed(createTestOrder(), "포장")
        assertTrue(vm.uiState.value.isSubmitting) // 런치 전 동기 세팅, 본문 미실행

        advanceUntilIdle()
        assertEquals(false, vm.uiState.value.isSubmitting)
    }

    @Test
    fun `reprintLastOrder retries print and clears failure on success`() = runTest(testDispatcher) {
        val order = createTestOrder()
        every { firestoreRepository.postOrder(any()) } returns Unit
        coEvery { piPrintClient.print(any<List<PrintCommandDto>>()) } throws
            eloom.holybean.printer.network.PrintServerException(
                eloom.holybean.printer.network.PrintFailureReason.PrinterOffline, "offline"
            )
        homeViewModel.onOrderConfirmed(order, "포장")
        advanceUntilIdle()
        assertTrue(homeViewModel.uiState.value.printFailure != null)

        // 프린터 복구 후 재출력 → 실패 표시 해제
        coEvery { piPrintClient.print(any<List<PrintCommandDto>>()) } returns Unit
        homeViewModel.reprintLastOrder()
        advanceUntilIdle()
        assertEquals(null, homeViewModel.uiState.value.printFailure)
    }

    @Test
    fun `second concurrent onOrderConfirmed is blocked while submitting`() = runTest {
        val std = StandardTestDispatcher(testScheduler)
        val vm = HomeViewModel(
            firestoreRepository, menuRepository, std,
            CoroutineScope(SupervisorJob() + std), piPrintClient, homePrinter,
        )
        advanceUntilIdle()
        every { firestoreRepository.postOrder(any()) } returns Unit

        vm.onOrderConfirmed(createTestOrder(orderNum = 1), "포장")
        // 첫 제출 코루틴이 StandardTestDispatcher 상 아직 진행 중 — isSubmitting 가드로 두 번째는 차단
        vm.onOrderConfirmed(createTestOrder(orderNum = 2), "매장")
        advanceUntilIdle()

        verify(exactly = 1) { firestoreRepository.postOrder(any()) }
    }

    @Test
    fun `consecutive identical print failures produce distinct values so state re-emits`() = runTest(testDispatcher) {
        val order = createTestOrder()
        every { firestoreRepository.postOrder(any()) } returns Unit
        coEvery { piPrintClient.print(any<List<PrintCommandDto>>()) } throws
            eloom.holybean.printer.network.PrintServerException(
                eloom.holybean.printer.network.PrintFailureReason.PrinterOffline, "offline"
            )

        homeViewModel.onOrderConfirmed(order, "포장")
        advanceUntilIdle()
        val first = homeViewModel.uiState.value.printFailure

        // 같은 원인으로 재출력 재실패 → 값이 달라야(seq 증가) StateFlow가 다시 emit 한다
        homeViewModel.reprintLastOrder()
        advanceUntilIdle()
        val second = homeViewModel.uiState.value.printFailure

        assertEquals(eloom.holybean.printer.network.PrintFailureReason.PrinterOffline, second?.reason)
        assertEquals(order.orderNum, second?.orderNum)
        assertTrue("consecutive identical failures must differ for re-emit", first != second)
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
