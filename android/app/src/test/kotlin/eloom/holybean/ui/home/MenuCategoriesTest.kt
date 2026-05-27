package eloom.holybean.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

class MenuCategoriesTest {
    @Test fun `index 0 keeps all items`() {
        val items = listOf(1001, 2002, 3003)
        assertEquals(items, MenuCategories.filterIds(items, 0))
    }
    @Test fun `index filters by id div 1000`() {
        val items = listOf(1001, 1002, 2001, 3001)
        assertEquals(listOf(1001, 1002), MenuCategories.filterIds(items, 1))
    }
    @Test fun `category names match the confirmed order`() {
        assertEquals(
            listOf("전체", "HOT커피", "ICE커피", "차", "음료", "기타"),
            MenuCategories.names
        )
    }
}
