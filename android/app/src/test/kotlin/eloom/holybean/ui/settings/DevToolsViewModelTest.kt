package eloom.holybean.ui.settings

import com.google.firebase.crashlytics.FirebaseCrashlytics
import eloom.holybean.data.repository.FirestoreRepository
import eloom.holybean.diag.NetworkStatus
import eloom.holybean.diag.NetworkStatusProvider
import eloom.holybean.printer.PiPrintClient
import eloom.holybean.printer.network.PrinterAddressResolver
import eloom.holybean.printer.network.PrinterStatus
import eloom.holybean.printer.transport.PrintMethod
import eloom.holybean.printer.transport.PrintTransportSelector
import eloom.holybean.printer.transport.PrinterTransportStore
import eloom.holybean.printer.transport.TransportSelection
import eloom.holybean.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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

    private val pi: PiPrintClient = mockk(relaxed = true)
    private val firestore: FirestoreRepository = mockk(relaxed = true)
    private val network: NetworkStatusProvider = mockk(relaxed = true)
    private val resolver: PrinterAddressResolver = mockk(relaxed = true)
    private val selector: PrintTransportSelector = mockk(relaxed = true)
    private val store: PrinterTransportStore = mockk(relaxed = true)

    @Before
    fun setUp() {
        mockkStatic(FirebaseCrashlytics::class)
        every { FirebaseCrashlytics.getInstance() } returns mockk(relaxed = true)
        every { resolver.status } returns MutableStateFlow(PrinterStatus.Unknown)
        every { selector.selection } returns MutableStateFlow(
            TransportSelection(PrintMethod.PI_HTTP),
        )
        every { store.forcePi } returns false
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun vm() = DevToolsViewModel(pi, firestore, network, resolver, selector, store)

    @Test fun `refresh populates printer network and firestore status`() = runTest {
        coEvery { pi.checkHealth() } returns true
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
        coEvery { pi.checkHealth() } returns false
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
        coVerify { pi.printTestReceipt() }
    }

    @Test fun `test print failure emits show toast`() = runTest {
        coEvery { pi.printTestReceipt() } throws RuntimeException("boom")
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

    @Test fun `rescanPrinter probes selector and rediscovers resolver`() = runTest {
        val vm = vm()
        vm.rescanPrinter()
        advanceUntilIdle()
        coVerify(exactly = 1) { selector.probe() }
        coVerify(exactly = 1) { resolver.rediscover() }
    }

    @Test fun `setForcePi updates store and probes selector`() = runTest {
        val vm = vm()
        vm.setForcePi(true)
        advanceUntilIdle()
        coVerify { store.forcePi = true }
        coVerify(exactly = 1) { selector.probe() }
    }

    @Test fun `setPrinterOverride delegates to resolver`() = runTest {
        val vm = vm()
        vm.setPrinterOverride("10.0.0.9:9100")
        advanceUntilIdle()
        coVerify(exactly = 1) { resolver.setManualOverride("10.0.0.9:9100") }
    }
}
