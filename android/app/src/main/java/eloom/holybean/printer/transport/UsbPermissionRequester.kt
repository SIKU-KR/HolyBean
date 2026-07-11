package eloom.holybean.printer.transport

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

internal const val ACTION_USB_PERMISSION = "android.hardware.usb.action.USB_PERMISSION"

interface UsbPermissionRequester {
    /** 사용자가 USB 권한을 허용했을 때 발생하는 이벤트 */
    val permissionGrants: SharedFlow<Unit>

    fun requestPermission(device: UsbDevice)

    fun onPermissionResult(granted: Boolean)
}

@Singleton
class UsbPermissionRequesterImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val usbManager: UsbManager,
) : UsbPermissionRequester {

    private val _permissionGrants = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val permissionGrants: SharedFlow<Unit> = _permissionGrants.asSharedFlow()

    override fun requestPermission(device: UsbDevice) {
        val intent = Intent(context, UsbPermissionReceiver::class.java)
            .setAction(ACTION_USB_PERMISSION)
        // 시스템이 EXTRA_PERMISSION_GRANTED를 채워 넣어야 하므로 MUTABLE 필요 (명시적 인텐트라 안전)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        usbManager.requestPermission(device, pendingIntent)
    }

    override fun onPermissionResult(granted: Boolean) {
        if (granted) _permissionGrants.tryEmit(Unit)
    }
}

// AndroidManifest에 exported=false로 등록된 명시적 리시버
class UsbPermissionReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface RequesterEntryPoint {
        fun usbPermissionRequester(): UsbPermissionRequester
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_USB_PERMISSION) return
        val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
        EntryPointAccessors.fromApplication(context, RequesterEntryPoint::class.java)
            .usbPermissionRequester()
            .onPermissionResult(granted)
    }
}
