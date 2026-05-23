package eloom.holybean.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun DevToolsRoute(onClose: () -> Unit, vm: DevToolsViewModel = hiltViewModel()) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(Unit) { vm.refresh() }
    LaunchedEffect(Unit) {
        vm.uiEvent.collect {
            when (it) {
                is DevToolsViewModel.DevToolsUiEvent.ShowToast ->
                    Toast.makeText(context, it.message, Toast.LENGTH_SHORT).show()
            }
        }
    }
    DevToolsScreen(state, onClose, vm::refresh, vm::testPrint)
}

@Composable
fun DevToolsScreen(
    state: DevToolsViewModel.State,
    onClose: () -> Unit,
    onRefresh: () -> Unit,
    onTestPrint: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "개발자 도구",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = onClose) { Text("닫기") }
        }
        Spacer(Modifier.height(12.dp))
        HealthRow("Pi 프린터 (/health)", state.printerOk)
        Text(
            "프린터 서버 URL: ${state.printerUrl}",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onRefresh) { Text("새로고침") }
            Button(onClick = onTestPrint) { Text("테스트 영수증 출력") }
        }
    }
}

@Composable
private fun HealthRow(label: String, ok: Boolean?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 6.dp),
    ) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(
                    when (ok) {
                        true -> Color(0xFF22C55E)
                        false -> Color(0xFFEF4444)
                        null -> Color(0xFFBBBBBB)
                    },
                ),
        )
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}
