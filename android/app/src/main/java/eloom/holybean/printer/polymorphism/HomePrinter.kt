package eloom.holybean.printer.polymorphism

import eloom.holybean.data.model.Order
import eloom.holybean.printer.network.PrintAlign
import eloom.holybean.printer.network.PrintCommandDto
import eloom.holybean.printer.network.PrintSize
import eloom.holybean.printer.network.ReceiptBuilder
import eloom.holybean.printer.network.ReceiptBuilder.Companion.seg
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 홈 화면 영수증 명령 생성기. 고객용/POS용 명령 배열을 만든다.
 * USB 직연결 전송 경로가 ESC/POS로 변환한다.
 */
@Singleton
class HomePrinter @Inject constructor() {

    fun receiptForCustomer(data: Order): List<PrintCommandDto> = ReceiptBuilder()
        .divider('=')
        .blank()
        .text("주문번호 : ${data.orderNum}", align = PrintAlign.CENTER, underline = true, size = PrintSize.BIG)
        .blank()
        .divider('-')
        .blank()
        .also { builder ->
            data.orderItems.forEach { item ->
                builder.row(seg(item.name, bold = true), seg(item.count.toString(), align = PrintAlign.RIGHT))
            }
        }
        .blank()
        .divider('=')
        .cut()
        .build()

    fun receiptForPOS(data: Order, option: String): List<PrintCommandDto> = ReceiptBuilder()
        .divider('=')
        .blank()
        .text("주문번호 : ${data.orderNum}", align = PrintAlign.CENTER, underline = true, size = PrintSize.BIG)
        .blank()
        .text(option, align = PrintAlign.LEFT, size = PrintSize.BIG)
        .blank()
        .text("주문자 : ${data.customerName}", align = PrintAlign.RIGHT)
        .divider('-')
        .blank()
        .also { builder ->
            data.orderItems.forEach { item ->
                builder.row(seg(item.name, bold = true), seg(item.count.toString(), align = PrintAlign.RIGHT))
            }
        }
        .blank()
        .text("합계 : ${data.totalAmount}", align = PrintAlign.RIGHT)
        .text(data.orderDate, align = PrintAlign.RIGHT)
        .divider('=')
        .cut()
        .build()
}
