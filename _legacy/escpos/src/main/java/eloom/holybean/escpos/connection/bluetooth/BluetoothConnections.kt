package eloom.holybean.escpos.connection.bluetooth

import android.Manifest
import android.bluetooth.BluetoothDevice
import androidx.annotation.RequiresPermission
import androidx.annotation.WorkerThread
import eloom.holybean.escpos.exceptions.PrinterConnectionException
import eloom.holybean.escpos.connection.di.BluetoothAdapterProvider
import eloom.holybean.escpos.connection.BluetoothPermissionChecker

open class BluetoothConnections(
    protected val adapterProvider: BluetoothAdapterProvider,
    protected val permissionChecker: BluetoothPermissionChecker,
) {

    @WorkerThread
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
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
