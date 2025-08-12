package eloom.holybean.ui.orderlist

import android.bluetooth.BluetoothAdapter
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import eloom.holybean.data.model.OrdersDetailItem
import eloom.holybean.data.repository.LambdaRepository
import eloom.holybean.printer.OrdersPrinter
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
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
        viewModel = OrdersViewModel(lambdaRepository)
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
    fun `fetchOrderDetail should update orderDetails LiveData on success`() = runTest {
        // Given
        val orderNumber = 123
        val mockOrderDetails = arrayListOf(
            OrdersDetailItem(name = "Americano", count = 2, subtotal = 8000),
            OrdersDetailItem(name = "Latte", count = 1, subtotal = 4500)
        )
        val observer = mockk<Observer<List<OrdersDetailItem>>>(relaxed = true)
        viewModel.orderDetails.observeForever(observer)

        val currentDate = viewModel.getCurrentDate()
        coEvery { lambdaRepository.getOrderDetail(currentDate, orderNumber) } returns mockOrderDetails

        // When
        viewModel.fetchOrderDetail(orderNumber)

        // Then
        coVerify { lambdaRepository.getOrderDetail(currentDate, orderNumber) }
        verify { observer.onChanged(mockOrderDetails) }
        viewModel.orderDetails.removeObserver(observer)
    }

    @Test
    fun `fetchOrderDetail should post error message on repository failure`() = runTest {
        // Given
        val orderNumber = 456
        val errorMessage = "Network error"
        val exception = RuntimeException(errorMessage)
        val errorObserver = mockk<Observer<String>>(relaxed = true)
        viewModel.error.observeForever(errorObserver)

        coEvery { lambdaRepository.getOrderDetail(any(), any()) } throws exception

        // When
        viewModel.fetchOrderDetail(orderNumber)

        // Then
        val expectedErrorMessage = "Failed to fetch order details: $errorMessage"
        verify { errorObserver.onChanged(expectedErrorMessage) }
        viewModel.error.removeObserver(errorObserver)
    }

    @Test
    fun `reprint should call printer methods correctly`() = runTest {
        // Given
        val orderNum = 101
        val basketList = arrayListOf(OrdersDetailItem("Coffee", 1, 1000))
        val testText = "Test Print Text"

        mockkStatic(BluetoothAdapter::class)
        every { BluetoothAdapter.getDefaultAdapter() } returns null
        mockkConstructor(OrdersPrinter::class)
        every { anyConstructed<OrdersPrinter>().makeText(any(), any()) } returns testText
        coEvery { anyConstructed<OrdersPrinter>().print(any()) } just runs
        coEvery { anyConstructed<OrdersPrinter>().disconnect() } just runs

        // When
        viewModel.reprint(orderNum, basketList)
        advanceUntilIdle()

        // Then
        verify { anyConstructed<OrdersPrinter>().makeText(orderNum, basketList) }
        coVerify { anyConstructed<OrdersPrinter>().print(testText) }
        coVerify { anyConstructed<OrdersPrinter>().disconnect() }
    }

    @Test
    fun `reprint should post error and disconnect when printing fails`() = runTest {
        // Given
        val orderNum = 102
        val basketList = arrayListOf(OrdersDetailItem("Tea", 1, 1500))
        val testText = "Test Print Text"
        val errorMessage = "Printer connection failed"
        val printException = Exception(errorMessage)
        val errorObserver = mockk<Observer<String>>(relaxed = true)
        viewModel.error.observeForever(errorObserver)

        mockkStatic(BluetoothAdapter::class)
        every { BluetoothAdapter.getDefaultAdapter() } returns null
        mockkConstructor(OrdersPrinter::class)
        every { anyConstructed<OrdersPrinter>().makeText(any(), any()) } returns testText
        coEvery { anyConstructed<OrdersPrinter>().print(any()) } throws printException
        coEvery { anyConstructed<OrdersPrinter>().disconnect() } just runs

        // When
        viewModel.reprint(orderNum, basketList)
        advanceUntilIdle()

        // Then
        val expectedErrorMessage = "Printer error: $errorMessage"
        verify { errorObserver.onChanged(expectedErrorMessage) }
        coVerify { anyConstructed<OrdersPrinter>().disconnect() }
        viewModel.error.removeObserver(errorObserver)
    }
}