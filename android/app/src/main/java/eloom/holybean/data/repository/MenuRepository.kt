package eloom.holybean.data.repository

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import eloom.holybean.data.firestore.FirestoreSchema
import eloom.holybean.data.model.MenuItem
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MenuRepository @Inject constructor(
    private val db: FirebaseFirestore
) {
    @Volatile
    private var cachedMenu: List<MenuItem>? = null

    /** 스플래시에서 채운 메뉴 캐시. 없으면 null. */
    fun getCachedMenu(): List<MenuItem>? = cachedMenu

    private fun menuDoc() = db.collection(FirestoreSchema.MENU).document(FirestoreSchema.MENU_CURRENT_DOC)

    private fun parse(raw: Any?): List<MenuItem> {
        @Suppress("UNCHECKED_CAST")
        val items = (raw as? List<Map<String, Any>>) ?: emptyList()
        return items.map {
            MenuItem(
                id = (it["id"] as? Number)?.toInt() ?: 0,
                name = it["name"] as? String ?: "",
                price = (it["price"] as? Number)?.toInt() ?: 0,
                order = (it["placement"] as? Number)?.toInt() ?: 0,
                inuse = it["inuse"] as? Boolean ?: true
            )
        }
    }

    private fun serialize(items: List<MenuItem>): List<Map<String, Any>> = items.map {
        mapOf("id" to it.id, "name" to it.name, "price" to it.price, "placement" to it.order, "inuse" to it.inuse)
    }

    /** menu/current 실시간 구독. */
    fun getMenuList(): Flow<List<MenuItem>> = callbackFlow {
        val reg = menuDoc().addSnapshotListener { snap, err ->
            if (err != null) { close(err); return@addSnapshotListener }
            trySend(parse(snap?.get("items")).sortedBy { it.id })
        }
        awaitClose { reg.remove() }
    }

    suspend fun getMenuListSync(): List<MenuItem> =
        parse(menuDoc().get().await().get("items")).sortedBy { it.id }
            .also { cachedMenu = it }

    private suspend fun writeAll(items: List<MenuItem>) {
        menuDoc().set(mapOf("items" to serialize(items), "updatedAt" to FieldValue.serverTimestamp())).await()
    }

    suspend fun overwriteMenuList(menuList: List<MenuItem>) = writeAll(menuList.sortedBy { it.id })

    /** 카테고리 내 placement 변경 저장: 해당 항목들의 order 갱신 후 전체 재기록. */
    suspend fun saveMenuOrders(items: List<MenuItem>) {
        val current = getMenuListSync().associateBy { it.id }.toMutableMap()
        items.forEach { current[it.id] = it }
        writeAll(current.values.sortedBy { it.id })
    }

    suspend fun updateSpecificMenu(item: MenuItem) {
        val current = getMenuListSync().associateBy { it.id }.toMutableMap()
        current[item.id] = item
        writeAll(current.values.sortedBy { it.id })
    }

    suspend fun addMenu(item: MenuItem) {
        val current = getMenuListSync().filter { it.id != item.id }
        writeAll((current + item).sortedBy { it.id })
    }

    suspend fun isValidMenuName(newName: String): Boolean =
        getMenuListSync().none { it.name == newName }

    suspend fun getNextAvailableIdForCategory(category: Int): Int =
        nextAvailable(getMenuListSync().map { it.id }, category)

    suspend fun getNextAvailablePlacementForCategory(category: Int): Int =
        nextAvailable(getMenuListSync().map { it.order }, category)

    private fun nextAvailable(values: List<Int>, category: Int): Int {
        val startRange = category * 1000 + 1
        val endRange = (category + 1) * 1000 - 1
        val sorted = values.filter { it in startRange..endRange }.sorted()
        var next = startRange
        for (v in sorted) {
            if (v > next) break
            next = v + 1
        }
        return if (next <= endRange) next else -1
    }
}
