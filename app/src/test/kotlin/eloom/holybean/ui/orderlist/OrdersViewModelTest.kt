package eloom.holybean.ui.orderlist

import android.bluetooth.BluetoothAdapter
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import eloom.holybean.data.model.OrderItem
import eloom.holybean.data.model.OrdersDetailItem
import eloom.holybean.data.repository.LambdaRepository
import eloom.holybean.printer.OrdersPrinter
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    private val lambdaRepository: LambdaRepository = mockk()
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        // Mock the initial loadOrdersOfDay call to prevent automatic execution
        coEvery { lambdaRepository.getOrdersOfDay() } returns arrayListOf()
        viewModel = OrdersViewModel(lambdaRepository, testDispatcher)
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
        val testViewModel = OrdersViewModel(lambdaRepository, testDispatcher)
        val mockOrders = arrayListOf(
            OrderItem(orderId = 1, totalAmount = 8000, method = "Card", orderer = "John"),
            OrderItem(orderId = 2, totalAmount = 4500, method = "Cash", orderer = "Jane")
        )
        val mockOrderDetails = arrayListOf(
            OrdersDetailItem(name = "Americano", count = 2, subtotal = 8000)
        )

        coEvery { lambdaRepository.getOrdersOfDay() } returns mockOrders
        coEvery { lambdaRepository.getOrderDetail(any(), any()) } returns mockOrderDetails

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

        coEvery { lambdaRepository.getOrderDetail(any(), any()) } returns mockOrderDetails

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
        coEvery { lambdaRepository.getOrderDetail(currentDate, orderNumber) } returns mockOrderDetails

        // When
        viewModel.fetchOrderDetail(orderNumber)

        // Then
        coVerify { lambdaRepository.getOrderDetail(currentDate, orderNumber) }
        val uiState = viewModel.uiState.first()
        assertEquals(mockOrderDetails, uiState.orderDetails)
    }

    @Test
    fun `fetchOrderDetail should emit toast event when order list is empty`() = runTest {
        // Given
        val testViewModel = OrdersViewModel(lambdaRepository, testDispatcher)
        val orderNumber = 123

        val currentDate = testViewModel.getCurrentDate()
        coEvery { lambdaRepository.getOrderDetail(currentDate, orderNumber) } returns arrayListOf()

        // Collect events before triggering the action
        val events = mutableListOf<OrdersViewModel.OrdersUiEvent>()
        val collectJob = launch {
            testViewModel.uiEvent.collect { events.add(it) }
        }

        // When
        testViewModel.fetchOrderDetail(orderNumber)

        // Wait for the coroutine to complete
        advanceUntilIdle()

        // Then
        coVerify { lambdaRepository.getOrderDetail(currentDate, orderNumber) }
        assertEquals(1, events.size)
        assertTrue(events.first() is OrdersViewModel.OrdersUiEvent.ShowToast)
        assertEquals("주문 내역이 없습니다.", (events.first() as OrdersViewModel.OrdersUiEvent.ShowToast).message)

        collectJob.cancel()
    }

    @Test
    fun `fetchOrderDetail should emit error event on repository failure`() = runTest {
        // Given
        val testViewModel = OrdersViewModel(lambdaRepository, testDispatcher)
        val orderNumber = 456
        val errorMessage = "Network error"
        val exception = RuntimeException(errorMessage)

        coEvery { lambdaRepository.getOrderDetail(any(), any()) } throws exception

        // Collect events before triggering the action
        val events = mutableListOf<OrdersViewModel.OrdersUiEvent>()
        val collectJob = launch {
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
    fun `reprint should call printer methods correctly when order details exist`() = runTest {
        // Given
        val orderDetails = arrayListOf(OrdersDetailItem("Coffee", 1, 1000))
        val testText = "Test Print Text"

        // Set up the UI state with order details
        viewModel.selectOrder(101, 1000)
        coEvery { lambdaRepository.getOrderDetail(any(), any()) } returns orderDetails
        viewModel.fetchOrderDetail(101)

        mockkStatic(BluetoothAdapter::class)
        every { BluetoothAdapter.getDefaultAdapter() } returns null
        mockkConstructor(OrdersPrinter::class)
        every { anyConstructed<OrdersPrinter>().makeText(any(), any()) } returns testText
        coEvery { anyConstructed<OrdersPrinter>().print(any()) } just runs
        coEvery { anyConstructed<OrdersPrinter>().disconnect() } just runs

        // When
        viewModel.reprint()
        advanceUntilIdle()

        // Then
        verify { anyConstructed<OrdersPrinter>().makeText(101, orderDetails) }
        coVerify { anyConstructed<OrdersPrinter>().print(testText) }
        coVerify { anyConstructed<OrdersPrinter>().disconnect() }
    }

    @Test
    fun `reprint should emit toast event when no order details exist`() = runTest {
        // Given - UI state has no order details (default empty state)
        val testViewModel = OrdersViewModel(lambdaRepository, testDispatcher)

        // Collect events before triggering the action
        val events = mutableListOf<OrdersViewModel.OrdersUiEvent>()
        val collectJob = launch {
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
    fun `reprint should emit error event and disconnect when printing fails`() = runTest {
        // Given
        val testViewModel = OrdersViewModel(lambdaRepository, testDispatcher)
        val orderDetails = arrayListOf(OrdersDetailItem("Tea", 1, 1500))
        val testText = "Test Print Text"
        val errorMessage = "Printer connection failed"
        val printException = Exception(errorMessage)

        // Set up the UI state with order details
        testViewModel.selectOrder(102, 1500)
        coEvery { lambdaRepository.getOrderDetail(any(), any()) } returns orderDetails
        testViewModel.fetchOrderDetail(102)

        mockkStatic(BluetoothAdapter::class)
        every { BluetoothAdapter.getDefaultAdapter() } returns null
        mockkConstructor(OrdersPrinter::class)
        every { anyConstructed<OrdersPrinter>().makeText(any(), any()) } returns testText
        coEvery { anyConstructed<OrdersPrinter>().print(any()) } throws printException
        coEvery { anyConstructed<OrdersPrinter>().disconnect() } just runs

        // Collect events before triggering the action
        val events = mutableListOf<OrdersViewModel.OrdersUiEvent>()
        val collectJob = launch {
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
        coVerify { anyConstructed<OrdersPrinter>().disconnect() }

        collectJob.cancel()
    }

    @Test
    fun `deleteOrder should emit toast event when no order details exist`() = runTest {
        // Given - UI state has no order details (default empty state)
        val testViewModel = OrdersViewModel(lambdaRepository, testDispatcher)

        // Collect events before triggering the action
        val events = mutableListOf<OrdersViewModel.OrdersUiEvent>()
        val collectJob = launch {
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
        coEvery { lambdaRepository.getOrderDetail(any(), any()) } returns orderDetails
        viewModel.fetchOrderDetail(orderNumber)

        coEvery { lambdaRepository.deleteOrder(currentDate, orderNumber) } returns true

        // When
        viewModel.deleteOrder()

        // Then
        coVerify { lambdaRepository.deleteOrder(currentDate, orderNumber) }
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
        coEvery { lambdaRepository.getOrderDetail(any(), any()) } returns orderDetails
        viewModel.fetchOrderDetail(orderNumber)

        coEvery { lambdaRepository.deleteOrder(currentDate, orderNumber) } returns false

        // When
        viewModel.deleteOrder()

        // Then
        coVerify { lambdaRepository.deleteOrder(currentDate, orderNumber) }
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
        coEvery { lambdaRepository.getOrderDetail(any(), any()) } returns orderDetails
        viewModel.fetchOrderDetail(orderNumber)

        coEvery { lambdaRepository.deleteOrder(any(), any()) } throws exception

        // When
        viewModel.deleteOrder()

        // Then
        val finalState = viewModel.uiState.first()
        assertTrue(finalState.deleteStatus is OrdersViewModel.DeleteStatus.Error)
        assertEquals("오류가 발생했습니다. 다시 시도해주세요.", (finalState.deleteStatus as OrdersViewModel.DeleteStatus.Error).message)
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