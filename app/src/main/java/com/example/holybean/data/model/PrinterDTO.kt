package com.example.holybean.data.model

data class PrinterDTO(
    val startdate: String,
    val enddate: String,
    val reportData: Map<String, Int>,
    val reportDetailItem: List<ReportDetailItem>
)
