package eloom.holybean.ui.report

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eloom.holybean.data.model.ReportDetailItem
import eloom.holybean.ui.components.StatChip
import eloom.holybean.ui.components.buttons.PrimaryButton
import eloom.holybean.ui.components.buttons.SecondaryButton
import eloom.holybean.ui.components.layout.ScreenContainer
import eloom.holybean.ui.components.layout.ScreenHeader
import eloom.holybean.ui.theme.Dimens
import eloom.holybean.ui.theme.OrangeOnContainer
import java.util.Calendar

@Composable
fun ReportRoute(onClose: () -> Unit, vm: ReportViewModel = hiltViewModel()) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val today = remember { java.time.LocalDate.now().toString() }
    var start by rememberSaveable { mutableStateOf(today) }
    var end by rememberSaveable { mutableStateOf(today) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        vm.uiEvent.collect { event ->
            when (event) {
                is ReportViewModel.ReportUiEvent.ShowToast ->
                    android.widget.Toast.makeText(context, event.message, android.widget.Toast.LENGTH_SHORT).show()
                is ReportViewModel.ReportUiEvent.ShowError ->
                    android.widget.Toast.makeText(context, event.message, android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun pick(onPicked: (String) -> Unit) {
        val c = Calendar.getInstance()
        DatePickerDialog(context, { _, y, m, d -> onPicked(vm.formatDate(y, m, d)) },
            c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
    }

    ScreenContainer {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader(
                "기간 매출 리포트",
                actions = {
                    SecondaryButton("닫기", onClick = onClose)
                },
            )
            Row(Modifier.padding(vertical = Dimens.spaceMd), horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm)) {
                SecondaryButton(start, onClick = { pick { start = it } })
                SecondaryButton(end, onClick = { pick { end = it } })
                PrimaryButton("조회", onClick = { vm.loadReportData(start, end) })
                SecondaryButton("출력", onClick = { vm.printReport() })
            }
            Text(state.reportTitle, style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.padding(vertical = Dimens.spaceSm), horizontalArrangement = Arrangement.spacedBy(Dimens.spaceMd)) {
                StatChip("총합", "%,d".format(state.reportData["총합"] ?: 0), OrangeOnContainer)
                StatChip("현금", "%,d".format(state.reportData["현금"] ?: 0))
                StatChip("계좌이체", "%,d".format(state.reportData["계좌이체"] ?: 0))
                StatChip("쿠폰", "%,d".format(state.reportData["쿠폰"] ?: 0))
            }
            LazyColumn(Modifier.weight(1f)) {
                items(state.reportDetailData, key = { it.name }) { d -> MenuSalesRow(d) }
            }
        }
    }
}

@Composable
private fun MenuSalesRow(d: ReportDetailItem) {
    Row(Modifier.fillMaxWidth().padding(vertical = Dimens.spaceSm), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(d.name, style = MaterialTheme.typography.bodyMedium)
        Text("${d.quantity}개", style = MaterialTheme.typography.bodyMedium)
        Text("%,d".format(d.subtotal), style = MaterialTheme.typography.bodyMedium)
    }
}
