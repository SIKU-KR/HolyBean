package eloom.holybean.ui.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import eloom.holybean.data.model.PrinterDTO
import eloom.holybean.data.model.ReportDetailItem
import eloom.holybean.network.ApiService
import eloom.holybean.printer.PrinterHelper
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*
import javax.inject.Inject

@HiltViewModel
class ReportViewModel @Inject constructor(
    private val apiService: ApiService,
    private val printerHelper: PrinterHelper,
    private val dispatcher: CoroutineDispatcher
) : ViewModel() {

    data class ReportUiState(
        val reportData: Map<String, Int> = emptyMap(),
        val reportDetailData: List<ReportDetailItem> = emptyList(),
        val reportTitle: String = "",
        val isLoading: Boolean = false
    )

    sealed class ReportUiEvent {
        data class ShowToast(val message: String) : ReportUiEvent()
        data class ShowError(val message: String) : ReportUiEvent()
    }

    private val _uiState = MutableStateFlow(ReportUiState())
    val uiState: StateFlow<ReportUiState> = _uiState.asStateFlow()

    private val _uiEvent = MutableSharedFlow<ReportUiEvent>(
        replay = 1,
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val uiEvent: SharedFlow<ReportUiEvent> = _uiEvent.asSharedFlow()

    private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT)

    fun loadReportData(startDate: String, endDate: String) {
        viewModelScope.launch(dispatcher) {
            if (!isValidDateRange(startDate, endDate)) {
                _uiEvent.tryEmit(ReportUiEvent.ShowError("잘못된 날짜 범위입니다"))
                return@launch
            }

            try {
                _uiState.update { it.copy(isLoading = true, reportTitle = "$startDate ~ $endDate") }
                val response = apiService.getReport(startDate, endDate)
                if (response.isSuccessful) {
                    val body = response.body()
                    val details = body?.menuSales?.map { info ->
                        ReportDetailItem(info.key, info.value.quantitySold, info.value.totalSales)
                    } ?: emptyList()

                    _uiState.update {
                        it.copy(
                            reportDetailData = details,
                            reportData = body?.paymentMethodSales ?: emptyMap(),
                            isLoading = false
                        )
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false) }
                    _uiEvent.tryEmit(ReportUiEvent.ShowError("리포트를 불러오는데 실패했습니다: ${response.message()}"))
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false) }
                _uiEvent.tryEmit(ReportUiEvent.ShowError("리포트를 불러오는데 실패했습니다: ${e.localizedMessage}"))
            }
        }
    }

    fun printReport() {
        val currentState = _uiState.value
        val summary = currentState.reportData
        val details = currentState.reportDetailData
        val title = currentState.reportTitle

        if (summary.isEmpty() || details.isEmpty() || title.isEmpty()) {
            viewModelScope.launch(dispatcher) {
                _uiEvent.tryEmit(ReportUiEvent.ShowError("인쇄할 데이터가 없습니다"))
            }
            return
        }

        viewModelScope.launch(dispatcher) {
            try {
                val dateParts = title.split(" ~ ")
                val printerDTO = PrinterDTO(dateParts[0], dateParts[1], summary, details)
                val success = printerHelper.printSalesReport(printerDTO)
                
                if (success) {
                    _uiEvent.tryEmit(ReportUiEvent.ShowToast("리포트 인쇄가 완료되었습니다"))
                } else {
                    _uiEvent.tryEmit(ReportUiEvent.ShowError("프린터 연결을 확인해주세요"))
                }
            } catch (e: Exception) {
                _uiEvent.tryEmit(ReportUiEvent.ShowError("인쇄 실패 : ${e.localizedMessage}"))
            }
        }
    }

    private fun isValidDateRange(start: String, end: String): Boolean {
        return try {
            val startDate = LocalDate.parse(start, dateFormatter)
            val endDate = LocalDate.parse(end, dateFormatter)
            !startDate.isAfter(endDate)
        } catch (e: Exception) {
            false
        }
    }

    fun formatDate(year: Int, month: Int, day: Int): String {
        val selectedDate = LocalDate.of(year, month + 1, day)
        return selectedDate.format(dateFormatter)
    }
}
