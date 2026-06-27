package eloom.holybean.printer.transport

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

interface UsbPermissionRequester {
    fun requestPermission(device: UsbDevice)
}

@Singleton
class UsbPermissionRequesterImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val usbManager: UsbManager,
) : UsbPermissionRequester {

    override fun requestPermission(device: UsbDevice) {
        val intent = Intent(context, UsbPermissionReceiver::class.java)
            .setAction("android.hardware.usb.action.USB_PERMISSION")
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE,
        )
        usbManager.requestPermission(device, pendingIntent)
    }
}

class UsbPermissionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // 권한 결과는 다음 probe/print 시 UsbManager.hasPermission()으로 확인한다.
    }
}
