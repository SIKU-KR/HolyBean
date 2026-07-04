package eloom.holybean.printer.transport

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import eloom.holybean.di.PrinterDispatcher
import eloom.holybean.printer.escpos.EscposRenderer
import eloom.holybean.printer.network.PrintCommandDto
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UsbDirectTransport @Inject constructor(
    private val usbManager: UsbManager,
    private val permissionRequester: UsbPermissionRequester,
    private val renderer: EscposRenderer,
    @PrinterDispatcher private val dispatcher: CoroutineDispatcher,
) : PrintTransport {

    override val method: PrintMethod = PrintMethod.USB_DIRECT

    // probe/checkHealth(스플래시, DevTools)와 print가 같은 장치를 동시에 open/claim하지 않도록 직렬화
    private val deviceMutex = Mutex()

    override suspend fun probeFastFail(requestPermission: Boolean): FastFailReason? = withContext(dispatcher) {
        deviceMutex.withLock {
            try {
                val target = resolveTarget(requestPermission)
                val connection = openClaimed(target)
                connection.closeAfterRelease(target.usbInterface)
                null
            } catch (e: UsbFastFailException) {
                e.reason
            }
        }
    }

    override suspend fun print(commands: List<PrintCommandDto>) = withContext(dispatcher) {
        deviceMutex.withLock {
            val target = resolveTarget(requestPermission = true)
            val connection = openClaimed(target)
            try {
                val bytes = renderer.render(commands).toByteArray()
                writeAll(connection, target.endpoint, bytes)
            } finally {
                connection.closeAfterRelease(target.usbInterface)
            }
        }
    }

    override suspend fun checkHealth(): Boolean {
        return probeFastFail(requestPermission = true) == null
    }

    private fun resolveTarget(requestPermission: Boolean): UsbPrinterTarget {
        val device = usbManager.findPrinterDevice()
            ?: throw UsbFastFailException(FastFailReason.NO_DEVICE, "USB printer device not found")
        val usbInterface = device.findPrinterInterface()
            ?: throw UsbFastFailException(
                FastFailReason.NO_PRINTER_INTERFACE,
                "USB printer interface not found",
            )
        val endpoint = usbInterface.findBulkOutEndpoint()
            ?: throw UsbFastFailException(
                FastFailReason.NO_PRINTER_INTERFACE,
                "USB printer bulk OUT endpoint not found",
            )
        if (!hasPermission(device, requestPermission)) {
            throw UsbFastFailException(FastFailReason.NO_PERMISSION, "USB printer permission not granted")
        }
        return UsbPrinterTarget(device, usbInterface, endpoint)
    }

    private fun hasPermission(device: UsbDevice, requestPermission: Boolean): Boolean {
        return try {
            if (usbManager.hasPermission(device)) {
                true
            } else {
                if (requestPermission) permissionRequester.requestPermission(device)
                false
            }
        } catch (e: SecurityException) {
            if (requestPermission) permissionRequester.requestPermission(device)
            false
        }
    }

    private fun openClaimed(target: UsbPrinterTarget): UsbDeviceConnection {
        val connection = try {
            usbManager.openDevice(target.device)
        } catch (e: SecurityException) {
            throw UsbFastFailException(FastFailReason.NO_PERMISSION, "USB printer permission denied")
        } ?: throw UsbFastFailException(FastFailReason.CLAIM_FAILED, "Unable to open USB printer")

        val claimed = try {
            connection.claimInterface(target.usbInterface, true)
        } catch (e: RuntimeException) {
            connection.close()
            throw UsbFastFailException(FastFailReason.CLAIM_FAILED, "Unable to claim USB printer interface")
        }
        if (!claimed) {
            connection.close()
            throw UsbFastFailException(FastFailReason.CLAIM_FAILED, "Unable to claim USB printer interface")
        }
        return connection
    }

    private fun writeAll(connection: UsbDeviceConnection, endpoint: UsbEndpoint, bytes: ByteArray) {
        var offset = 0
        while (offset < bytes.size) {
            val count = minOf(bytes.size - offset, BULK_WRITE_CHUNK_BYTES)
            val written = connection.bulkTransfer(
                endpoint,
                bytes,
                offset,
                count,
                BULK_WRITE_TIMEOUT_MS,
            )
            if (written <= 0) {
                if (offset == 0) {
                    // 아직 아무 바이트도 전송되지 않음: Pi로 폴백해도 중복 출력 위험이 없다
                    throw UsbFastFailException(
                        FastFailReason.CLAIM_FAILED,
                        "USB printer rejected first write",
                    )
                }
                // 일부가 이미 인쇄됨: 재시도/폴백하면 영수증이 중복 출력된다
                throw UsbPartialPrintException(bytesSent = offset, totalBytes = bytes.size)
            }
            offset += written
        }
    }

    private fun UsbDeviceConnection.closeAfterRelease(usbInterface: UsbInterface) {
        runCatching { releaseInterface(usbInterface) }
        close()
    }

    private data class UsbPrinterTarget(
        val device: UsbDevice,
        val usbInterface: UsbInterface,
        val endpoint: UsbEndpoint,
    )

    private companion object {
        const val BULK_WRITE_CHUNK_BYTES = 16 * 1024

        // 부분 전송 실패는 재시도/폴백이 불가능해 곧바로 영수증 절단이 된다.
        // 열전사 프린터는 인쇄 중 벌크 전송을 블록하므로 버퍼가 빠질 시간을 충분히 준다.
        const val BULK_WRITE_TIMEOUT_MS = 5_000
    }
}
