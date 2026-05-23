package eloom.holybean.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import eloom.holybean.BuildConfig
import eloom.holybean.printer.PiPrintClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Named

@HiltViewModel
class DevToolsViewModel @Inject constructor(
    private val piPrintClient: PiPrintClient,
    @Named("IO") private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {
    data class State(
        val printerOk: Boolean? = null,
        val printerUrl: String = BuildConfig.PRINT_SERVER_URL,
    )

    private val _uiState = MutableStateFlow(State())
    val uiState: StateFlow<State> = _uiState.asStateFlow()

    fun refresh() {
        viewModelScope.launch(ioDispatcher) {
            val ok = runCatching { piPrintClient.checkHealth() }.getOrDefault(false)
            _uiState.update { it.copy(printerOk = ok) }
        }
    }

    fun testPrint() {
        viewModelScope.launch(ioDispatcher) { runCatching { piPrintClient.printTestReceipt() } }
    }
}
