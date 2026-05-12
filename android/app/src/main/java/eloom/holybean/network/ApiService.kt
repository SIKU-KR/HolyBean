package eloom.holybean.network

import eloom.holybean.data.model.MenuItem
import eloom.holybean.data.model.Order
import eloom.holybean.network.dto.*
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    @GET("ordernum")
    suspend fun getOrderNumber(): Response<ResponseOrderNum>

    @POST("order")
    suspend fun postOrder(@Body data: Order): Response<Unit>

    @GET("order/{orderDate}")
    suspend fun getOrderOfDay(@Path("orderDate") orderdate: String): Response<List<ResponseOrder>>

    @GET("order")
    suspend fun getSpecificOrder(
        @Query("orderDate") orderDate: String, @Query("orderNum") orderNum: Int
    ): Response<ResponseOrderDetail>

    @DELETE("order")
    suspend fun deleteOrder(@Query("orderDate") orderDate: String, @Query("orderNum") orderNum: Int): Response<Unit>

    @GET("report")
    suspend fun getReport(
        @Query("start") startDate: String, @Query("end") endDate: String
    ): Response<ResponseSalesReport>

    @GET("/credit")
    suspend fun getAllCreditOrders(): Response<List<ResponseCredit>>

    @PUT("/credit/{orderDate}/{number}")
    suspend fun updateCreditStatus(
        @Path("orderDate") orderDate: String, @Path("number") orderNum: Int
    ): Response<Unit>

    @GET("/menu")
    suspend fun getMenuList(): Response<ResponseMenuList>

    @POST("/menu")
    suspend fun postMenuList(@Body menuList: List<MenuItem>): Response<Unit>
}
