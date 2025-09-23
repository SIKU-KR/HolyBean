package eloom.holybean.escpos.connection.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.os.ParcelUuid
import eloom.holybean.escpos.connection.DeviceConnection
import eloom.holybean.escpos.exceptions.PrinterConnectionException
import eloom.holybean.escpos.connection.di.BluetoothAdapterProvider
import eloom.holybean.escpos.connection.BluetoothPermissionChecker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException
import java.util.*

class BluetoothConnection(
    private val device: BluetoothDevice,
    private val adapterProvider: BluetoothAdapterProvider,
    private val permissionChecker: BluetoothPermissionChecker,
) : DeviceConnection() {

    private var socket: BluetoothSocket? = null
    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    fun getDevice(): BluetoothDevice = device

    override fun isConnected(): Boolean =
        socket?.isConnected == true && super.isConnected()

    @SuppressLint("MissingPermission")
    override fun connect(): DeviceConnection {
        if (isConnected()) {
            return this
        }

        _state.value = ConnectionState.Connecting(device)

        val bluetoothAdapter = adapterProvider.get() ?: throw PrinterConnectionException.BluetoothUnavailable

        return try {
            permissionChecker.assertConnectPermission()
            val uuid = resolveDeviceUuid()
            val bluetoothSocket = device.createRfcommSocketToServiceRecord(uuid)
            bluetoothAdapter.cancelDiscovery()
            bluetoothSocket.connect()
            socket = bluetoothSocket
            outputStream = bluetoothSocket.outputStream
            _state.value = ConnectionState.Connected(device)
            this
        } catch (error: IOException) {
            disconnect()
            _state.value = ConnectionState.Failed(device, error)
            throw PrinterConnectionException.ConnectionFailed(error)
        }
    }

    override fun disconnect(): DeviceConnection {
        _state.value = ConnectionState.Disconnected
        outputStream?.let {
            try {
                it.close()
            } catch (_: IOException) {
                // Closing stream best-effort
            }
        }
        outputStream = null

        socket?.let { currentSocket ->
            try {
                currentSocket.close()
            } catch (_: IOException) {
                // Closing socket best-effort
            }
        }
        socket = null
        return this
    }

    private fun resolveDeviceUuid(): UUID {
        val uuids: Array<ParcelUuid>? = device.uuids
        if (!uuids.isNullOrEmpty()) {
            if (uuids.any { it.uuid == SPP_UUID }) {
                return SPP_UUID
            }
            return uuids.first().uuid
        }
        return SPP_UUID
    }

    sealed class ConnectionState {
        object Disconnected : ConnectionState()
        data class Connecting(val device: BluetoothDevice) : ConnectionState()
        data class Connected(val device: BluetoothDevice) : ConnectionState()
        data class Failed(val device: BluetoothDevice, val error: Throwable) : ConnectionState()
    }

    private companion object {
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")
    }
}
