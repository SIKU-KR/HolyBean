package eloom.holybean.ui.report

import android.bluetooth.BluetoothAdapter
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import eloom.holybean.data.model.ReportDetailItem
import eloom.holybean.network.ApiService
import eloom.holybean.network.dto.ResponseMenuSales
import eloom.holybean.network.dto.ResponseSalesReport
import eloom.holybean.printer.ReportPrinter
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import retrofit2.Response

@ExperimentalCoroutinesApi
class ReportViewModelTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private lateinit var viewModel: ReportViewModel
    private val apiService: ApiService = mockk()
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = ReportViewModel(apiService)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        clearAllMocks()
    }

    @Test
    fun `formatDate should format date correctly`() {
        // When
        val formattedDate = viewModel.formatDate(2024, 0, 15) // month is 0-based
        // Then
        assertEquals("2024-01-15", formattedDate)
    }

    @Test
    fun `loadReportData should update LiveData on successful fetch`() = runTest {
        // Given
        val startDate = "2024-01-01"
        val endDate = "2024-01-31"
        val mockResponse = ResponseSalesReport(
            menuSales = mapOf("Coffee" to ResponseMenuSales(10, 50000)),
            paymentMethodSales = mapOf("Card" to 50000)
        )
        coEvery { apiService.getReport(startDate, endDate) } returns Response.success(mockResponse)

        val reportDataObserver = mockk<Observer<Map<String, Int>>>(relaxed = true)
        val reportDetailObserver = mockk<Observer<List<ReportDetailItem>>>(relaxed = true)
        val reportTitleObserver = mockk<Observer<String>>(relaxed = true)
        viewModel.reportData.observeForever(reportDataObserver)
        viewModel.reportDetailData.observeForever(reportDetailObserver)
        viewModel.reportTitle.observeForever(reportTitleObserver)

        // When
        viewModel.loadReportData(startDate, endDate)

        // Then
        verify { reportDataObserver.onChanged(mockResponse.paymentMethodSales) }
        verify { reportDetailObserver.onChanged(listOf(ReportDetailItem("Coffee", 10, 50000))) }
        verify { reportTitleObserver.onChanged("$startDate ~ $endDate") }
    }

    @Test
    fun `loadReportData should post error on invalid date range`() = runTest {
        // Given
        val startDate = "2024-02-01"
        val endDate = "2024-01-31"
        val errorObserver = mockk<Observer<String>>(relaxed = true)
        viewModel.errorMessage.observeForever(errorObserver)

        // When
        viewModel.loadReportData(startDate, endDate)

        // Then
        verify { errorObserver.onChanged("잘못된 날짜 범위입니다") }
        coVerify(exactly = 0) { apiService.getReport(any(), any()) }
    }

    @Test
    fun `loadReportData should post error on api failure`() = runTest {
        // Given
        val startDate = "2024-01-01"
        val endDate = "2024-01-31"
        val exception = RuntimeException("Network failed")
        coEvery { apiService.getReport(startDate, endDate) } throws exception
        val errorObserver = mockk<Observer<String>>(relaxed = true)
        viewModel.errorMessage.observeForever(errorObserver)

        // When
        viewModel.loadReportData(startDate, endDate)

        // Then
        verify { errorObserver.onChanged("리포트를 불러오는데 실패했습니다: ${exception.localizedMessage}") }
    }

    @Test
    fun `printReport should call printer methods when data is available`() = runTest {
        // Given
        val startDate = "2024-01-01"
        val endDate = "2024-01-31"
        val mockResponse = ResponseSalesReport(
            menuSales = mapOf("Coffee" to ResponseMenuSales(10, 50000)),
            paymentMethodSales = mapOf("Card" to 50000)
        )
        coEvery { apiService.getReport(startDate, endDate) } returns Response.success(mockResponse)
        viewModel.loadReportData(startDate, endDate)

        mockkStatic(BluetoothAdapter::class)
        every { BluetoothAdapter.getDefaultAdapter() } returns null
        mockkConstructor(ReportPrinter::class)
        every { anyConstructed<ReportPrinter>().getPrintingText(any()) } returns "print text"
        every { anyConstructed<ReportPrinter>().print(any()) } just runs
        every { anyConstructed<ReportPrinter>().disconnect() } just runs

        // When
        viewModel.printReport()
        advanceUntilIdle()

        // Then
        verify { anyConstructed<ReportPrinter>().getPrintingText(any()) }
        verify { anyConstructed<ReportPrinter>().print("print text") }
        verify { anyConstructed<ReportPrinter>().disconnect() }
    }

    @Test
    fun `printReport should post error when data is not available`() = runTest {
        // Given
        val errorObserver = mockk<Observer<String>>(relaxed = true)
        viewModel.errorMessage.observeForever(errorObserver)

        // When
        viewModel.printReport()

        // Then
        verify { errorObserver.onChanged("인쇄할 데이터가 없습니다") }
    }
}