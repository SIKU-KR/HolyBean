package eloom.holybean.escpos.connection.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import eloom.holybean.escpos.exceptions.PrinterConnectionException
import eloom.holybean.escpos.connection.di.BluetoothAdapterProvider
import eloom.holybean.escpos.connection.BluetoothPermissionChecker

open class BluetoothConnections(
    protected val adapterProvider: BluetoothAdapterProvider,
    protected val permissionChecker: BluetoothPermissionChecker,
) {

    @SuppressLint("MissingPermission")
    open fun getList(): List<BluetoothConnection> {
        permissionChecker.assertConnectPermission()
        val adapter = adapterProvider.get() ?: throw PrinterConnectionException.BluetoothUnavailable

        if (!adapter.isEnabled) return emptyList()

        val bondedDevices: Set<BluetoothDevice> = adapter.bondedDevices ?: emptySet()
        if (bondedDevices.isEmpty()) return emptyList()

        return bondedDevices.map { device ->
            BluetoothConnection(device, adapterProvider, permissionChecker)
        }
    }
}
