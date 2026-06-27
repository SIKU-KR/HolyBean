package eloom.holybean.printer.transport

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

interface PrinterTransportStore {
    var forcePi: Boolean
}

class SharedPrefsPrinterTransportStore @Inject constructor(
    @ApplicationContext context: Context,
) : PrinterTransportStore {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("printer_transport", Context.MODE_PRIVATE)

    override var forcePi: Boolean
        get() = prefs.getBoolean(KEY_FORCE_PI, false)
        set(value) = prefs.edit().putBoolean(KEY_FORCE_PI, value).apply()

    private companion object {
        const val KEY_FORCE_PI = "force_pi"
    }
}
