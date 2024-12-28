package com.example.holybean.network

import com.example.holybean.data.model.Order
import com.example.holybean.network.dto.ResponseOrder
import com.example.holybean.network.dto.ResponseOrderDetail
import com.example.holybean.network.dto.ResponseOrderNum
import com.example.holybean.network.dto.ResponseSalesReport
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
}
