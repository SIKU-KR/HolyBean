package eloom.holybean.ui.startup

import eloom.holybean.data.repository.FirestoreRepository
import eloom.holybean.data.repository.MenuRepository
import eloom.holybean.printer.PiPrintClient
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StartupViewModelTest {
    private val menu: MenuRepository = mockk(relaxed = true)
    private val firestore: FirestoreRepository = mockk(relaxed = true)
    private val pi: PiPrintClient = mockk(relaxed = true)

    private fun vm() = StartupViewModel(menu, firestore, pi, UnconfinedTestDispatcher())

    @Test fun `both succeed sets success and autoEnter`() = runTest {
        coEvery { menu.getMenuListSync() } returns emptyList()
        coEvery { firestore.getOrderNumber() } returns 1
        coEvery { pi.checkHealth() } returns true
        val sut = vm()
        advanceUntilIdle()
        val s = sut.uiState.value
        assertEquals(StepStatus.Success, s.data)
        assertEquals(StepStatus.Success, s.printer)
        assertTrue(s.autoEnter)
    }

    @Test fun `printer failure keeps data success but no autoEnter`() = runTest {
        coEvery { menu.getMenuListSync() } returns emptyList()
        coEvery { firestore.getOrderNumber() } returns 1
        coEvery { pi.checkHealth() } returns false
        val sut = vm()
        advanceUntilIdle()
        val s = sut.uiState.value
        assertEquals(StepStatus.Success, s.data)
        assertEquals(StepStatus.Failed, s.printer)
        assertTrue(s.canEnter)
        assertFalse(s.autoEnter)
    }

    @Test fun `menu fetch throwing marks data failed`() = runTest {
        coEvery { menu.getMenuListSync() } throws RuntimeException("net")
        coEvery { firestore.getOrderNumber() } returns 1
        coEvery { pi.checkHealth() } returns true
        val sut = vm()
        advanceUntilIdle()
        assertEquals(StepStatus.Failed, sut.uiState.value.data)
        assertFalse(sut.uiState.value.canEnter)
    }

    @Test fun `order number sentinel marks data failed`() = runTest {
        coEvery { menu.getMenuListSync() } returns emptyList()
        coEvery { firestore.getOrderNumber() } returns -1
        coEvery { pi.checkHealth() } returns true
        val sut = vm()
        advanceUntilIdle()
        assertEquals(StepStatus.Failed, sut.uiState.value.data)
    }

    @Test fun `retry recovers data from failed to success`() = runTest {
        coEvery { menu.getMenuListSync() } throws RuntimeException("net")
        coEvery { firestore.getOrderNumber() } returns 1
        coEvery { pi.checkHealth() } returns true
        val sut = vm()
        advanceUntilIdle()
        assertEquals(StepStatus.Failed, sut.uiState.value.data)

        // 네트워크 회복 후 재시도
        coEvery { menu.getMenuListSync() } returns emptyList()
        sut.retry()
        advanceUntilIdle()
        assertEquals(StepStatus.Success, sut.uiState.value.data)
        assertTrue(sut.uiState.value.autoEnter)
    }
}
