package eloom.holybean.printer.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface PrintServerApi {
    @POST("print")
    suspend fun print(@Body body: PrintRequestDto): Response<Unit>

    @GET("health")
    suspend fun health(): Response<Unit>
}
