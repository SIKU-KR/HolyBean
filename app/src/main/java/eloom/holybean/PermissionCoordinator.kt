package eloom.holybean

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class PermissionCoordinator(
    private val activity: ComponentActivity
) {

    private val bluetoothPermissionsLauncher: ActivityResultLauncher<Array<String>> =
        activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val deniedPermissions = BLUETOOTH_PERMISSIONS.filter { permission ->
                result[permission] != true
            }

            if (deniedPermissions.isEmpty()) {
                pendingCallback?.invoke(PermissionResult.Granted)
            } else {
                val permanentlyDenied = deniedPermissions.any { permission ->
                    !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
                }
                pendingCallback?.invoke(
                    PermissionResult.Denied(
                        permanentlyDenied = permanentlyDenied, deniedPermissions = deniedPermissions
                    )
                )
            }
            pendingCallback = null
        }

    private var pendingCallback: ((PermissionResult) -> Unit)? = null

    fun requestBluetoothPermissions(onResult: (PermissionResult) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            onResult(PermissionResult.Granted)
            return
        }

        val missingPermissions = BLUETOOTH_PERMISSIONS.filter { permission ->
            ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isEmpty()) {
            onResult(PermissionResult.Granted)
            return
        }

        val shouldShowRationale = missingPermissions.any { permission ->
            ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
        }

        if (shouldShowRationale) {
            onResult(
                PermissionResult.ShowRationale(
                    missingPermissions = missingPermissions
                )
            )
            return
        }

        pendingCallback = onResult
        bluetoothPermissionsLauncher.launch(missingPermissions.toTypedArray())
    }


    sealed interface PermissionResult {
        object Granted : PermissionResult
        data class ShowRationale(val missingPermissions: List<String>) : PermissionResult
        data class Denied(
            val permanentlyDenied: Boolean, val deniedPermissions: List<String>
        ) : PermissionResult
    }

    private companion object {
        val BLUETOOTH_PERMISSIONS = arrayOf(
            Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT
        )
    }
}