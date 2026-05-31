package eloom.holybean.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import eloom.holybean.config.FeatureFlags
import eloom.holybean.printer.network.FakePrintServerApi
import eloom.holybean.printer.network.MdnsDiscovery
import eloom.holybean.printer.network.NsdMdnsDiscovery
import eloom.holybean.printer.network.PrinterAddressStore
import eloom.holybean.printer.network.PrinterHostInterceptor
import eloom.holybean.printer.network.PrintServerApi
import eloom.holybean.printer.network.SharedPrefsPrinterAddressStore
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

/**
 * Pi 프린트 서버 전용 Retrofit. apikey 헤더 없음(AWS와 무관한 격리 경로).
 * baseUrl은 더미이며 PrinterHostInterceptor가 매 요청에서 런타임 해석된
 * host:port로 치환한다.
 */
@Module
@InstallIn(SingletonComponent::class)
object PrintNetworkModule {

    /** 인터셉터가 host를 갈아끼우므로 baseUrl 자체는 더미 placeholder. */
    private const val PLACEHOLDER_BASE_URL = "http://holybean.invalid/"

    @Provides
    @Singleton
    @Named("PrintServer")
    fun providePrintServerOkHttp(
        interceptor: PrinterHostInterceptor,
    ): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .addInterceptor(interceptor)
            .build()

    @Provides
    @Singleton
    @Named("PrintServer")
    fun providePrintServerRetrofit(
        @Named("PrintServer") client: OkHttpClient,
    ): Retrofit =
        Retrofit.Builder()
            .baseUrl(PLACEHOLDER_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    @Provides
    @Singleton
    fun providePrintServerApi(
        @Named("PrintServer") retrofit: Retrofit,
    ): PrintServerApi =
        if (FeatureFlags.useFakePrinter) {
            // useFakePrinter 플래그가 켜진 빌드는 실제 프린터를 호출하지 않는다(인터셉터/네트워크 미경유).
            FakePrintServerApi()
        } else {
            retrofit.create(PrintServerApi::class.java)
        }

    @Module
    @InstallIn(SingletonComponent::class)
    abstract class Bindings {
        @Binds
        @Singleton
        abstract fun bindPrinterAddressStore(impl: SharedPrefsPrinterAddressStore): PrinterAddressStore

        @Binds
        @Singleton
        abstract fun bindMdnsDiscovery(impl: NsdMdnsDiscovery): MdnsDiscovery
    }
}
