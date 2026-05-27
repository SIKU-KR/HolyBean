package eloom.holybean.ui.home

object MenuCategories {
    val names = listOf("전체", "HOT커피", "ICE커피", "차", "음료", "기타")

    /** index 0 = 전체, 그 외 = id / 1000 == index. */
    fun filterIds(ids: List<Int>, index: Int): List<Int> =
        if (index == 0) ids else ids.filter { it / 1000 == index }
}
