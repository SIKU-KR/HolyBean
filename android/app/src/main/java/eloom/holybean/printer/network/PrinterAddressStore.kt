package eloom.holybean.printer.network

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Pi 주소 영속 저장소.
 * - override: 관리자가 수동 입력한 주소(있으면 mDNS보다 우선).
 * - lastGood: 마지막으로 도달에 성공한 주소(빠른 경로 시드).
 */
interface PrinterAddressStore {
    var override: String?
    var lastGood: String?
}

class SharedPrefsPrinterAddressStore @Inject constructor(
    @ApplicationContext context: Context,
) : PrinterAddressStore {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("printer_address", Context.MODE_PRIVATE)

    override var override: String?
        get() = prefs.getString(KEY_OVERRIDE, null)
        set(value) = prefs.edit().apply {
            if (value.isNullOrBlank()) remove(KEY_OVERRIDE) else putString(KEY_OVERRIDE, value)
        }.apply()

    override var lastGood: String?
        get() = prefs.getString(KEY_LAST_GOOD, null)
        set(value) = prefs.edit().apply {
            if (value.isNullOrBlank()) remove(KEY_LAST_GOOD) else putString(KEY_LAST_GOOD, value)
        }.apply()

    private companion object {
        const val KEY_OVERRIDE = "override"
        const val KEY_LAST_GOOD = "last_good"
    }
}
