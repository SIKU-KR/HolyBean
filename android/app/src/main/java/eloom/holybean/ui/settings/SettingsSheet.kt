package eloom.holybean.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eloom.holybean.ui.theme.Dimens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    onDismiss: () -> Unit,
    onMenuMgmt: () -> Unit,
    onCredits: () -> Unit,
    onReport: () -> Unit,
    onDevTools: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Text("설정", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
            SettingsRow("메뉴 관리 (비밀번호)", onMenuMgmt)
            SettingsRow("외상 관리", onCredits)
            SettingsRow("기간 매출 리포트", onReport)
            SettingsRow("개발자 도구", onDevTools)
        }
    }
}

@Composable
private fun SettingsRow(label: String, onClick: () -> Unit) {
    Text(label, style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.minTouchTarget)
            .clickable(onClick = onClick).padding(16.dp))
}
