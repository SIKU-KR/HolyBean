package eloom.holybean.escpos.connection.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import eloom.holybean.escpos.connection.BluetoothPermissionChecker
import eloom.holybean.escpos.exceptions.PrinterConnectionException
import eloom.holybean.escpos.connection.di.BluetoothAdapterProvider

class BluetoothPrintersConnections(
    private val context: Context,
    adapterProvider: BluetoothAdapterProvider,
    permissionChecker: BluetoothPermissionChecker,
) : BluetoothConnections(adapterProvider, permissionChecker) {

    @SuppressLint("MissingPermission")
    override fun getList(): List<BluetoothConnection> {
        permissionChecker.assertScanPermission()
        return super.getList()
    }

    @SuppressLint("MissingPermission")
    fun listenForDiscovery(
        owner: LifecycleOwner,
        onPrinterFound: (BluetoothConnection) -> Unit,
    ) {
        permissionChecker.assertScanPermission()
        val adapter = adapterProvider.get() ?: throw PrinterConnectionException.BluetoothUnavailable

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action != BluetoothDevice.ACTION_FOUND) return

                val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return
                onPrinterFound(BluetoothConnection(device, adapterProvider, permissionChecker))
            }
        }

        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        if (adapter.isDiscovering) {
            adapter.cancelDiscovery()
        }
        adapter.startDiscovery()

        owner.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                try {
                    context.unregisterReceiver(receiver)
                } catch (_: IllegalArgumentException) {
                    // Receiver already unregistered
                }
                adapter.cancelDiscovery()
                owner.lifecycle.removeObserver(this)
            }
        })
    }

}
