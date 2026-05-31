package eloom.holybean.printer.polymorphism

import eloom.holybean.data.model.PrinterDTO
import eloom.holybean.printer.network.PrintAlign
import eloom.holybean.printer.network.PrintCommandDto
import eloom.holybean.printer.network.PrintSize
import eloom.holybean.printer.network.ReceiptBuilder
import eloom.holybean.printer.network.ReceiptBuilder.Companion.seg
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 리포트 화면 매출 영수증 명령 생성기.
 */
@Singleton
class ReportPrinter @Inject constructor() {

    fun makeCommands(data: PrinterDTO): List<PrintCommandDto> = ReceiptBuilder()
        .blank()
        .text("${data.startdate}~", align = PrintAlign.CENTER, underline = true, size = PrintSize.BIG)
        .text(data.enddate, align = PrintAlign.CENTER, underline = true, size = PrintSize.BIG)
        .divider('-')
        .text("총 판매금액 : ${data.reportData["총합"] ?: 0}")
        .text("현금 판매금액 : ${data.reportData["현금"] ?: 0}")
        .text("쿠폰 판매금액 : ${data.reportData["쿠폰"] ?: 0}")
        .text("계좌이체 판매금액 : ${data.reportData["계좌이체"] ?: 0}")
        .text("외상 판매금액 : ${data.reportData["외상"] ?: 0}")
        .text("무료쿠폰 판매금액 : ${data.reportData["무료쿠폰"] ?: 0}")
        .text("무료제공 판매금액 : ${data.reportData["무료제공"] ?: 0}")
        .divider('-')
        .row(seg("이름"), seg("수량", align = PrintAlign.RIGHT), seg("판매액", align = PrintAlign.RIGHT))
        .also { builder ->
            data.reportDetailItem.forEach { item ->
                builder.row(
                    seg(item.name),
                    seg(item.quantity.toString(), align = PrintAlign.RIGHT),
                    seg(item.subtotal.toString(), align = PrintAlign.RIGHT),
                )
            }
        }
        .blank()
        .cut()
        .build()
}
