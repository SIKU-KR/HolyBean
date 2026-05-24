package eloom.holybean.ui.startup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import eloom.holybean.ui.components.buttons.PrimaryButton
import eloom.holybean.ui.components.buttons.SecondaryButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eloom.holybean.ui.components.layout.StatusDot
import eloom.holybean.ui.theme.Dimens
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
        modifier = Modifier.fillMaxSize().padding(Dimens.spaceXl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("HolyBean", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(Dimens.sectionGap))

        StatusRow("데이터", state.data, loadingText = "데이터 불러오는 중…", successText = "데이터 준비 완료")
        StatusRow("프린터", state.printer, loadingText = "프린터 연결 확인 중…", successText = "프린터 연결됨")

        Spacer(Modifier.height(Dimens.sectionGap))

        when {
            // 데이터 실패 → 진입 차단, 재시도만
            state.data == StepStatus.Failed -> {
                Text(
                    "데이터를 불러오지 못했습니다",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(Dimens.spaceSm))
                Text(
                    "인터넷 연결 상태를 확인한 뒤 다시 시도해 주세요.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(Dimens.spaceMd))
                PrimaryButton("다시 시도", onClick = onRetry)
            }
            // 데이터 성공 + 프린터 실패 → 경고 후 진입 허용
            state.data == StepStatus.Success && state.printer == StepStatus.Failed -> {
                Text(
                    "프린터에 연결할 수 없습니다",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(Dimens.spaceSm))
                Text(
                    "영수증이 출력되지 않을 수 있습니다. 프린터 전원과 와이파이 연결을 확인해 주세요. 이대로도 주문은 가능합니다.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(Dimens.spaceMd))
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceMd)) {
                    SecondaryButton("다시 시도", onClick = onRetry)
                    PrimaryButton("그대로 진입", onClick = onEnterAnyway)
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
        modifier = Modifier.padding(vertical = Dimens.spaceSm),
    ) {
        StatusDot(
            when (status) {
                StepStatus.Success -> true
                StepStatus.Failed -> false
                StepStatus.Loading -> null
            },
        )
        Spacer(Modifier.width(Dimens.spaceSm))
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
