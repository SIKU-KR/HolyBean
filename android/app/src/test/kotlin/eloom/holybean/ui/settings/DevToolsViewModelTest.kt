package eloom.holybean.ui.settings

import com.google.firebase.crashlytics.FirebaseCrashlytics
import eloom.holybean.data.repository.FirestoreRepository
import eloom.holybean.diag.NetworkStatus
import eloom.holybean.diag.NetworkStatusProvider
import eloom.holybean.printer.PrintClient
import eloom.holybean.printer.transport.PrintTransportSelector
import eloom.holybean.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class DevToolsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val printClient: PrintClient = mockk(relaxed = true)
    private val firestore: FirestoreRepository = mockk(relaxed = true)
    private val network: NetworkStatusProvider = mockk(relaxed = true)
    private val selector: PrintTransportSelector = mockk(relaxed = true)

    @Before
    fun setUp() {
        mockkStatic(FirebaseCrashlytics::class)
        every { FirebaseCrashlytics.getInstance() } returns mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun vm() = DevToolsViewModel(printClient, firestore, network, selector)

    @Test fun `refresh populates printer network and firestore status`() = runTest {
        coEvery { printClient.checkHealth() } returns true
        coEvery { firestore.checkConnection() } returns true
        every { network.current() } returns NetworkStatus(true, "Wi-Fi")
        val vm = vm()
        vm.refresh()
        advanceUntilIdle()
        val s = vm.uiState.value
        assertEquals(true, s.printerOk)
        assertEquals(true, s.networkOk)
        assertEquals("Wi-Fi", s.networkInfo)
        assertEquals(true, s.firestoreOk)
        assertTrue(s.printerLatencyMs != null)
    }

    @Test fun `refresh reflects failures`() = runTest {
        coEvery { printClient.checkHealth() } returns false
        coEvery { firestore.checkConnection() } returns false
        every { network.current() } returns NetworkStatus(false, "연결 없음")
        val vm = vm()
        vm.refresh()
        advanceUntilIdle()
        val s = vm.uiState.value
        assertEquals(false, s.printerOk)
        assertEquals(false, s.networkOk)
        assertEquals(false, s.firestoreOk)
    }

    @Test fun `test print delegates to client`() = runTest {
        val vm = vm()
        vm.testPrint()
        advanceUntilIdle()
        coVerify { printClient.printTestReceipt() }
    }

    @Test fun `test print failure emits show toast`() = runTest {
        coEvery { printClient.printTestReceipt() } throws RuntimeException("boom")
        val vm = vm()
        val events = mutableListOf<DevToolsViewModel.DevToolsUiEvent>()
        val collector = launch(mainDispatcherRule.dispatcher) {
            vm.uiEvent.collect { events.add(it) }
        }
        vm.testPrint()
        advanceUntilIdle()
        assertTrue(events.any { it is DevToolsViewModel.DevToolsUiEvent.ShowToast })
        collector.cancel()
    }

    @Test fun `rescanPrinter probes selector`() = runTest {
        val vm = vm()
        vm.rescanPrinter()
        advanceUntilIdle()
        coVerify(exactly = 1) { selector.probe() }
    }
}
