package eloom.holybean.di

import android.content.Context
import android.hardware.usb.UsbManager
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import eloom.holybean.di.UsbTransport
import eloom.holybean.printer.transport.PrintTransport
import eloom.holybean.printer.transport.UsbDirectTransport
import eloom.holybean.printer.transport.UsbPermissionRequester
import eloom.holybean.printer.transport.UsbPermissionRequesterImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class UsbModule {

    @Binds
    @Singleton
    @UsbTransport
    abstract fun bindUsbTransport(impl: UsbDirectTransport): PrintTransport

    @Binds
    @Singleton
    abstract fun bindUsbPermissionRequester(
        impl: UsbPermissionRequesterImpl,
    ): UsbPermissionRequester

    companion object {
        @Provides
        @Singleton
        fun provideUsbManager(@ApplicationContext context: Context): UsbManager {
            return context.getSystemService(Context.USB_SERVICE) as UsbManager
        }
    }
}
