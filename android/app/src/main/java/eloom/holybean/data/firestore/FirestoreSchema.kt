package eloom.holybean.data.firestore

object FirestoreSchema {
    const val ORDERS = "orders"
    const val DAY_SUMMARIES = "daySummaries"
    const val REPORT_ROLLUPS = "reportRollups"
    const val AGGREGATES = "aggregates"
    const val MENU = "menu"

    const val OPEN_CREDITS_DOC = "openCredits"
    const val MENU_CURRENT_DOC = "current"

    fun orderId(date: String, num: Int): String = "${date}_$num"
    fun creditKey(date: String, num: Int): String = "${date}_$num"

    const val CREDIT_UNPAID = 1
    const val CREDIT_SETTLED = 0
}
