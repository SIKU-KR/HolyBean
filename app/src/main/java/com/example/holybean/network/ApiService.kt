package com.example.holybean.network

import com.example.holybean.network.dto.ResponseOrderNum
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    @GET("{endpoint}")
    suspend fun getRequest(
        @Path("endpoint") endpoint: String, @QueryMap queryParameters: Map<String, String> = emptyMap()
    ): Response<String>

    @POST("{endpoint}")
    suspend fun postRequest(
        @Path("endpoint") endpoint: String, @Body body: Map<String, Any>
    ): Response<String>

    @GET("ordernum")
    suspend fun getOrderNumber(): Response<ResponseOrderNum>
}