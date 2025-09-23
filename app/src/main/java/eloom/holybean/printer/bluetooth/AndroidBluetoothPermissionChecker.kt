package eloom.holybean.printer.bluetooth

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import eloom.holybean.escpos.connection.BluetoothPermissionChecker
import eloom.holybean.escpos.exceptions.BluetoothPermissionException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidBluetoothPermissionChecker @Inject constructor(
    @ApplicationContext private val context: Context,
) : BluetoothPermissionChecker {

    override fun assertConnectPermission() {
        ensurePermission(Manifest.permission.BLUETOOTH_CONNECT)
    }

    override fun assertScanPermission() {
        ensurePermission(Manifest.permission.BLUETOOTH_SCAN)
    }

    private fun ensurePermission(permission: String) {
        val granted =
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            throw BluetoothPermissionException("Missing permission: $permission")
        }
    }
}
