package com.example.holybean.network

import com.example.holybean.data.model.Order
import com.example.holybean.network.dto.ResponseOrderNum
import retrofit2.Response
import retrofit2.http.*

interface LambdaConnection {

    @GET("ordernum")
    suspend fun getOrderNumber(): Response<ResponseOrderNum>

    @POST("order")
    suspend fun postOrder(@Body data: Order): Response<Unit>
}