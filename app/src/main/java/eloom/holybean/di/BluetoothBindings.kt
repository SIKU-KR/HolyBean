package eloom.holybean.di

import android.bluetooth.BluetoothManager
import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import eloom.holybean.escpos.connection.bluetooth.BluetoothPrintersConnections
import javax.inject.Singleton
import eloom.holybean.escpos.connection.di.BluetoothAdapterProvider
import eloom.holybean.escpos.connection.BluetoothPermissionChecker
import eloom.holybean.printer.bluetooth.AndroidBluetoothPermissionChecker
import eloom.holybean.printer.bluetooth.BluetoothManagerAdapterProvider

@Module
@InstallIn(SingletonComponent::class)
interface BluetoothBindings {
    @Binds
    fun bindBluetoothAdapterProvider(
        impl: BluetoothManagerAdapterProvider
    ): BluetoothAdapterProvider

    @Binds
    fun bindBluetoothPermissionChecker(
        impl: AndroidBluetoothPermissionChecker
    ): BluetoothPermissionChecker

    companion object {
        @Provides
        fun provideBluetoothManager(
            @ApplicationContext context: Context
        ): BluetoothManager = requireNotNull(
            context.getSystemService(BluetoothManager::class.java)
        ) { "BluetoothManager unavailable" }

        @Provides
        @Singleton
        fun provideBluetoothPrintersConnections(
            @ApplicationContext context: Context,
            adapterProvider: BluetoothAdapterProvider,
            permissionChecker: BluetoothPermissionChecker,
        ): BluetoothPrintersConnections =
            BluetoothPrintersConnections(context, adapterProvider, permissionChecker)
    }
}
