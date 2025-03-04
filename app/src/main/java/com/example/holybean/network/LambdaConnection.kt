package com.example.holybean.network

import com.example.holybean.data.model.Order
import com.example.holybean.network.dto.*
import retrofit2.Response
import retrofit2.http.*

interface LambdaConnection {

    @GET("ordernum")
    suspend fun getOrderNumber(): Response<ResponseOrderNum>

    @POST("order")
    suspend fun postOrder(@Body data: Order): Response<Unit>

    @GET("order/{orderDate}")
    suspend fun getOrderOfDay(@Path("orderDate") orderdate: String): Response<List<ResponseOrder>>

    @GET("order")
    suspend fun getSpecificOrder(@Query("orderDate") orderDate: String, @Query("orderNum") orderNum: Int): Response<ResponseOrderDetail>

    @DELETE("order")
    suspend fun deleteOrder(@Query("orderDate") orderDate: String, @Query("orderNum") orderNum: Int): Response<Unit>

    @GET("report")
    suspend fun getReport(@Query("start") startDate: String, @Query("end") endDate: String): Response<ResponseSalesReport>

    @GET("/menu")
    suspend fun getMenuList(): Response<ResponseMenuList>

    @GET("/credit")
    suspend fun getAllCreditOrders(): Response<ResponseCredit>

    @POST("/credit")
    suspend fun updateCreditStatus(@Body orderNum: Int, @Body orderDate: String): Response<Unit>
}
