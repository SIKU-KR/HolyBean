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
import eloom.holybean.util.MainDispatcherRule
import io.mockk.Runs
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
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

    private val testDispatcher = UnconfinedTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(testDispatcher)

    private lateinit var homeViewModel: HomeViewModel
    private val firestoreRepository: FirestoreRepository = mockk(relaxed = true)
    private val menuRepository: MenuRepository = mockk(relaxed = true)
    private val homePrinter: HomePrinter = mockk(relaxed = true)
    private val piPrintClient: PiPrintClient = mockk(relaxed = true)

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
        coEvery { firestoreRepository.postOrder(any()) } just Runs

        val events = mutableListOf<HomeViewModel.UiEvent>()
        val job: Job = launch { homeViewModel.uiEvent.collect { events.add(it) } }

        // When
        homeViewModel.onOrderConfirmed(testOrder, takeOption)
        advanceUntilIdle()

        // Then
        coVerify(exactly = 1) { firestoreRepository.postOrder(testOrder) }
        assertTrue(events.any { it is HomeViewModel.UiEvent.NavigateHome })
        job.cancel()
    }

    @Test
    fun `onOrderConfirmed should handle multiple order confirmations correctly`() = runTest(testDispatcher) {
        // Given
        val testOrder1 = createTestOrder(orderNum = 1)
        val testOrder2 = createTestOrder(orderNum = 2)
        coEvery { firestoreRepository.postOrder(any()) } just Runs

        // When
        homeViewModel.onOrderConfirmed(testOrder1, "포장")
        homeViewModel.onOrderConfirmed(testOrder2, "매장")
        advanceUntilIdle()

        // Then
        coVerify(exactly = 2) { firestoreRepository.postOrder(any()) }
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
        coVerify(exactly = 0) { firestoreRepository.postOrder(any()) }
        assertTrue(events.any { it is HomeViewModel.UiEvent.ShowToast })
        assertTrue(events.none { it is HomeViewModel.UiEvent.NavigateHome })
        job.cancel()
    }

    @Test
    fun `onOrderConfirmed should call piPrintClient with receipt commands`() = runTest(testDispatcher) {
        // Given
        val testOrder = createTestOrder()
        coEvery { firestoreRepository.postOrder(any()) } just Runs

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
        coEvery { firestoreRepository.postOrder(any()) } just Runs
        homeViewModel = HomeViewModel(
            firestoreRepository,
            menuRepository,
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
    fun `init excludes inactive menus from displayed list`() = runTest(testDispatcher) {
        // Given - 캐시에 활성/비활성 메뉴가 섞여 있다
        val active = eloom.holybean.data.model.MenuItem(1001, "아메리카노", 4000, 1, true)
        val inactive = eloom.holybean.data.model.MenuItem(1002, "라떼", 4500, 2, false)
        io.mockk.every { menuRepository.getCachedMenu() } returns listOf(active, inactive)

        // When - ViewModel 재구성(init 재실행)
        homeViewModel = HomeViewModel(
            firestoreRepository,
            menuRepository,
            piPrintClient,
            homePrinter,
        )
        advanceUntilIdle()

        // Then - 비활성 메뉴는 전체/필터 목록 모두에서 제외된다
        assertEquals(listOf(active), homeViewModel.uiState.value.allMenuItems)
        assertEquals(listOf(active), homeViewModel.uiState.value.filteredMenuItems)
    }

    @Test
    fun `category selection never surfaces inactive menus`() = runTest(testDispatcher) {
        // Given - 같은 카테고리(id/1000 == 1)에 활성/비활성 메뉴가 섞여 있다
        val active = eloom.holybean.data.model.MenuItem(1001, "아메리카노", 4000, 1, true)
        val inactive = eloom.holybean.data.model.MenuItem(1002, "라떼", 4500, 2, false)
        io.mockk.every { menuRepository.getCachedMenu() } returns listOf(active, inactive)
        homeViewModel = HomeViewModel(
            firestoreRepository,
            menuRepository,
            piPrintClient,
            homePrinter,
        )
        advanceUntilIdle()

        // When - 해당 카테고리 선택
        homeViewModel.onCategorySelected(1)
        advanceUntilIdle()

        // Then - 비활성 메뉴는 노출되지 않는다
        assertEquals(listOf(active), homeViewModel.uiState.value.filteredMenuItems)
    }

    @Test
    fun `displayed menus are ordered by placement, not id`() = runTest(testDispatcher) {
        // Given - 레포지토리는 id 순으로 주지만 placement(order)는 id 역순이다
        val first = eloom.holybean.data.model.MenuItem(1001, "아메리카노", 4000, 3, true)
        val second = eloom.holybean.data.model.MenuItem(1002, "라떼", 4500, 2, true)
        val third = eloom.holybean.data.model.MenuItem(1003, "콜드브루", 5000, 1, true)
        io.mockk.every { menuRepository.getCachedMenu() } returns listOf(first, second, third)

        // When - ViewModel 재구성(init 재실행)
        homeViewModel = HomeViewModel(
            firestoreRepository,
            menuRepository,
            piPrintClient,
            homePrinter,
        )
        advanceUntilIdle()

        // Then - placement(order) 오름차순으로 정렬된다
        assertEquals(listOf(third, second, first), homeViewModel.uiState.value.allMenuItems)
        assertEquals(listOf(third, second, first), homeViewModel.uiState.value.filteredMenuItems)
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
            piPrintClient,
            homePrinter,
        )
        advanceUntilIdle()

        // Then - 캐시 사용, 네트워크 페치 미호출
        assertEquals(cached, homeViewModel.uiState.value.allMenuItems)
        coVerify(exactly = 0) { menuRepository.getMenuListSync() }
    }

    @Test
    fun `isSubmitting is true after confirm and false after completion`() = runTest {
        val std = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(std)
        val vm = HomeViewModel(
            firestoreRepository, menuRepository, piPrintClient, homePrinter,
        )
        advanceUntilIdle() // init 소비
        coEvery { firestoreRepository.postOrder(any()) } just Runs

        vm.onOrderConfirmed(createTestOrder(), "포장")
        assertTrue(vm.uiState.value.isSubmitting) // 런치 전 동기 세팅, 본문 미실행

        advanceUntilIdle()
        assertEquals(false, vm.uiState.value.isSubmitting)
    }

    @Test
    fun `second concurrent onOrderConfirmed is blocked while submitting`() = runTest {
        val std = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(std)
        val vm = HomeViewModel(
            firestoreRepository, menuRepository, piPrintClient, homePrinter,
        )
        advanceUntilIdle()
        coEvery { firestoreRepository.postOrder(any()) } just Runs

        vm.onOrderConfirmed(createTestOrder(orderNum = 1), "포장")
        // 첫 제출 코루틴이 StandardTestDispatcher 상 아직 진행 중 — isSubmitting 가드로 두 번째는 차단
        vm.onOrderConfirmed(createTestOrder(orderNum = 2), "매장")
        advanceUntilIdle()

        coVerify(exactly = 1) { firestoreRepository.postOrder(any()) }
    }

    // --- New tests for await-all submit flow ---

    @Test
    fun `print failure keeps user on screen with PrintFailed error and no navigate`() = runTest(testDispatcher) {
        val order = createTestOrder()
        coEvery { firestoreRepository.postOrder(any()) } just Runs
        coEvery { piPrintClient.print(any<List<PrintCommandDto>>()) } throws
            eloom.holybean.printer.network.PrintServerException(
                eloom.holybean.printer.network.PrintFailureReason.PrinterOffline, "offline"
            )
        val events = mutableListOf<HomeViewModel.UiEvent>()
        val job: Job = launch { homeViewModel.uiEvent.collect { events.add(it) } }

        homeViewModel.onOrderConfirmed(order, "포장")
        advanceUntilIdle()

        val err = homeViewModel.uiState.value.submitError
        assertTrue(err is HomeViewModel.SubmitError.PrintFailed)
        assertEquals(
            eloom.holybean.printer.network.PrintFailureReason.PrinterOffline,
            (err as HomeViewModel.SubmitError.PrintFailed).reason,
        )
        assertTrue(events.none { it is HomeViewModel.UiEvent.NavigateHome })
        assertEquals(false, homeViewModel.uiState.value.isSubmitting)
        job.cancel()
    }

    @Test
    fun `successful submit leaves submitError null and navigates`() = runTest(testDispatcher) {
        val order = createTestOrder()
        coEvery { firestoreRepository.postOrder(any()) } just Runs
        val events = mutableListOf<HomeViewModel.UiEvent>()
        val job: Job = launch { homeViewModel.uiEvent.collect { events.add(it) } }

        homeViewModel.onOrderConfirmed(order, "포장")
        advanceUntilIdle()

        assertEquals(null, homeViewModel.uiState.value.submitError)
        assertTrue(events.any { it is HomeViewModel.UiEvent.NavigateHome })
        job.cancel()
    }

    @Test
    fun `save failure sets SaveFailed and does not navigate`() = runTest(testDispatcher) {
        val order = createTestOrder()
        coEvery { firestoreRepository.postOrder(any()) } throws Exception("commit failed")
        val events = mutableListOf<HomeViewModel.UiEvent>()
        val job: Job = launch { homeViewModel.uiEvent.collect { events.add(it) } }

        homeViewModel.onOrderConfirmed(order, "포장")
        advanceUntilIdle()

        assertTrue(homeViewModel.uiState.value.submitError is HomeViewModel.SubmitError.SaveFailed)
        assertTrue(events.none { it is HomeViewModel.UiEvent.NavigateHome })
        job.cancel()
    }

    @Test
    fun `retry after print failure reprints only and does not re-save order`() = runTest(testDispatcher) {
        val order = createTestOrder()
        coEvery { firestoreRepository.postOrder(any()) } just Runs
        coEvery { piPrintClient.print(any<List<PrintCommandDto>>()) } throws
            eloom.holybean.printer.network.PrintServerException(
                eloom.holybean.printer.network.PrintFailureReason.PrinterOffline, "offline"
            )
        homeViewModel.onOrderConfirmed(order, "포장")
        advanceUntilIdle()
        assertTrue(homeViewModel.uiState.value.submitError is HomeViewModel.SubmitError.PrintFailed)

        coEvery { piPrintClient.print(any<List<PrintCommandDto>>()) } returns Unit
        val events = mutableListOf<HomeViewModel.UiEvent>()
        val job: Job = launch { homeViewModel.uiEvent.collect { events.add(it) } }
        homeViewModel.retrySubmission()
        advanceUntilIdle()

        coVerify(exactly = 1) { firestoreRepository.postOrder(order) }
        assertEquals(null, homeViewModel.uiState.value.submitError)
        assertTrue(events.any { it is HomeViewModel.UiEvent.NavigateHome })
        job.cancel()
    }

    @Test
    fun `retry after save failure re-saves order`() = runTest(testDispatcher) {
        val order = createTestOrder()
        coEvery { firestoreRepository.postOrder(any()) } throws Exception("commit failed")
        homeViewModel.onOrderConfirmed(order, "포장")
        advanceUntilIdle()
        assertTrue(homeViewModel.uiState.value.submitError is HomeViewModel.SubmitError.SaveFailed)

        coEvery { firestoreRepository.postOrder(any()) } just Runs
        homeViewModel.retrySubmission()
        advanceUntilIdle()

        coVerify(exactly = 2) { firestoreRepository.postOrder(order) }
        assertEquals(null, homeViewModel.uiState.value.submitError)
    }

    @Test
    fun `retry after save failure does not reprint an already-printed receipt`() = runTest(testDispatcher) {
        // 저장은 실패했지만 인쇄는 이미 성공한 경우(예: 서버 ack 타임아웃) — 재시도 시 영수증이 두 번
        // 출력되면 안 된다. printReceipt 는 print()를 2회(고객/POS) 호출하므로 최초 1회만 = 총 2회여야 한다.
        val order = createTestOrder()
        coEvery { firestoreRepository.postOrder(any()) } throws Exception("commit failed")
        coEvery { piPrintClient.print(any<List<PrintCommandDto>>()) } returns Unit
        homeViewModel.onOrderConfirmed(order, "포장")
        advanceUntilIdle()
        assertTrue(homeViewModel.uiState.value.submitError is HomeViewModel.SubmitError.SaveFailed)

        coEvery { firestoreRepository.postOrder(any()) } just Runs
        homeViewModel.retrySubmission()
        advanceUntilIdle()

        assertEquals(null, homeViewModel.uiState.value.submitError)
        coVerify(exactly = 2) { firestoreRepository.postOrder(order) }   // 저장은 재시도(멱등 아님)
        coVerify(exactly = 2) { piPrintClient.print(any<List<PrintCommandDto>>()) } // 인쇄는 최초 1회뿐
    }

    @Test
    fun `skipPrintAndComplete completes saved order without reprint`() = runTest(testDispatcher) {
        val order = createTestOrder()
        coEvery { firestoreRepository.postOrder(any()) } just Runs
        coEvery { piPrintClient.print(any<List<PrintCommandDto>>()) } throws
            eloom.holybean.printer.network.PrintServerException(
                eloom.holybean.printer.network.PrintFailureReason.PrinterError, "err"
            )
        homeViewModel.onOrderConfirmed(order, "포장")
        advanceUntilIdle()
        assertTrue(homeViewModel.uiState.value.submitError is HomeViewModel.SubmitError.PrintFailed)

        val events = mutableListOf<HomeViewModel.UiEvent>()
        val job: Job = launch { homeViewModel.uiEvent.collect { events.add(it) } }
        homeViewModel.skipPrintAndComplete()
        advanceUntilIdle()

        assertEquals(null, homeViewModel.uiState.value.submitError)
        assertTrue(events.any { it is HomeViewModel.UiEvent.NavigateHome })
        job.cancel()
    }

    @Test
    fun `when both save and print fail SaveFailed takes precedence`() = runTest(testDispatcher) {
        val order = createTestOrder()
        coEvery { firestoreRepository.postOrder(any()) } throws Exception("commit failed")
        coEvery { piPrintClient.print(any<List<PrintCommandDto>>()) } throws
            eloom.holybean.printer.network.PrintServerException(
                eloom.holybean.printer.network.PrintFailureReason.PrinterOffline, "offline"
            )
        val events = mutableListOf<HomeViewModel.UiEvent>()
        val job: Job = launch { homeViewModel.uiEvent.collect { events.add(it) } }

        homeViewModel.onOrderConfirmed(order, "포장")
        advanceUntilIdle()

        assertTrue(homeViewModel.uiState.value.submitError is HomeViewModel.SubmitError.SaveFailed)
        assertTrue(events.none { it is HomeViewModel.UiEvent.NavigateHome })
        job.cancel()
    }

    @Test
    fun `postOrder timeout is reported as SaveFailed and does not navigate`() = runTest(testDispatcher) {
        val order = createTestOrder()
        // postOrder 는 ack 타임아웃을 DataException.Timeout 으로 변환해 던진다 → SaveFailed 로 처리되어야 한다.
        coEvery { firestoreRepository.postOrder(any()) } throws eloom.holybean.exception.DataException.Timeout()
        val events = mutableListOf<HomeViewModel.UiEvent>()
        val job: Job = launch { homeViewModel.uiEvent.collect { events.add(it) } }

        homeViewModel.onOrderConfirmed(order, "포장")
        advanceUntilIdle()

        assertTrue(homeViewModel.uiState.value.submitError is HomeViewModel.SubmitError.SaveFailed)
        assertTrue(events.none { it is HomeViewModel.UiEvent.NavigateHome })
        assertEquals(false, homeViewModel.uiState.value.isSubmitting)
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
