package com.example.holybean.report

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.holybean.common.DatabaseManager
import com.example.holybean.common.getCurrentDate
import com.example.holybean.databinding.FragmentReportBinding

class ReportFragment: Fragment() {
    private lateinit var binding: FragmentReportBinding
    private lateinit var context: Context

    private lateinit var totalText: TextView
    private lateinit var couponText: TextView
    private lateinit var cashText: TextView
    private lateinit var transferText: TextView
    private lateinit var creditText: TextView

    private lateinit var reportData: Map<String, Int>

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentReportBinding.inflate(inflater, container, false)
        val view = binding.root
        context = view.context

        reportData = DatabaseManager.getReportData(view.context, getCurrentDate())

        totalText = binding.totalSell
        couponText = binding.couponSell
        cashText = binding.cashSell
        transferText = binding.transferSell
        creditText = binding.creditSell

        initTextViews()

        return view
    }

    private fun initTextViews() {
        totalText.text = "총 판매금액 : ${reportData["전체"]}"
        cashText.text = "현금 판매금액 : ${reportData["현금"]}"
        couponText.text = "쿠폰 판매금액 : ${reportData["쿠폰"]}"
        transferText.text = "계좌이체 판매금액 : ${reportData["계좌이체"]}"
        creditText.text = "외상 판매금액 : ${reportData["외상"]}"
    }

}
