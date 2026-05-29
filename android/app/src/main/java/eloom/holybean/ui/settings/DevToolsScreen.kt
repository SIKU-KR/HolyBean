package eloom.holybean.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eloom.holybean.ui.components.buttons.PrimaryButton
import eloom.holybean.ui.components.buttons.SecondaryButton
import eloom.holybean.ui.components.layout.ScreenContainer
import eloom.holybean.ui.components.layout.ScreenHeader
import eloom.holybean.ui.components.layout.StatusDot
import eloom.holybean.ui.theme.Dimens
import eloom.holybean.ui.theme.OnSurfaceMuted

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
    ScreenContainer {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader(
                "🛠 개발자 도구",
                actions = {
                    SecondaryButton("닫기", onClick = onClose)
                },
            )
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
                "프린터 연결: ${state.printerStatusText}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = Dimens.spaceSm),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm)) {
                SecondaryButton("새로고침", onClick = onRefresh)
                PrimaryButton("테스트 영수증 출력", onClick = onTestPrint)
            }
        }
    }
}

@Composable
private fun HealthRow(label: String, ok: Boolean?, value: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = Dimens.spaceSm),
    ) {
        StatusDot(ok)
        Spacer(Modifier.width(Dimens.spaceSm))
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.labelSmall, color = OnSurfaceMuted)
    }
}
