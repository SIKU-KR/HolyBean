package eloom.holybean.data.repository

import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import eloom.holybean.data.firestore.FirestoreSchema
import eloom.holybean.data.firestore.OrderAggregation
import eloom.holybean.data.firestore.ReportAggregation
import eloom.holybean.data.firestore.ReportAggregation.DailyRollup
import eloom.holybean.data.firestore.ReportAggregation.MenuSale
import eloom.holybean.data.model.*
import eloom.holybean.di.IoDispatcher
import eloom.holybean.exception.DataException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreRepository @Inject constructor(
    private val db: FirebaseFirestore,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private fun today(): String = LocalDate.now().format(dateFormatter)

    suspend fun getOrderNumber(): Int = withContext(ioDispatcher) {
        val snap = db.collection(FirestoreSchema.DAY_SUMMARIES).document(today()).get().await()
        (snap.getLong("lastOrderNum") ?: 0L).toInt() + 1
    }

    /** 개발자도구용 best-effort 연결 점검. 실패도 결과이므로 예외를 던지지 않고 false 반환. */
    suspend fun checkConnection(): Boolean = withContext(ioDispatcher) {
        try {
            withTimeout(3000) {
                db.collection(FirestoreSchema.DAY_SUMMARIES).document(today()).get().await()
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun getOrdersOfDay(): ArrayList<OrderItem> = withContext(ioDispatcher) {
        val snap = db.collection(FirestoreSchema.DAY_SUMMARIES).document(today()).get().await()
        @Suppress("UNCHECKED_CAST")
        val orders = (snap.get("orders") as? Map<String, Map<String, Any>>) ?: emptyMap()
        val list = orders.entries
            .sortedBy { it.key.toIntOrNull() ?: 0 }
            .map { (num, m) ->
                OrderItem(
                    orderId = num.toIntOrNull() ?: 0,
                    totalAmount = (m["totalAmount"] as? Number)?.toInt() ?: 0,
                    method = m["orderMethod"] as? String ?: "Unknown",
                    orderer = m["customerName"] as? String ?: ""
                )
            }
        ArrayList(list)
    }

    suspend fun getOrderDetail(date: String, num: Int): ArrayList<OrdersDetailItem> = withContext(ioDispatcher) {
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
    }

    suspend fun getCreditsList(): ArrayList<CreditItem> = withContext(ioDispatcher) {
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
    }

    suspend fun getReport(start: String, end: String): SalesReport = withContext(ioDispatcher) {
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
    }

    /**
     * 주문을 저장하고 서버 commit ack까지 대기한다(결제 완료 흐름이 저장 성공을 보장받아야 하므로).
     * 오프라인 등으로 ack가 지연되면 무한 대기하지 않도록 타임아웃을 둔다 — 타임아웃 시 예외를 던져
     * 호출부가 저장 실패로 처리(재시도)하게 한다.
     */
    suspend fun postOrder(data: Order): Unit = withContext(ioDispatcher) {
        val batch = db.batch()
        val orderRef = db.collection(FirestoreSchema.ORDERS)
            .document(FirestoreSchema.orderId(data.orderDate, data.orderNum))
        batch.set(orderRef, OrderAggregation.orderDoc(data) + mapOf("createdAt" to FieldValue.serverTimestamp()))

        val dayRef = db.collection(FirestoreSchema.DAY_SUMMARIES).document(data.orderDate)
        batch.set(
            dayRef,
            mapOf(
                "lastOrderNum" to data.orderNum,
                "orders" to mapOf(data.orderNum.toString() to OrderAggregation.daySummaryEntry(data))
            ),
            SetOptions.merge()
        )

        if (data.creditStatus == FirestoreSchema.CREDIT_SETTLED) {
            applyRollupDelta(batch, data.orderDate, OrderAggregation.rollupDelta(data), sign = 1)
        } else {
            val creditsRef = db.collection(FirestoreSchema.AGGREGATES)
                .document(FirestoreSchema.OPEN_CREDITS_DOC)
            batch.set(
                creditsRef,
                mapOf("items" to mapOf(
                    FirestoreSchema.creditKey(data.orderDate, data.orderNum) to mapOf(
                        "customerName" to data.customerName,
                        "totalAmount" to data.totalAmount,
                        "orderNum" to data.orderNum,
                        "orderDate" to data.orderDate
                    )
                )),
                SetOptions.merge()
            )
        }
        try {
            withTimeout(POST_ORDER_ACK_TIMEOUT_MS) { batch.commit().await() }  // 서버 ack 대기
        } catch (e: TimeoutCancellationException) {
            throw DataException.Timeout(e)   // 정상 취소가 아니라 저장 실패로 다룬다
        }
    }

    private companion object {
        const val POST_ORDER_ACK_TIMEOUT_MS = 10_000L
    }

    private fun applyRollupDelta(
        batch: com.google.firebase.firestore.WriteBatch,
        date: String,
        delta: OrderAggregation.RollupDelta,
        sign: Int
    ) {
        val ref = db.collection(FirestoreSchema.REPORT_ROLLUPS).document(date)
        val menu = delta.menuSales.mapValues { (_, v) ->
            mapOf(
                "quantity" to FieldValue.increment((v.quantity * sign).toLong()),
                "sales" to FieldValue.increment((v.sales * sign).toLong())
            )
        }
        val pay = delta.paymentSales.mapValues { (_, v) -> FieldValue.increment((v * sign).toLong()) }
        batch.set(
            ref,
            mapOf(
                "menuSales" to menu,
                "paymentSales" to pay,
                "total" to FieldValue.increment((delta.total * sign).toLong())
            ),
            SetOptions.merge()
        )
    }

    /** 외상 정산(1→0): 원 주문일 reportRollups에 가산. 주문 본문을 읽어 델타 계산. */
    suspend fun setCreditOrderPaid(date: String, num: Int) = withContext(ioDispatcher) {
        val orderRef = db.collection(FirestoreSchema.ORDERS)
            .document(FirestoreSchema.orderId(date, num))
        val snap = orderRef.get().await()
        if (!snap.exists()) return@withContext
        if ((snap.getLong("creditStatus") ?: 0L).toInt() == FirestoreSchema.CREDIT_SETTLED) return@withContext

        @Suppress("UNCHECKED_CAST")
        val items = (snap.get("items") as? List<Map<String, Any>>) ?: emptyList()
        @Suppress("UNCHECKED_CAST")
        val payments = (snap.get("payments") as? List<Map<String, Any>>) ?: emptyList()

        val cartItems = items.map {
            eloom.holybean.data.model.CartItem(
                0, it["name"] as? String ?: "", (it["unitPrice"] as? Number)?.toInt() ?: 0,
                (it["quantity"] as? Number)?.toInt() ?: 0, (it["subtotal"] as? Number)?.toInt() ?: 0
            )
        }
        val paymentMethods = payments.map {
            eloom.holybean.data.model.PaymentMethod(
                it["method"] as? String ?: "", (it["amount"] as? Number)?.toInt() ?: 0
            )
        }
        val delta = OrderAggregation.rollupDelta(cartItems, paymentMethods)

        val batch = db.batch()
        batch.update(orderRef, "creditStatus", FirestoreSchema.CREDIT_SETTLED)
        batch.update(
            db.collection(FirestoreSchema.DAY_SUMMARIES).document(date),
            "orders.$num.creditStatus", FirestoreSchema.CREDIT_SETTLED
        )
        batch.update(
            db.collection(FirestoreSchema.AGGREGATES).document(FirestoreSchema.OPEN_CREDITS_DOC),
            FieldPath.of("items", FirestoreSchema.creditKey(date, num)), FieldValue.delete()
        )
        applyRollupDelta(batch, date, delta, sign = 1)
        batch.commit().await()
    }

    /** 주문 삭제: 정산분이면 reportRollups 감산, 미수면 openCredits 제거. lastOrderNum은 되돌리지 않음(번호 재사용 금지). */
    suspend fun deleteOrder(date: String, num: Int): Boolean = withContext(ioDispatcher) {
        val orderRef = db.collection(FirestoreSchema.ORDERS)
            .document(FirestoreSchema.orderId(date, num))
        val snap = orderRef.get().await()
        if (!snap.exists()) return@withContext false
        val creditStatus = (snap.getLong("creditStatus") ?: 0L).toInt()

        val batch = db.batch()
        batch.delete(orderRef)
        batch.update(
            db.collection(FirestoreSchema.DAY_SUMMARIES).document(date),
            FieldPath.of("orders", num.toString()), FieldValue.delete()
        )
        if (creditStatus == FirestoreSchema.CREDIT_SETTLED) {
            @Suppress("UNCHECKED_CAST")
            val items = (snap.get("items") as? List<Map<String, Any>>) ?: emptyList()
            @Suppress("UNCHECKED_CAST")
            val payments = (snap.get("payments") as? List<Map<String, Any>>) ?: emptyList()
            val cartItems = items.map {
                eloom.holybean.data.model.CartItem(
                    0, it["name"] as? String ?: "", (it["unitPrice"] as? Number)?.toInt() ?: 0,
                    (it["quantity"] as? Number)?.toInt() ?: 0, (it["subtotal"] as? Number)?.toInt() ?: 0
                )
            }
            val paymentMethods = payments.map {
                eloom.holybean.data.model.PaymentMethod(
                    it["method"] as? String ?: "", (it["amount"] as? Number)?.toInt() ?: 0
                )
            }
            applyRollupDelta(batch, date, OrderAggregation.rollupDelta(cartItems, paymentMethods), sign = -1)
        } else {
            batch.update(
                db.collection(FirestoreSchema.AGGREGATES).document(FirestoreSchema.OPEN_CREDITS_DOC),
                FieldPath.of("items", FirestoreSchema.creditKey(date, num)), FieldValue.delete()
            )
        }
        batch.commit().await()
        true
    }
}
