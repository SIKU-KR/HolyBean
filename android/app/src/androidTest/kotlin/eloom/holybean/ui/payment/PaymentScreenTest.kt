package eloom.holybean.ui.payment

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import eloom.holybean.data.model.CartItem
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test

class PaymentScreenTest {
    @get:Rule val rule = createComposeRule()

    @Test fun confirmEmitsCashSelectionByDefault() {
        var sel: PaymentSelection? = null
        rule.setContent {
            PaymentScreen(128, persistentListOf(CartItem(1001, "아메리카노", 3500, 2, 7000)), 7000, {}, { sel = it })
        }
        rule.onNodeWithText("결제 완료").performClick()
        assert(sel?.firstMethod == "현금")
        assert(sel?.cupOption == "일회용컵")
    }
}
