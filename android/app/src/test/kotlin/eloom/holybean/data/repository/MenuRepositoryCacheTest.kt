package eloom.holybean.data.repository

import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MenuRepositoryCacheTest {

    private fun repoReturning(items: List<Map<String, Any>>): MenuRepository {
        val db: FirebaseFirestore = mockk()
        val collection: CollectionReference = mockk()
        val docRef: DocumentReference = mockk()
        val snap: DocumentSnapshot = mockk()
        every { db.collection(any()) } returns collection
        every { collection.document(any()) } returns docRef
        every { docRef.get() } returns Tasks.forResult(snap)
        every { snap.get("items") } returns items
        return MenuRepository(db)
    }

    @Test fun `getCachedMenu is null before any load`() {
        val repo = repoReturning(emptyList())
        assertNull(repo.getCachedMenu())
    }

    @Test fun `getMenuListSync populates cache with same list`() = runTest {
        val repo = repoReturning(
            listOf(mapOf("id" to 1001, "name" to "아메리카노", "price" to 4000, "placement" to 1, "inuse" to true))
        )
        val loaded = repo.getMenuListSync()
        assertEquals(loaded, repo.getCachedMenu())
        assertEquals(1, repo.getCachedMenu()!!.size)
        assertEquals(1001, repo.getCachedMenu()!!.first().id)
    }
}
