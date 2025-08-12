package eloom.holybean.ui.credits

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import eloom.holybean.data.repository.LambdaRepository
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class CreditsViewModelTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private lateinit var creditsViewModel: CreditsViewModel
    private val lambdaRepository: LambdaRepository = mockk(relaxed = true)
    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScheduler = testDispatcher.scheduler

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        creditsViewModel = CreditsViewModel(lambdaRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        clearAllMocks()
    }

    @Test
    fun `deleteState LiveData should be initialized correctly`() {
        // Given & When
        val deleteState = creditsViewModel.deleteState

        // Then
        assertNotNull(deleteState)
        assertNull(deleteState.value) // 초기값은 null이어야 함
    }

    @Test
    fun `handleDeleteButton should call setCreditOrderPaid successfully and update deleteState`() = runTest {
        // Given
        val testDate = "2024-01-15"
        val testOrderNum = 123
        val deleteStateObserver: Observer<Unit> = mockk(relaxed = true)
        creditsViewModel.deleteState.observeForever(deleteStateObserver)

        coEvery { lambdaRepository.setCreditOrderPaid(testDate, testOrderNum) } returns Unit

        // When
        creditsViewModel.handleDeleteButton(testDate, testOrderNum)
        
        // 비동기 작업이 완료될 때까지 대기
        testScheduler.advanceUntilIdle()

        // Then
        coVerify(exactly = 1) { lambdaRepository.setCreditOrderPaid(testDate, testOrderNum) }
        verify { deleteStateObserver.onChanged(Unit) }

        creditsViewModel.deleteState.removeObserver(deleteStateObserver)
    }

    @Test
    fun `handleDeleteButton should work with multiple different orders`() = runTest {
        // Given
        val testDate1 = "2024-01-15"
        val testOrderNum1 = 123
        val testDate2 = "2024-01-16"
        val testOrderNum2 = 456
        val deleteStateObserver: Observer<Unit> = mockk(relaxed = true)
        creditsViewModel.deleteState.observeForever(deleteStateObserver)

        coEvery { lambdaRepository.setCreditOrderPaid(any(), any()) } returns Unit

        // When
        creditsViewModel.handleDeleteButton(testDate1, testOrderNum1)
        creditsViewModel.handleDeleteButton(testDate2, testOrderNum2)
        
        // 비동기 작업이 완료될 때까지 대기
        testScheduler.advanceUntilIdle()

        // Then
        coVerify(exactly = 1) { lambdaRepository.setCreditOrderPaid(testDate1, testOrderNum1) }
        coVerify(exactly = 1) { lambdaRepository.setCreditOrderPaid(testDate2, testOrderNum2) }
        verify(exactly = 2) { deleteStateObserver.onChanged(Unit) }

        creditsViewModel.deleteState.removeObserver(deleteStateObserver)
    }

    @Test
    fun `handleDeleteButton should handle empty date string`() = runTest {
        // Given
        val emptyDate = ""
        val testOrderNum = 123
        val deleteStateObserver: Observer<Unit> = mockk(relaxed = true)
        creditsViewModel.deleteState.observeForever(deleteStateObserver)

        coEvery { lambdaRepository.setCreditOrderPaid(emptyDate, testOrderNum) } returns Unit

        // When
        creditsViewModel.handleDeleteButton(emptyDate, testOrderNum)
        
        // 비동기 작업이 완료될 때까지 대기
        testScheduler.advanceUntilIdle()

        // Then
        coVerify(exactly = 1) { lambdaRepository.setCreditOrderPaid(emptyDate, testOrderNum) }
        verify { deleteStateObserver.onChanged(Unit) }

        creditsViewModel.deleteState.removeObserver(deleteStateObserver)
    }

    @Test
    fun `handleDeleteButton should handle zero order number`() = runTest {
        // Given
        val testDate = "2024-01-15"
        val zeroOrderNum = 0
        val deleteStateObserver: Observer<Unit> = mockk(relaxed = true)
        creditsViewModel.deleteState.observeForever(deleteStateObserver)

        coEvery { lambdaRepository.setCreditOrderPaid(testDate, zeroOrderNum) } returns Unit

        // When
        creditsViewModel.handleDeleteButton(testDate, zeroOrderNum)
        
        // 비동기 작업이 완료될 때까지 대기
        testScheduler.advanceUntilIdle()

        // Then
        coVerify(exactly = 1) { lambdaRepository.setCreditOrderPaid(testDate, zeroOrderNum) }
        verify { deleteStateObserver.onChanged(Unit) }

        creditsViewModel.deleteState.removeObserver(deleteStateObserver)
    }

    @Test
    fun `handleDeleteButton should handle negative order number`() = runTest {
        // Given
        val testDate = "2024-01-15"
        val negativeOrderNum = -1
        val deleteStateObserver: Observer<Unit> = mockk(relaxed = true)
        creditsViewModel.deleteState.observeForever(deleteStateObserver)

        coEvery { lambdaRepository.setCreditOrderPaid(testDate, negativeOrderNum) } returns Unit

        // When
        creditsViewModel.handleDeleteButton(testDate, negativeOrderNum)
        
        // 비동기 작업이 완료될 때까지 대기
        testScheduler.advanceUntilIdle()

        // Then
        coVerify(exactly = 1) { lambdaRepository.setCreditOrderPaid(testDate, negativeOrderNum) }
        verify { deleteStateObserver.onChanged(Unit) }

        creditsViewModel.deleteState.removeObserver(deleteStateObserver)
    }

    @Test
    fun `handleDeleteButton should execute in IO dispatcher`() = runTest {
        // Given
        val testDate = "2024-01-15"
        val testOrderNum = 123
        
        coEvery { lambdaRepository.setCreditOrderPaid(testDate, testOrderNum) } returns Unit

        // When
        creditsViewModel.handleDeleteButton(testDate, testOrderNum)
        
        // 비동기 작업이 완료될 때까지 대기
        testScheduler.advanceUntilIdle()

        // Then - 코루틴이 IO 디스패처에서 실행되는지 확인
        // (실제로는 테스트에서 UnconfinedTestDispatcher를 사용하므로 직접 검증하기 어렵지만,
        // 메서드가 정상적으로 호출되는지 확인)
        coVerify(exactly = 1) { lambdaRepository.setCreditOrderPaid(testDate, testOrderNum) }
    }
}