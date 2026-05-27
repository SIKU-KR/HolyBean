package eloom.holybean.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import eloom.holybean.BuildConfig
import eloom.holybean.printer.network.FakePrintServerApi
import eloom.holybean.printer.network.PrintServerApi
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

/**
 * Pi 프린트 서버 전용 Retrofit. apikey 헤더 없음(AWS와 무관한 격리 경로).
 */
@Module
@InstallIn(SingletonComponent::class)
object PrintNetworkModule {

    @Provides
    @Singleton
    @Named("PrintServer")
    fun providePrintServerOkHttp(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()

    @Provides
    @Singleton
    @Named("PrintServer")
    fun providePrintServerRetrofit(
        @Named("PrintServer") client: OkHttpClient,
    ): Retrofit =
        Retrofit.Builder()
            .baseUrl(BuildConfig.PRINT_SERVER_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    @Provides
    @Singleton
    fun providePrintServerApi(
        @Named("PrintServer") retrofit: Retrofit,
    ): PrintServerApi =
        if (BuildConfig.DEBUG) {
            // debug 빌드는 실제 프린터를 호출하지 않는다.
            FakePrintServerApi()
        } else {
            retrofit.create(PrintServerApi::class.java)
        }
}
