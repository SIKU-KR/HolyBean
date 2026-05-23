package eloom.holybean.ui.settings

import android.widget.Toast
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eloom.holybean.ui.theme.Dimens
import eloom.holybean.ui.theme.OnSurfaceMuted
import eloom.holybean.ui.theme.Orange
import eloom.holybean.ui.theme.StatusError
import eloom.holybean.ui.theme.StatusOk
import eloom.holybean.ui.theme.StatusUnknown

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
                "🛠 개발자 도구",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = onClose, shape = RoundedCornerShape(Dimens.radiusButton)) {
                Text("닫기")
            }
        }
        Spacer(Modifier.height(12.dp))
        HealthRow("Pi 프린터 (/health)", state.printerOk,
            when (state.printerOk) {
                true -> state.printerLatencyMs?.let { "정상 · ${it}ms" } ?: "정상"
                false -> "응답 실패"
                null -> "—"
            })
        HealthRow(
            label = "네트워크 연결",
            ok = state.networkOk,
            value = state.networkInfo,
        )
        HealthRow("Firestore", state.firestoreOk,
            when (state.firestoreOk) { true -> "정상"; false -> "응답 없음"; null -> "—" })
        Text(
            "프린터 서버 URL: ${state.printerUrl}",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onRefresh, shape = RoundedCornerShape(Dimens.radiusButton)) {
                Text("새로고침")
            }
            Button(
                onClick = onTestPrint,
                modifier = Modifier.height(Dimens.primaryTouchTarget),
                shape = RoundedCornerShape(Dimens.radiusButton),
                colors = ButtonDefaults.buttonColors(containerColor = Orange),
            ) {
                Text("테스트 영수증 출력", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun HealthRow(label: String, ok: Boolean?, value: String) {
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
                        true -> StatusOk
                        false -> StatusError
                        null -> StatusUnknown
                    },
                ),
        )
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.labelSmall, color = OnSurfaceMuted)
    }
}
