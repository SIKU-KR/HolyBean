package eloom.holybean.escpos.exceptions

sealed class PrinterConnectionException(message: String, cause: Throwable? = null) :
    EscPosConnectionException(message, cause) {

    object BluetoothUnavailable : PrinterConnectionException("Bluetooth adapter is unavailable")

    class ConnectionFailed(cause: Throwable?) :
        PrinterConnectionException("Unable to connect to bluetooth device.", cause)
}