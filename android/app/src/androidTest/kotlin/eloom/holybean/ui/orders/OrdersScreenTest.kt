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
                summary = OrdersViewModel.TodaySummary(1240000, 86, 152),
                orders = persistentListOf(OrderItem(128, 15000, "현금", "홍길동")),
                selectedOrderNumber = 128,
                details = persistentListOf(OrdersDetailItem("아메리카노", 2, 7000)),
                selectedTotal = 15000, onClose = {}, onPrintReport = {}, onSelect = {}, onReprint = {}, onDelete = {},
            )
        }
        rule.onNodeWithText("총 잔수").assertIsDisplayed()
        rule.onNodeWithText("152잔").assertIsDisplayed()
        rule.onNodeWithText("재출력").assertIsDisplayed()
    }
}
