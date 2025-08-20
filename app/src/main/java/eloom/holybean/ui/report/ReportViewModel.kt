package eloom.holybean.ui.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import eloom.holybean.data.model.PrinterDTO
import eloom.holybean.data.model.ReportDetailItem
import eloom.holybean.network.ApiService
import eloom.holybean.printer.PrintResult
import eloom.holybean.printer.PrinterManager
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
    private val printerManager: PrinterManager,
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
                val reportText = formatReportText(printerDTO)
                val result = printerManager.print(reportText)

                when (result) {
                    is PrintResult.Success -> {
                        _uiEvent.tryEmit(ReportUiEvent.ShowToast("리포트 인쇄가 완료되었습니다"))
                    }

                    is PrintResult.Failure -> {
                        _uiEvent.tryEmit(ReportUiEvent.ShowError("프린터 연결을 확인해주세요"))
                    }
                }
            } catch (e: Exception) {
                _uiEvent.tryEmit(ReportUiEvent.ShowError("인쇄 실패 : ${e.localizedMessage}"))
            }
        }
    }

    private fun formatReportText(data: PrinterDTO): String {
        var result = "[L]\n"
        result += "[C]<u><font size='big'>${data.startdate}~</font></u>\n"
        result += "[C]<u><font size='big'>${data.enddate}</font></u>\n"
        result += "[C]-------------------------------------\n"
        result += "[L]총 판매금액 : ${data.reportData["총합"] ?: 0}\n"
        result += "[L]현금 판매금액 : ${data.reportData["현금"] ?: 0}\n"
        result += "[L]쿠폰 판매금액 : ${data.reportData["쿠폰"] ?: 0}\n"
        result += "[L]계좌이체 판매금액 : ${data.reportData["계좌이체"] ?: 0}\n"
        result += "[L]외상 판매금액 : ${data.reportData["외상"] ?: 0}\n"
        result += "[L]무료쿠폰 판매금액 : ${data.reportData["무료쿠폰"] ?: 0}\n"
        result += "[L]무료제공 판매금액 : ${data.reportData["무료제공"] ?: 0}\n"
        result += "[C]-------------------------------------\n"
        result += "[L]이름[R]수량[R]판매액\n"
        for (item in data.reportDetailItem) {
            result += "[L]${item.name}[R]${item.quantity}[R]${item.subtotal}\n"
        }
        result += "[L]\n"
        return result
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
