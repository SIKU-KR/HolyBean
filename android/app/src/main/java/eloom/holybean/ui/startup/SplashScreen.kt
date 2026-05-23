package eloom.holybean.ui.startup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay

@Composable
fun SplashRoute(
    onNavigateToHome: () -> Unit,
    vm: StartupViewModel = hiltViewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    // 데이터·프린터 모두 성공이면 잠깐 노출 후 자동 진입
    LaunchedEffect(state.autoEnter) {
        if (state.autoEnter) {
            delay(500)
            onNavigateToHome()
        }
    }
    SplashScreen(
        state = state,
        onRetry = vm::retry,
        onEnterAnyway = onNavigateToHome,
    )
}

@Composable
fun SplashScreen(
    state: StartupViewModel.UiState,
    onRetry: () -> Unit,
    onEnterAnyway: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("HolyBean", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(24.dp))

        StatusRow("데이터", state.data, loadingText = "데이터 불러오는 중…", successText = "데이터 준비 완료")
        StatusRow("프린터", state.printer, loadingText = "프린터 연결 확인 중…", successText = "프린터 연결됨")

        Spacer(Modifier.height(24.dp))

        when {
            // 데이터 실패 → 진입 차단, 재시도만
            state.data == StepStatus.Failed -> {
                Text(
                    "데이터를 불러오지 못했습니다",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "인터넷 연결 상태를 확인한 뒤 다시 시도해 주세요.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = onRetry) { Text("다시 시도") }
            }
            // 데이터 성공 + 프린터 실패 → 경고 후 진입 허용
            state.data == StepStatus.Success && state.printer == StepStatus.Failed -> {
                Text(
                    "프린터에 연결할 수 없습니다",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "영수증이 출력되지 않을 수 있습니다. 프린터 전원과 와이파이 연결을 확인해 주세요. 이대로도 주문은 가능합니다.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onRetry) { Text("다시 시도") }
                    Button(onClick = onEnterAnyway) { Text("그대로 진입") }
                }
            }
            // 그 외(진행 중 또는 모두 성공 직전): 스피너
            else -> CircularProgressIndicator()
        }
    }
}

@Composable
private fun StatusRow(
    label: String,
    status: StepStatus,
    loadingText: String,
    successText: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 6.dp),
    ) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(
                    when (status) {
                        StepStatus.Success -> Color(0xFF22C55E)
                        StepStatus.Failed -> Color(0xFFEF4444)
                        StepStatus.Loading -> Color(0xFFBBBBBB)
                    },
                ),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = when (status) {
                StepStatus.Loading -> loadingText
                StepStatus.Success -> successText
                StepStatus.Failed -> "$label 실패"
            },
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
