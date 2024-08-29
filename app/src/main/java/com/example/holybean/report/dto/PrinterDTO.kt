package com.example.holybean.report.dto

data class PrinterDTO(
    val startdate: String,
    val enddate: String,
    val reportData: Map<String, Int>,
    val reportDetailItem: ArrayList<ReportDetailItem>
)
