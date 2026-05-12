package eloom.holybean.printer.polymorphism

import eloom.holybean.data.model.PrinterDTO
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Text formatter for report screen prints.
 * Generates ESC/POS formatted text for sales reports.
 */
@Singleton
class ReportPrinter @Inject constructor() {

    fun getPrintingText(data: PrinterDTO): String = buildString {
        appendLine("[L]")
        appendLine("[C]<u><font size='big'>${data.startdate}~</font></u>")
        appendLine("[C]<u><font size='big'>${data.enddate}</font></u>")
        appendLine("[C]-------------------------------------")
        appendLine("[L]총 판매금액 : ${data.reportData["총합"] ?: 0}")
        appendLine("[L]현금 판매금액 : ${data.reportData["현금"] ?: 0}")
        appendLine("[L]쿠폰 판매금액 : ${data.reportData["쿠폰"] ?: 0}")
        appendLine("[L]계좌이체 판매금액 : ${data.reportData["계좌이체"] ?: 0}")
        appendLine("[L]외상 판매금액 : ${data.reportData["외상"] ?: 0}")
        appendLine("[L]무료쿠폰 판매금액 : ${data.reportData["무료쿠폰"] ?: 0}")
        appendLine("[L]무료제공 판매금액 : ${data.reportData["무료제공"] ?: 0}")
        appendLine("[C]-------------------------------------")
        appendLine("[L]이름[R]수량[R]판매액")
        data.reportDetailItem.forEach { item ->
            appendLine("[L]${item.name}[R]${item.quantity}[R]${item.subtotal}")
        }
        appendLine("[L]")
    }
}
