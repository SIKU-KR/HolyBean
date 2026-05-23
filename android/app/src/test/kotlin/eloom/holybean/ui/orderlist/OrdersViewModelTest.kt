package eloom.holybean.ui.orderlist

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import eloom.holybean.data.model.OrderItem
import eloom.holybean.data.model.OrdersDetailItem
import eloom.holybean.data.model.ReportDetailItem
import eloom.holybean.data.model.SalesReport
import eloom.holybean.data.repository.FirestoreRepository
import eloom.holybean.printer.PiPrintClient
import eloom.holybean.printer.network.PrintCommandDto
import eloom.holybean.printer.polymorphism.OrdersPrinter
import eloom.holybean.printer.polymorphism.ReportPrinter
import io.mockk.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.*

@ExperimentalCoroutinesApi
class OrdersViewModelTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private lateinit var viewModel: OrdersViewModel
    private val firestoreRepository: FirestoreRepository = mockk()
    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var ordersPrinter: OrdersPrinter
    private lateinit var piPrintClient: PiPrintClient
    private lateinit var reportPrinter: ReportPrinter

    private fun createViewModelWithPrinter(
        printer: OrdersPrinter = mockk(relaxed = true),
        client: PiPrintClient = mockk(relaxed = true),
        report: ReportPrinter = mockk(relaxed = true),
    ): OrdersViewModel {
        coEvery { firestoreRepository.getOrdersOfDay() } returns arrayListOf()
        coEvery { firestoreRepository.getReport(any(), any()) } returns
            SalesReport(emptyList(), mapOf("총합" to 0))
        return OrdersViewModel(
            firestoreRepository,
            testDispatcher,
            CoroutineScope(SupervisorJob() + testDispatcher),
            client,
            printer,
            report,
        )
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        // Mock the initial loadOrdersOfDay / loadTodaySummary calls to prevent automatic execution
        coEvery { firestoreRepository.getOrdersOfDay() } returns arrayListOf()
        coEvery { firestoreRepository.getReport(any(), any()) } returns
            SalesReport(emptyList(), mapOf("총합" to 0))
        ordersPrinter = mockk(relaxed = true)
        piPrintClient = mockk(relaxed = true)
        reportPrinter = mockk(relaxed = true)
        viewModel = OrdersViewModel(
            firestoreRepository,
            testDispatcher,
            CoroutineScope(SupervisorJob() + testDispatcher),
            piPrintClient,
            ordersPrinter,
            reportPrinter,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        clearAllMocks()
    }

    @Test
    fun `getCurrentDate should return current date in yyyy-MM-dd format`() {
        // Given
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val expectedDate = dateFormat.format(Date())

        // When
        val actualDate = viewModel.getCurrentDate()

        // Then
        assertEquals(expectedDate, actualDate)
    }

    @Test
    fun `loadOrdersOfDay should update uiState with orders list on success`() = runTest {
        // Given
        val testViewModel = createViewModelWithPrinter()
        val mockOrders = arrayListOf(
            OrderItem(orderId = 1, totalAmount = 8000, method = "Card", orderer = "John"),
            OrderItem(orderId = 2, totalAmount = 4500, method = "Cash", orderer = "Jane")
        )
        val mockOrderDetails = arrayListOf(
            OrdersDetailItem(name = "Americano", count = 2, subtotal = 8000)
        )

        coEvery { firestoreRepository.getOrdersOfDay() } returns mockOrders
        coEvery { firestoreRepository.getOrderDetail(any(), any()) } returns mockOrderDetails

        // When
        testViewModel.loadOrdersOfDay()

        // Then
        val uiState = testViewModel.uiState.first()
        assertEquals(mockOrders, uiState.ordersList)
        assertEquals(1, uiState.selectedOrderNumber)
        assertEquals(8000, uiState.selectedOrderTotal)
        assertEquals(mockOrderDetails, uiState.orderDetails)
        assertEquals(false, uiState.isLoading)
    }

    @Test
    fun `selectOrder should update uiState with selected order info`() = runTest {
        // Given
        val orderNumber = 123
        val totalAmount = 5000
        val mockOrderDetails = arrayListOf(
            OrdersDetailItem(name = "Latte", count = 1, subtotal = 4500)
        )

        coEvery { firestoreRepository.getOrderDetail(any(), any()) } returns mockOrderDetails

        // When
        viewModel.selectOrder(orderNumber, totalAmount)

        // Then
        val uiState = viewModel.uiState.first()
        assertEquals(orderNumber, uiState.selectedOrderNumber)
        assertEquals(totalAmount, uiState.selectedOrderTotal)
        assertEquals(mockOrderDetails, uiState.orderDetails)
    }

    @Test
    fun `fetchOrderDetail should update uiState on success`() = runTest {
        // Given
        val orderNumber = 123
        val mockOrderDetails = arrayListOf(
            OrdersDetailItem(name = "Americano", count = 2, subtotal = 8000),
            OrdersDetailItem(name = "Latte", count = 1, subtotal = 4500)
        )

        val currentDate = viewModel.getCurrentDate()
        coEvery { firestoreRepository.getOrderDetail(currentDate, orderNumber) } returns mockOrderDetails

        // When
        viewModel.fetchOrderDetail(orderNumber)

        // Then
        coVerify { firestoreRepository.getOrderDetail(currentDate, orderNumber) }
        val uiState = viewModel.uiState.first()
        assertEquals(mockOrderDetails, uiState.orderDetails)
    }

    @Test
    fun `fetchOrderDetail should emit toast event when order list is empty`() = runTest {
        // Given
        val testViewModel = createViewModelWithPrinter()
        val orderNumber = 123

        val currentDate = testViewModel.getCurrentDate()
        coEvery { firestoreRepository.getOrderDetail(currentDate, orderNumber) } returns arrayListOf()

        // Collect events before triggering the action
        val events = mutableListOf<OrdersViewModel.OrdersUiEvent>()
        // Subscribe eagerly so the collector is registered before the action emits
        // (replay = 0: late subscribers do not receive buffered events).
        val collectJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            testViewModel.uiEvent.collect { events.add(it) }
        }

        // When
        testViewModel.fetchOrderDetail(orderNumber)

        // Wait for the coroutine to complete
        advanceUntilIdle()

        // Then
        coVerify { firestoreRepository.getOrderDetail(currentDate, orderNumber) }
        assertEquals(1, events.size)
        assertTrue(events.first() is OrdersViewModel.OrdersUiEvent.ShowToast)
        assertEquals("주문 내역이 없습니다.", (events.first() as OrdersViewModel.OrdersUiEvent.ShowToast).message)

        collectJob.cancel()
    }

    @Test
    fun `fetchOrderDetail should emit error event on repository failure`() = runTest {
        // Given
        val testViewModel = createViewModelWithPrinter()
        val orderNumber = 456
        val errorMessage = "Network error"
        val exception = RuntimeException(errorMessage)

        coEvery { firestoreRepository.getOrderDetail(any(), any()) } throws exception

        // Collect events before triggering the action
        val events = mutableListOf<OrdersViewModel.OrdersUiEvent>()
        // Subscribe eagerly so the collector is registered before the action emits
        // (replay = 0: late subscribers do not receive buffered events).
        val collectJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            testViewModel.uiEvent.collect { events.add(it) }
        }

        // When
        testViewModel.fetchOrderDetail(orderNumber)

        // Wait for the coroutine to complete
        advanceUntilIdle()

        // Then
        assertEquals(1, events.size)
        assertTrue(events.first() is OrdersViewModel.OrdersUiEvent.ShowToast)
        val expectedErrorMessage = "주문 조회 중 오류가 발생했습니다: $errorMessage"
        assertEquals(expectedErrorMessage, (events.first() as OrdersViewModel.OrdersUiEvent.ShowToast).message)

        collectJob.cancel()
    }

    @Test
    fun `reprint should call piPrintClient when order details exist`() = runTest {
        // Given
        val orderDetails = arrayListOf(OrdersDetailItem("Coffee", 1, 1000))

        // Set up the UI state with order details
        viewModel.selectOrder(101, 1000)
        coEvery { firestoreRepository.getOrderDetail(any(), any()) } returns orderDetails
        viewModel.fetchOrderDetail(101)

        every { ordersPrinter.makeCommands(any(), any()) } returns emptyList()

        // When
        viewModel.reprint()
        advanceUntilIdle()

        // Then
        verify { ordersPrinter.makeCommands(101, any()) }
        coVerify { piPrintClient.print(any<List<PrintCommandDto>>()) }
    }

    @Test
    fun `reprint should emit toast event when no order details exist`() = runTest {
        // Given - UI state has no order details (default empty state)
        val testViewModel = createViewModelWithPrinter()

        // Collect events before triggering the action
        val events = mutableListOf<OrdersViewModel.OrdersUiEvent>()
        // Subscribe eagerly so the collector is registered before the action emits
        // (replay = 0: late subscribers do not receive buffered events).
        val collectJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            testViewModel.uiEvent.collect { events.add(it) }
        }

        // When
        testViewModel.reprint()

        // Wait for the coroutine to complete
        advanceUntilIdle()

        // Then
        assertEquals(1, events.size)
        assertTrue(events.first() is OrdersViewModel.OrdersUiEvent.ShowToast)
        assertEquals("주문 조회 후 클릭해주세요", (events.first() as OrdersViewModel.OrdersUiEvent.ShowToast).message)

        collectJob.cancel()
    }

    @Test
    fun `reprint should emit error event when printing fails`() = runTest {
        // Given
        val printerMock = mockk<OrdersPrinter>(relaxed = true)
        val clientMock = mockk<PiPrintClient>()
        val testViewModel = createViewModelWithPrinter(printerMock, clientMock)
        val orderDetails = arrayListOf(OrdersDetailItem("Tea", 1, 1500))
        val errorMessage = "Printer connection failed"
        val printException = Exception(errorMessage)

        // Set up the UI state with order details
        testViewModel.selectOrder(102, 1500)
        coEvery { firestoreRepository.getOrderDetail(any(), any()) } returns orderDetails
        testViewModel.fetchOrderDetail(102)

        every { printerMock.makeCommands(any(), any()) } returns emptyList()
        coEvery { clientMock.print(any()) } throws printException

        // Collect events before triggering the action
        val events = mutableListOf<OrdersViewModel.OrdersUiEvent>()
        // Subscribe eagerly so the collector is registered before the action emits
        // (replay = 0: late subscribers do not receive buffered events).
        val collectJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            testViewModel.uiEvent.collect { events.add(it) }
        }

        // When
        testViewModel.reprint()
        advanceUntilIdle()

        // Then
        // Find the printer error event (might be multiple events from fetchOrderDetail first)
        val printerErrorEvent = events.find {
            it is OrdersViewModel.OrdersUiEvent.ShowToast &&
                    it.message.contains("Printer error")
        }
        assertNotNull(printerErrorEvent)
        val expectedErrorMessage = "Printer error: $errorMessage"
        assertEquals(expectedErrorMessage, (printerErrorEvent as OrdersViewModel.OrdersUiEvent.ShowToast).message)

        collectJob.cancel()
    }

    @Test
    fun `deleteOrder should emit toast event when no order details exist`() = runTest {
        // Given - UI state has no order details (default empty state)
        val testViewModel = createViewModelWithPrinter()

        // Collect events before triggering the action
        val events = mutableListOf<OrdersViewModel.OrdersUiEvent>()
        // Subscribe eagerly so the collector is registered before the action emits
        // (replay = 0: late subscribers do not receive buffered events).
        val collectJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            testViewModel.uiEvent.collect { events.add(it) }
        }

        // When
        testViewModel.deleteOrder()

        // Wait for the coroutine to complete
        advanceUntilIdle()

        // Then
        assertEquals(1, events.size)
        assertTrue(events.first() is OrdersViewModel.OrdersUiEvent.ShowToast)
        assertEquals("주문 조회 후 클릭해주세요", (events.first() as OrdersViewModel.OrdersUiEvent.ShowToast).message)

        collectJob.cancel()
    }

    @Test
    fun `deleteOrder should update deleteStatus to Loading then Success when deletion succeeds`() = runTest {
        // Given
        val orderDetails = arrayListOf(OrdersDetailItem("Coffee", 1, 1000))
        val orderNumber = 123
        val currentDate = viewModel.getCurrentDate()

        // Set up the UI state with order details
        viewModel.selectOrder(orderNumber, 1000)
        coEvery { firestoreRepository.getOrderDetail(any(), any()) } returns orderDetails
        viewModel.fetchOrderDetail(orderNumber)

        coEvery { firestoreRepository.deleteOrder(currentDate, orderNumber) } returns true

        // When
        viewModel.deleteOrder()

        // Then
        coVerify { firestoreRepository.deleteOrder(currentDate, orderNumber) }
        val finalState = viewModel.uiState.first()
        assertTrue(finalState.deleteStatus is OrdersViewModel.DeleteStatus.Success)
    }

    @Test
    fun `deleteOrder should update deleteStatus to Loading then Error when deletion fails`() = runTest {
        // Given
        val orderDetails = arrayListOf(OrdersDetailItem("Coffee", 1, 1000))
        val orderNumber = 456
        val currentDate = viewModel.getCurrentDate()

        // Set up the UI state with order details
        viewModel.selectOrder(orderNumber, 1000)
        coEvery { firestoreRepository.getOrderDetail(any(), any()) } returns orderDetails
        viewModel.fetchOrderDetail(orderNumber)

        coEvery { firestoreRepository.deleteOrder(currentDate, orderNumber) } returns false

        // When
        viewModel.deleteOrder()

        // Then
        coVerify { firestoreRepository.deleteOrder(currentDate, orderNumber) }
        val finalState = viewModel.uiState.first()
        assertTrue(finalState.deleteStatus is OrdersViewModel.DeleteStatus.Error)
        assertEquals("주문 삭제에 실패했습니다.", (finalState.deleteStatus as OrdersViewModel.DeleteStatus.Error).message)
    }

    @Test
    fun `deleteOrder should update deleteStatus to Error when repository throws exception`() = runTest {
        // Given
        val orderDetails = arrayListOf(OrdersDetailItem("Coffee", 1, 1000))
        val orderNumber = 789
        val errorMessage = "Network connection failed"
        val exception = RuntimeException(errorMessage)

        // Set up the UI state with order details
        viewModel.selectOrder(orderNumber, 1000)
        coEvery { firestoreRepository.getOrderDetail(any(), any()) } returns orderDetails
        viewModel.fetchOrderDetail(orderNumber)

        coEvery { firestoreRepository.deleteOrder(any(), any()) } throws exception

        // When
        viewModel.deleteOrder()

        // Then
        val finalState = viewModel.uiState.first()
        assertTrue(finalState.deleteStatus is OrdersViewModel.DeleteStatus.Error)
        assertEquals("오류가 발생했습니다. 다시 시도해주세요.", (finalState.deleteStatus as OrdersViewModel.DeleteStatus.Error).message)
    }

    @Test
    fun `deleting last order clears selection when refreshed list is empty`() = runTest {
        // Given - an order is selected with details
        val orderDetails = arrayListOf(OrdersDetailItem("Coffee", 1, 1000))
        val orderNumber = 321
        val currentDate = viewModel.getCurrentDate()

        viewModel.selectOrder(orderNumber, 1000)
        coEvery { firestoreRepository.getOrderDetail(any(), any()) } returns orderDetails
        viewModel.fetchOrderDetail(orderNumber)

        coEvery { firestoreRepository.deleteOrder(currentDate, orderNumber) } returns true
        // After deletion the day has no remaining orders
        coEvery { firestoreRepository.getOrdersOfDay() } returns arrayListOf()

        // When - delete succeeds, then the screen refreshes the (now empty) list
        viewModel.deleteOrder()
        advanceUntilIdle()
        viewModel.loadOrdersOfDay()
        advanceUntilIdle()

        // Then - stale selection/detail is cleared
        val finalState = viewModel.uiState.value
        assertEquals(0, finalState.selectedOrderNumber)
        assertEquals(0, finalState.selectedOrderTotal)
        assertTrue(finalState.orderDetails.isEmpty())
    }

    @Test
    fun `loadTodaySummary populates totals`() = runTest {
        coEvery { firestoreRepository.getReport(any(), any()) } returns
            SalesReport(
                menuSales = listOf(ReportDetailItem("아메리카노", 5, 17500)),
                paymentSales = mapOf("총합" to 100000),
            )
        coEvery { firestoreRepository.getOrdersOfDay() } returns arrayListOf(
            OrderItem(1, 5000, "현금", "")
        )

        viewModel.loadTodaySummary()
        advanceUntilIdle()

        val s = viewModel.uiState.value.todaySummary
        assertEquals(100000, s.totalSales)
        assertEquals(1, s.orderCount)
        assertEquals(5, s.drinkCount)
    }

    @Test
    fun `loadTodaySummary excludes 쿠폰 from drinkCount`() = runTest {
        coEvery { firestoreRepository.getReport(any(), any()) } returns
            SalesReport(
                menuSales = listOf(
                    ReportDetailItem("아메리카노", 5, 17500),
                    ReportDetailItem("쿠폰", 3, 9000),
                ),
                paymentSales = mapOf("총합" to 100000),
            )
        coEvery { firestoreRepository.getOrdersOfDay() } returns arrayListOf(
            OrderItem(1, 5000, "현금", "")
        )

        viewModel.loadTodaySummary()
        advanceUntilIdle()

        val s = viewModel.uiState.value.todaySummary
        assertEquals(5, s.drinkCount)
    }

    @Test
    fun `resetDeleteStatus should set deleteStatus to Idle`() = runTest {
        // When
        viewModel.resetDeleteStatus()

        // Then
        val uiState = viewModel.uiState.first()
        assertTrue(uiState.deleteStatus is OrdersViewModel.DeleteStatus.Idle)
    }
}
