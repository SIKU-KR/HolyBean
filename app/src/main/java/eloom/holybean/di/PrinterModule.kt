package eloom.holybean.di

import com.dantsu.escposprinter.EscPosCharsetEncoding
import com.dantsu.escposprinter.EscPosPrinter
import com.dantsu.escposprinter.connection.bluetooth.BluetoothPrintersConnections
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PrinterModule {

    @Provides
    @Singleton
    fun provideEscPosPrinter(): EscPosPrinter {
        return EscPosPrinter(
            BluetoothPrintersConnections.selectFirstPaired(),
            180,
            72f,
            32,
            EscPosCharsetEncoding("EUC-KR", 13)
        )
    }
}