package com.example.holybean.ui.report

import android.app.DatePickerDialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.holybean.databinding.FragmentReportBinding
import com.example.holybean.printer.ReportPrinter
import com.example.holybean.data.model.PrinterDTO
import com.example.holybean.data.model.ReportDetailItem
import com.example.holybean.data.repository.ReportRepository
import dagger.hilt.android.AndroidEntryPoint
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Date
import javax.inject.Inject
import kotlin.concurrent.thread

@AndroidEntryPoint
class ReportController: Fragment() {

    @Inject
    lateinit var repository: ReportRepository

    private lateinit var binding: FragmentReportBinding
    private lateinit var context: Context

    private lateinit var reportTitle: TextView
    private lateinit var totalText: TextView
    private lateinit var couponText: TextView
    private lateinit var cashText: TextView
    private lateinit var transferText: TextView
    private lateinit var creditText: TextView
    private lateinit var date1: TextView
    private lateinit var date2: TextView
    private lateinit var reportDetail: RecyclerView

    private lateinit var reportData: Map<String, Int>
    private lateinit var reportDetailData: ArrayList<ReportDetailItem>

    private lateinit var todayDate: String

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentReportBinding.inflate(inflater, container, false)
        val view = binding.root
        context = view.context

        // binding widgets
        reportTitle = binding.reportTitle
        totalText = binding.totalSell
        couponText = binding.couponSell
        cashText = binding.cashSell
        transferText = binding.transferSell
        creditText = binding.creditSell
        date1 = binding.date1Text
        date2 = binding.date2Text
        reportDetail = binding.reportDetail

        // init as a empty data
        reportData = emptyMap()
        reportDetailData = arrayListOf()

        // set default as today (1 day)
        todayDate = getCurrentDate()
        date1.text = todayDate
        date2.text = todayDate

        binding.tocsvbutton.setOnClickListener{
//            DatabaseManager.exportTableToCSV(context, "Orders")
//            DatabaseManager.exportTableToCSV(context, "Details")
        }

        // set button onClickListener
        binding.date1Button.setOnClickListener {
            getDateFromUser { selectedDate ->
                date1.text = selectedDate
            }
        }

        binding.date2Button.setOnClickListener {
            getDateFromUser { selectedDate ->
                date2.text = selectedDate
            }
        }

        binding.printButton.setOnClickListener {
            printButtonFunction()
        }

        binding.loadButton.setOnClickListener {
            loadButtonFunction()
        }

        return view
    }

    private fun getCurrentDate(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd")
        val currentDate = Date()
        return dateFormat.format(currentDate)
    }

    private fun setReportTitles() {
        totalText.text = "총 판매금액 : ${reportData["전체"]}"
        cashText.text = "현금 판매금액 : ${reportData["현금"]}"
        couponText.text = "쿠폰 판매금액 : ${reportData["쿠폰"]}"
        transferText.text = "계좌이체 판매금액 : ${reportData["계좌이체"]}"
        creditText.text = "외상 판매금액 : ${reportData["외상"]}"
    }

    private fun getDateFromUser(onDateSelected: (String) -> Unit) {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        DatePickerDialog(context, { _, year, month, day ->
            run {
                val formattedMonth = if (month + 1 < 10) "0${month + 1}" else (month + 1).toString()
                val formattedDay = if (day < 10) "0$day" else day.toString()
                val selectedDate = "$year-$formattedMonth-$formattedDay"
                onDateSelected(selectedDate)
            }
        }, year, month, day)?.show()
    }

    private fun printButtonFunction(){
        if(reportDetailData.size > 0){
            val printer = ReportPrinter()
            val text = printer.getPrintingText(
                PrinterDTO(
                date1.text.toString(),
                date2.text.toString(),
                reportData,
                reportDetailData
            )
            )
            thread {
                printer.print(text)
                Thread.sleep(2000)
                printer.disconnect()
            }
        } else {
            Toast.makeText(context, "기간 설정 후 조회해주세요", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadButtonFunction() {
        val startDate = date1.text.toString()
        val endDate = date2.text.toString()
        // check whether date are valid
        val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val date1parse = LocalDate.parse(startDate, dateFormat)
        val date2parse = LocalDate.parse(endDate, dateFormat)
        if (date1parse.isBefore(date2parse) || date1parse.isEqual(date2parse)) {
            reportTitle.text = "${startDate}~${endDate}"

            reportData = repository.makeReport(startDate, endDate)
            reportDetailData = repository.makeReportDetail(startDate, endDate)

            setReportTitles()

            val boardAdapter = ReportDetailAdapter(reportDetailData)
            reportDetail.apply {
                adapter = boardAdapter
                layoutManager = GridLayoutManager(view?.context ?: context, 1)
            }
        } else {
            // when invalid date input
            Toast.makeText(view?.context ?: context, "올바른 조회 기간이 아닙니다", Toast.LENGTH_SHORT).show()
        }
    }

}
