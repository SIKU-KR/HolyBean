package eloom.holybean.util

import com.google.firebase.crashlytics.FirebaseCrashlytics
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@ExperimentalCoroutinesApi
class CoroutineExtensionsTest {

    @Before fun setUp() {
        mockkStatic(FirebaseCrashlytics::class)
        every { FirebaseCrashlytics.getInstance() } returns mockk(relaxed = true)
    }

    @After fun tearDown() { unmockkAll() }

    @Test fun `runs block on success and does not call onError`() = runTest {
        var ran = false
        var err: Throwable? = null
        launchSafely(onError = { err = it }) { ran = true }
        advanceUntilIdle()
        assertTrue(ran)
        assertNull(err)
    }

    @Test fun `routes ordinary exception to onError`() = runTest {
        var err: Throwable? = null
        launchSafely(onError = { err = it }) { throw IllegalStateException("boom") }
        advanceUntilIdle()
        assertTrue(err is IllegalStateException)
    }

    @Test fun `rethrows CancellationException and does not call onError`() = runTest {
        var onErrorCalled = false
        val started = CompletableDeferred<Unit>()
        val job = launch {
            launchSafely(onError = { onErrorCalled = true }) {
                started.complete(Unit)
                delay(10_000)
            }
        }
        started.await()
        job.cancel()
        advanceUntilIdle()
        assertEquals(false, onErrorCalled)
    }

    @Test fun `TimeoutCancellationException is rethrown not routed to onError`() = runTest {
        var onErrorCalled = false
        launchSafely(onError = { onErrorCalled = true }) {
            withTimeout(1) { delay(100) }
        }
        advanceUntilIdle()
        assertEquals(false, onErrorCalled)
    }
}
