package eloom.holybean.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import eloom.holybean.data.firestore.FirestoreSchema
import eloom.holybean.data.firestore.ReportAggregation
import eloom.holybean.data.firestore.ReportAggregation.DailyRollup
import eloom.holybean.data.firestore.ReportAggregation.MenuSale
import eloom.holybean.data.model.*
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreRepository @Inject constructor(
    private val db: FirebaseFirestore
) {
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private fun today(): String = LocalDate.now().format(dateFormatter)

    suspend fun getOrderNumber(): Int {
        return try {
            val snap = db.collection(FirestoreSchema.DAY_SUMMARIES).document(today()).get().await()
            val last = (snap.getLong("lastOrderNum") ?: 0L).toInt()
            last + 1
        } catch (e: Exception) {
            e.printStackTrace()
            -1
        }
    }

    suspend fun getOrdersOfDay(): ArrayList<OrderItem> {
        return try {
            val snap = db.collection(FirestoreSchema.DAY_SUMMARIES).document(today()).get().await()
            @Suppress("UNCHECKED_CAST")
            val orders = (snap.get("orders") as? Map<String, Map<String, Any>>) ?: emptyMap()
            val list = orders.entries
                .sortedBy { it.key.toIntOrNull() ?: 0 }
                .map { (num, m) ->
                    OrderItem(
                        orderId = num.toInt(),
                        totalAmount = (m["totalAmount"] as? Number)?.toInt() ?: 0,
                        method = m["orderMethod"] as? String ?: "Unknown",
                        orderer = m["customerName"] as? String ?: ""
                    )
                }
            ArrayList(list)
        } catch (e: Exception) {
            e.printStackTrace()
            arrayListOf()
        }
    }

    suspend fun getOrderDetail(date: String, num: Int): ArrayList<OrdersDetailItem> {
        return try {
            val snap = db.collection(FirestoreSchema.ORDERS)
                .document(FirestoreSchema.orderId(date, num)).get().await()
            @Suppress("UNCHECKED_CAST")
            val items = (snap.get("items") as? List<Map<String, Any>>) ?: emptyList()
            ArrayList(items.map {
                OrdersDetailItem(
                    name = it["name"] as? String ?: "",
                    count = (it["quantity"] as? Number)?.toInt() ?: 0,
                    subtotal = (it["subtotal"] as? Number)?.toInt() ?: 0
                )
            })
        } catch (e: Exception) {
            e.printStackTrace()
            arrayListOf()
        }
    }

    suspend fun getCreditsList(): ArrayList<CreditItem> {
        return try {
            val snap = db.collection(FirestoreSchema.AGGREGATES)
                .document(FirestoreSchema.OPEN_CREDITS_DOC).get().await()
            @Suppress("UNCHECKED_CAST")
            val items = (snap.get("items") as? Map<String, Map<String, Any>>) ?: emptyMap()
            ArrayList(items.values.map {
                CreditItem(
                    orderId = (it["orderNum"] as? Number)?.toInt() ?: 0,
                    totalAmount = (it["totalAmount"] as? Number)?.toInt() ?: 0,
                    date = it["orderDate"] as? String ?: "",
                    orderer = it["customerName"] as? String ?: ""
                )
            }.sortedWith(compareBy({ it.date }, { it.orderId })))
        } catch (e: Exception) {
            e.printStackTrace()
            arrayListOf()
        }
    }

    suspend fun getReport(start: String, end: String): SalesReport {
        return try {
            val startDate = LocalDate.parse(start, dateFormatter)
            val endDate = LocalDate.parse(end, dateFormatter)
            val rollups = ArrayList<DailyRollup>()
            var d = startDate
            while (!d.isAfter(endDate)) {
                val snap = db.collection(FirestoreSchema.REPORT_ROLLUPS)
                    .document(d.format(dateFormatter)).get().await()
                if (snap.exists()) {
                    @Suppress("UNCHECKED_CAST")
                    val ms = (snap.get("menuSales") as? Map<String, Map<String, Any>>) ?: emptyMap()
                    @Suppress("UNCHECKED_CAST")
                    val ps = (snap.get("paymentSales") as? Map<String, Any>) ?: emptyMap()
                    rollups.add(
                        DailyRollup(
                            menuSales = ms.mapValues {
                                MenuSale(
                                    (it.value["quantity"] as? Number)?.toInt() ?: 0,
                                    (it.value["sales"] as? Number)?.toInt() ?: 0
                                )
                            },
                            paymentSales = ps.mapValues { (it.value as? Number)?.toInt() ?: 0 }
                        )
                    )
                }
                d = d.plusDays(1)
            }
            ReportAggregation.combine(rollups)
        } catch (e: Exception) {
            e.printStackTrace()
            SalesReport(emptyList(), mapOf("총합" to 0))
        }
    }
}
