package eloom.holybean.escpos.connection.di

import android.bluetooth.BluetoothAdapter

interface BluetoothAdapterProvider {
    fun get(): BluetoothAdapter?
}