package eloom.holybean.di

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
object CoroutineModule {

    @Provides @Singleton @IoDispatcher
    fun provideIODispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides @Singleton @PrinterDispatcher
    fun providePrinterDispatcher(): CoroutineDispatcher = Dispatchers.IO.limitedParallelism(2)

    @Provides @Singleton @AppScope
    fun provideApplicationScope(
        @PrinterDispatcher printerDispatcher: CoroutineDispatcher
    ): CoroutineScope = CoroutineScope(SupervisorJob() + printerDispatcher)

    // Bridge bindings: keep @Named("IO"), @Named("Printer"), @Named("ApplicationScope") until
    // all injection sites are migrated to typed qualifiers (@IoDispatcher, @PrinterDispatcher,
    // @AppScope). Remove these once every ViewModel is updated.
    @Provides @Singleton @Named("IO")
    fun provideNamedIODispatcher(@IoDispatcher d: CoroutineDispatcher): CoroutineDispatcher = d

    @Provides @Singleton @Named("Printer")
    fun provideNamedPrinterDispatcher(@PrinterDispatcher d: CoroutineDispatcher): CoroutineDispatcher = d

    @Provides @Singleton @Named("ApplicationScope")
    fun provideNamedApplicationScope(@AppScope s: CoroutineScope): CoroutineScope = s
}
