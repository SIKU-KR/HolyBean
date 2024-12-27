package com.example.holybean.ui.report

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.holybean.data.model.PrinterDTO
import com.example.holybean.data.model.ReportDetailItem
import com.example.holybean.network.LambdaConnection
import com.example.holybean.network.RetrofitClient
import com.example.holybean.printer.ReportPrinter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*

class ReportViewModel : ViewModel() {

    private val lambdaConnection = RetrofitClient.retrofit.create(LambdaConnection::class.java)

    private val _reportData = MutableLiveData<Map<String, Int>>()
    val reportData: LiveData<Map<String, Int>> get() = _reportData

    private val _reportDetailData = MutableLiveData<List<ReportDetailItem>>()
    val reportDetailData: LiveData<List<ReportDetailItem>> get() = _reportDetailData

    private val _reportTitle = MutableLiveData<String>()
    val reportTitle: LiveData<String> get() = _reportTitle

    private val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String> get() = _errorMessage

    private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT)

    fun loadReportData(startDate: String, endDate: String) {

        viewModelScope.launch {
            val details = ArrayList<ReportDetailItem>()
            if (isValidDateRange(startDate, endDate)) {
                _reportTitle.value = "$startDate ~ $endDate"
                try {
                    val response = lambdaConnection.getReport(startDate, endDate)
                    if (response.isSuccessful) {
                        val saleslist = response.body()?.menuSales
                        saleslist?.forEach { info ->
                            details.add(ReportDetailItem(info.key, info.value.quantitySold, info.value.totalSales))
                        }
                        _reportDetailData.value = details
                        _reportData.value = response.body()?.paymentMethodSales
                    }
                } catch (e: Exception) {
                    _errorMessage.value = "리포트를 불러오는데 실패했습니다: ${e.localizedMessage}"
                }
            } else {
                _errorMessage.value = "잘못된 날짜 범위입니다"
            }
        }
    }

    fun printReport() {
        val summary = _reportData.value
        val details = _reportDetailData.value
        val title = _reportTitle.value

        if (summary != null && details != null && title != null) {
            viewModelScope.launch {
                try {
                    val reportPrinter = ReportPrinter()
                    val printerDTO = PrinterDTO(title.split(" ~ ")[0], title.split(" ~ ")[1], summary, details)
                    val printText = reportPrinter.getPrintingText(printerDTO)
                    withContext(Dispatchers.IO) {
                        reportPrinter.print(printText)
                        reportPrinter.disconnect()
                    }
                } catch (e: Exception) {
                    _errorMessage.value = "인쇄 실패 : ${e.localizedMessage}"
                }
            }
        } else {
            _errorMessage.value = "잘못된 날짜 범위입니다"
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
