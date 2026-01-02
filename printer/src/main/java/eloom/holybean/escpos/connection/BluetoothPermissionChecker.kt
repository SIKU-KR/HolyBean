package eloom.holybean.escpos.connection

interface BluetoothPermissionChecker {
    fun assertConnectPermission()
    fun assertScanPermission()
}