package com.example.holybean.printer

import com.example.holybean.data.model.PrinterDTO

class ReportPrinter: Printer() {

    fun getPrintingText(data: PrinterDTO): String{
        var result = "[L]\n"
        result += "[C]<u><font size='big'>${data.startdate}~</font></u>\n"
        result += "[C]<u><font size='big'>${data.enddate}</font></u>\n"
        result += "[C]-------------------------------------\n"
        result += "[L]총 판매금액 : ${data.reportData["전체"]}\n"
        result += "[L]현금 판매금액 : ${data.reportData["현금"]}\n"
        result += "[L]쿠폰 판매금액 : ${data.reportData["쿠폰"]}\n"
        result += "[L]계좌이체 판매금액 : ${data.reportData["계좌이체"]}\n"
        result += "[L]외상 판매금액 : ${data.reportData["외상"]}\n"
        result += "[C]-------------------------------------\n"
        result += "[L]이름[R]수량[R]판매액\n"
        for(item in data.reportDetailItem) {
            result += "[L]${item.name}[R]${item.quantity}[R]${item.subtotal}\n"
        }
        result += "[L]\n"
        return result
    }

}