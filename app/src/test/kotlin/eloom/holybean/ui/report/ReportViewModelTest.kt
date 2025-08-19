package eloom.holybean.ui.report

import android.bluetooth.BluetoothAdapter
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import eloom.holybean.data.model.ReportDetailItem
import eloom.holybean.network.ApiService
import eloom.holybean.network.dto.ResponseMenuSales
import eloom.holybean.network.dto.ResponseSalesReport
import eloom.holybean.printer.ReportPrinter
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
        viewModel = ReportViewModel(apiService, testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        clearAllMocks()
    }

    @Test
    fun `uiState should be initialized correctly`() = runTest {
        // Given & When
        val initialState = viewModel.uiState.first()

        // Then
        assertEquals(emptyMap<String, Int>(), initialState.reportData)
        assertEquals(emptyList<ReportDetailItem>(), initialState.reportDetailData)
        assertEquals("", initialState.reportTitle)
        assertEquals(false, initialState.isLoading)
    }

    @Test
    fun `formatDate should format date correctly`() {
        // When
        val formattedDate = viewModel.formatDate(2024, 0, 15) // month is 0-based
        // Then
        assertEquals("2024-01-15", formattedDate)
    }

    @Test
    fun `loadReportData should update uiState on successful fetch`() = runTest {
        // Given
        val startDate = "2024-01-01"
        val endDate = "2024-01-31"
        val mockResponse = ResponseSalesReport(
            menuSales = mapOf("Coffee" to ResponseMenuSales(10, 50000)),
            paymentMethodSales = mapOf("Card" to 50000)
        )
        coEvery { apiService.getReport(startDate, endDate) } returns Response.success(mockResponse)

        // When
        viewModel.loadReportData(startDate, endDate)

        // Then
        val uiState = viewModel.uiState.first()
        assertEquals(mockResponse.paymentMethodSales, uiState.reportData)
        assertEquals(listOf(ReportDetailItem("Coffee", 10, 50000)), uiState.reportDetailData)
        assertEquals("$startDate ~ $endDate", uiState.reportTitle)
        assertEquals(false, uiState.isLoading)
    }

    @Test
    fun `loadReportData should emit error event on invalid date range`() = runTest {
        // Given
        val startDate = "2024-02-01"
        val endDate = "2024-01-31"

        // Collect events before triggering the action
        val events = mutableListOf<ReportViewModel.ReportUiEvent>()
        val collectJob = launch {
            viewModel.uiEvent.collect { events.add(it) }
        }

        // When
        viewModel.loadReportData(startDate, endDate)

        // Wait for the coroutine to complete
        advanceUntilIdle()

        // Then
        assertEquals(1, events.size)
        assertTrue(events.first() is ReportViewModel.ReportUiEvent.ShowError)
        assertEquals("잘못된 날짜 범위입니다", (events.first() as ReportViewModel.ReportUiEvent.ShowError).message)
        coVerify(exactly = 0) { apiService.getReport(any(), any()) }

        collectJob.cancel()
    }

    @Test
    fun `loadReportData should emit error event on api failure`() = runTest {
        // Given
        val startDate = "2024-01-01"
        val endDate = "2024-01-31"
        val exception = RuntimeException("Network failed")
        coEvery { apiService.getReport(startDate, endDate) } throws exception

        // Collect events before triggering the action
        val events = mutableListOf<ReportViewModel.ReportUiEvent>()
        val collectJob = launch {
            viewModel.uiEvent.collect { events.add(it) }
        }

        // When
        viewModel.loadReportData(startDate, endDate)

        // Wait for the coroutine to complete
        advanceUntilIdle()

        // Then
        assertEquals(1, events.size)
        assertTrue(events.first() is ReportViewModel.ReportUiEvent.ShowError)
        val expectedErrorMessage = "리포트를 불러오는데 실패했습니다: ${exception.localizedMessage}"
        assertEquals(expectedErrorMessage, (events.first() as ReportViewModel.ReportUiEvent.ShowError).message)

        collectJob.cancel()
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

        // Collect events before triggering the action
        val events = mutableListOf<ReportViewModel.ReportUiEvent>()
        val collectJob = launch {
            viewModel.uiEvent.collect { events.add(it) }
        }

        // When
        viewModel.printReport()
        advanceUntilIdle()

        // Then
        verify { anyConstructed<ReportPrinter>().getPrintingText(any()) }
        verify { anyConstructed<ReportPrinter>().print("print text") }
        verify { anyConstructed<ReportPrinter>().disconnect() }
        // Check success toast event
        val successEvent = events.find { it is ReportViewModel.ReportUiEvent.ShowToast }
        assertNotNull(successEvent)
        assertEquals("리포트 인쇄가 완료되었습니다", (successEvent as ReportViewModel.ReportUiEvent.ShowToast).message)

        collectJob.cancel()
    }

    @Test
    fun `printReport should emit error event when data is not available`() = runTest {
        // Given - empty data (default state)

        // Collect events before triggering the action
        val events = mutableListOf<ReportViewModel.ReportUiEvent>()
        val collectJob = launch {
            viewModel.uiEvent.collect { events.add(it) }
        }

        // When
        viewModel.printReport()

        // Wait for the coroutine to complete
        advanceUntilIdle()

        // Then
        assertEquals(1, events.size)
        assertTrue(events.first() is ReportViewModel.ReportUiEvent.ShowError)
        assertEquals("인쇄할 데이터가 없습니다", (events.first() as ReportViewModel.ReportUiEvent.ShowError).message)

        collectJob.cancel()
    }
}