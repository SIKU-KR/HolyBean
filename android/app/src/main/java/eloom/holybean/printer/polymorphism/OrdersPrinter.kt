package eloom.holybean.printer.polymorphism

import eloom.holybean.data.model.OrdersDetailItem
import eloom.holybean.printer.network.PrintAlign
import eloom.holybean.printer.network.PrintCommandDto
import eloom.holybean.printer.network.PrintSize
import eloom.holybean.printer.network.ReceiptBuilder
import eloom.holybean.printer.network.ReceiptBuilder.Companion.seg
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 주문 목록 화면 영수증 재출력 명령 생성기.
 */
@Singleton
class OrdersPrinter @Inject constructor() {

    fun makeCommands(orderNum: Int, basketList: List<OrdersDetailItem>): List<PrintCommandDto> =
        ReceiptBuilder()
            .text("영수증 재출력", align = PrintAlign.RIGHT)
            .divider('=')
            .blank()
            .text("주문번호 : $orderNum", align = PrintAlign.CENTER, underline = true, size = PrintSize.BIG)
            .blank()
            .divider('-')
            .also { builder ->
                basketList.forEach { item ->
                    builder.row(seg(item.name, bold = true), seg(item.count.toString(), align = PrintAlign.RIGHT))
                }
            }
            .blank()
            .divider('=')
            .cut()
            .build()
}
