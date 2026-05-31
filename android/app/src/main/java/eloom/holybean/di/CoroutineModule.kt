package eloom.holybean.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CoroutineModule {

    @Provides @Singleton @IoDispatcher
    fun provideIODispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides @Singleton @PrinterDispatcher
    fun providePrinterDispatcher(): CoroutineDispatcher = Dispatchers.IO.limitedParallelism(2)

    @Provides @Singleton @AppScope
    fun provideApplicationScope(
        @PrinterDispatcher printerDispatcher: CoroutineDispatcher
    ): CoroutineScope = CoroutineScope(SupervisorJob() + printerDispatcher)
}
