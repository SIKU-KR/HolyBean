package eloom.holybean.printer.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import eloom.holybean.escpos.connection.di.BluetoothAdapterProvider
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BluetoothManagerAdapterProvider @Inject constructor(
    private val manager: BluetoothManager
) : BluetoothAdapterProvider {
    override fun get(): BluetoothAdapter? = manager.adapter
}