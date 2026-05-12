package eloom.holybean.data.repository

import eloom.holybean.data.model.CartItem
import eloom.holybean.data.model.MenuItem
import eloom.holybean.data.model.Order
import eloom.holybean.data.model.PaymentMethod
import eloom.holybean.network.ApiService
import eloom.holybean.network.RetrofitClient
import eloom.holybean.network.dto.*
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.*

@ExperimentalCoroutinesApi
class LambdaRepositoryTest {

    private lateinit var lambdaRepository: LambdaRepository
    private val mockApiService: ApiService = mockk()
    private val mockRetrofitClient: RetrofitClient = mockk()

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        // RetrofitClient.retrofit.create()를 모킹
        mockkObject(RetrofitClient)
        every { RetrofitClient.retrofit.create(ApiService::class.java) } returns mockApiService

        lambdaRepository = LambdaRepository()
    }

    @After
    fun tearDown() {
        clearAllMocks()
        unmockkAll()
    }

    // getCurrentDate 테스트 (private 메서드이지만 다른 메서드를 통해 간접 테스트)
    @Test
    fun `getCurrentDate should return current date in yyyy-MM-dd format`() {
        // Given
        val expectedDate = SimpleDateFormat("yyyy-MM-dd").format(Date())

        // When - getOrdersOfDay를 통해 getCurrentDate 동작 확인
        val mockResponse = Response.success(listOf<ResponseOrder>())
        coEvery { mockApiService.getOrderOfDay(expectedDate) } returns mockResponse

        // getCurrentDate가 올바른 형식으로 호출되는지 간접 확인
        runTest {
            lambdaRepository.getOrdersOfDay()
        }

        // Then
        coVerify { mockApiService.getOrderOfDay(expectedDate) }
    }

    // getOrderNumber 테스트
    @Test
    fun `getOrderNumber should return order number when API call is successful`() = runTest {
        // Given
        val expectedOrderNum = 123
        val responseOrderNum = ResponseOrderNum(expectedOrderNum)
        val successResponse = Response.success(responseOrderNum)
        coEvery { mockApiService.getOrderNumber() } returns successResponse

        // When
        val result = lambdaRepository.getOrderNumber()

        // Then
        assertEquals(expectedOrderNum, result)
        coVerify(exactly = 1) { mockApiService.getOrderNumber() }
    }

    @Test
    fun `getOrderNumber should return -1 when response body is null`() = runTest {
        // Given
        val nullBodyResponse = Response.success<ResponseOrderNum>(null)
        coEvery { mockApiService.getOrderNumber() } returns nullBodyResponse

        // When
        val result = lambdaRepository.getOrderNumber()

        // Then
        assertEquals(-1, result)
        coVerify(exactly = 1) { mockApiService.getOrderNumber() }
    }

    @Test
    fun `getOrderNumber should return -1 when nextOrderNum is null`() = runTest {
        // Given
        val responseOrderNum = ResponseOrderNum(null)
        val successResponse = Response.success(responseOrderNum)
        coEvery { mockApiService.getOrderNumber() } returns successResponse

        // When
        val result = lambdaRepository.getOrderNumber()

        // Then
        assertEquals(-1, result)
        coVerify(exactly = 1) { mockApiService.getOrderNumber() }
    }

    @Test
    fun `getOrderNumber should return -1 when API call fails`() = runTest {
        // Given
        val errorResponse = Response.error<ResponseOrderNum>(400, ResponseBody.create(null, "Error"))
        coEvery { mockApiService.getOrderNumber() } returns errorResponse

        // When
        val result = lambdaRepository.getOrderNumber()

        // Then
        assertEquals(-1, result)
        coVerify(exactly = 1) { mockApiService.getOrderNumber() }
    }

    @Test
    fun `getOrderNumber should return -1 when exception occurs`() = runTest {
        // Given
        coEvery { mockApiService.getOrderNumber() } throws Exception("Network error")

        // When
        val result = lambdaRepository.getOrderNumber()

        // Then
        assertEquals(-1, result)
        coVerify(exactly = 1) { mockApiService.getOrderNumber() }
    }

    // postOrder 테스트
    @Test
    fun `postOrder should complete successfully when API call succeeds`() = runTest {
        // Given
        val testOrder = createTestOrder()
        val successResponse = Response.success(Unit)
        coEvery { mockApiService.postOrder(testOrder) } returns successResponse

        // When
        lambdaRepository.postOrder(testOrder)

        // Then
        coVerify(exactly = 1) { mockApiService.postOrder(testOrder) }
    }

    @Test
    fun `postOrder should handle API error response`() = runTest {
        // Given
        val testOrder = createTestOrder()
        val errorResponse = Response.error<Unit>(400, ResponseBody.create(null, """{"message":"Bad Request"}"""))
        coEvery { mockApiService.postOrder(testOrder) } returns errorResponse

        // When
        lambdaRepository.postOrder(testOrder)

        // Then
        coVerify(exactly = 1) { mockApiService.postOrder(testOrder) }
        // 예외가 발생해도 메서드는 정상 완료되어야 함 (내부에서 처리됨)
    }

    @Test
    fun `postOrder should handle exception`() = runTest {
        // Given
        val testOrder = createTestOrder()
        coEvery { mockApiService.postOrder(testOrder) } throws Exception("Network error")

        // When
        lambdaRepository.postOrder(testOrder)

        // Then
        coVerify(exactly = 1) { mockApiService.postOrder(testOrder) }
        // 예외가 발생해도 메서드는 정상 완료되어야 함 (내부에서 처리됨)
    }

    // getOrdersOfDay 테스트
    @Test
    fun `getOrdersOfDay should return order list when API call succeeds`() = runTest {
        // Given
        val responseOrders = listOf(
            ResponseOrder("고객1", 10000, "포장", 1),
            ResponseOrder("고객2", 15000, "매장", 2)
        )
        val successResponse = Response.success(responseOrders)
        val expectedDate = SimpleDateFormat("yyyy-MM-dd").format(Date())
        coEvery { mockApiService.getOrderOfDay(expectedDate) } returns successResponse

        // When
        val result = lambdaRepository.getOrdersOfDay()

        // Then
        assertEquals(2, result.size)
        assertEquals("고객1", result[0].orderer)
        assertEquals(10000, result[0].totalAmount)
        assertEquals("포장", result[0].method)
        assertEquals(1, result[0].orderId)
        coVerify(exactly = 1) { mockApiService.getOrderOfDay(expectedDate) }
    }

    @Test
    fun `getOrdersOfDay should return empty list when API call fails`() = runTest {
        // Given
        val errorResponse = Response.error<List<ResponseOrder>>(400, ResponseBody.create(null, "Error"))
        val expectedDate = SimpleDateFormat("yyyy-MM-dd").format(Date())
        coEvery { mockApiService.getOrderOfDay(expectedDate) } returns errorResponse

        // When
        val result = lambdaRepository.getOrdersOfDay()

        // Then
        assertTrue(result.isEmpty())
        coVerify(exactly = 1) { mockApiService.getOrderOfDay(expectedDate) }
    }

    @Test
    fun `getOrdersOfDay should return empty list when exception occurs`() = runTest {
        // Given
        val expectedDate = SimpleDateFormat("yyyy-MM-dd").format(Date())
        coEvery { mockApiService.getOrderOfDay(expectedDate) } throws Exception("Network error")

        // When
        val result = lambdaRepository.getOrdersOfDay()

        // Then
        assertTrue(result.isEmpty())
        coVerify(exactly = 1) { mockApiService.getOrderOfDay(expectedDate) }
    }

    // getOrderDetail 테스트
    @Test
    fun `getOrderDetail should return order detail list when API call succeeds`() = runTest {
        // Given
        val testDate = "2024-01-15"
        val testOrderNum = 123
        val responseOrderItems = listOf(
            ResponseOrderItem(4000, "아메리카노", 2, 8000),
            ResponseOrderItem(4500, "라떼", 1, 4500)
        )
        val responseOrderDetail = ResponseOrderDetail(
            orderDate = testDate,
            customerName = "테스트고객",
            totalAmount = 12500,
            orderItems = responseOrderItems,
            paymentMethods = emptyList(),
            creditStatus = 0,
            orderNum = testOrderNum
        )
        val successResponse = Response.success(responseOrderDetail)
        coEvery { mockApiService.getSpecificOrder(testDate, testOrderNum) } returns successResponse

        // When
        val result = lambdaRepository.getOrderDetail(testDate, testOrderNum)

        // Then
        assertEquals(2, result.size)
        assertEquals("아메리카노", result[0].name)
        assertEquals(2, result[0].count)
        assertEquals(8000, result[0].subtotal)
        coVerify(exactly = 1) { mockApiService.getSpecificOrder(testDate, testOrderNum) }
    }

    @Test
    fun `getOrderDetail should return empty list when API call fails`() = runTest {
        // Given
        val testDate = "2024-01-15"
        val testOrderNum = 123
        val errorResponse = Response.error<ResponseOrderDetail>(404, ResponseBody.create(null, "Not Found"))
        coEvery { mockApiService.getSpecificOrder(testDate, testOrderNum) } returns errorResponse

        // When
        val result = lambdaRepository.getOrderDetail(testDate, testOrderNum)

        // Then
        assertTrue(result.isEmpty())
        coVerify(exactly = 1) { mockApiService.getSpecificOrder(testDate, testOrderNum) }
    }

    // deleteOrder 테스트
    @Test
    fun `deleteOrder should return true when API call succeeds`() = runTest {
        // Given
        val testDate = "2024-01-15"
        val testOrderNum = 123
        val successResponse = Response.success(Unit)
        coEvery { mockApiService.deleteOrder(testDate, testOrderNum) } returns successResponse

        // When
        val result = lambdaRepository.deleteOrder(testDate, testOrderNum)

        // Then
        assertTrue(result)
        coVerify(exactly = 1) { mockApiService.deleteOrder(testDate, testOrderNum) }
    }

    @Test
    fun `deleteOrder should return false when API call fails`() = runTest {
        // Given
        val testDate = "2024-01-15"
        val testOrderNum = 123
        val errorResponse = Response.error<Unit>(400, ResponseBody.create(null, "Error"))
        coEvery { mockApiService.deleteOrder(testDate, testOrderNum) } returns errorResponse

        // When
        val result = lambdaRepository.deleteOrder(testDate, testOrderNum)

        // Then
        assertFalse(result)
        coVerify(exactly = 1) { mockApiService.deleteOrder(testDate, testOrderNum) }
    }

    // getCreditsList 테스트
    @Test
    fun `getCreditsList should return credit list when API call succeeds`() = runTest {
        // Given
        val responseCredits = listOf(
            ResponseCredit(10000, 1, "2024-01-15", "고객1"),
            ResponseCredit(15000, 2, "2024-01-16", "고객2")
        )
        val successResponse = Response.success(responseCredits)
        coEvery { mockApiService.getAllCreditOrders() } returns successResponse

        // When
        val result = lambdaRepository.getCreditsList()

        // Then
        assertEquals(2, result.size)
        assertEquals(1, result[0].orderId)
        assertEquals(10000, result[0].totalAmount)
        assertEquals("2024-01-15", result[0].date)
        assertEquals("고객1", result[0].orderer)
        coVerify(exactly = 1) { mockApiService.getAllCreditOrders() }
    }

    @Test
    fun `getCreditsList should return empty list when API call fails`() = runTest {
        // Given
        val errorResponse = Response.error<List<ResponseCredit>>(400, ResponseBody.create(null, "Error"))
        coEvery { mockApiService.getAllCreditOrders() } returns errorResponse

        // When
        val result = lambdaRepository.getCreditsList()

        // Then
        assertTrue(result.isEmpty())
        coVerify(exactly = 1) { mockApiService.getAllCreditOrders() }
    }

    // setCreditOrderPaid 테스트
    @Test
    fun `setCreditOrderPaid should complete successfully when API call succeeds`() = runTest {
        // Given
        val testDate = "2024-01-15"
        val testOrderNum = 123
        val successResponse = Response.success(Unit)
        coEvery { mockApiService.updateCreditStatus(testDate, testOrderNum) } returns successResponse

        // When
        lambdaRepository.setCreditOrderPaid(testDate, testOrderNum)

        // Then
        coVerify(exactly = 1) { mockApiService.updateCreditStatus(testDate, testOrderNum) }
    }

    @Test
    fun `setCreditOrderPaid should handle API error`() = runTest {
        // Given
        val testDate = "2024-01-15"
        val testOrderNum = 123
        val errorResponse = Response.error<Unit>(400, ResponseBody.create(null, "Error"))
        coEvery { mockApiService.updateCreditStatus(testDate, testOrderNum) } returns errorResponse

        // When
        lambdaRepository.setCreditOrderPaid(testDate, testOrderNum)

        // Then
        coVerify(exactly = 1) { mockApiService.updateCreditStatus(testDate, testOrderNum) }
    }

    // saveMenuListToServer 테스트
    @Test
    fun `saveMenuListToServer should complete successfully when API call succeeds`() = runTest {
        // Given
        val testMenuList = arrayListOf(
            MenuItem(1, "아메리카노", 4000, 1, true),
            MenuItem(2, "라떼", 4500, 2, true)
        )
        val successResponse = Response.success(Unit)
        coEvery { mockApiService.postMenuList(testMenuList) } returns successResponse

        // When
        lambdaRepository.saveMenuListToServer(testMenuList)

        // Then
        coVerify(exactly = 1) { mockApiService.postMenuList(testMenuList) }
    }

    @Test
    fun `saveMenuListToServer should handle API error`() = runTest {
        // Given
        val testMenuList = arrayListOf<MenuItem>()
        val errorResponse = Response.error<Unit>(400, ResponseBody.create(null, "Error"))
        coEvery { mockApiService.postMenuList(testMenuList) } returns errorResponse

        // When
        lambdaRepository.saveMenuListToServer(testMenuList)

        // Then
        coVerify(exactly = 1) { mockApiService.postMenuList(testMenuList) }
    }

    // getLastedSavedMenuList 테스트
    @Test
    fun `getLastedSavedMenuList should return menu list when API call succeeds`() = runTest {
        // Given
        val menuItems = listOf(
            MenuItem(1, "아메리카노", 4000, 1, true),
            MenuItem(2, "라떼", 4500, 2, true)
        )
        val responseMenuList = ResponseMenuList("2024-01-15T10:30:00", menuItems)
        val successResponse = Response.success(responseMenuList)
        coEvery { mockApiService.getMenuList() } returns successResponse

        // When
        val result = lambdaRepository.getLastedSavedMenuList()

        // Then
        assertEquals(2, result.size)
        assertEquals(1, result[0].id)
        assertEquals("아메리카노", result[0].name)
        assertEquals(4000, result[0].price)
        assertEquals(1, result[0].order)
        assertTrue(result[0].inuse)
        coVerify(exactly = 1) { mockApiService.getMenuList() }
    }

    @Test
    fun `getLastedSavedMenuList should return empty list when API call fails`() = runTest {
        // Given
        val errorResponse = Response.error<ResponseMenuList>(400, ResponseBody.create(null, "Error"))
        coEvery { mockApiService.getMenuList() } returns errorResponse

        // When
        val result = lambdaRepository.getLastedSavedMenuList()

        // Then
        assertTrue(result.isEmpty())
        coVerify(exactly = 1) { mockApiService.getMenuList() }
    }

    // validateResponse 메서드 테스트 (API 에러 응답을 통해 간접 테스트)
    @Test
    fun `validateResponse should throw ApiException with error message when response fails`() = runTest {
        // Given
        val errorJson = """{"message":"Invalid request"}"""
        val errorResponse = Response.error<ResponseOrderNum>(400, ResponseBody.create(null, errorJson))
        coEvery { mockApiService.getOrderNumber() } returns errorResponse

        // When
        val result = lambdaRepository.getOrderNumber()

        // Then
        // ApiException이 내부에서 처리되어 -1이 반환되어야 함
        assertEquals(-1, result)
        coVerify(exactly = 1) { mockApiService.getOrderNumber() }
    }

    // 헬퍼 메서드: 테스트용 Order 객체 생성
    private fun createTestOrder(): Order {
        val cartItems = listOf(
            CartItem(1, "아메리카노", 4000, 2, 8000),
            CartItem(2, "라떼", 4500, 1, 4500)
        )
        val paymentMethods = listOf(
            PaymentMethod("현금", 12500)
        )

        return Order(
            orderDate = "2024-01-15",
            orderNum = 1,
            creditStatus = 0,
            customerName = "테스트고객",
            orderItems = cartItems,
            paymentMethods = paymentMethods,
            totalAmount = 12500
        )
    }
}