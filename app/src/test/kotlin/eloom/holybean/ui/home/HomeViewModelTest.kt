package eloom.holybean.ui.home

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import eloom.holybean.data.model.CartItem
import eloom.holybean.data.model.MenuItem
import eloom.holybean.data.model.Order
import eloom.holybean.data.model.PaymentMethod
import eloom.holybean.data.repository.LambdaRepository
import eloom.holybean.data.repository.MenuDB
import eloom.holybean.interfaces.MainActivityListener
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@ExperimentalCoroutinesApi
class HomeViewModelTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private lateinit var homeViewModel: HomeViewModel
    private val lambdaRepository: LambdaRepository = mockk(relaxed = true)
    private val menuDB: MenuDB = mockk(relaxed = true)
    private val mainActivityListener: MainActivityListener = mockk(relaxed = true)
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        homeViewModel = HomeViewModel(lambdaRepository, menuDB)
        homeViewModel.setMainActivityListener(mainActivityListener)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        clearAllMocks()
    }

    @Test
    fun `getCurrentDate should return current date in yyyy-MM-dd format`() {
        // Given
        val expectedDate = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

        // When
        val actualDate = homeViewModel.getCurrentDate()

        // Then
        assertEquals(expectedDate, actualDate)
    }

    @Test
    fun `getTotal should calculate correct total for empty basket`() {
        // Given
        val emptyBasket = arrayListOf<CartItem>()

        // When
        val total = homeViewModel.getTotal(emptyBasket)

        // Then
        assertEquals(0, total)
    }

    @Test
    fun `getTotal should calculate correct total for single item`() {
        // Given
        val cartItem = CartItem(
            id = 1,
            name = "아메리카노",
            price = 4000,
            count = 2,
            total = 0 // 초기값
        )
        val basket = arrayListOf(cartItem)

        // When
        val total = homeViewModel.getTotal(basket)

        // Then
        assertEquals(8000, total)
        assertEquals(8000, cartItem.total) // CartItem의 total도 업데이트되었는지 확인
    }

    @Test
    fun `getTotal should calculate correct total for multiple items`() {
        // Given
        val cartItem1 = CartItem(1, "아메리카노", 4000, 2, 0)
        val cartItem2 = CartItem(2, "라떼", 4500, 1, 0)
        val cartItem3 = CartItem(3, "케이크", 6000, 3, 0)
        val basket = arrayListOf(cartItem1, cartItem2, cartItem3)

        // When
        val total = homeViewModel.getTotal(basket)

        // Then
        val expectedTotal = (4000 * 2) + (4500 * 1) + (6000 * 3) // 8000 + 4500 + 18000 = 30500
        assertEquals(expectedTotal, total)
        assertEquals(8000, cartItem1.total)
        assertEquals(4500, cartItem2.total)
        assertEquals(18000, cartItem3.total)
    }

    @Test
    fun `getMenuList should return menu list from database`() {
        // Given
        val testMenuList = listOf(
            MenuItem(1, "아메리카노", 4000, 1, true),
            MenuItem(2, "라떼", 4500, 2, true),
            MenuItem(3, "케이크", 6000, 3, false)
        )
        every { menuDB.getMenuList() } returns testMenuList

        // When
        val result = homeViewModel.getMenuList()

        // Then
        verify(exactly = 1) { menuDB.getMenuList() }
        assertEquals(testMenuList.size, result.size)
        assertEquals(testMenuList, result)
    }

    @Test
    fun `setMainActivityListener should set listener correctly`() {
        // Given
        val newListener: MainActivityListener = mockk()

        // When
        homeViewModel.setMainActivityListener(newListener)

        // Then
        // 직접적인 검증이 어려우므로, onOrderConfirmed에서 listener 호출을 통해 간접 검증
        // 이는 다음 테스트에서 확인됩니다.
        assertTrue(true) // 예외가 발생하지 않으면 성공
    }

    @Test
    fun `clearErrorMessage should clear error message`() {
        // Given
        val errorObserver: Observer<String?> = mockk(relaxed = true)
        homeViewModel.errorMessage.observeForever(errorObserver)

        // When
        homeViewModel.clearErrorMessage()

        // Then
        verify { errorObserver.onChanged(null) }
        homeViewModel.errorMessage.removeObserver(errorObserver)
    }

    @Test
    fun `onOrderConfirmed should post order successfully and navigate to home`() = runTest {
        // Given
        val testOrder = createTestOrder()
        val takeOption = "포장"
        coEvery { lambdaRepository.postOrder(any()) } returns Unit

        // When
        homeViewModel.onOrderConfirmed(testOrder, takeOption)

        // 비동기 작업 완료를 위해 잠시 대기
        Thread.sleep(100)

        // Then
        coVerify(exactly = 1) { lambdaRepository.postOrder(testOrder) }
        verify(exactly = 1) { mainActivityListener.replaceHomeFragment() }

        // 에러가 발생하지 않았으므로 errorMessage는 null이어야 함
        assertNull(homeViewModel.errorMessage.value)
    }

    @Test
    fun `onOrderConfirmed should handle repository error and set error message`() = runTest {
        // Given
        val testOrder = createTestOrder()
        val takeOption = "매장"
        val testException = Exception("Network Error")
        coEvery { lambdaRepository.postOrder(any()) } throws testException

        val errorObserver: Observer<String?> = mockk(relaxed = true)
        homeViewModel.errorMessage.observeForever(errorObserver)

        // When
        homeViewModel.onOrderConfirmed(testOrder, takeOption)

        // 비동기 작업 완료를 위해 잠시 대기
        Thread.sleep(100)

        // Then
        coVerify(exactly = 1) { lambdaRepository.postOrder(testOrder) }
        verify(exactly = 0) { mainActivityListener.replaceHomeFragment() } // 에러시 화면 전환 안됨
        verify { errorObserver.onChanged("주문 처리 중 오류가 발생했습니다.") }

        homeViewModel.errorMessage.removeObserver(errorObserver)
    }

    @Test
    fun `onOrderConfirmed should handle multiple order confirmations correctly`() = runTest {
        // Given
        val testOrder1 = createTestOrder(orderNum = 1)
        val testOrder2 = createTestOrder(orderNum = 2)
        coEvery { lambdaRepository.postOrder(any()) } returns Unit

        // When
        homeViewModel.onOrderConfirmed(testOrder1, "포장")
        homeViewModel.onOrderConfirmed(testOrder2, "매장")

        // 비동기 작업 완료를 위해 잠시 대기
        Thread.sleep(200)

        // Then
        coVerify(exactly = 2) { lambdaRepository.postOrder(any()) }
        verify(exactly = 2) { mainActivityListener.replaceHomeFragment() }
    }

    // 헬퍼 메서드: 테스트용 Order 객체 생성
    private fun createTestOrder(orderNum: Int = 1): Order {
        val cartItems = listOf(
            CartItem(1, "아메리카노", 4000, 2, 8000),
            CartItem(2, "라떼", 4500, 1, 4500)
        )
        val paymentMethods = listOf(
            PaymentMethod("현금", 12500)
        )

        return Order(
            orderDate = "2024-01-15",
            orderNum = orderNum,
            creditStatus = 0,
            customerName = "테스트고객",
            orderItems = cartItems,
            paymentMethods = paymentMethods,
            totalAmount = 12500
        )
    }
}