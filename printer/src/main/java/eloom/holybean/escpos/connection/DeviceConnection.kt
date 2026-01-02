package eloom.holybean.escpos.connection

import androidx.annotation.WorkerThread
import eloom.holybean.escpos.exceptions.EscPosConnectionException
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream

abstract class DeviceConnection {

    protected var outputStream: OutputStream? = null
    private val buffer = ByteArrayOutputStream()

    abstract fun connect(): DeviceConnection

    abstract fun disconnect(): DeviceConnection

    open fun isConnected(): Boolean = outputStream != null

    fun write(bytes: ByteArray) {
        buffer.write(bytes)
    }

    @WorkerThread
    @Throws(EscPosConnectionException::class)
    @JvmOverloads
    fun send(addWaitingTime: Int = 0) {
        if (!isConnected()) {
            throw EscPosConnectionException("Unable to send data to device.")
        }

        val targetStream = outputStream ?: throw EscPosConnectionException("Output stream unavailable.")
        val payload = buffer.toByteArray()
        if (payload.isEmpty()) {
            return
        }

        try {
            targetStream.write(payload)
            targetStream.flush()
            val waitingTime = addWaitingTime + payload.size / 16
            buffer.reset()
            if (waitingTime > 0) {
                Thread.sleep(waitingTime.toLong())
            }
        } catch (exception: IOException) {
            throw EscPosConnectionException(
                exception.message ?: "Unknown connection failure",
                exception,
            )
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            throw EscPosConnectionException(
                interrupted.message ?: "Interrupted while sending to device",
                interrupted,
            )
        }
    }
}
