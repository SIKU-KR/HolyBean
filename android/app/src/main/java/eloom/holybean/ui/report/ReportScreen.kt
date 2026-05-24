package eloom.holybean.ui.report

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eloom.holybean.data.model.ReportDetailItem
import eloom.holybean.ui.components.StatChip
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

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("기간 매출 리포트", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = onClose) { Text("닫기", style = MaterialTheme.typography.bodyMedium) }
        }
        Row(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { pick { start = it } }) { Text(start, style = MaterialTheme.typography.bodyMedium) }
            OutlinedButton(onClick = { pick { end = it } }) { Text(end, style = MaterialTheme.typography.bodyMedium) }
            Button(onClick = { vm.loadReportData(start, end) }) { Text("조회", style = MaterialTheme.typography.bodyMedium) }
            OutlinedButton(onClick = { vm.printReport() }) { Text("출력", style = MaterialTheme.typography.bodyMedium) }
        }
        Text(state.reportTitle, style = MaterialTheme.typography.bodyMedium)
        Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
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

@Composable
private fun MenuSalesRow(d: ReportDetailItem) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(d.name, style = MaterialTheme.typography.bodyMedium)
        Text("${d.quantity}개", style = MaterialTheme.typography.bodyMedium)
        Text("%,d".format(d.subtotal), style = MaterialTheme.typography.bodyMedium)
    }
}
