package eloom.holybean.ui.settings

import eloom.holybean.printer.PiPrintClient
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DevToolsViewModelTest {
    private val pi: PiPrintClient = mockk(relaxed = true)

    @Test fun `health success sets printer ok`() = runTest {
        coEvery { pi.checkHealth() } returns true
        val vm = DevToolsViewModel(pi, UnconfinedTestDispatcher())
        vm.refresh()
        advanceUntilIdle()
        assertEquals(true, vm.uiState.value.printerOk)
    }

    @Test fun `health failure sets printer not ok`() = runTest {
        coEvery { pi.checkHealth() } returns false
        val vm = DevToolsViewModel(pi, UnconfinedTestDispatcher())
        vm.refresh()
        advanceUntilIdle()
        assertEquals(false, vm.uiState.value.printerOk)
    }

    @Test fun `test print delegates to client`() = runTest {
        val vm = DevToolsViewModel(pi, UnconfinedTestDispatcher())
        vm.testPrint()
        advanceUntilIdle()
        coVerify { pi.printTestReceipt() }
    }

    @Test fun `test print failure emits show toast`() = runTest {
        coEvery { pi.printTestReceipt() } throws RuntimeException("boom")
        val vm = DevToolsViewModel(pi, UnconfinedTestDispatcher())
        val events = mutableListOf<DevToolsViewModel.DevToolsUiEvent>()
        val collector = launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiEvent.collect { events.add(it) }
        }
        vm.testPrint()
        advanceUntilIdle()
        assertTrue(events.any { it is DevToolsViewModel.DevToolsUiEvent.ShowToast })
        collector.cancel()
    }
}
