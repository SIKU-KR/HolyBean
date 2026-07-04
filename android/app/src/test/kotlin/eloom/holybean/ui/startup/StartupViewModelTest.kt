package eloom.holybean.ui.startup

import com.google.firebase.crashlytics.FirebaseCrashlytics
import eloom.holybean.data.repository.FirestoreRepository
import eloom.holybean.data.repository.MenuRepository
import eloom.holybean.printer.PiPrintClient
import eloom.holybean.printer.network.PrinterAddressResolver
import eloom.holybean.printer.transport.PrintMethod
import eloom.holybean.printer.transport.PrintTransportSelector
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
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class StartupViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val menu: MenuRepository = mockk(relaxed = true)
    private val firestore: FirestoreRepository = mockk(relaxed = true)
    private val pi: PiPrintClient = mockk(relaxed = true)
    private val resolver: PrinterAddressResolver = mockk(relaxed = true)
    private val selector: PrintTransportSelector = mockk(relaxed = true)
    private val selection = MutableStateFlow(TransportSelection(PrintMethod.PI_HTTP))

    @Before
    fun setUp() {
        mockkStatic(FirebaseCrashlytics::class)
        every { FirebaseCrashlytics.getInstance() } returns mockk(relaxed = true)
        every { selector.selection } returns selection
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun vm() = StartupViewModel(menu, firestore, pi, resolver, selector)

    @Test fun `warms up printer address on init`() = runTest {
        coEvery { menu.getMenuListSync() } returns emptyList()
        coEvery { firestore.getOrderNumber() } returns 1
        coEvery { pi.checkHealth() } returns true
        vm()
        advanceUntilIdle()
        coVerify(atLeast = 1) { selector.probe() }
        coVerify(atLeast = 1) { resolver.rediscover() }
    }

    @Test fun `warms up printer address even when USB transport selected`() = runTest {
        selection.value = TransportSelection(PrintMethod.USB_DIRECT)
        coEvery { menu.getMenuListSync() } returns emptyList()
        coEvery { firestore.getOrderNumber() } returns 1
        coEvery { pi.checkHealth() } returns true
        vm()
        advanceUntilIdle()
        coVerify(atLeast = 1) { resolver.rediscover() }
    }

    @Test fun `USB warmup failure does not fail printer step`() = runTest {
        selection.value = TransportSelection(PrintMethod.USB_DIRECT)
        coEvery { menu.getMenuListSync() } returns emptyList()
        coEvery { firestore.getOrderNumber() } returns 1
        coEvery { resolver.rediscover() } throws RuntimeException("unreachable")
        coEvery { pi.checkHealth() } returns true
        val sut = vm()
        advanceUntilIdle()
        assertEquals(StepStatus.Success, sut.uiState.value.printer)
    }

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

    @Test fun `order number failure marks data failed`() = runTest {
        coEvery { menu.getMenuListSync() } returns emptyList()
        coEvery { firestore.getOrderNumber() } throws RuntimeException("net")
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
