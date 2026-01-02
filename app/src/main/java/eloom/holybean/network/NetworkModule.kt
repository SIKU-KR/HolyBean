package eloom.holybean.network

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideApiService(): ApiService =
        RetrofitClient.retrofit.create(ApiService::class.java)

    @Provides
    @Singleton
    @Named("IO")
    fun provideIODispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @Singleton
    @Named("Printer")
    fun providePrinterDispatcher(): CoroutineDispatcher =
        Dispatchers.IO.limitedParallelism(2)

    @Provides
    @Singleton
    @Named("ApplicationScope")
    fun provideApplicationScope(
        @Named("Printer") printerDispatcher: CoroutineDispatcher
    ): CoroutineScope = CoroutineScope(SupervisorJob() + printerDispatcher)
}