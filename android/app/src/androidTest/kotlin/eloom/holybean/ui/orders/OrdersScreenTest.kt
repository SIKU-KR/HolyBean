package eloom.holybean.ui.orders

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import eloom.holybean.data.model.OrderItem
import eloom.holybean.data.model.OrdersDetailItem
import eloom.holybean.ui.orderlist.OrdersViewModel
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test

class OrdersScreenTest {
    @get:Rule val rule = createComposeRule()

    @Test fun showsSummaryAndSelectedDetail() {
        rule.setContent {
            OrdersScreen(
                summary = OrdersViewModel.DaySummary(1240000, 86, 152),
                selectedDate = "2026-07-27",
                hasPreviousOrderDate = true,
                hasNextOrderDate = false,
                isLoading = false,
                orders = persistentListOf(OrderItem(128, 15000, "현금", "홍길동")),
                selectedOrderNumber = 128,
                details = persistentListOf(OrdersDetailItem("아메리카노", 2, 7000)),
                selectedTotal = 15000, onClose = {}, onPreviousOrderDate = {}, onNextOrderDate = {},
                onPrintReport = {}, onSelect = {}, onReprint = {}, onDelete = {},
            )
        }
        rule.onNodeWithText("총 잔수").assertIsDisplayed()
        rule.onNodeWithText("152잔").assertIsDisplayed()
        rule.onNodeWithText("2026년 7월 27일").assertIsDisplayed()
        rule.onNodeWithText("이전 주문일").assertIsEnabled()
        rule.onNodeWithText("다음 주문일").assertIsNotEnabled()
        rule.onNodeWithText("재출력").assertIsDisplayed()
    }

    @Test fun emptyDayDisablesReceiptActions() {
        rule.setContent {
            OrdersScreen(
                summary = OrdersViewModel.DaySummary(),
                selectedDate = "2026-07-27",
                hasPreviousOrderDate = false,
                hasNextOrderDate = false,
                isLoading = false,
                orders = persistentListOf(),
                selectedOrderNumber = 0,
                details = persistentListOf(),
                selectedTotal = 0,
                onClose = {}, onPreviousOrderDate = {}, onNextOrderDate = {}, onPrintReport = {},
                onSelect = {}, onReprint = {}, onDelete = {},
            )
        }

        rule.onNodeWithText("선택한 날짜에 주문이 없습니다.").assertIsDisplayed()
        rule.onNodeWithText("재출력").assertIsNotEnabled()
        rule.onNodeWithText("삭제").assertIsNotEnabled()
        rule.onNodeWithText("보고서 출력").assertIsNotEnabled()
    }
}
